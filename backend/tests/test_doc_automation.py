import asyncio
import os
import logging
from services.document_automation_service import DocumentAutomationService

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("Doc-Validation")

async def validate_document_outputs():
    doc_service = DocumentAutomationService(output_dir="data/test_outputs")
    
    logger.info("Starting Document Generation Validation...")
    
    # 1. Word Validation
    logger.info("  Testing Word (.docx) generation...")
    sections = [
        {"heading": "Executive Summary", "content": "This is a high-level summary of the PKY AI Assistant Project achievements."},
        {"heading": "Key Metrics", "content": "Latency reduced by 40%, user engagement up by 150%."}
    ]
    word_path = await doc_service.create_word_report("PKY AI Assistant Q1 Report", sections)
    if os.path.exists(word_path):
        logger.info(f"  [✓] Word Report generated: {word_path}")
    else:
        logger.error("  [✗] Word Report FAILED to generate!")

    # 2. Excel Validation
    logger.info("  Testing Excel (.xlsx) generation...")
    data = [
        {"Quarter": "Q1", "Revenue": 15000, "Users": 1200},
        {"Quarter": "Q2", "Revenue": 28000, "Users": 2400},
        {"Quarter": "Q3", "Revenue": 42000, "Users": 3800}
    ]
    excel_path = await doc_service.create_excel_model("Financial Performance", data)
    if os.path.exists(excel_path):
        logger.info(f"  [✓] Excel Model generated: {excel_path}")
    else:
        logger.error("  [✗] Excel Model FAILED to generate!")

    # 3. PowerPoint Validation
    logger.info("  Testing PowerPoint (.pptx) generation...")
    slides = [
        {"title": "Vision", "content": "Building the most secure AI assistant for India.", "bullets": ["DPDP Compliance", "Offline First"]},
        {"title": "Tech Stack", "content": "Cutting edge open-source tools.", "bullets": ["Whisper ASR", "Coqui TTS", "FastAPI"]}
    ]
    pptx_path = await doc_service.create_presentation("Pitch Deck", slides)
    if os.path.exists(pptx_path):
        logger.info(f"  [✓] PowerPoint Deck generated: {pptx_path}")
    else:
        logger.error("  [✗] PowerPoint Deck FAILED to generate!")

    # 4. PDF Validation
    logger.info("  Testing PDF (.pdf) generation...")
    pdf_content = "This is a detailed legal brief regarding the PKY AI Assistant data privacy approach.\n\nAll data is encrypted via SQLCipher and stored locally-first."
    pdf_path = await doc_service.create_pdf_report("Privacy Brief", pdf_content)
    if os.path.exists(pdf_path):
        logger.info(f"  [✓] PDF Brief generated: {pdf_path}")
    else:
        logger.error("  [✗] PDF Brief FAILED to generate!")

    logger.info("Document Validation Complete.")

if __name__ == "__main__":
    asyncio.run(validate_document_outputs())
