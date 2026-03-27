import os
import pytest
from config.settings import Settings

def test_settings_load_api_keys_with_prefix(monkeypatch):
    """Test that settings are loaded with PKY_AI_ prefix."""
    monkeypatch.setenv("PKY_AI_OPENAI_API_KEY", "test-openai-key")
    monkeypatch.setenv("PKY_AI_NEWS_API_KEY", "test-news-key")
    
    settings = Settings(_env_file=None)
    assert settings.openai_api_key == "test-openai-key"
    assert settings.news_api_key == "test-news-key"

def test_settings_load_app_name(monkeypatch):
    """Test that PKY_AI_APP_NAME is loaded correctly."""
    monkeypatch.setenv("PKY_AI_APP_NAME", "Custom App Name")
    
    settings = Settings(_env_file=None)
    assert settings.app_name == "Custom App Name"

def test_settings_load_api_keys_without_prefix(monkeypatch):
    """
    Test that settings are loaded WITHOUT PKY_AI_ prefix for API keys.
    """
    monkeypatch.delenv("PKY_AI_OPENAI_API_KEY", raising=False)
    monkeypatch.setenv("OPENAI_API_KEY", "no-prefix-key")
    
    settings = Settings(_env_file=None)
    assert settings.openai_api_key == "no-prefix-key"

def test_settings_load_secret_key_without_prefix(monkeypatch):
    """Test that secret_key can be loaded without PKY_AI_ prefix."""
    monkeypatch.setenv("SECRET_KEY", "custom-secret")
    
    settings = Settings(_env_file=None)
    assert settings.secret_key == "custom-secret"
