import os
import logging
from typing import List, Dict, Any, Optional
import chromadb
from chromadb.config import Settings
from neo4j import GraphDatabase

logger = logging.getLogger(__name__)

class MemoryService:
    def __init__(self):
        # ChromaDB setup
        self.chroma_host = os.getenv("CHROMA_HOST", "localhost")
        self.chroma_port = int(os.getenv("CHROMA_PORT", 8000))
        try:
            self.chroma_client = chromadb.HttpClient(host=self.chroma_host, port=self.chroma_port)
            self.collection = self.chroma_client.get_or_create_collection(name="pky_ai_memory")
            logger.info("ChromaDB connected.")
        except Exception as e:
            logger.error(f"Failed to connect to ChromaDB: {e}")
            self.chroma_client = None

        # Neo4j setup
        self.neo4j_uri = os.getenv("NEO4J_URI", "bolt://localhost:7687")
        self.neo4j_user = os.getenv("NEO4J_USER", "neo4j")
        self.neo4j_password = os.getenv("NEO4J_PASSWORD")
        if not self.neo4j_password:
            logger.warning("NEO4J_PASSWORD not set — graph memory disabled.")
            self.neo4j_driver = None
        else:
            try:
                self.neo4j_driver = GraphDatabase.driver(self.neo4j_uri, auth=(self.neo4j_user, self.neo4j_password))
                logger.info("Neo4j connected.")
            except Exception as e:
                logger.error(f"Failed to connect to Neo4j: {e}")
                self.neo4j_driver = None

    def close(self):
        if self.neo4j_driver:
            self.neo4j_driver.close()

    # --- Vector Memory (ChromaDB) ---

    async def add_vector_memory(self, user_id: str, text: str, metadata: Dict[str, Any] = None):
        if not self.chroma_client:
            return
        
        metadata = metadata or {}
        metadata["user_id"] = user_id
        
        import uuid
        self.collection.add(
            documents=[text],
            metadatas=[metadata],
            ids=[str(uuid.uuid4())]
        )

    async def search_vector_memory(self, user_id: str, query: str, limit: int = 5) -> List[str]:
        if not self.chroma_client:
            return []
        
        results = self.collection.query(
            query_texts=[query],
            n_results=limit,
            where={"user_id": user_id}
        )
        return results["documents"][0] if results["documents"] else []

    # --- Graph Memory (Neo4j) ---

    async def add_graph_relation(self, user_id: str, entity1: str, relation: str, entity2: str, properties: Dict[str, Any] = None):
        if not self.neo4j_driver:
            return
        
        properties = properties or {}
        properties["user_id"] = user_id
        
        query = (
            "MERGE (a:Entity {name: $e1, user_id: $user_id}) "
            "MERGE (b:Entity {name: $e2, user_id: $user_id}) "
            f"MERGE (a)-[r:{relation.upper()}]->(b) "
            "SET r += $props"
        )
        
        with self.neo4j_driver.session() as session:
            session.run(query, e1=entity1, e2=entity2, user_id=user_id, props=properties)

    async def get_graph_context(self, user_id: str, entity_name: str) -> List[Dict[str, Any]]:
        if not self.neo4j_driver:
            return []
        
        query = (
            "MATCH (a:Entity {name: $name, user_id: $user_id})-[r]->(b:Entity) "
            "RETURN type(r) as relation, b.name as target, properties(r) as props"
        )
        
        with self.neo4j_driver.session() as session:
            result = session.run(query, name=entity_name, user_id=user_id)
            return [dict(record) for record in result]
