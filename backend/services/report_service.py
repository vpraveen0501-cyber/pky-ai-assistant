import datetime
from typing import List, Dict, Any

class ReportService:
    def __init__(self, news_service, task_planner):
        self.news_service = news_service
        self.task_planner = task_planner

    async def generate_daily_report(self) -> Dict[str, Any]:
        """
        Generates a daily report summarizing news, tasks, and insights.
        """
        # 1. Fetch News Digest
        news_digest = await self.news_service.get_daily_digest()
        
        # 2. Fetch Tasks (Pending and Completed)
        tasks = await self.task_planner.get_tasks()
        pending_tasks = [t for t in tasks if t.get('status') == 'pending']
        
        # 3. Generate Summary
        date_str = datetime.datetime.now().strftime("%Y-%m-%d")
        
        # Simple analysis of news (keyword extraction simulation)
        keywords = set()
        for item in news_digest.get('items', []):
            # Extract potential keywords (simple split and filter)
            words = item.get('title', '').lower().split()
            keywords.update([w for w in words if len(w) > 4])
        
        report = {
            "date": date_str,
            "news_summary": {
                "total_items": len(news_digest.get('items', [])),
                "top_keywords": list(keywords)[:10], # Top 10 unique keywords
                "items": news_digest.get('items', [])[:5] # Top 5 items
            },
            "tasks_summary": {
                "total_tasks": len(tasks),
                "pending_tasks": len(pending_tasks),
                "upcoming_tasks": pending_tasks[:3] # Next 3 pending tasks
            },
            "insights": f"Today is {date_str}. You have {len(pending_tasks)} pending tasks. " \
                        f"Top news topics include: {', '.join(list(keywords)[:3])}."
        }
        
        return report