<h1 align="center">🤖 PKY AI Assistant — Open Source Personal AI Assistant</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Android-Kotlin%20%2B%20Compose-blueviolet?style=for-the-badge&logo=android" />
  <img src="https://img.shields.io/badge/Backend-FastAPI%20%2B%20Python-009688?style=for-the-badge&logo=fastapi" />
  <img src="https://img.shields.io/badge/AI-OpenRouter%20(Free)-orange?style=for-the-badge" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" />
</p>

<p align="center">
A next-generation, open-source AI assistant for Android with voice interaction, deep device integration, and a modular multi-agent backend — powered by OpenRouter's free tier.
</p>

---

## ✨ Features

| Category | Feature |
|---|---|
| 🎤 Voice | Wake word, speech-to-text (Whisper), text-to-speech (Coqui) |
| 🧠 AI | Multi-model support via OpenRouter — all 10 top free models |
| 🤖 Agents | LangGraph multi-agent supervisor with Researcher, Planner & Creative sub-agents |
| 🔒 Security | JWT auth, biometric unlock, AES-256 encryption, SQLCipher |
| 📅 Automation | Task planning, news aggregation, web research, email management |
| 🎨 Design | Glassmorphism 2.0 UI with Inter font and fluid orb visualizer |

---

## 🚀 Quick Start

### Step 1 — Get a Free OpenRouter API Key

1. Go to **[openrouter.ai](https://openrouter.ai)** and sign up (it's free!)
2. Navigate to **Settings → Keys**
3. Click **Create Key** and copy it

> All 10 models listed below are **completely free** on OpenRouter.

---

### Step 2 — Configure the Backend

```bash
# Clone the repo
git clone https://github.com/your-username/pky-ai.git
cd pky-ai/pky-ai-backend

# Copy the environment template
cp .env.example .env

# Open .env and paste your OpenRouter key
# OPENROUTER_API_KEY="sk-or-v1-your-key-here"
```

---

### Step 3 — Run the Backend

```bash
# Create a virtual environment
python -m venv venv
venv\Scripts\activate        # Windows
source venv/bin/activate     # Mac/Linux

# Install dependencies
pip install -r requirements.txt

# Start the server
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

> Backend will be running at **http://localhost:8000**  
> API docs at **http://localhost:8000/docs**

---

### Step 4 — Build the Android App

1. Open `pky-ai-android/` in **Android Studio**
2. Connect your phone or start an emulator
3. Update the backend URL in `MainActivity.kt`:
   - **Emulator**: `ws://10.0.2.2:8000/ws/voice`
   - **Physical device**: `ws://YOUR_COMPUTER_IP:8000/ws/voice`
4. Click **Run ▶**

---

## 🧠 Top 10 Free Models — Pick Your Brain

You can configure which model powers each use-case in your `.env` file:

| # | ENV Variable | Default Model | Best For |
|---|---|---|---|
| 1 | `PKY_DEFAULT_MODEL` | `meta-llama/llama-3.3-70b-instruct:free` | General chat (GPT-4 level) |
| 2 | `PKY_CODING_MODEL` | `qwen/qwen3-coder:free` | Code generation (262K ctx) |
| 3 | `PKY_SUPERVISOR_MODEL` | `google/gemini-2.0-flash-exp:free` | Routing supervisor (1M ctx) |
| 4 | `PKY_REASONING_MODEL` | `deepseek/deepseek-r1:free` | Deep analysis & reasoning |
| 5 | `PKY_DOCUMENT_MODEL` | `nvidia/nemotron-3-super:free` | Long documents & RAG |
| 6 | `PKY_PLANNING_MODEL` | `openai/gpt-oss-120b:free` | Planning & architecture |
| 7 | *(adaptive)* | `stepfun/step-3.5-flash:free` | Extended thinking |
| 8 | *(agent)* | `nvidia/nemotron-3-nano-30b-a3b:free` | Agentic workflows |
| 9 | *(memory)* | `moonshotai/kimi-k2.5:free` | Long multi-turn sessions |
| 10 | *(rag)* | `qwen/qwen3-next-80b:free` | RAG & function calling |

**Free tier limits:** 20 requests/minute, 200 requests/day per model. Add OpenRouter credits if you need more.

---

## 📁 Project Structure

```
pky-ai/
├── pky-ai-android/       # Kotlin + Jetpack Compose Android App
│   └── app/src/main/
│       ├── MainActivity.kt     # Voice UI + Navigation
│       ├── ui/                 # Glassmorphism 2.0 screens
│       └── ui/theme/           # Color, Type, Theme
├── pky-ai-backend/       # Python FastAPI Backend
│   ├── main.py                 # FastAPI app entry point
│   ├── services/               # AI, Voice, Task, News services
│   ├── .env.example            # ← Copy this to .env and add your key
│   └── requirements.txt
├── react-app/            # Web blueprint / planning UI
├── API - JSON/           # Brain configurations (model settings)
└── README.md             # This file
```

---

## 🔧 How Users Can Change the AI Model

Users can switch models **without touching any code** — just edit `.env`:

```bash
# Use DeepSeek for all requests (great for reasoning)
PKY_DEFAULT_MODEL="deepseek/deepseek-r1:free"

# Use Gemini for coding
PKY_CODING_MODEL="google/gemini-2.0-flash-exp:free"
```

Then restart the backend: `uvicorn main:app --reload`

---

## 🔒 Security

| ✅ Safe to commit | ❌ NEVER commit |
|---|---|
| `API - JSON/settings.json` (keys are blank) | `.env` (your private key) |
| `.env.example` (template only) | Any file with `sk-or-v1-...` |
| Source code | `data/` directory |

---

## 🤝 Contributing

1. Fork the repo
2. Create a branch: `git checkout -b feature/my-feature`
3. Make your changes
4. Submit a Pull Request

---

## 📄 License

MIT License — see [LICENSE](LICENSE)

---

<p align="center">Built with ❤️ by PKY AI Assistant Team</p>
