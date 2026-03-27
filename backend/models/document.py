from sqlalchemy import Column, Integer, String, DateTime
from database import Base
from datetime import datetime

class DocumentMetadata(Base):
    __tablename__ = 'documents'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    title = Column(String, nullable=False)
    filename = Column(String, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)
    # Keeping faiss_index for backward compatibility until ChromaDB migration is complete
    faiss_index = Column(Integer, nullable=False)
