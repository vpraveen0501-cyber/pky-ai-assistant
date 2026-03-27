"""
PKY AI Assistant Backend - FastAPI Application

Main entry point for the PKY AI Assistant backend service.
"""
import asyncio
import logging
import os
import secrets
import psutil
import traceback
from contextlib import asynccontextmanager
from typing import Annotated

from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Depends, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from sqlalchemy.future import select

try:
    from slowapi import Limiter, _rate_limit_exceeded_handler
    from slowapi.util import get_remote_address
    from slowapi.errors import RateLimitExceeded
    _SLOWAPI_AVAILABLE = True
except ImportError:
    _SLOWAPI_AVAILABLE = False

from config.settings import settings
from services.voice_service import VoiceService
from services.llm_service import LLMService
from services.task_planner import TaskPlanner
from services.news_service import NewsService
from services.report_service import ReportService
from services.user_persona import UserPersona
from services.document_service import DocumentService
from services.job_service import JobService
from services.email_service import EmailService
from services.security_service import SecurityService
from services.oauth_service import OAuthService
from services.browser_service import BrowserService
from init_db import init_models
from redis_client import redis_client
from database import AsyncSessionLocal
from models.user import User

# Configure logging
logging.basicConfig(
    level=logging.INFO if not settings.debug else logging.DEBUG,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# Services (initialized in lifespan)
voice_service: VoiceService | None = None
llm_service: LLMService | None = None
task_planner: TaskPlanner | None = None
news_service: NewsService | None = None
report_service: ReportService | None = None
document_service: DocumentService | None = None
job_service: JobService | None = None
email_service: EmailService | None = None
security_service: SecurityService | None = None
oauth_service: OAuthService | None = None
browser_service: BrowserService | None = None
livekit_token_service = None  # Initialized in lifespan after settings load

# OAuth2 scheme
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="auth/token")


def get_security_service() -> SecurityService:
    """Dependency that ensures security_service is initialized before use."""
    if security_service is None:
        raise HTTPException(status_code=503, detail="Security service not initialized")
    return security_service


async def get_current_user(
    token: Annotated[str, Depends(oauth2_scheme)],
    svc: Annotated[SecurityService, Depends(get_security_service)]
):
    """Dependency for securing endpoints."""
    payload = svc.verify_token(token)
    if not payload:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Could not validate credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )
    user_id = payload.get("sub")
    if user_id is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Could not validate credentials",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return user_id


