import os
import json
import logging
from typing import Dict, Any, List
from sqlalchemy.future import select
from models.user import UserPersona as UserPersonaModel
from database import AsyncSessionLocal
from services.memory_service import MemoryService

logger = logging.getLogger(__name__)

class UserPersona:
    def __init__(self, user_id: str, data_dir: str = "data"):
        self.user_id = user_id
        self.persona_data = {
            "active_role": "Assistant",
            "preferences": {},
            "learning_history": [],
            "corrections": [],
            "job_search_status": {}
        }
        self.memory_service = MemoryService()

    async def _load_persona(self):
        async with AsyncSessionLocal() as session:
            stmt = select(UserPersonaModel).where(UserPersonaModel.user_id == self.user_id)
            result = await session.execute(stmt)
            user_model = result.scalar_one_or_none()
            if user_model:
                self.persona_data = {
                    "active_role": user_model.active_role or "Assistant",
                    "preferences": user_model.preferences,
                    "learning_history": user_model.learning_history,
                    "corrections": user_model.corrections,
                    "job_search_status": user_model.job_search_status
                }
            else:
                new_user = UserPersonaModel(
                    user_id=self.user_id,
                    active_role="Assistant",
                    preferences={},
                    learning_history=[],
                    corrections=[],
                    job_search_status={}
                )
                session.add(new_user)
                await session.commit()

    async def save(self):
        async with AsyncSessionLocal() as session:
            stmt = select(UserPersonaModel).where(UserPersonaModel.user_id == self.user_id)
            result = await session.execute(stmt)
            user_model = result.scalar_one_or_none()
            if user_model:
                user_model.active_role = self.persona_data["active_role"]
                user_model.preferences = self.persona_data["preferences"]
                user_model.learning_history = self.persona_data["learning_history"]
                user_model.corrections = self.persona_data["corrections"]
                user_model.job_search_status = self.persona_data["job_search_status"]
                await session.commit()

    async def set_active_role(self, role: str):
        """Phase 7: Role-Based Persona Engine - Switch the dynamic role."""
        valid_roles = ["Assistant", "Researcher", "Planner", "Creative", "Coder"]
        if role in valid_roles:
            await self._load_persona()
            self.persona_data["active_role"] = role
            await self.save()
            logger.info(f"User {self.user_id} switched role to {role}")
            return True
        return False

    async def update_preferences(self, preferences: Dict[str, Any]):
        await self._load_persona()
        self.persona_data["preferences"].update(preferences)
        await self.save()
        
        # Add to vector memory for semantic search
        if "name" in preferences:
             await self.memory_service.add_vector_memory(
                 self.user_id, 
                 f"User's preferred name is {preferences['name']}", 
                 {"type": "preference"}
             )

    async def add_learning(self, topic: str, summary: str):
        await self._load_persona()
        self.persona_data["learning_history"].append({
            "topic": topic,
            "summary": summary,
            "timestamp": "2026-03-20"
        })
        await self.save()
        
        # Phase 10: Store in Vector Store
        await self.memory_service.add_vector_memory(
            self.user_id, 
            f"Topic: {topic}. Summary: {summary}", 
            {"type": "learning", "topic": topic}
        )
        
        # Phase 10: Store in Knowledge Graph
        await self.memory_service.add_graph_relation(
            self.user_id, 
            "User", 
            "KNOWS", 
            topic, 
            {"summary": summary}
        )

    async def add_correction(self, original: str, corrected: str, context: str):
        await self._load_persona()
        self.persona_data["corrections"].append({
            "original": original,
            "corrected": corrected,
            "context": context,
            "timestamp": "2026-03-20"
        })
        await self.save()

    async def update_job_search(self, status: Dict[str, Any]):
        await self._load_persona()
        self.persona_data["job_search_status"].update(status)
        await self.save()

    async def get_persona(self) -> Dict[str, Any]:
        await self._load_persona()
        return self.persona_data

    async def get_relevant_memories(self, query: str) -> List[str]:
        """Phase 10: Hybrid recall."""
        return await self.memory_service.search_vector_memory(self.user_id, query)
