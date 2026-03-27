import asyncio
import json
import logging
from typing import Any, Dict

from services.pky_ai_mcp import create_pky_mcp_server
from services.news_service import NewsService
from services.task_planner import TaskPlanner
from services.document_service import DocumentService
from services.email_service import EmailService
from services.document_automation_service import DocumentAutomationService
from services.internal_comms_service import InternalCommsService
from services.browser_service import BrowserService

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("MCP-Verification")

async def verify_mcp():
    logger.info("Initializing services for MCP verification...")
    
    # Initialize mock/real services
    news_service = NewsService()
    task_planner = TaskPlanner()
    document_service = DocumentService()
    email_service = EmailService()
    doc_automation = DocumentAutomationService()
    internal_comms = InternalCommsService()
    browser_service = BrowserService()
    
    logger.info("Creating MCP Server...")
    mcp = create_pky_mcp_server(
        news_service=news_service,
        task_planner=task_planner,
        document_service=document_service,
        email_service=email_service,
        doc_automation=doc_automation,
        internal_comms=internal_comms,
        browser_service=browser_service
    )
    
    tools = mcp._tools
    logger.info(f"Discovered {len(tools)} tools in MCP Server.")
    
    expected_tools = [
        "mgs_search_web",
        "mgs_manage_tasks",
        "mgs_read_documents",
        "mgs_send_email",
        "mgs_generate_professional_doc",
        "mgs_parse_any_doc",
        "mgs_manage_internal_comms",
        "mgs_automate_web"
    ]
    
    results = {
        "passed": [],
        "failed": [],
        "missing": []
    }
    
    for tool_name in expected_tools:
        if tool_name in tools:
            tool_func = tools[tool_name]
            logger.info(f"Verifying tool: {tool_name}")
            
            # Verify if it has a docstring (description)
            if tool_func.__doc__:
                logger.info(f"  [✓] Description: {tool_func.__doc__.strip()}")
                results["passed"].append(tool_name)
            else:
                logger.warning(f"  [✗] Tool {tool_name} is missing a description!")
                results["failed"].append(tool_name)
        else:
            logger.error(f"  [✗] Expected tool {tool_name} NOT FOUND in MCP server!")
            results["missing"].append(tool_name)
            
    logger.info("--- MCP Verification Summary ---")
    logger.info(f"Total Expected: {len(expected_tools)}")
    logger.info(f"Passed: {len(results['passed'])}")
    logger.info(f"Failed: {len(results['failed'])}")
    logger.info(f"Missing: {len(results['missing'])}")
    
    if len(results["missing"]) == 0 and len(results["failed"]) == 0:
        logger.info("MCP Reliability Check: SUCCESS")
    else:
        logger.error("MCP Reliability Check: FAILED")

if __name__ == "__main__":
    asyncio.run(verify_mcp())