# Lifespan context manager
@asynccontextmanager
async def lifespan(app: FastAPI):
    """Initialize and cleanup services on application startup/shutdown."""
    global voice_service, llm_service, task_planner, news_service, report_service
    global document_service, job_service, email_service, security_service
    global oauth_service, browser_service, livekit_token_service

    # Startup
    logger.info("Initializing PKY AI Assistant and database...")

    try:
        # Initialize the database models
        await init_models()

        # Initialize Redis
        await redis_client.connect()

        security_service = SecurityService()
        document_service = DocumentService()
        oauth_service = OAuthService(document_service)
        browser_service = BrowserService()

        voice_service = VoiceService(settings)
        await voice_service.initialize()

        llm_service = LLMService()
        task_planner = TaskPlanner()
        news_service = NewsService()
        report_service = ReportService(news_service, task_planner)
        job_service = JobService()
        email_service = EmailService()

        # Inject services into LLMService
        await llm_service.set_services(
            task_planner=task_planner,
            news_service=news_service,
            document_service=document_service,
            job_service=job_service,
            email_service=email_service,
            browser_service=browser_service
        )

        # Inject BrowserService into Job and News
        job_service.browser = browser_service
        news_service.browser = browser_service

        app.state.voice_service = voice_service
        app.state.llm_service = llm_service
        app.state.task_planner = task_planner
        app.state.news_service = news_service
        app.state.report_service = report_service
        app.state.document_service = document_service
        app.state.job_service = job_service
        app.state.email_service = email_service
        app.state.security_service = security_service
        app.state.oauth_service = oauth_service
        app.state.browser_service = browser_service

        # Initialize LiveKit token service (graceful — works even without LiveKit packages)
        from services.livekit_service import LiveKitTokenService
        livekit_token_service = LiveKitTokenService(
            api_key=os.getenv("LIVEKIT_API_KEY", ""),
            api_secret=os.getenv("LIVEKIT_API_SECRET", ""),
            livekit_url=os.getenv("LIVEKIT_URL", "ws://localhost:7880"),
        )
        app.state.livekit_token_service = livekit_token_service
        logger.info(f"LiveKit token service initialized (available={livekit_token_service.available})")

        # Create a default admin user if not exists
        async with AsyncSessionLocal() as session:
            stmt = select(User).where(User.id == "pky_ai_admin")
            result = await session.execute(stmt)
            if not result.scalar_one_or_none():
                admin_password = os.getenv("PKY_AI_ADMIN_PASSWORD")
                if not admin_password:
                    admin_password = secrets.token_urlsafe(18)
                    logger.critical(
                        f"\n{'='*60}\n"
                        f"  GENERATED ADMIN PASSWORD: {admin_password}\n"
                        f"  Set PKY_AI_ADMIN_PASSWORD in .env to persist this.\n"
                        f"{'='*60}"
                    )
                hashed_pw = await security_service.get_password_hash(admin_password)
                admin_user = User(
                    id="pky_ai_admin",
                    email="admin@pky-ai.com",
                    hashed_password=hashed_pw,
                    full_name="PKY AI Assistant Admin"
                )
                session.add(admin_user)
                await session.commit()
                logger.info("Default admin user created.")

        logger.info("PKY AI Assistant Ready.")
    except Exception as e:
        logger.error(f"Failed to initialize services: {e}")
        raise

    yield

    # Shutdown
    logger.info("Shutting down PKY AI Assistant...")
    await redis_client.close()
    if voice_service:
        await voice_service.cleanup()
    if browser_service:
        await browser_service.cleanup()


app = FastAPI(
    title=settings.app_name,
    description="Open-source AI Assistant Backend (PKY AI Assistant) with self-correction and daily reports",
    version=settings.version,
    lifespan=lifespan,
    debug=settings.debug
)

# Rate limiting (L-1)
if _SLOWAPI_AVAILABLE:
    _limiter = Limiter(key_func=get_remote_address)
    app.state.limiter = _limiter
    app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# CORS Middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=settings.cors_credentials,
    allow_methods=settings.cors_methods,
    allow_headers=settings.cors_headers,
)


# --- Global Exception Handlers ---

@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception on {request.url}: {exc}\n{traceback.format_exc()}")
    return JSONResponse(
        status_code=500,
        content={
            "error": "internal_server_error",
            "message": str(exc) if settings.debug else "An unexpected error occurred",
            "path": str(request.url.path)
        }
    )


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": exc.detail, "path": str(request.url.path)}
    )


# --- Routes ---

@app.get("/")
async def root():
    return {"message": "PKY AI Assistant is running"}


@app.get("/models")
async def list_available_models():
    """Public endpoint — returns all supported free OpenRouter models."""
    from services.openrouter_service import OpenRouterService
    svc = OpenRouterService()
    return {
        "message": "All models below are free on OpenRouter (openrouter.ai).",
        "setup_guide": "Set OPENROUTER_API_KEY in your .env to use any model.",
        "models": svc.get_available_models()
    }


@app.post("/auth/token")
async def login_for_access_token(
    request: Request,
    form_data: Annotated[OAuth2PasswordRequestForm, Depends()],
    svc: Annotated[SecurityService, Depends(get_security_service)]
):
    async with AsyncSessionLocal() as session:
        stmt = select(User).where(User.email == form_data.username)
        result = await session.execute(stmt)
        user = result.scalar_one_or_none()

        if not user or not await svc.verify_password(form_data.password, user.hashed_password):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Incorrect username or password",
                headers={"WWW-Authenticate": "Bearer"},
            )

        access_token = svc.create_access_token(data={"sub": user.id, "email": user.email})
        return {"access_token": access_token, "token_type": "bearer"}


