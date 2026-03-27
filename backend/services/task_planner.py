from typing import List, Dict, Any, Optional
from datetime import datetime, timedelta
from sqlalchemy.future import select
from database import AsyncSessionLocal
from models.task import Task, CalendarEvent

class TaskPlanner:
    def __init__(self):
        # We'll use AsyncSessionLocal directly for now, or it can be passed in
        pass

    async def create_task(self, task_data: Dict[str, Any], user_id: str = "default") -> Dict[str, Any]:
        async with AsyncSessionLocal() as session:
            due_date = task_data.get("due_date")
            if isinstance(due_date, str):
                due_date = datetime.fromisoformat(due_date.replace('Z', '+00:00'))
            
            task = Task(
                user_id=user_id,
                title=task_data.get("title"),
                description=task_data.get("description"),
                due_date=due_date,
                status=task_data.get("status", "pending"),
                priority=task_data.get("priority", "medium"),
                meta_data=task_data.get("meta_data", {})
            )
            session.add(task)
            await session.commit()
            await session.refresh(task)
            
            return {
                "id": task.id,
                "title": task.title,
                "description": task.description,
                "due_date": task.due_date.isoformat() if task.due_date else None,
                "status": task.status,
                "created_at": task.created_at.isoformat()
            }

    async def schedule_meeting(self, title: str, start_time: datetime, duration_minutes: int, attendees: List[str], user_id: str = "default"):
        async with AsyncSessionLocal() as session:
            event = CalendarEvent(
                user_id=user_id,
                title=title,
                start_time=start_time,
                end_time=start_time + timedelta(minutes=duration_minutes),
                attendees=attendees
            )
            session.add(event)
            await session.commit()
            await session.refresh(event)
            
            return {
                "id": event.id,
                "title": event.title,
                "start_time": event.start_time.isoformat(),
                "end_time": event.end_time.isoformat(),
                "attendees": event.attendees
            }

    async def get_tasks(self, user_id: str = "default") -> List[Dict[str, Any]]:
        async with AsyncSessionLocal() as session:
            stmt = select(Task).where(Task.user_id == user_id)
            result = await session.execute(stmt)
            tasks = result.scalars().all()
            
            return [
                {
                    "id": t.id,
                    "title": t.title,
                    "description": t.description,
                    "due_date": t.due_date.isoformat() if t.due_date else None,
                    "status": t.status,
                    "created_at": t.created_at.isoformat()
                } for t in tasks
            ]

    async def update_task_status(self, task_id: int, status: str, user_id: str = "default"):
        async with AsyncSessionLocal() as session:
            stmt = select(Task).where(Task.id == task_id, Task.user_id == user_id)
            result = await session.execute(stmt)
            task = result.scalar_one_or_none()
            if task:
                task.status = status
                await session.commit()
                return True
            return False
