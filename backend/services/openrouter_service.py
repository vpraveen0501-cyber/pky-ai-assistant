import aiohttp
import json
import os
import logging
from typing import Dict, Any, Optional

logger = logging.getLogger(__name__)

# ── Top 10 Free OpenRouter Models ────────────────────────────────────────────
# Users can override these via .env (e.g. PKY_DEFAULT_MODEL=deepseek/deepseek-r1:free)
FREE_MODELS = {
    "general":   os.getenv("PKY_DEFAULT_MODEL",    "meta-llama/llama-3.3-70b-instruct:free"),
    "build":     os.getenv("PKY_CODING_MODEL",     "qwen/qwen3-coder:free"),
    "plan":      os.getenv("PKY_PLANNING_MODEL",   "openai/gpt-oss-120b:free"),
    "adaptive":  os.getenv("PKY_REASONING_MODEL",  "deepseek/deepseek-r1:free"),
    "extended":  os.getenv("PKY_REASONING_MODEL",  "stepfun/step-3.5-flash:free"),
    "document":  os.getenv("PKY_DOCUMENT_MODEL",   "nvidia/nemotron-3-super:free"),
    "supervisor":os.getenv("PKY_SUPERVISOR_MODEL", "google/gemini-2.0-flash-exp:free"),
    "agent":                                        "nvidia/nemotron-3-nano-30b-a3b:free",
    "memory":                                       "moonshotai/kimi-k2.5:free",
    "rag":                                          "qwen/qwen3-next-80b:free",
}

