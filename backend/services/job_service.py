import os
import datetime
import logging
from typing import List, Dict, Any, Optional
from sqlalchemy.future import select
from database import AsyncSessionLocal
from models.job import JobApplication, UpskillingPath
from config.settings import settings

logger = logging.getLogger(__name__)

_JOB_SERVICE_UNAVAILABLE = {
    "status": "unavailable",
    "message": "Job service is not enabled. Set PKY_AI_ENABLE_JOB_SERVICE=true in .env when browser automation is ready."
}

class JobService:
    def __init__(self, data_dir: str = "data/jobs"):
        self.data_dir = data_dir
        os.makedirs(data_dir, exist_ok=True)
        self.browser = None # Injected via main.py

    async def search_jobs(self, user_id: str, keywords: str, location: str = "remote") -> List[Dict[str, Any]]:
        """
        Performs an automated job search on LinkedIn and Indeed using BrowserService.
        """
        if not settings.enable_job_service:
            logger.info("Job service is disabled. Returning unavailable response.")
            return [_JOB_SERVICE_UNAVAILABLE]

        if not self.browser:
            logger.warning("BrowserService not available for job search.")
            return []
            
        logger.info(f"Live searching jobs for {user_id} on LinkedIn/Indeed...")
        
        linkedin_jobs = await self.browser.get_job_postings("linkedin", keywords)
        indeed_jobs = await self.browser.get_job_postings("indeed", keywords)
        
        return linkedin_jobs + indeed_jobs

    async def apply_to_job(self, user_id: str, job_title: str, company: str) -> Dict[str, Any]:
        """
        Simulates an automated job application process.
        """
        logger.info(f"Applying for {job_title} at {company} for user {user_id}...")
        async with AsyncSessionLocal() as session:
            application = JobApplication(
                user_id=user_id,
                job_title=job_title,
                company=company,
                status="applied"
            )
            session.add(application)
            await session.commit()
            await session.refresh(application)
            
            app_dict = {
                "id": application.id,
                "user_id": application.user_id,
                "job_title": application.job_title,
                "company": application.company,
                "status": application.status,
                "applied_at": application.applied_at.isoformat()
            }
            return {"status": "success", "application": app_dict}

    async def get_upskilling_path(self, user_id: str, current_skills: List[str]) -> Dict[str, Any]:
        """
        Generates a personalized upskilling path for the user based on career goals.
        """
        logger.info(f"Generating upskilling path for {user_id}...")
        
        async with AsyncSessionLocal() as session:
            stmt = select(UpskillingPath).where(UpskillingPath.user_id == user_id)
            result = await session.execute(stmt)
            path_model = result.scalar_one_or_none()
            
            if not path_model:
                # AI would normally analyze gaps and market trends
                track = {
                    "current_date": datetime.datetime.now().isoformat(),
                    "target_roles": ["Senior AI Engineer", "Staff Solutions Architect"],
                    "recommended_skills": ["Vector DBs (Chroma/FAISS)", "Quantized LLM fine-tuning", "FastAPI Optimization"],
                    "learning_resources": [
                        {"topic": "FAISS", "url": "https://github.com/facebookresearch/faiss/wiki"},
                        {"topic": "Quantization", "url": "https://huggingface.co/docs/transformers/main_classes/quantization"}
                    ],
                    "milestones": ["Build a RAG system", "Optimize on-device inference", "Implement XTTS v2"]
                }
                
                path_model = UpskillingPath(
                    user_id=user_id,
                    track_data=track
                )
                session.add(path_model)
                await session.commit()
                await session.refresh(path_model)
            
            return path_model.track_data

    async def get_interview_prep(self, job_title: str, company: str) -> str:
        """Generates interview prep material."""
        return f"Interview Prep for {job_title} at {company}: \n- Study company mission.\n- Practice coding for {job_title}.\n- Review system design for {company}'s scale."
