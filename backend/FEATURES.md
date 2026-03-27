# PKY AI Assistant Features Documentation

## Overview
PKY AI Assistant is an open-source voice-first AI assistant with self-correction capabilities, daily reporting, and user persona management.

## Core Features

### 1. Voice Interaction
- **WebSocket-based**: Real-time audio streaming via `/ws/voice`
- **ASR**: Whisper speech-to-text (with fallback simulation)
- **TTS**: Coqui text-to-speech with female voice
- **Audio Format**: PCM 16-bit, 16kHz sampling rate

### 2. User Persona & Self-Correction
- **Persona Storage**: JSON-based user profiles in `data/` directory
- **Preferences**: Name, language, and custom settings
- **Learning History**: Tracks conversations and corrections
- **Self-Correction**: Users can correct PKY AI Assistant's responses

#### Correction Syntax
Voice: "Correct [original] to [corrected]"
Example: "Correct schedule to arrange"

#### API Endpoints
- `POST /user/preferences`: Update user preferences
- `POST /user/correction`: Record user corrections

### 3. Daily Reports
- **Automated Generation**: Combines news, tasks, and user insights
- **News Analysis**: Keyword extraction from aggregated news
- **Task Summary**: Pending and upcoming tasks
- **Personalized Insights**: Based on user persona

#### Report Structure
```json
{
  "date": "2026-03-10",
  "news_summary": {
    "total_items": 25,
    "top_keywords": ["technology", "AI", "innovation"],
    "items": [...]
  },
  "tasks_summary": {
    "total_tasks": 10,
    "pending_tasks": 3,
    "upcoming_tasks": [...]
  },
  "insights": "Today is 2026-03-10. You have 3 pending tasks..."
}
```

### 4. Task Management
- **Create Tasks**: `POST /tasks/create`
- **Track Status**: Pending, completed, overdue
- **Integration**: Linked with job search features

### 5. News Aggregation
- **Sources**: Wikipedia, Times of India, BBC, Google News, Feedly, YouTube
- **RSS Parsing**: Using feedparser library
- **Daily Digest**: `GET /news/digest`

## API Reference

### Voice WebSocket (`/ws/voice`)
**Protocol**: WebSocket (binary audio chunks)
**Flow**:
1. Client sends audio chunks (PCM 16-bit)
2. Server transcribes with Whisper
3. LLM processes intent
4. TTS generates response audio
5. Server sends audio back

**Correction Commands**:
- "Correct [original] to [corrected]"
- Example: "Correct schedule to arrange"

### REST Endpoints

#### `GET /`
Health check endpoint.

#### `GET /news/digest`
Returns daily news digest with items and summary.

#### `POST /tasks/create`
Creates a new task.
```json
{
  "title": "Task title",
  "description": "Task description",
  "due_date": "2026-12-31"
}
```

#### `GET /reports/daily`
Generates daily report with news, tasks, and insights.

#### `POST /user/preferences`
Updates user preferences.
```json
{
  "user_id": "user123",
  "preferences": {
    "name": "Alice",
    "language": "en"
  }
}
```

#### `POST /user/correction`
Records user correction for self-learning.
```json
{
  "user_id": "user123",
  "original": "schedule",
  "corrected": "arrange",
  "context": "User preference"
}
```

## Architecture

### Service Layers
1. **VoiceService**: ASR and TTS processing
2. **LLMService**: Intent processing and response generation
3. **TaskPlanner**: Task management and scheduling
4. **NewsService**: News aggregation and digestion
5. **ReportService**: Daily report generation
6. **UserPersona**: User profile and learning management

### Data Flow
```
User Input (Voice/Text)
    ↓
Intent Recognition
    ↓
Service Routing (Task/News/Report)
    ↓
Response Generation
    ↓
Output (Voice/Text)
```

## Self-Learning Mechanism

### Correction Learning
1. User provides correction: "Correct X to Y"
2. System records correction in user persona
3. Future responses use corrected terminology
4. Learning history is maintained for analysis

### Preference Adaptation
- Name personalization in greetings
- Language and format preferences
- Custom response styles

## Daily Report Generation

### Components
1. **News Analysis**: Keyword extraction and topic clustering
2. **Task Analytics**: Pending counts, deadline tracking
3. **User Insights**: Personalized recommendations based on history

### Schedule
- Reports generated on demand
- Can be scheduled via cron job or task scheduler
- Stored in user persona for historical tracking

## Setup and Configuration

### Environment Variables
```bash
USE_LOCAL_LLM=false  # Use local LLM instead of placeholder
OPENAI_API_KEY=      # API key for cloud LLM (optional)
```

### Data Storage
- User personas: `data/{user_id}_persona.json`
- Task data: Stored in memory (persistent storage recommended for production)
- News cache: In-memory with refresh capability

## Testing

### Unit Tests
- `test_persona.py`: User persona and correction features
- `test_backend.py`: Full API endpoint testing

### Manual Testing
1. Start backend: `uvicorn main:app --reload`
2. Test WebSocket: Use Android app or WebSocket client
3. Test REST endpoints: Use curl or Postman

## Production Considerations

### Security
- Implement authentication for user endpoints
- Validate and sanitize all inputs
- Use HTTPS for production deployments

### Performance
- Implement caching for news aggregation
- Use connection pooling for database (if added)
- Monitor ASR/TTS latency

### Scalability
- Consider Redis for session management
- Use message queue for heavy processing
- Implement rate limiting

## Future Enhancements
- Multi-language support
- Voice cloning with XTTS v2
- Integration with calendar services (Google/Microsoft)
- Job search automation
- Interview preparation features