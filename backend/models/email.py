from sqlalchemy import Column, Integer, String, DateTime
from database import Base
from datetime import datetime

class EmailDraft(Base):
    __tablename__ = 'email_drafts'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(String, index=True)
    recipient = Column(String, nullable=False)
    subject = Column(String, nullable=False)
    content = Column(String)
    sender = Column(String)
    drafted_at = Column(DateTime, default=datetime.utcnow)