import asyncio
import logging
import os
from database import engine, Base
from models import User, UserPersona, UserToken, DocumentMetadata, Task, CalendarEvent

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

async def init_models():
    logger.info("Initializing PKY AI Assistant database models...")
    async with engine.begin() as conn:
        # Import models here to ensure they are registered with Base
        await conn.run_sync(Base.metadata.create_all)
    logger.info("PKY AI Assistant Database models initialized successfully.")

if __name__ == "__main__":
    asyncio.run(init_models())