class OpenRouterService:
    def __init__(self, settings_base_path: Optional[str] = None):
        if settings_base_path is None:
            # Resolve brain-config folder inside the backend folder
            base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
            self.settings_base_path = os.path.join(base_dir, "brain-config")
        else:
            self.settings_base_path = settings_base_path
        self.brains: Dict[str, Dict[str, Any]] = {}
        self._load_brains()

    def _load_brains(self):
        """Loads all brain configurations from the settings.json files.
        Falls back gracefully to FREE_MODELS if no settings.json is found."""
        brain_dirs = {
            "general":  "general",
            "build":    "build",
            "plan":     "plan",
            "adaptive": "adaptive",
            "extended": "extended"
        }

        for brain_name, dir_name in brain_dirs.items():
            path = os.path.join(self.settings_base_path, dir_name, "settings.json")
            if os.path.exists(path):
                try:
                    with open(path, 'r') as f:
                        self.brains[brain_name] = json.load(f)
                    logger.info(f"Loaded brain config for: {brain_name}")
                except Exception as e:
                    logger.error(f"Failed to load brain config for {brain_name}: {e}")
            else:
                # Build a minimal config from the FREE_MODELS table
                self.brains[brain_name] = {
                    "env": {"ANTHROPIC_MODEL": FREE_MODELS.get(brain_name, FREE_MODELS["general"])},
                    "model_config": {"temperature": 0.7, "max_tokens": 4096,
                                     "system_prompt": "You are PKY AI Assistant, a helpful AI assistant."}
                }
                logger.info(f"Brain config file not found for '{brain_name}' — using FREE_MODELS default.")

    def get_available_models(self) -> Dict[str, str]:
        """Returns the list of all 10 available free models for user selection."""
        return {
            "1_general":    FREE_MODELS["general"],
            "2_coding":     FREE_MODELS["build"],
            "3_planning":   FREE_MODELS["plan"],
            "4_reasoning":  FREE_MODELS["adaptive"],
            "5_thinking":   FREE_MODELS["extended"],
            "6_documents":  FREE_MODELS["document"],
            "7_supervisor": FREE_MODELS["supervisor"],
            "8_agent":      FREE_MODELS["agent"],
            "9_memory":     FREE_MODELS["memory"],
            "10_rag":       FREE_MODELS["rag"],
        }

    async def generate_response(self, prompt: str, brain_type: str = "general",
                                system_prompt: Optional[str] = None,
                                model_override: Optional[str] = None) -> str:
        """Generates a response using the specified brain type via OpenRouter.
        
        Args:
            prompt: The user's message.
            brain_type: Which brain to use (general, build, plan, adaptive, extended, etc.)
            system_prompt: Optional override for the system prompt.
            model_override: Optional direct model ID override (e.g. 'deepseek/deepseek-r1:free').
        """
        if brain_type not in self.brains:
            logger.warning(f"Brain type '{brain_type}' not found, falling back to 'general'")
            brain_type = "general"

        config = self.brains.get(brain_type, {})
        env = config.get("env", {})
        model_config = config.get("model_config", {})

        # Priority: direct override > per-brain env > environment variable model > FREE_MODELS default
        model = (
            model_override
            or env.get("ANTHROPIC_MODEL")
            or FREE_MODELS.get(brain_type, FREE_MODELS["general"])
        )

        # Priority: OPENROUTER_API_KEY env > per-brain token (legacy)
        auth_token = os.getenv("OPENROUTER_API_KEY") or env.get("ANTHROPIC_AUTH_TOKEN", "")

        if not auth_token:
            logger.error("OPENROUTER_API_KEY is not set.")
            return (
                "⚙️ **Setup required**: Please set your OpenRouter API key.\n\n"
                "1. Get a free key at https://openrouter.ai/settings/keys\n"
                "2. Add `OPENROUTER_API_KEY=sk-or-v1-...` to your `.env` file\n"
                "3. Restart the backend"
            )

        url = "https://openrouter.ai/api/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {auth_token}",
            "Content-Type": "application/json",
            "HTTP-Referer": "https://github.com/pky-ai-project",
            "X-Title": "PKY AI Assistant"
        }

        payload = {
            "model": model,
            "messages": [
                {"role": "system", "content": system_prompt or model_config.get(
                    "system_prompt", "You are PKY AI Assistant, a helpful AI assistant.")},
                {"role": "user", "content": prompt}
            ],
            "temperature": model_config.get("temperature", 0.7),
            "max_tokens": model_config.get("max_tokens", 4096)
        }

        try:
            timeout = aiohttp.ClientTimeout(total=60, connect=10, sock_connect=10, sock_read=55)
            async with aiohttp.ClientSession() as session:
                async with session.post(url, headers=headers, json=payload, timeout=timeout) as response:
                    if response.status == 200:
                        data = await response.json()
                        return data["choices"][0]["message"]["content"]
                    elif response.status == 401:
                        return "❌ Invalid API key. Please check your OPENROUTER_API_KEY in .env"
                    elif response.status == 429:
                        return "⏳ Rate limit reached (200 req/day on free tier). Add OpenRouter credits for more."
                    else:
                        error_text = await response.text()
                        logger.error(f"OpenRouter API error ({response.status}): {error_text}")
                        return f"Error: OpenRouter returned status {response.status}"
        except aiohttp.ClientConnectorError:
            return "❌ Cannot connect to OpenRouter. Check your internet connection."
        except Exception as e:
            logger.error(f"OpenRouter request failed: {e}")
            return f"Error: Failed to connect to OpenRouter. {e}"

        return "Error: Unexpected state reached in generate_response."

    async def chat_raw(self, messages: list, model: str = "google/gemini-2.0-flash-exp:free") -> str:
        """
        Send a pre-built messages list directly to OpenRouter.
        Used for multimodal requests where messages contain image content blocks.

        Args:
            messages: List of OpenAI-format message dicts (role + content).
                      Content can be a string or a list of content blocks
                      (text + image_url for multimodal).
            model:    OpenRouter model ID. Defaults to Gemini Flash (free, multimodal).

        Returns:
            The assistant's response text.
        """
        auth_token = os.getenv("OPENROUTER_API_KEY", "")
        if not auth_token:
            return "⚙️ OPENROUTER_API_KEY not set — multimodal chat unavailable."

        url = "https://openrouter.ai/api/v1/chat/completions"
        headers = {
            "Authorization": f"Bearer {auth_token}",
            "Content-Type": "application/json",
            "HTTP-Referer": "https://github.com/pky-ai-project",
            "X-Title": "PKY AI Assistant"
        }
        payload = {
            "model": model,
            "messages": messages,
            "temperature": 0.7,
            "max_tokens": 1024
        }

        try:
            timeout = aiohttp.ClientTimeout(total=60, connect=10, sock_connect=10, sock_read=55)
            async with aiohttp.ClientSession() as session:
                async with session.post(url, headers=headers, json=payload, timeout=timeout) as response:
                    if response.status == 200:
                        data = await response.json()
                        return data["choices"][0]["message"]["content"]
                    else:
                        error_text = await response.text()
                        logger.error(f"OpenRouter multimodal error ({response.status}): {error_text}")
                        return f"Multimodal request failed (HTTP {response.status})"
        except Exception as e:
            logger.error(f"chat_raw failed: {e}")
            return f"Error processing multimodal request: {e}"
