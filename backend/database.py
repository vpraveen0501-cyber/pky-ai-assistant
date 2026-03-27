import os
import sys
import logging
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.orm import declarative_base

logger = logging.getLogger(__name__)

DATABASE_URL = os.getenv("DATABASE_URL")

if not DATABASE_URL:
    debug_mode = os.getenv("PKY_AI_DEBUG", "false").lower() == "true"
    if debug_mode:
        DATABASE_URL = "sqlite+aiosqlite:///./data/pky_ai.db"
        logger.warning(
            "DATABASE_URL not set — falling back to SQLite (development only). "
            "Set DATABASE_URL to a PostgreSQL connection string for production."
        )
    else:
        sys.exit(
            "FATAL: DATABASE_URL is not set. "
            "Set it to a PostgreSQL connection string (e.g. postgresql+asyncpg://user:pass@host/db). "
            "To use SQLite for local development, also set PKY_AI_DEBUG=true."
        )

engine = create_async_engine(DATABASE_URL, echo=False)

AsyncSessionLocal = async_sessionmaker(
    bind=engine,
    class_=AsyncSession,
    expire_on_commit=False,
)

Base = declarative_base()


async def get_db():
    async with AsyncSessionLocal() as session:
        yield session