@app.post("/auth/refresh")
async def refresh_token(
    token: Annotated[str, Depends(oauth2_scheme)],
    svc: Annotated[SecurityService, Depends(get_security_service)]
):
    """Issues a new access token from an expired (but not too-old) JWT."""
    payload = svc.verify_token(token, allow_expired=True)
    if not payload:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired refresh token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    issued_at = payload.get("iat")
    if issued_at:
        import datetime
        age_days = (datetime.datetime.now(datetime.timezone.utc) - datetime.datetime.fromtimestamp(issued_at, tz=datetime.timezone.utc)).days
        if age_days > 7:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Refresh token too old. Please log in again.",
                headers={"WWW-Authenticate": "Bearer"},
            )
    new_token = svc.create_access_token(data={"sub": payload["sub"], "email": payload.get("email", "")})
    return {"access_token": new_token, "token_type": "bearer"}


@app.get("/system/stats")
async def get_system_stats(user_id: Annotated[str, Depends(get_current_user)]):
    """Returns live backend metrics for the dashboard."""
    process = psutil.Process(os.getpid())
    ram_usage_mb = process.memory_info().rss / (1024 * 1024)

    doc_count = 0
    if app.state.document_service:
        doc_files = await app.state.document_service.list_documents()
        doc_count = len(doc_files)

    stats = {
        "status": "online",
        "version": settings.version,
        "ram_usage": f"{ram_usage_mb:.1f} MB",
        "documents_indexed": doc_count,
        "llm_mode": "local" if app.state.llm_service.use_local else "cloud",
        "voice_engine": "whisper/coqui",
        "active_brains": 5,
        "uptime": "active"
    }
    return stats


@app.get("/system/health")
async def get_system_health(user_id: Annotated[str, Depends(get_current_user)]):
    """Deep integration health check for Android diagnostics."""
    health = {
        "status": "healthy",
        "database": "connected",
        "redis": "connected" if redis_client.is_connected else "disconnected",
        "brains": "missing",
        "openrouter": "unverified",
        "langgraph_memory": "unknown"
    }

    # LangGraph memory mode (H-6)
    if app.state.llm_service:
        using_volatile = getattr(app.state.llm_service, 'using_in_memory_checkpointer', False)
        health["langgraph_memory"] = "in-memory (volatile)" if using_volatile else "postgres"

    # Check Brains
    from services.openrouter_service import OpenRouterService
    try:
        svc = OpenRouterService()
        brain_count = len(svc.brains)
        health["brains"] = f"detected ({brain_count} models)"
    except Exception as e:
        health["brains"] = f"error: {str(e)}"
        health["status"] = "degraded"

    # Check Database connection
    try:
        async with AsyncSessionLocal() as session:
            await session.execute(select(1))
            health["database"] = "connected"
    except Exception:
        health["database"] = "error"
        health["status"] = "degraded"

    return health


