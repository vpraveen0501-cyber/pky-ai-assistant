import logging
import json
import asyncio
import datetime
from sqlalchemy.future import select
from celery_app import celery_app
from services.news_service import NewsService
from services.report_service import ReportService
from services.task_planner import TaskPlanner
from redis_client import redis_client
from database import AsyncSessionLocal
from models.task import Task

logger = logging.getLogger(__name__)

def run_async(coro):
    """Helper to run async code in sync celery tasks."""
    loop = asyncio.get_event_loop()
    if loop.is_running():
        return asyncio.ensure_future(coro)
    return loop.run_until_complete(coro)

@celery_app.task(name="tasks.monitor_breaking_news")
def monitor_breaking_news():
    """Proactive background AI: Monitor news and push WebSocket alerts."""
    async def _async_task():
        logger.info("Background task: Checking for breaking news...")
        news_service = NewsService()
        breaking_news = await news_service.fetch_feeds()
        
        if breaking_news:
            alert_data = {
                "type": "breaking_news",
                "content": breaking_news[0],
                "timestamp": datetime.datetime.now().isoformat()
            }
            await redis_client.connect()
            await redis_client.set("latest_pky_alert", alert_data)
            logger.info(f"PROACTIVE ALERT: {alert_data['content']['title']}")
            return f"Proactive alert pushed: {alert_data['content']['title']}"
        return "No breaking news found."
    
    return run_async(_async_task())

@celery_app.task(name="tasks.check_upcoming_tasks")
def check_upcoming_tasks():
    """Phase 6: Proactive Intelligence Engine - Check for tasks due soon."""
    async def _async_task():
        logger.info("Background task: Checking for upcoming tasks...")
        now = datetime.datetime.utcnow()
        one_hour_later = now + datetime.timedelta(hours=1)
        
        async with AsyncSessionLocal() as session:
            # Query for pending tasks due in the next hour
            stmt = select(Task).where(
                Task.status == "pending",
                Task.due_date >= now,
                Task.due_date <= one_hour_later
            )
            result = await session.execute(stmt)
            tasks = result.scalars().all()
            
            if tasks:
                await redis_client.connect()
                for task in tasks:
                    alert_data = {
                        "type": "task_reminder",
                        "content": {
                            "id": task.id,
                            "title": task.title,
                            "due_date": task.due_date.isoformat()
                        },
                        "user_id": task.user_id,
                        "timestamp": datetime.datetime.now().isoformat()
                    }
                    # Set a per-user alert or a global latest alert
                    await redis_client.set(f"alert_user_{task.user_id}", alert_data)
                    logger.info(f"TASK ALERT for {task.user_id}: {task.title}")
                
                return f"Alerts generated for {len(tasks)} tasks."
        return "No upcoming tasks found."

    return run_async(_async_task())

@celery_app.task(name="tasks.pre_meeting_briefing")
def pre_meeting_briefing():
    """Predictive Scheduling: Gather docs automatically before a meeting."""
    async def _async_task():
        logger.info("Background task: Generating pre-meeting briefings...")
        now = datetime.datetime.utcnow()
        soon = now + datetime.timedelta(minutes=30)
        
        async with AsyncSessionLocal() as session:
            stmt = select(Task).where(
                Task.status == "pending",
                Task.due_date >= now,
                Task.due_date <= soon
            )
            result = await session.execute(stmt)
            meetings = result.scalars().all()
            
            for meeting in meetings:
                if "meeting" in meeting.title.lower():
                    # Generate brief
                    alert_data = {
                        "type": "meeting_briefing",
                        "content": f"Briefing for {meeting.title}: Review latest emails and documents.",
                        "timestamp": datetime.datetime.now().isoformat()
                    }
                    await redis_client.connect()
                    await redis_client.set(f"alert_user_{meeting.user_id}", alert_data)
                    logger.info(f"PRE-MEETING BRIEFING for {meeting.user_id}: {meeting.title}")
                    
        return "Pre-meeting briefings checked."
        
    return run_async(_async_task())

@celery_app.task(name="tasks.generate_daily_summary")
def generate_daily_summary():
    """Generate the daily morning summary for the user."""
    async def _async_task():
        logger.info("Background task: Generating daily summary...")
        news_service = NewsService()
        task_planner = TaskPlanner()
        report_service = ReportService(news_service, task_planner)
        report = await report_service.generate_daily_report()
        
        # Cache report in Redis for quick retrieval
        await redis_client.connect()
        await redis_client.set("daily_report_default", report)
        return "Daily report generated and cached."

    return run_async(_async_task())
