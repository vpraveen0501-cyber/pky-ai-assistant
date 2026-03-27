import os
import logging
import datetime
from typing import Dict, Any, Optional
from sqlalchemy.future import select
from models.user import UserToken
from database import AsyncSessionLocal

logger = logging.getLogger(__name__)

class OAuthService:
    def __init__(self, doc_service=None):
        self.doc_service = doc_service

    async def save_token(self, user_id: str, provider: str, email: str, access_token: str, refresh_token: str, expires_in: int):
        """Saves or updates user OAuth tokens in PostgreSQL via SQLAlchemy."""
        expires_at = (datetime.datetime.now() + datetime.timedelta(seconds=expires_in)).isoformat()
        
        async with AsyncSessionLocal() as session:
            stmt = select(UserToken).where(UserToken.user_id == user_id)
            result = await session.execute(stmt)
            token_model = result.scalar_one_or_none()
            
            if not token_model:
                token_model = UserToken(user_id=user_id)
                session.add(token_model)
            
            token_model.provider = provider
            token_model.access_token = access_token
            token_model.refresh_token = refresh_token
            token_model.expires_at = expires_at
            token_model.email = email
            
            await session.commit()
            logger.info(f"Saved {provider} tokens for user {user_id} to PostgreSQL")

    async def get_token(self, user_id: str) -> Optional[Dict[str, Any]]:
        """Retrieves user tokens from PostgreSQL."""
        async with AsyncSessionLocal() as session:
            stmt = select(UserToken).where(UserToken.user_id == user_id)
            result = await session.execute(stmt)
            token_model = result.scalar_one_or_none()
            
            if token_model:
                return {
                    "provider": token_model.provider,
                    "access_token": token_model.access_token,
                    "refresh_token": token_model.refresh_token,
                    "expires_at": token_model.expires_at,
                    "email": token_model.email
                }
        return None

    async def refresh_token_if_needed(self, user_id: str) -> Optional[str]:
        """Asynchronous token refresh logic."""
        token_data = await self.get_token(user_id)
        if not token_data:
            return None
            
        expires_at = datetime.datetime.fromisoformat(token_data["expires_at"])
        if datetime.datetime.now() > expires_at:
            logger.info(f"Token expired for {user_id}, simulating refresh...")
            new_access_token = f"refreshed_{token_data['access_token']}"
            await self.save_token(
                user_id, 
                token_data["provider"], 
                token_data["email"], 
                new_access_token, 
                token_data["refresh_token"], 
                3600
            )
            return new_access_token
            
        return token_data["access_token"]

    async def get_google_client(self, user_id: str):
        """Placeholder for getting an authenticated Google API client."""
        token = await self.refresh_token_if_needed(user_id)
        if not token:
            return None
        return {"token": token, "status": "authenticated"}
