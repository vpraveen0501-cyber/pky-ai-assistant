import asyncio
from services.document_service import DocumentService
from services.llm_service import LLMService

async def test_rag():
    print("Initializing services...")
    # Using specific test directories
    doc_service = DocumentService(data_dir="test_data/docs", vector_dir="test_data/vectors")
    llm_service = LLMService()
    llm_service.set_services(document_service=doc_service)

    # 1. Create a document
    print("\n1. Creating document about PKY AI Assistant Project...")
    title = "Project Overview"
    content = """
    The PKY AI Assistant Project is a futuristic personal assistant. 
    It features 5 specialized AI brains, real-time voice cloning via XTTS v2, 
    and a local-first privacy architecture compliant with India's DPDP Act.
    The UI uses a Glassmorphism aesthetic and holographic elements.
    """
    await doc_service.create_document(title, content)

    # 2. Verify Persistence (Simulate restart by re-initializing)
    print("\n2. Simulating service restart to verify persistence...")
    doc_service_restarted = DocumentService(data_dir="test_data/docs", vector_dir="test_data/vectors")
    
    # 3. Query the LLM (which uses RAG)
    llm_service.set_services(document_service=doc_service_restarted)
    print("\n3. Querying LLM about the project (Triggering RAG from persisted data)...")
    prompt = "Tell me about the PKY AI Assistant Project's UI and its privacy approach."
    
    # Verify search directly first
    search_results = await doc_service_restarted.search_documents(prompt)
    if search_results and any(title in r["title"] for r in search_results):
        print("SUCCESS: Persisted document found via semantic search.")
    else:
        print("FAILURE: Persisted document NOT found.")

    # Generate response
    response = await llm_service.generate(prompt)
    print(f"\nPrompt: {prompt}")
    print(f"Response (RAG-enhanced): {response}")

    print("\nRAG Persistence Test Complete.")

if __name__ == "__main__":
    asyncio.run(test_rag())
