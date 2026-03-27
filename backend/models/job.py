from sqlalchemy import Column, Integer, String, DateTime, JSON
from database import Base
from datetime import datetime

class JobApplication(Base):
    __tablename__ = 'job_applications'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String, index=True)
    job_title = Column(String, nullable=False)
    company = Column(String, nullable=False)
    status = Column(String, default="applied")
    applied_at = Column(DateTime, default=datetime.utcnow)

class UpskillingPath(Base):
    __tablename__ = 'upskilling_paths'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String, unique=True, index=True)
    track_data = Column(JSON, default=dict)
    created_at = Column(DateTime, default=datetime.utcnow)