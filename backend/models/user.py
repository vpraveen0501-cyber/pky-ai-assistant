from sqlalchemy import Column, String, JSON, DateTime, Boolean
from database import Base
from datetime import datetime

class User(Base):
    __tablename__ = 'users'
    
    id = Column(String, primary_key=True, index=True)
    email = Column(String, unique=True, index=True)
    hashed_password = Column(String)
    full_name = Column(String)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)

class UserPersona(Base):
    __tablename__ = 'user_personas'
    
    user_id = Column(String, primary_key=True, index=True)
    active_role = Column(String, default="Assistant") # Researcher, Coder, Assistant, Planner, Creative
    preferences = Column(JSON, default=dict)
    learning_history = Column(JSON, default=list)
    corrections = Column(JSON, default=list)
    job_search_status = Column(JSON, default=dict)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

class UserToken(Base):
    __tablename__ = 'user_tokens'

    user_id = Column(String, primary_key=True, index=True)
    provider = Column(String, nullable=False)
    access_token = Column(String)
    refresh_token = Column(String)
    expires_at = Column(String)
    email = Column(String)
