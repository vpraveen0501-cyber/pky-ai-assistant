import os
import datetime
import logging
from typing import Dict, Any, List, Optional
from sqlalchemy.future import select
from models.document import DocumentMetadata
from database import AsyncSessionLocal
import chromadb
from chromadb.config import Settings

logger = logging.getLogger(__name__)

class DocumentService:
    def __init__(self, data_dir: str = "data/documents"):
        self.data_dir = data_dir
        os.makedirs(data_dir, exist_ok=True)
        
        # Initialize ChromaDB client
        chroma_host = os.getenv("CHROMA_HOST", "localhost")
        chroma_port = os.getenv("CHROMA_PORT", "8000")
        try:
            # We use the HTTP client for the dockerized ChromaDB instance.
            # If not available, fallback to ephemeral local client.
            if os.getenv("CHROMA_HOST"):
                self.chroma_client = chromadb.HttpClient(host=chroma_host, port=chroma_port)
            else:
                self.chroma_client = chromadb.PersistentClient(path="data/chroma")
                
            self.collection = self.chroma_client.get_or_create_collection(
                name="documents_collection",
                metadata={"hnsw:space": "cosine"}
            )
            logger.info("ChromaDB initialized for Vector Storage.")
        except Exception as e:
            logger.error(f"Failed to initialize ChromaDB: {e}")
            self.chroma_client = None

    async def create_document(self, title: str, content: str, doc_type: str = "txt") -> Dict[str, Any]:
        """Creates a document, saves it to disk, and indexes it for RAG using SQLAlchemy and ChromaDB."""
        timestamp = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"{title.replace(' ', '_')}_{timestamp}.{doc_type}"
        file_path = os.path.join(self.data_dir, filename)

        try:
            with open(file_path, "w", encoding="utf-8") as f:
                f.write(content)

            # Save metadata to SQLAlchemy
            async with AsyncSessionLocal() as session:
                new_doc = DocumentMetadata(
                    title=title,
                    filename=filename,
                    faiss_index=0  # Deprecated but kept for schema compatibility
                )
                session.add(new_doc)
                await session.commit()
                await session.refresh(new_doc)
                doc_id = str(new_doc.id)

            # Index for RAG in ChromaDB
            if self.chroma_client:
                self.collection.add(
                    documents=[content],
                    metadatas=[{"title": title, "filename": filename}],
                    ids=[doc_id]
                )
            
            return {
                "status": "success",
                "filename": filename,
                "path": file_path,
                "title": title,
                "created_at": datetime.datetime.now().isoformat()
            }
        except Exception as e:
            logger.error(f"Failed to create document: {e}")
            return {"status": "error", "message": str(e)}

    async def search_documents(self, query: str, top_k: int = 3) -> List[Dict[str, Any]]:
        """Semantic search using ChromaDB and SQLAlchemy metadata."""
        if not self.chroma_client:
            return []
        
        try:
            results = self.collection.query(
                query_texts=[query],
                n_results=top_k
            )
            
            formatted_results = []
            
            # results["documents"] is a list of lists: [[doc1, doc2]]
            if results and results["documents"] and results["documents"][0]:
                for idx, doc_content in enumerate(results["documents"][0]):
                    metadata = results["metadatas"][0][idx] if results["metadatas"] else {}
                    formatted_results.append({
                        "title": metadata.get("title", "Unknown"),
                        "content": doc_content,
                        "filename": metadata.get("filename", "")
                    })
            return formatted_results
        except Exception as e:
            logger.error(f"Semantic search failed: {e}")
            return []

    async def list_documents(self):
        """Lists all document filenames from the storage directory."""
        return os.listdir(self.data_dir)

