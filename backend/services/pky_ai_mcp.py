import logging
import json
import os
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field, ConfigDict
from mcp.server.fastmcp import FastMCP

from services.news_service import NewsService
from services.task_planner import TaskPlanner
from services.document_service import DocumentService
from services.email_service import EmailService
from services.document_automation_service import DocumentAutomationService
from services.internal_comms_service import InternalCommsService
from services.browser_service import BrowserService
from services.mqtt_service import MQTTService
from services.oauth_service import OAuthService

logger = logging.getLogger(__name__)

# Pydantic Models for Input Validation
class SearchWebInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)
    query: str = Field(..., description="Search query for news or information")

class ManageTasksInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)
    title: str = Field(..., description="Task title")
    description: Optional[str] = Field(None, description="Detailed task description")
    due_date: Optional[str] = Field(None, description="Due date in ISO format (YYYY-MM-DD)")

class ReadDocumentsInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)
    query: str = Field(..., description="Search query to find information in documents")

class SendEmailInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)
    recipient: str = Field(..., description="Email address of the recipient")
    subject: str = Field(..., description="Subject of the email")
    body: str = Field(..., description="Content of the email")

class ProfessionalDocInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)
    type: str = Field(..., description="Document type: docx, xlsx, pptx, or pdf")
    title: str = Field(..., description="Main title of the document")
    content: Any = Field(..., description="Content structure for the document")

class ParseDocInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)
    file_path: str = Field(..., description="Path to the document file to parse")

class InternalCommsInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)
    type: str = Field(..., description="Comms type: 3p_update or slack_gif")
    data: Any = Field(..., description="Data for the communication")

class BrowserInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)
    action: str = Field(..., description="Action to perform: search, apply_job, or test_app")
    url: str = Field(..., description="URL of the website")
    params: Optional[Dict[str, Any]] = Field(default_factory=dict, description="Additional parameters for the action")

class SmartHomeInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)
    device_id: str = Field(..., description="ID of the smart home device")
    action: str = Field(..., description="Action to perform (on/off)")

class OAuthStatusInput(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True, validate_assignment=True)
    user_id: str = Field(..., description="User ID to check OAuth status for")

