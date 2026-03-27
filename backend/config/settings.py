"""
Configuration management for PKY AI Assistant Backend.

Uses pydantic-settings for type-safe configuration with environment variable support.
"""
from pydantic import Field, AliasChoices, ConfigDict
from pydantic_settings import BaseSettings
from typing import List, Optional


class Settings(BaseSettings):
    """Application settings with environment variable support."""
    
    model_config = ConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        env_prefix="PKY_AI_",
        extra="ignore"
    )
    
    # Application
    app_name: str = "PKY AI Assistant"
    version: str = "0.1.0"
    debug: bool = False
    
    # Security
    secret_key: str = Field(..., description="Must be provided in production", validation_alias=AliasChoices("PKY_AI_SECRET_KEY", "SECRET_KEY"))
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 30
    
    # CORS
    cors_origins: List[str] = ["http://localhost", "http://localhost:3000", "http://10.0.2.2:8000"]
    cors_credentials: bool = True
    cors_methods: List[str] = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    cors_headers: List[str] = ["Authorization", "Content-Type", "Accept", "X-Requested-With"]
    
    # Rate Limiting
    rate_limit_requests: int = 10
    rate_limit_window: int = 60  # seconds
    
    # Data Storage
    data_dir: str = "data"
    encryption_key: Optional[str] = Field(None, description="Set via PKY_AI_ENCRYPTION_KEY. Required for SecurityService.", validation_alias=AliasChoices("PKY_AI_ENCRYPTION_KEY", "ENCRYPTION_KEY"))
    
    # Redis (for caching and distributed state)
    redis_url: str = "redis://localhost:6379/0"
    
    # ML Models
    whisper_model: str = "small"
    whisper_device: str = "cuda"  # or "cpu"
    tts_model: str = "tts_models/en/ljspeech/tacotron2-DDC"
    tts_device: str = "cuda"  # or "cpu"
    
    # Timeouts (seconds)
    whisper_timeout: int = 30
    tts_timeout: int = 15
    rss_fetch_timeout: int = 10
    websocket_receive_timeout: int = 30
    
    # Feature Flags
    use_local_llm: bool = False
    enable_voice_cloning: bool = False
    enable_daily_reports: bool = True
    enable_self_correction: bool = True
    enable_job_service: bool = False
    enable_multimodal: bool = True

    # TTS Engine — kokoro (dual-streaming, on-device) | coqui | gtts
    tts_engine: str = Field(default="kokoro", description="TTS backend: kokoro | coqui | gtts")

    # LiveKit WebRTC voice transport
    livekit_url: str = Field(default="ws://localhost:7880", validation_alias=AliasChoices("LIVEKIT_URL", "PKY_AI_LIVEKIT_URL"))
    livekit_api_key: str = Field(default="", validation_alias=AliasChoices("LIVEKIT_API_KEY", "PKY_AI_LIVEKIT_API_KEY"))
    livekit_api_secret: str = Field(default="", validation_alias=AliasChoices("LIVEKIT_API_SECRET", "PKY_AI_LIVEKIT_API_SECRET"))

    # Multimodal
    max_upload_size_mb: int = Field(default=10, description="Max file upload size in MB")

    # API Keys (optional)
    openai_api_key: Optional[str] = Field(None, validation_alias=AliasChoices("PKY_AI_OPENAI_API_KEY", "OPENAI_API_KEY"))
    news_api_key: Optional[str] = Field(None, validation_alias=AliasChoices("PKY_AI_NEWS_API_KEY", "NEWS_API_KEY"))


# Global settings instance
settings = Settings()


def get_settings() -> Settings:
    """Get application settings."""
    return settings
