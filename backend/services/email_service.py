import datetime
import logging
from typing import Dict, Any, List, Optional
from sqlalchemy.future import select
from database import AsyncSessionLocal
from models.email import EmailDraft

logger = logging.getLogger(__name__)

class EmailService:
    def __init__(self):
        self.authenticated_accounts = {} # Store tokens per user_id

    async def authenticate(self, user_id: str, provider: str = "google"):
        """
        Simulates OAuth 2.0 authentication for Google/Microsoft.
        """
        logger.info(f"Authenticating user {user_id} with {provider}...")
        # Placeholder for real OAuth flow
        self.authenticated_accounts[user_id] = {
            "provider": provider,
            "email": f"{user_id}@gmail.com", # Placeholder
            "token": "valid_token_placeholder"
        }
        return {"status": "success", "message": f"Authenticated with {provider}"}

    async def draft_email(self, user_id: str, recipient: str, subject: str, content: str) -> Dict[str, Any]:
        """
        Drafts a personalized email.
        """
        auth = self.authenticated_accounts.get(user_id)
        if not auth:
            return {"status": "error", "message": "User not authenticated for email services."}
            
        async with AsyncSessionLocal() as session:
            draft = EmailDraft(
                user_id=user_id,
                recipient=recipient,
                subject=subject,
                content=content,
                sender=auth["email"]
            )
            session.add(draft)
            await session.commit()
            await session.refresh(draft)
            
            logger.info(f"Drafted email to {recipient} from {auth['email']}")
            return {
                "status": "success", 
                "draft": {
                    "id": draft.id,
                    "recipient": draft.recipient,
                    "subject": draft.subject,
                    "content": draft.content,
                    "sender": draft.sender,
                    "drafted_at": draft.drafted_at.isoformat()
                }
            }

    async def send_email(self, user_id: str, draft_id: int) -> Dict[str, Any]:
        """Simulates sending an email."""
        async with AsyncSessionLocal() as session:
            stmt = select(EmailDraft).where(EmailDraft.id == draft_id, EmailDraft.user_id == user_id)
            result = await session.execute(stmt)
            draft = result.scalar_one_or_none()
            
            if not draft:
                return {"status": "error", "message": "Draft not found."}
            
            logger.info(f"Sending email {draft_id}...")
            return {"status": "success", "message": f"Email sent to {draft.recipient}"}

    async def get_drafts(self, user_id: str = "default"):
        async with AsyncSessionLocal() as session:
            stmt = select(EmailDraft).where(EmailDraft.user_id == user_id)
            result = await session.execute(stmt)
            drafts = result.scalars().all()
            return [
                {
                    "id": d.id,
                    "recipient": d.recipient,
                    "subject": d.subject,
                    "content": d.content,
                    "sender": d.sender,
                    "drafted_at": d.drafted_at.isoformat()
                }
                for d in drafts
            ]