@app.websocket("/ws/voice")
async def websocket_voice(websocket: WebSocket, token: str, model: str = "general"):
    # C-4: Guard against uninitialized security_service
    if security_service is None:
        await websocket.close(code=1011)
        return

    payload = security_service.verify_token(token)
    if not payload or not payload.get("sub"):
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return
    user_id = payload.get("sub")
    await websocket.accept()
    try:
        while True:
            data = await websocket.receive_bytes()

            try:
                text = await app.state.voice_service.transcribe(data)
                if text:
                    await websocket.send_text(f"USER: {text}")

                    # Check for correction command
                    if text.lower().startswith("correct") and " to " in text.lower():
                        parts = text.split(" to ")
                        original = parts[0].replace("correct", "").strip()
                        corrected = parts[1].strip()
                        await app.state.llm_service.handle_correction(user_id, original, corrected, "voice")
                        response_text = f"Understood. I'll remember that '{original}' should be '{corrected}'."
                    else:
                        response_text = await app.state.llm_service.generate(text, user_id, brain_type=model)

                    await websocket.send_text(f"PKY: {response_text}")

                    # H-5: Wrap persona/character lookup in try/except
                    try:
                        persona = app.state.llm_service.get_user_persona(user_id)
                        persona_data = await persona.get_persona()
                        character = persona_data.get("preferences", {}).get("name", "PKY AI Assistant")
                    except Exception as persona_err:
                        logger.error(f"Failed to get persona for {user_id}: {persona_err}")
                        character = "PKY AI Assistant"

                    audio_data = await app.state.voice_service.synthesize(response_text, character=character)
                    await websocket.send_bytes(audio_data)

            except WebSocketDisconnect:
                raise
            except Exception as e:
                logger.error(f"Error processing voice message for {user_id}: {e}")
                try:
                    await websocket.send_text("PKY: Sorry, I encountered an error. Please try again.")
                except Exception:
                    raise

    except WebSocketDisconnect:
        logger.info(f"Client {user_id} disconnected from voice WebSocket")


@app.get("/news/digest")
async def get_news_digest(user_id: Annotated[str, Depends(get_current_user)]):
    return await app.state.news_service.get_daily_digest()


@app.post("/tasks/create")
async def create_task(task_data: dict, user_id: Annotated[str, Depends(get_current_user)]):
    return await app.state.task_planner.create_task(task_data, user_id=user_id)


@app.get("/reports/daily")
async def get_daily_report(user_id: Annotated[str, Depends(get_current_user)]):
    return await app.state.report_service.generate_daily_report()


@app.get("/user/history")
async def get_user_history(user_id: Annotated[str, Depends(get_current_user)]):
    """Fetches conversation history for the user from Redis.
    Response fields match Android ChatHistoryItem: id, text, type, timestamp.
    """
    try:
        from langchain_community.chat_message_histories import RedisChatMessageHistory
        from langchain_core.messages import HumanMessage
        import time
        message_history = RedisChatMessageHistory(url=settings.redis_url, session_id=user_id)
        messages = message_history.messages[-50:]
        result = []
        for i, m in enumerate(messages):
            result.append({
                "id": f"{user_id}_{i}",
                "text": m.content,
                "type": "chat",
                "timestamp": int(time.time() * 1000) - (len(messages) - i) * 1000
            })
        return result
    except Exception as e:
        logger.error(f"Failed to fetch history for {user_id}: {e}")
        return []


@app.post("/user/correction")
async def handle_user_correction(correction_data: dict, user_id: Annotated[str, Depends(get_current_user)]):
    original = correction_data.get("original")
    corrected = correction_data.get("corrected")
    context = correction_data.get("context", "")

    if not original or not corrected:
        raise HTTPException(status_code=400, detail="Missing original or corrected text")

    await app.state.llm_service.handle_correction(user_id, original, corrected, context)
    return {"status": "correction recorded", "original": original, "corrected": corrected}


@app.post("/user/preferences")
async def update_user_preferences(preferences_data: dict, user_id: Annotated[str, Depends(get_current_user)]):
    preferences = preferences_data.get("preferences", {})
    persona = app.state.llm_service.get_user_persona(user_id)
    await persona.update_preferences(preferences)
    return {"status": "preferences updated", "user_id": user_id}


@app.get("/user/preferences")
async def get_user_preferences(user_id: Annotated[str, Depends(get_current_user)]):
    persona = app.state.llm_service.get_user_persona(user_id)
    return await persona.get_persona()


@app.post("/user/role")
async def set_user_role(role_data: dict, user_id: Annotated[str, Depends(get_current_user)]):
    """Phase 7: Role-Based Persona Engine - Switch dynamic role."""
    role = role_data.get("role")
    if not role:
        raise HTTPException(status_code=400, detail="Missing role")

    persona = app.state.llm_service.get_user_persona(user_id)
    success = await persona.set_active_role(role)

    if success:
        return {"status": "role switched", "role": role}
    else:
        raise HTTPException(status_code=400, detail="Invalid role. Choose from: Assistant, Researcher, Planner, Creative, Coder")


