import os
import re
import logging
import json
import asyncio
from typing import Optional, Dict, Any, List, Annotated, Sequence, TypedDict
from datetime import datetime

from langchain_core.messages import BaseMessage, HumanMessage, AIMessage, SystemMessage
from langchain_core.prompts import ChatPromptTemplate, MessagesPlaceholder
from langchain_openai import ChatOpenAI
from langchain_community.chat_message_histories import RedisChatMessageHistory
from langchain_core.runnables import RunnableConfig

from langgraph.graph import StateGraph, END
from langgraph.prebuilt import create_react_agent
from langchain_core.tools import Tool

from services.user_persona import UserPersona
from services.openrouter_service import OpenRouterService
from services.pky_ai_mcp import create_pky_mcp_server
from services.document_automation_service import DocumentAutomationService
from services.internal_comms_service import InternalCommsService
from services.browser_service import BrowserService
from redis_client import redis_client

logger = logging.getLogger(__name__)

# --- LangGraph State Definition ---

class AgentState(TypedDict):
    messages: Annotated[Sequence[BaseMessage], "The messages in the conversation"]
    next: str
    user_id: str
    active_role: str # Phase 7: Dynamic Role

# --- LLM Service Implementation ---

class LLMService:
    def __init__(self):
        self.use_local = os.getenv("USE_LOCAL_LLM", "false").lower() == "true"
        self.openrouter = OpenRouterService()
        self.user_personas: Dict[str, UserPersona] = {}
        
        # Local model state
        self.local_model = None
        self.model_path = os.getenv("LOCAL_MODEL_PATH", "models/smollm-135m-instruct.Q4_K_M.gguf")
        
        # Injected services
        self.task_planner = None
        self.news_service = None
        self.document_service = None
        self.job_service = None
        self.email_service = None

        # MCP and LangGraph state
        self.mcp_server = None
        self.graph = None

        if self.use_local:
            self._init_local_model()

    async def set_services(self, task_planner=None, news_service=None, document_service=None, job_service=None, email_service=None, browser_service=None):
        self.task_planner = task_planner
        self.news_service = news_service
        self.document_service = document_service
        self.job_service = job_service
        self.email_service = email_service
        self.doc_automation = DocumentAutomationService()
        self.internal_comms = InternalCommsService()
        self.browser_service = browser_service if browser_service else BrowserService()
        
        # Initialize MCP Server
        self.mcp_server = create_pky_mcp_server(
            news_service=self.news_service,
            task_planner=self.task_planner,
            document_service=self.document_service,
            email_service=self.email_service,
            doc_automation=self.doc_automation,
            internal_comms=self.internal_comms,
            browser_service=self.browser_service
        )
        
        await self._init_langgraph_supervisor()

    async def _init_langgraph_supervisor(self):
        """Initializes the LangGraph Supervisor with specialized sub-agents."""
        if self.use_local or not self.mcp_server:
            return

        api_key = os.getenv("OPENROUTER_API_KEY")
        if not api_key:
            logger.warning("OPENROUTER_API_KEY is not set. Skipping LangGraph initialization.")
            return

        # 1. Setup LLM (OpenRouter)
        llm = ChatOpenAI(
            model="google/gemini-2.0-flash-exp:free",
            openai_api_key=api_key,
            openai_api_base="https://openrouter.ai/api/v1",
            temperature=0.7
        )

        # 2. Categorize MCP Tools (M-3: use getattr to avoid crashing on private API changes)
        tools_map = {}
        tool_manager = getattr(self.mcp_server, '_tool_manager', None)
        raw_tools = getattr(tool_manager, '_tools', {}) if tool_manager else {}
        for tool_name, tool_obj in raw_tools.items():
            description = getattr(tool_obj, 'description', None) or f"MCP Tool: {tool_name}"
            fn = getattr(tool_obj, 'fn', None)
            if fn:
                tools_map[tool_name] = Tool(name=tool_name, func=fn, description=description)
        if not tools_map:
            logger.warning("No MCP tools loaded — LangGraph agents will have no tools.")

        research_tools = [tools_map[t] for t in ["pky_search_web", "pky_read_documents", "pky_automate_web"] if t in tools_map]
        planner_tools = [tools_map[t] for t in ["pky_manage_tasks", "pky_send_email", "pky_get_oauth_status"] if t in tools_map]
        creative_tools = [tools_map[t] for t in ["pky_generate_professional_doc", "pky_parse_any_doc", "pky_manage_internal_comms"] if t in tools_map]

        # 3. Create Sub-Agents
        researcher_agent = create_react_agent(llm, research_tools, prompt="You are the Researcher sub-agent. Focus on finding information from the web or internal documents.")
        planner_agent = create_react_agent(llm, planner_tools, prompt="You are the Planner sub-agent. Focus on managing tasks, emails, and calendar events.")
        creative_agent = create_react_agent(llm, creative_tools, prompt="You are the Creative sub-agent. Focus on generating reports, presentations, and internal communications.")

        # 4. Define Supervisor Node
        members = ["Researcher", "Planner", "Creative"]
        
        async def supervisor_node(state: AgentState):
            messages = state["messages"]
            active_role = state.get("active_role", "Assistant")
            
            role_description = f"Your current primary role is: {active_role}. "
            if active_role == "Researcher":
                role_description += "Prioritize deep web searches and document analysis."
            elif active_role == "Planner":
                role_description += "Prioritize task organization, email management, and scheduling."
            elif active_role == "Creative":
                role_description += "Prioritize drafting high-quality documents and internal comms."
            
            system_prompt = (
                f"You are the Supervisor for PKY AI Assistant. {role_description} "
                "Your job is to route the user's request to the correct sub-agent. "
                "Researcher: For web search, document search, or web automation. "
                "Planner: For tasks, emails, and scheduling. "
                "Creative: For generating Word/PDF/PPT reports or internal comms updates. "
                "If the request is fulfilled or you can answer directly, respond with FINISH."
            )

            options = ["FINISH"] + members
            prompt = ChatPromptTemplate.from_messages([
                ("system", system_prompt),
                MessagesPlaceholder(variable_name="messages"),
                ("system", f"Given the conversation above, who should act next? Or should we FINISH? Select one of: {options}"),
            ])
            chain = prompt | llm
            # Now we can safely use ainvoke since supervisor_node is an async function (LangGraph supports async nodes)
            response = await chain.ainvoke({"messages": messages})
            
            text = response.content.upper()
            next_agent = "FINISH"
            for m in members:
                if m.upper() in text:
                    next_agent = m
                    break
            
            return {"next": next_agent}

        # 5. Build Graph
        builder = StateGraph(AgentState)
        builder.add_node("Supervisor", supervisor_node)
        builder.add_node("Researcher", lambda state: researcher_agent.invoke(state))
        builder.add_node("Planner", lambda state: planner_agent.invoke(state))
        builder.add_node("Creative", lambda state: creative_agent.invoke(state))

        for member in members:
            builder.add_edge(member, "Supervisor")

        conditional_map = {m: m for m in members}
        conditional_map["FINISH"] = END
        builder.add_conditional_edges("Supervisor", lambda x: x["next"], conditional_map)

        builder.set_entry_point("Supervisor")
        
        # Integrate Checkpointer for Stateful Graph
        from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver
        from psycopg_pool import AsyncConnectionPool
        
        # Use existing DATABASE_URL for checkpointer
        db_url = os.getenv("DATABASE_URL", "postgresql+asyncpg://pky_ai_user:pky_ai_password@postgres:5432/pky_ai")
        # AsyncPostgresSaver expects a standard postgres URL or a pool.
        pg_url = db_url.replace("+asyncpg", "")
        
        try:
            if not getattr(self, '_pg_pool_created', False):
                self._pool = AsyncConnectionPool(conninfo=pg_url, max_size=10)
                await self._pool.open()
                self._pg_pool_created = True

            self.memory = AsyncPostgresSaver(self._pool)
            await self.memory.setup()
            self.graph = builder.compile(checkpointer=self.memory)
            self.using_in_memory_checkpointer = False
            logger.info("LangGraph configured with AsyncPostgresSaver.")
        except Exception as e:
            logger.error(f"Failed to setup PostgresSaver: {e}. Conversation state will be lost on restart.")
            from langgraph.checkpoint.memory import MemorySaver
            self.memory = MemorySaver()
            self.graph = builder.compile(checkpointer=self.memory)
            self.using_in_memory_checkpointer = True
            logger.warning("AsyncPostgresSaver failed. Falling back to MemorySaver (volatile).")

    def get_user_persona(self, user_id: str) -> UserPersona:
        if user_id not in self.user_personas:
            self.user_personas[user_id] = UserPersona(user_id)
        return self.user_personas[user_id]

    async def generate(self, prompt: str, user_id: str = "default", brain_type: str = "general") -> str:
        """Generates a response using the LangGraph Supervisor or Local LLM.
        
        Args:
            prompt: User message
            user_id: User identifier for state
            brain_type: Which 'brain' config to use (general, build, plan, adaptive, extended)
        """
        persona = self.get_user_persona(user_id)
        persona_data = await persona.get_persona()
        active_role = persona_data.get("active_role", "Assistant")
        
        # Apply corrections
        corrections = persona_data.get("corrections", [])
        for correction in corrections:
            if correction["original"].lower() in prompt.lower():
                pattern = re.compile(re.escape(correction["original"]), re.IGNORECASE)
                prompt = pattern.sub(correction["corrected"], prompt)

        # 1. Local Mode Check
        if self.use_local:
            return await self._generate_local(prompt)

        # 2. Redis History
        redis_url = os.getenv("REDIS_URL", "redis://localhost:6379/0")
        message_history = RedisChatMessageHistory(url=redis_url, session_id=user_id)
        past_messages = message_history.messages[-10:]

        # 3. Graph Execution
        if not self.graph:
            if not os.getenv("OPENROUTER_API_KEY"):
                return "Sorry, I am unable to respond because the OPENROUTER_API_KEY is missing in the backend configuration."
            return await self.openrouter.generate_response(prompt, brain_type=brain_type)

        try:
            initial_state = {
                "messages": past_messages + [HumanMessage(content=prompt)],
                "user_id": user_id,
                "active_role": active_role # Pass the role to the graph state
            }
            
            # Pass thread_id configuration for checkpointer
            config = {"configurable": {"thread_id": user_id}}
            # M-8: Add timeout to prevent indefinite hang on LangGraph execution
            result = await asyncio.wait_for(
                self.graph.ainvoke(initial_state, config=config),
                timeout=90.0
            )

            final_message = result["messages"][-1]
            final_output = final_message.content

            # M-9: Save to Redis history with error handling and non-blocking thread
            try:
                await asyncio.to_thread(message_history.add_user_message, prompt)
                await asyncio.to_thread(message_history.add_ai_message, final_output)
            except Exception as history_err:
                logger.warning(f"Failed to persist conversation history for {user_id}: {history_err}")
            
            await persona.add_learning("conversation", prompt)
            return final_output
            
        except Exception as e:
            logger.error(f"LangGraph execution failed: {e}")
            if not os.getenv("OPENROUTER_API_KEY"):
                return "Sorry, I am unable to respond because the OPENROUTER_API_KEY is missing in the backend configuration."
            return await self.openrouter.generate_response(prompt, brain_type=brain_type)

    async def _generate_local(self, prompt: str) -> str:
        """Local GGUF generation."""
        if not self.local_model:
            self._init_local_model()
        if not self.local_model:
            return "Error: Local model unavailable."
        
        try:
            # Wrap blocking local model inference in a thread
            output = await asyncio.to_thread(
                self.local_model, 
                f"User: {prompt}\nAssistant:", 
                max_tokens=256, 
                stop=["User:"], 
                echo=False
            )
            return output["choices"][0]["text"].strip()
        except Exception as e:
            logger.error(f"Local LLM failed: {e}")
            return "Error: Local processing failed."

    def _init_local_model(self):
        try:
            from llama_cpp import Llama
            self.local_model = Llama(model_path=self.model_path, n_ctx=512, n_threads=4)
        except Exception as e:
            logger.error(f"Failed to init local LLM: {e}")

    async def handle_correction(self, user_id: str, original: str, corrected: str, context: str = ""):
        persona = self.get_user_persona(user_id)
        await persona.add_correction(original, corrected, context)