def create_pky_mcp_server(
    news_service: NewsService,
    task_planner: TaskPlanner,
    document_service: DocumentService,
    email_service: EmailService,
    doc_automation: Optional[DocumentAutomationService] = None,
    internal_comms: Optional[InternalCommsService] = None,
    browser_service: Optional[BrowserService] = None,
    mqtt_service: Optional[MQTTService] = None,
    oauth_service: Optional[OAuthService] = None
) -> FastMCP:
    """Creates and configures the PKY AI Assistant MCP server with advanced tools."""

    mcp = FastMCP("pky_ai_mcp")

    # --- Core Tools (Phase 2) ---

    @mcp.tool(name="pky_search_web")
    async def pky_search_web(params: SearchWebInput) -> str:
        """Searches the internet for news and general information."""
        try:
            results = await news_service.deep_research(params.query)
            return json.dumps(results, indent=2)
        except Exception as e:
            logger.error(f"Search tool failed: {e}")
            return f"Error: Web search failed: {str(e)}"

    @mcp.tool(name="pky_manage_tasks")
    async def pky_manage_tasks(params: ManageTasksInput) -> str:
        """Creates or schedules a new task for the user."""
        try:
            task_data = {
                "title": params.title,
                "description": params.description,
                "due_date": params.due_date
            }
            result = await task_planner.create_task(task_data)
            return json.dumps(result, indent=2)
        except Exception as e:
            logger.error(f"Task management tool failed: {e}")
            return f"Error: Failed to create task: {str(e)}"

    @mcp.tool(name="pky_read_documents")
    async def pky_read_documents(params: ReadDocumentsInput) -> str:
        """Searches through the user's private documents and notes."""
        try:
            results = await document_service.search_documents(params.query)
            return json.dumps(results, indent=2)
        except Exception as e:
            logger.error(f"Document search tool failed: {e}")
            return f"Error: Document search failed: {str(e)}"

    @mcp.tool(name="pky_send_email")
    async def pky_send_email(params: SendEmailInput) -> str:
        """Drafts and sends an email to a contact."""
        try:
            draft_result = await email_service.draft_email("default", params.recipient, params.subject, params.body)
            if draft_result.get("status") == "success":
                draft_id = draft_result["draft"]["id"]
                result = await email_service.send_email("default", draft_id)
                return json.dumps(result, indent=2)
            return json.dumps(draft_result, indent=2)
        except Exception as e:
            logger.error(f"Email tool failed: {e}")
            return f"Error: Failed to send email: {str(e)}"

    # --- Advanced Tools (Phase 3: Doc & Media Automation) ---

    if doc_automation:
        @mcp.tool(name="pky_generate_professional_doc")
        async def pky_generate_professional_doc(params: ProfessionalDocInput) -> str:
            """Generates Word reports, Excel models, PowerPoint pitch decks, and PDFs."""
            try:
                if params.type == "docx":
                    path = await doc_automation.create_word_report(params.title, params.content)
                elif params.type == "xlsx":
                    path = await doc_automation.create_excel_model(params.title, params.content)
                elif params.type == "pptx":
                    path = await doc_automation.create_presentation(params.title, params.content)
                elif params.type == "pdf":
                    path = await doc_automation.create_pdf_report(params.title, params.content)
                else:
                    return f"Error: Unsupported document type: {params.type}"
                return f"Document generated successfully: {path}"
            except Exception as e:
                logger.error(f"Doc generation tool failed: {e}")
                return f"Error: Failed to generate document: {str(e)}"

        @mcp.tool(name="pky_parse_any_doc")
        async def pky_parse_any_doc(params: ParseDocInput) -> str:
            """Parses and extracts text from PDF, Docx, Pptx, and Xlsx files."""
            try:
                content = await doc_automation.parse_document(params.file_path)
                return content
            except Exception as e:
                logger.error(f"Doc parsing tool failed: {e}")
                return f"Error: Failed to parse document: {str(e)}"

    if internal_comms:
        @mcp.tool(name="pky_manage_internal_comms")
        async def pky_manage_internal_comms(params: InternalCommsInput) -> str:
            """Automates team 3P updates, company newsletters, and Slack GIF creation."""
            try:
                if params.type == "3p_update":
                    result = await internal_comms.generate_3p_update(
                        params.data.get("progress", []),
                        params.data.get("plans", []),
                        params.data.get("problems", [])
                    )
                    return f"3P Update generated:\n{result}"
                elif params.type == "slack_gif":
                    path = await internal_comms.create_slack_gif(params.data.get("text", "PKY AI Assistant"))
                    return f"Slack GIF created: {path}"
                else:
                    return f"Error: Unsupported comms type: {params.type}"
            except Exception as e:
                logger.error(f"Internal comms tool failed: {e}")
                return f"Error: Failed to create comms: {str(e)}"

    if browser_service:
        @mcp.tool(name="pky_automate_web")
        async def pky_automate_web(params: BrowserInput) -> str:
            """Handles complex web interactions, tests apps, and automates job applications."""
            try:
                if params.action == "search":
                    result = await browser_service.search_and_extract(params.url, params.params.get("query", ""))
                elif params.action == "apply_job":
                    result = await browser_service.automate_job_application(
                        params.url, 
                        params.params.get("resume_path", ""), 
                        params.params.get("user_info", {})
                    )
                elif params.action == "test_app":
                    result = await browser_service.test_webapp(params.url, params.params.get("scenario", ""))
                else:
                    return f"Error: Unsupported browser action: {params.action}"
                return json.dumps(result, indent=2)
            except Exception as e:
                logger.error(f"Web automation tool failed: {e}")
                return f"Error: Failed to automate web: {str(e)}"

    if mqtt_service:
        @mcp.tool(name="pky_control_smart_home")
        async def pky_control_smart_home(params: SmartHomeInput) -> str:
            """Controls Smart Home IoT devices (lights, switches, etc.) via MQTT."""
            try:
                mqtt_service.control_device(params.device_id, params.action)
                return f"Sent {params.action} command to device {params.device_id}"
            except Exception as e:
                logger.error(f"Smart home tool failed: {e}")
                return f"Error: Failed to control device: {str(e)}"

    if oauth_service:
        @mcp.tool(name="pky_get_oauth_status")
        async def pky_get_oauth_status(params: OAuthStatusInput) -> str:
            """Checks the authentication status for external services (Google, Microsoft)."""
            try:
                status = await oauth_service.get_token(params.user_id)
                if status:
                    return f"Authenticated as {status.get('email')} for {status.get('provider')}"
                return "Not authenticated. Please link your account."
            except Exception as e:
                logger.error(f"OAuth status tool failed: {e}")
                return f"Error: Failed to get OAuth status: {str(e)}"

    return mcp