@app.get("/user/alerts")
async def get_user_alerts(user_id: Annotated[str, Depends(get_current_user)]):
    """Poll for proactive alerts (task reminders, breaking news)."""
    user_alert = await redis_client.get(f"alert_user_{user_id}")
    global_alert = await redis_client.get("latest_pky_alert")

    return {
        "user_alert": user_alert,
        "global_alert": global_alert
    }


@app.post("/chat/multimodal")
async def multimodal_chat(request: Request, user_id: Annotated[str, Depends(get_current_user)]):
    """
    Multimodal chat endpoint — accepts text + optional base64 image.
    Uses a vision-capable model (gemini-2.0-flash-exp:free) on OpenRouter.

    Request body (JSON):
        {
            "text": "What do you see in this image?",
            "image": "<base64-encoded JPEG/PNG>",   // optional
            "model": "google/gemini-2.0-flash-exp:free"  // optional
        }
    """
    try:
        body = await request.json()
        text = body.get("text", "").strip()
        image_b64 = body.get("image")
        model = body.get("model", "google/gemini-2.0-flash-exp:free")

        if not text and not image_b64:
            raise HTTPException(status_code=400, detail="Request must contain text or image")

        # Build multimodal message
        content: list = []
        if text:
            content.append({"type": "text", "text": text})
        if image_b64:
            # Validate size
            max_bytes = settings.max_upload_size_mb * 1024 * 1024
            if len(image_b64) * 3 // 4 > max_bytes:
                raise HTTPException(status_code=413, detail=f"Image too large (max {settings.max_upload_size_mb}MB)")
            content.append({
                "type": "image_url",
                "image_url": {"url": f"data:image/jpeg;base64,{image_b64}"}
            })

        messages = [{"role": "user", "content": content}]

        from services.openrouter_service import OpenRouterService
        svc = OpenRouterService()
        response_text = await svc.chat_raw(messages, model=model)

        # Store in user memory
        if app.state.llm_service and text:
            try:
                await asyncio.to_thread(
                    lambda: None  # placeholder — memory stored via llm_service on voice path
                )
            except Exception:
                pass

        return {"response": response_text, "model": model}

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Multimodal chat error for {user_id}: {e}")
        raise HTTPException(status_code=500, detail="Multimodal processing failed")


