"""
LiveKit Voice Agent Service for PKY AI Assistant.

Provides:
  - Room token generation for Android clients
  - VoicePipelineAgent (STT → LLM → TTS) running as a LiveKit agent worker
  - Semantic turn detection via Silero VAD
  - Barge-in / interrupt support via data channel signal

Architecture:
  Android (LiveKit SDK) ←─ WebRTC ─→ LiveKit Server ←─ LiveKit SDK ─→ VoiceAgent (this service)
                                                                              ↓
                                                                   Whisper STT → LLMService → Kokoro TTS
"""

import logging
import os
from typing import Optional

logger = logging.getLogger(__name__)

# ──────────────────────────────────────────────────────────────────────────────
# LiveKit availability check — all livekit imports are guarded so the rest of
# the backend works even if livekit packages are not yet installed.
# ──────────────────────────────────────────────────────────────────────────────
try:
    from livekit import api as lkapi
    from livekit.agents import (
        AutoSubscribe,
        JobContext,
        WorkerOptions,
        cli,
        llm as agents_llm,
    )
    from livekit.agents.voice_assistant import VoiceAssistant
    from livekit.plugins import silero
    _LIVEKIT_AVAILABLE = True
except ImportError:
    _LIVEKIT_AVAILABLE = False
    logger.warning(
        "LiveKit packages not installed. /voice/token will still work for room tokens, "
        "but the agent worker will be unavailable. "
        "Install: pip install livekit livekit-agents livekit-plugins-silero"
    )


# ──────────────────────────────────────────────────────────────────────────────
# Room Token Generator — used by the /voice/token FastAPI endpoint
# ──────────────────────────────────────────────────────────────────────────────

class LiveKitTokenService:
    """Generates LiveKit JWT room tokens for authenticated users."""

    def __init__(self, api_key: str, api_secret: str, livekit_url: str):
        self.api_key = api_key
        self.api_secret = api_secret
        self.livekit_url = livekit_url
        self.available = _LIVEKIT_AVAILABLE and bool(api_key) and bool(api_secret)

    def generate_token(self, user_id: str, room_name: Optional[str] = None) -> dict:
        """
        Generate a LiveKit room token for a user.

        Returns:
            dict with 'token' (JWT string) and 'url' (LiveKit server URL).
            If LiveKit is unavailable, returns {'error': ..., 'fallback': 'websocket'}.
        """
        if not self.available:
            return {
                "error": "LiveKit not configured — using WebSocket fallback",
                "fallback": "websocket",
                "ws_url": "/ws/voice"
            }

        if not _LIVEKIT_AVAILABLE:
            return {
                "error": "LiveKit packages not installed on server",
                "fallback": "websocket",
                "ws_url": "/ws/voice"
            }

        room = room_name or f"pky-{user_id}"

        try:
            token = lkapi.AccessToken(self.api_key, self.api_secret)
            token.with_identity(user_id)
            token.with_name(f"PKY User {user_id[:8]}")
            token.with_grants(
                lkapi.VideoGrants(
                    room_join=True,
                    room=room,
                    can_publish=True,
                    can_subscribe=True,
                    can_publish_data=True,
                )
            )
            jwt = token.to_jwt()
            logger.info(f"Generated LiveKit token for user={user_id} room={room}")
            return {
                "token": jwt,
                "url": self.livekit_url,
                "room": room,
            }
        except Exception as e:
            logger.error(f"LiveKit token generation failed: {e}")
            return {
                "error": str(e),
                "fallback": "websocket",
                "ws_url": "/ws/voice"
            }


# ──────────────────────────────────────────────────────────────────────────────
# LiveKit Voice Agent Worker
# ──────────────────────────────────────────────────────────────────────────────

def create_voice_agent_entrypoint(llm_service, voice_service):
    """
    Factory that returns a LiveKit agent entrypoint bound to PKY AI services.

    Usage (run as separate worker process):
        python -m livekit.agents start --entrypoint backend.services.livekit_service:entrypoint

    The agent connects to a LiveKit room when an Android client joins,
    then runs the STT → LLM → TTS pipeline automatically.
    """
    if not _LIVEKIT_AVAILABLE:
        logger.warning("LiveKit agent worker unavailable — packages missing")
        return None

    async def entrypoint(ctx: JobContext):
        """Agent entrypoint called by LiveKit when a new room job is dispatched."""
        logger.info(f"LiveKit agent joining room: {ctx.room.name}")

        # ── VAD + Semantic Turn Detection ──────────────────────────────────
        vad = silero.VAD.load(
            min_silence_duration=0.3,   # 300ms silence = end of turn
            min_speech_duration=0.1,    # ignore very short sounds (< 100ms)
        )

        # ── STT: wrap Whisper in LiveKit STT interface ─────────────────────
        stt = _WhisperSTTAdapter(voice_service)

        # ── TTS: wrap Kokoro in LiveKit TTS interface ──────────────────────
        tts = _KokoroTTSAdapter(voice_service)

        # ── LLM: wrap PKY LLMService ───────────────────────────────────────
        lm = _PKYLLMAdapter(llm_service)

        assistant = VoiceAssistant(
            vad=vad,
            stt=stt,
            llm=lm,
            tts=tts,
            allow_interruptions=True,          # barge-in enabled
            interrupt_speech_duration=0.5,     # 500ms of user speech triggers interrupt
            interrupt_min_words=2,             # require at least 2 words before interrupting
        )

        await ctx.connect(auto_subscribe=AutoSubscribe.AUDIO_ONLY)
        assistant.start(ctx.room)

        # ── Data channel: handle INTERRUPT signals from Android client ─────
        @ctx.room.on("data_received")
        def on_data(data: bytes, participant, *_):
            if data == b"INTERRUPT":
                logger.info("Barge-in INTERRUPT received — stopping TTS")
                assistant.interrupt()

        logger.info("PKY AI voice agent is active in room")
        await assistant.say("Hello! PKY AI is ready. How can I help you?", allow_interruptions=True)

    return entrypoint


# ──────────────────────────────────────────────────────────────────────────────
# Adapter classes — bridge PKY AI services to LiveKit plugin interfaces
# ──────────────────────────────────────────────────────────────────────────────

class _WhisperSTTAdapter:
    """Wraps PKY VoiceService.transcribe() to work as a LiveKit STT plugin."""

    def __init__(self, voice_service):
        self._svc = voice_service

    async def recognize(self, audio_bytes: bytes, **kwargs) -> str:
        result = await self._svc.transcribe(audio_bytes)
        return result or ""


class _KokoroTTSAdapter:
    """Wraps PKY VoiceService.synthesize_streaming() as a LiveKit TTS plugin."""

    def __init__(self, voice_service):
        self._svc = voice_service

    async def synthesize(self, text: str, **kwargs):
        """Yields audio chunks for streaming playback via LiveKit."""
        async for chunk in self._svc.synthesize_streaming(text):
            yield chunk


class _PKYLLMAdapter:
    """Wraps PKY LLMService.generate() as a LiveKit LLM plugin."""

    def __init__(self, llm_service):
        self._svc = llm_service

    async def chat(self, messages: list, user_id: str = "livekit_user", **kwargs) -> str:
        # Extract latest user message
        user_text = ""
        for msg in reversed(messages):
            if getattr(msg, "role", None) == "user":
                user_text = str(msg.content)
                break
        if not user_text:
            return ""
        return await self._svc.generate(user_text, user_id, brain_type="general")
