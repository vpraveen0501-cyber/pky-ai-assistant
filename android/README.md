# PKY AI Assistant - Next Generation AI Assistant

## Overview
A next-generation personal AI assistant Android application. This project implements a sophisticated AI assistant with voice interaction, proactive capabilities, and deep system integration.

**Note**: This project now includes PKY AI Assistant integration - a voice-first AI assistant powered by open-source technology.

## Project Structure
```
pky-ai-android/
├── app/                 # Android application source
│   ├── src/main/java/com/pkyai/android/
│   │   ├── MainActivity.kt          # Main activity with voice UI
│   │   ├── voice/                   # Voice service module
│   │   │   └── VoiceService.kt      # Voice recording, WebSocket, playback
│   │   └── ui/theme/                # UI theme
│   ├── build.gradle                 # App dependencies
│   └── src/main/AndroidManifest.xml # Permissions and app config
├── docs/               # Project documentation
└── README.md          # This file
```

## Key Features
- 🎤 Voice-first interface with wake word detection
- 🧠 On-device & cloud AI capabilities
- 🔒 Privacy-first architecture with local data storage
- 🏠 Smart home integration
- 📅 Task automation and scheduling
- 💬 Natural language understanding
- 🎙️ **PKY AI Assistant Voice Integration**: Open-source voice assistant with Whisper ASR and Coqui TTS

## Technology Stack
### Android App
- **Platform**: Android (Kotlin)
- **UI**: Jetpack Compose
- **Voice**: Standard Android MediaRecorder/MediaPlayer
- **Networking**: OkHttp WebSocket for voice, Retrofit for REST
- **Architecture**: MVVM with Clean Architecture principles

### PKY AI Assistant Backend
- **Language**: Python
- **API Framework**: FastAPI
- **ASR**: Whisper (OpenAI)
- **TTS**: Coqui TTS
- **Task Planning**: Custom Python service
- **News Aggregation**: RSS/Atom feeds

## PKY AI Assistant Integration
The Android app connects to the pky-ai-backend via WebSocket for voice interaction. The backend handles:
- Speech-to-text (Whisper ASR)
- Intent processing and response generation
- Text-to-speech (Coqui TTS)
- Task scheduling and news aggregation

## Development Phases
1. Phase 0: Discovery & Prerequisites (2-4 weeks)
2. Phase 1: MVP Foundations (8-12 weeks)
3. Phase 2: Core Automation (8-12 weeks)
4. Phase 3: Smart Features (12-16 weeks)
5. Phase 4: Quality & Scale (ongoing)

## Getting Started

### Android App
1. Open the project in Android Studio
2. Ensure you have Android SDK 34 and minimum SDK 26
3. Run the app on an emulator or physical device
4. Grant microphone permission when prompted

### PKY AI Assistant Backend
1. Navigate to `pky-ai-backend` directory
2. Install dependencies: `pip install -r requirements.txt`
3. Run the server: `uvicorn main:app --reload --host 0.0.0.0 --port 8000`
4. Update the `backendUrl` in `MainActivity.kt` to point to your backend

### Development Environment Setup
For development, use the Android emulator and forward ports:
```bash
adb forward tcp:8000 tcp:8000
```

Update the `backendUrl` in `MainActivity.kt`:
- For Android emulator: `ws://10.0.2.2:8000/ws/voice`
- For physical device: `ws://<your-backend-ip>:8000/ws/voice`

## License
Proprietary - PKY AI Assistant Internal