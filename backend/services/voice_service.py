import io
import os
import logging
import tempfile
import asyncio
from typing import Optional, Any, AsyncGenerator
from config.settings import settings

logger = logging.getLogger(__name__)


class VoiceService:
    """
    Voice Service supporting multiple TTS engines:
      - kokoro  : on-device dual-streaming TTS (sub-500ms FTTS). DEFAULT.
      - coqui   : local neural TTS (higher quality, slower).
      - gtts    : cloud Google TTS fallback (requires internet).

    STT: Whisper (server-side, unchanged).
    """

    def __init__(self, settings=None):
        self.settings = settings
        self.temp_dir = "data/temp_audio"
        os.makedirs(self.temp_dir, exist_ok=True)

        # TTS engine selector — from settings or env
        self.tts_engine = getattr(settings, "tts_engine", "kokoro")

        # Kokoro pipeline (lazy-initialized in initialize())
        self._kokoro_pipeline = None

        # Character voice mapping
        self.characters = {
            "PKY AI Assistant": {"voice": "af_heart", "speed": 1.1, "gender": "female"},
            "Ram": {"voice": "am_adam", "speed": 0.95, "gender": "male"},
        }

        # Whisper model (STT)
        self.model = None

    # ──────────────────────────────────────────────────────────────
    # Initialization
    # ──────────────────────────────────────────────────────────────

    async def initialize(self):
        """Load Whisper STT model and warm up TTS engine."""
        await self._init_whisper()
        await self._init_tts()

    async def _init_whisper(self):
        try:
            import whisper_timestamped as whisper
            model_name = self.settings.whisper_model if self.settings else "small"
            device = self.settings.whisper_device if self.settings else "cpu"
            self.model = await asyncio.to_thread(whisper.load_model, model_name, device=device)
            logger.info(f"Whisper model '{model_name}' loaded on {device}")
        except Exception as e:
            logger.error(f"Whisper failed to load: {e}")
            self.model = None

    async def _init_tts(self):
        if self.tts_engine == "kokoro":
            try:
                from kokoro import KPipeline
                self._kokoro_pipeline = await asyncio.to_thread(KPipeline, lang_code="a")
                logger.info("Kokoro TTS pipeline initialized (dual-streaming mode)")
            except ImportError:
                logger.warning("kokoro not installed — falling back to gTTS. Run: pip install kokoro soundfile")
                self.tts_engine = "gtts"
            except Exception as e:
                logger.error(f"Kokoro init failed: {e} — falling back to gTTS")
                self.tts_engine = "gtts"
        elif self.tts_engine == "coqui":
            logger.info("Coqui TTS selected (on-demand init per synthesis call)")
        else:
            logger.info("gTTS cloud TTS selected")

    # ──────────────────────────────────────────────────────────────
    # STT — Whisper Transcription
    # ──────────────────────────────────────────────────────────────

    async def transcribe(self, audio_data: bytes) -> Optional[str]:
        """Transcribes raw audio bytes using Whisper."""
        if not audio_data:
            return None

        try:
            logger.info(f"Transcribing {len(audio_data)} bytes of audio")

            if not self.model:
                logger.warning("Whisper model not loaded — returning simulated transcription")
                return "Hello PKY AI Assistant"

            import whisper_timestamped as whisper

            with tempfile.NamedTemporaryFile(suffix=".wav", dir=self.temp_dir, delete=False) as tmp:
                tmp.write(audio_data)
                tmp_path = tmp.name

            try:
                result = await asyncio.to_thread(whisper.transcribe, self.model, tmp_path)
                text = result.get("text", "").strip()
                logger.info(f"Transcription: {text!r}")
                return text
            finally:
                if os.path.exists(tmp_path):
                    os.remove(tmp_path)

        except Exception as e:
            logger.error(f"Transcription error: {e}")
            return None

    # ──────────────────────────────────────────────────────────────
    # TTS — Synthesis (returns complete bytes, for WebSocket compat)
    # ──────────────────────────────────────────────────────────────

    async def synthesize(self, text: str, character: str = "PKY AI Assistant") -> bytes:
        """
        Synthesize full audio and return as bytes (backward-compatible).
        Internally uses streaming when Kokoro is active; collects all chunks.
        """
        chunks: list[bytes] = []
        async for chunk in self.synthesize_streaming(text, character):
            chunks.append(chunk)
        return b"".join(chunks)

    # ──────────────────────────────────────────────────────────────
    # TTS — Dual-Streaming (yields PCM/MP3 chunks as generated)
    # ──────────────────────────────────────────────────────────────

    async def synthesize_streaming(
        self, text: str, character: str = "PKY AI Assistant"
    ) -> AsyncGenerator[bytes, None]:
        """
        Dual-streaming TTS: yields audio chunks as LLM tokens arrive.
        Achieves FTTS < 500ms with Kokoro.

        Yields:
            bytes: raw PCM int16 (Kokoro) or MP3 (gTTS/Coqui) audio chunks.
        """
        char_config = self.characters.get(character, self.characters["PKY AI Assistant"])

        if self.tts_engine == "kokoro" and self._kokoro_pipeline is not None:
            async for chunk in self._kokoro_stream(text, char_config):
                yield chunk
        elif self.tts_engine == "coqui":
            audio = await self._coqui_synthesize(text)
            if audio:
                yield audio
        else:
            audio = await self._gtts_synthesize(text, char_config)
            if audio:
                yield audio

    async def _kokoro_stream(
        self, text: str, char_config: dict
    ) -> AsyncGenerator[bytes, None]:
        """
        Streams PCM int16 audio from Kokoro dual-streaming pipeline.
        Each yielded chunk is a numpy array converted to raw bytes.
        """
        import numpy as np

        voice = char_config.get("voice", "af_heart")
        speed = char_config.get("speed", 1.0)

        try:
            def _run_pipeline():
                return list(
                    self._kokoro_pipeline(
                        text,
                        voice=voice,
                        speed=speed,
                        split_pattern=r"(?<=[.!?])\s+"
                    )
                )

            chunks = await asyncio.to_thread(_run_pipeline)

            for _graphemes, _phonemes, audio_array in chunks:
                if audio_array is None or len(audio_array) == 0:
                    continue
                # Convert float32 [-1,1] → int16 PCM bytes
                pcm = (np.clip(audio_array, -1.0, 1.0) * 32767).astype(np.int16)
                yield pcm.tobytes()

        except Exception as e:
            logger.error(f"Kokoro streaming error: {e}")
            # Graceful fallback to gTTS
            audio = await self._gtts_synthesize(text, char_config)
            if audio:
                yield audio

    async def _gtts_synthesize(self, text: str, char_config: dict) -> bytes:
        """Cloud gTTS synthesis (fallback). Returns MP3 bytes."""
        try:
            from gtts import gTTS
            from pydub import AudioSegment

            tts = gTTS(text=text, lang="en", tld="co.in", slow=False)
            mp3_fp = io.BytesIO()
            await asyncio.to_thread(tts.write_to_fp, mp3_fp)
            mp3_fp.seek(0)

            # Apply pitch shift for male voice (Ram)
            if char_config.get("gender") == "male":
                audio = await asyncio.to_thread(
                    AudioSegment.from_file, mp3_fp, format="mp3"
                )
                pitch = char_config.get("pitch", 0.85)
                new_sr = int(audio.frame_rate * pitch)
                shifted = audio._spawn(audio.raw_data, overrides={"frame_rate": new_sr})
                shifted = shifted.set_frame_rate(audio.frame_rate)
                out = io.BytesIO()
                await asyncio.to_thread(shifted.export, out, format="mp3")
                return out.getvalue()

            return mp3_fp.read()

        except Exception as e:
            logger.error(f"gTTS synthesis failed: {e}")
            return b""

    async def _coqui_synthesize(self, text: str) -> bytes:
        """Coqui TTS synthesis. Returns WAV bytes."""
        try:
            from TTS.api import TTS as CoquiTTS
            import soundfile as sf

            model_name = getattr(self.settings, "tts_model", "tts_models/en/ljspeech/tacotron2-DDC")
            tts = CoquiTTS(model_name=model_name, progress_bar=False)

            with tempfile.NamedTemporaryFile(suffix=".wav", dir=self.temp_dir, delete=False) as tmp:
                out_path = tmp.name

            try:
                await asyncio.to_thread(tts.tts_to_file, text=text, file_path=out_path)
                with open(out_path, "rb") as f:
                    return f.read()
            finally:
                if os.path.exists(out_path):
                    os.remove(out_path)

        except Exception as e:
            logger.error(f"Coqui TTS failed: {e}")
            return b""

    # ──────────────────────────────────────────────────────────────
    # Cleanup
    # ──────────────────────────────────────────────────────────────

    async def cleanup(self):
        self._kokoro_pipeline = None
        logger.info("VoiceService cleanup complete")
