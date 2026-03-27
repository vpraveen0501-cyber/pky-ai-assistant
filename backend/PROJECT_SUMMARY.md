# PKY AI Assistant Project Summary

## Overview
PKY AI Assistant is an open-source voice-first AI assistant that integrates with the PKY AI Assistant Android application. The system provides natural voice interaction, task automation, and news aggregation.

## Components

### 1. Android App (PKY AI Assistant)
- **Main Activity**: `MainActivity.kt` with Compose UI for voice interaction
- **Voice Service**: `VoiceService.kt` handles recording, WebSocket, and playback
- **Permissions**: Audio recording, internet access, and storage permissions
- **UI**: Simple, accessible interface with recording controls

### 2. Backend (PKY AI Assistant)
- **Framework**: FastAPI (Python)
- **Voice Pipeline**: Whisper ASR → LLM Processing → Coqui TTS
- **Services**:
  - `VoiceService`: ASR and TTS processing
  - `LLMService`: Intent processing and response generation
  - `TaskPlanner`: Scheduling and task management
  - `NewsService`: RSS feed aggregation
- **API Endpoints**:
  - `GET /`: Health check
  - `WS /ws/voice`: WebSocket for voice interaction
  - `GET /news/digest`: Daily news digest
  - `POST /tasks/create`: Create new tasks

## Key Features
- **Voice-first interaction**: Record audio, send to backend, receive audio response
- **Open-source stack**: Whisper, Coqui TTS, FastAPI, Kotlin
- **Modular architecture**: Clear separation of concerns
- **Privacy-focused**: Local processing options, encrypted communications

## Development Setup

### Prerequisites
- Android Studio (for Android app)
- Python 3.12+ (for backend)
- Microphone access for voice recording

### Running the Backend
```bash
cd "pky-ai-backend"
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### Running the Android App
1. Open `pky-ai-android` in Android Studio
2. Connect to backend (update `backendUrl` in `MainActivity.kt`)
3. Run on emulator or physical device
4. Grant microphone permission

## Architecture Diagram
```
┌─────────────────────────────────────────────────────────────┐
│                     Android Device                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              MainActivity (Compose UI)              │   │
│  │  - Voice recording UI                               │   │
│  │  - Permission handling                              │   │
│  └───────────────────┬─────────────────────────────────┘   │
│                      │                                     │
│  ┌───────────────────▼─────────────────────────────────┐   │
│  │            VoiceService (Kotlin)                    │   │
│  │  - Audio recording (MediaRecorder)                 │   │
│  │  - WebSocket connection to backend                 │   │
│  │  - Audio playback (MediaPlayer)                    │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │ WebSocket (audio bytes)
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                    Backend Server                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │               FastAPI (main.py)                     │   │
│  │  - WebSocket endpoint /ws/voice                    │   │
│  │  - REST endpoints for tasks, news                  │   │
│  └───────────────────┬─────────────────────────────────┘   │
│                      │                                     │
│  ┌───────────────────▼─────────────────────────────────┐   │
│  │              VoiceService (Python)                  │   │
│  │  - Whisper ASR (speech-to-text)                    │   │
│  │  - Coqui TTS (text-to-speech)                      │   │
│  └───────────────────┬─────────────────────────────────┘   │
│                      │                                     │
│  ┌───────────────────▼─────────────────────────────────┐   │
│  │              LLMService (Python)                    │   │
│  │  - Intent processing & response generation         │   │
│  │  - Task planning integration                       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Next Steps
1. [ ] Complete backend dependency installation (Torch, Coqui TTS)
2. [ ] Test voice pipeline end-to-end
3. [ ] Add error handling and retries
4. [ ] Implement proper logging
5. [ ] Add unit tests for all services
6. [ ] Create CI/CD pipelines
7. [ ] Deploy backend to cloud (optional)

## Challenges & Solutions
- **Challenge**: Voice latency
  **Solution**: Use efficient ASR/TTS models, implement streaming where possible
- **Challenge**: Backend dependencies (Torch, Coqui TTS)
  **Solution**: Use Docker for consistent environment, provide fallbacks
- **Challenge**: Android permissions
  **Solution**: Request permissions at runtime, provide clear UX

## Android Development Phases (PKY AI Assistant Plan)

### Phase 0: Discovery & Prerequisites (2-4 weeks)
- Define target audience and device constraints
- Decide on on-device vs cloud mix
- Create lightweight prototype

### Phase 1: MVP Foundations (8-12 weeks)
- Core voice UX: wake word, ASR, TTS
- Local memory store
- Task orchestrator skeleton
- Basic integrations: calendar, reminders, notes

### Phase 2: Core Automation & Expansion (8-12 weeks)
- Expand integrations: messaging, emails, weather, web queries
- Improve NLU: intent classification, slot filling
- Multi-turn conversations
- Memory controls

### Phase 3: Smart Features & Personalization (12-16 weeks)
- Advanced task planning & integrations
- Smart home orchestration
- Enhanced TTS with persona options

### Success Metrics
- Latency: < 2s per core interaction
- Intent success rate: ≥ 90%
- Crash-free rate: > 99%

## License
Proprietary - PKY AI Assistant Internal