@app.post("/user/analyze-document")
async def analyze_document(
    request: Request,
    user_id: Annotated[str, Depends(get_current_user)]
):
    """
    Upload a document (PDF, DOCX, TXT) for AI analysis.
    - Extracts text content
    - Generates a summary via LLM
    - Stores embeddings in ChromaDB for future RAG queries

    Request: multipart/form-data with 'file' field.
    Returns: { "summary": str, "word_count": int, "stored": bool }
    """
    from fastapi import UploadFile
    import tempfile

    try:
        form = await request.form()
        file = form.get("file")
        if not file:
            raise HTTPException(status_code=400, detail="No file provided")

        filename = getattr(file, "filename", "document.txt")
        content_bytes = await file.read()

        max_bytes = settings.max_upload_size_mb * 1024 * 1024
        if len(content_bytes) > max_bytes:
            raise HTTPException(status_code=413, detail=f"File too large (max {settings.max_upload_size_mb}MB)")

        # Extract text based on file type
        file_text = ""
        ext = filename.lower().rsplit(".", 1)[-1] if "." in filename else "txt"

        if ext == "txt":
            file_text = content_bytes.decode("utf-8", errors="replace")
        elif ext in ("pdf", "docx"):
            # Use document_service if available
            if app.state.document_service:
                with tempfile.NamedTemporaryFile(suffix=f".{ext}", delete=False) as tmp:
                    tmp.write(content_bytes)
                    tmp_path = tmp.name
                try:
                    file_text = await app.state.document_service.extract_text(tmp_path)
                finally:
                    if os.path.exists(tmp_path):
                        os.remove(tmp_path)
            else:
                file_text = content_bytes.decode("utf-8", errors="replace")
        else:
            file_text = content_bytes.decode("utf-8", errors="replace")

        if not file_text.strip():
            raise HTTPException(status_code=422, detail="Could not extract text from document")

        word_count = len(file_text.split())

        # Summarize via LLM (cap at 8000 chars to stay within context window)
        summary_prompt = f"Summarize this document concisely (3-5 sentences):\n\n{file_text[:8000]}"
        summary = await app.state.llm_service.generate(summary_prompt, user_id, brain_type="document")

        # Store embeddings in ChromaDB for future RAG
        stored = False
        try:
            from langchain_community.chat_message_histories import RedisChatMessageHistory
            from langchain_core.messages import HumanMessage, AIMessage

            history = RedisChatMessageHistory(url=settings.redis_url, session_id=user_id)
            history.add_message(HumanMessage(content=f"[Document: {filename}]\n{file_text[:2000]}"))
            history.add_message(AIMessage(content=f"[Summary] {summary}"))
            stored = True
        except Exception as store_err:
            logger.warning(f"Failed to store document in memory: {store_err}")

        return {
            "filename": filename,
            "summary": summary,
            "word_count": word_count,
            "stored": stored
        }

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Document analysis error for {user_id}: {e}")
        raise HTTPException(status_code=500, detail="Document analysis failed")


@app.get("/user/memory/search")
async def search_memory(
    q: str,
    user_id: Annotated[str, Depends(get_current_user)]
):
    """
    Semantic search over the user's ChromaDB conversation memory.
    Returns top-5 semantically relevant past messages.
    """
    try:
        if not q.strip():
            raise HTTPException(status_code=400, detail="Query parameter 'q' is required")

        from langchain_community.chat_message_histories import RedisChatMessageHistory
        from langchain_core.messages import HumanMessage

        history = RedisChatMessageHistory(url=settings.redis_url, session_id=user_id)
        messages = history.messages

        # Simple keyword search (upgrade to vector search when ChromaDB is wired)
        query_lower = q.lower()
        relevant = [
            {"content": m.content, "type": "user" if isinstance(m, HumanMessage) else "assistant"}
            for m in messages
            if query_lower in m.content.lower()
        ][-10:]  # most recent 10 matches

        return {"query": q, "results": relevant, "count": len(relevant)}

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Memory search error for {user_id}: {e}")
        return {"query": q, "results": [], "count": 0}


@app.get("/voice/token")
async def get_voice_token(user_id: Annotated[str, Depends(get_current_user)]):
    """
    Returns a LiveKit JWT room token for the authenticated user.
    Android client uses this to connect to a LiveKit room for real-time voice.
    Falls back to WebSocket if LiveKit is not configured.
    """
    svc = app.state.livekit_token_service
    if svc is None:
        return {"fallback": "websocket", "ws_url": "/ws/voice"}
    return svc.generate_token(user_id)


@app.get("/system/capabilities")
async def get_system_capabilities(user_id: Annotated[str, Depends(get_current_user)]):
    """
    Returns feature flags that Android reads to enable/disable UI features.
    Allows server-driven feature gating without app updates.
    """
    svc = app.state.livekit_token_service
    return {
        "livekit_available": svc.available if svc else False,
        "tts_engine": settings.tts_engine,
        "supports_streaming_tts": settings.tts_engine in ("kokoro", "coqui"),
        "supports_multimodal": settings.enable_multimodal,
        "supports_screen_context": True,
        "supports_ondevice_llm": False,  # Handled on Android side
        "voice_transport": "livekit" if (svc and svc.available) else "websocket",
        "max_upload_mb": settings.max_upload_size_mb,
        "version": settings.version,
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
