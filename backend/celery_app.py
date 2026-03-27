import os
from celery import Celery
from celery.schedules import crontab

REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379/0")

celery_app = Celery(
    "pky_ai_tasks",
    broker=REDIS_URL,
    backend=REDIS_URL
)

celery_app.conf.update(
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="Asia/Kolkata",
    enable_utc=True,
    beat_schedule={
        "check-breaking-news-every-15-mins": {
            "task": "tasks.monitor_breaking_news",
            "schedule": 900.0,
        },
        "check-upcoming-tasks-every-5-mins": {
            "task": "tasks.check_upcoming_tasks",
            "schedule": 300.0,
        },
        "pre-meeting-briefing-every-10-mins": {
            "task": "tasks.pre_meeting_briefing",
            "schedule": 600.0,
        },
        "generate-daily-summary-at-6am": {
            "task": "tasks.generate_daily_summary",
            "schedule": crontab(hour=6, minute=0),
        },
    }
)

# Auto-discover tasks
import tasks
