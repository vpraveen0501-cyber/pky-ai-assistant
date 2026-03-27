package com.pkyai.android.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.pkyai.android.util.LocalLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import com.google.gson.Gson
import com.google.gson.JsonParser
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VoiceService — dual-mode voice transport for PKY AI Assistant.
 *
 * Mode A — LiveKit WebRTC (preferred):
 *   - Fetches a room token from GET /voice/token
 *   - Connects via LiveKit Android SDK for bi-directional, low-latency audio
 *   - Handles barge-in by sending "INTERRUPT" data channel message
 *   - VART target: < 500ms (dual-streaming Kokoro TTS on backend)
 *
 * Mode B — WebSocket fallback (when LiveKit is unavailable):
 *   - Legacy AudioRecord → raw PCM → OkHttp WebSocket → Whisper → gTTS
 *   - VART: 3–5s (traditional TTS)
 *   - Auto-selected when /voice/token returns { fallback: "websocket" }
 *
 * The active mode is determined at connect time by querying /system/capabilities.
 */
class VoiceService(
    private val context: Context,
    private val backendBaseUrl: String,     // e.g. "http://10.0.2.2:8000"
    private val client: OkHttpClient,
    private var authToken: String? = null,
    private val onMessageReceived: (String) -> Unit = {},
    private val onAmplitudeChanged: (Float) -> Unit = {}   // live mic amplitude for waveform UI
) {

    // ──────────────────────────────────────────────────────────────
    // Transport mode
    // ──────────────────────────────────────────────────────────────

    enum class TransportMode { LIVEKIT, WEBSOCKET }

    private var transportMode = TransportMode.WEBSOCKET

    // ──────────────────────────────────────────────────────────────
    // LiveKit room handle (lazy import — optional dependency)
    // ──────────────────────────────────────────────────────────────

    private var liveKitRoom: Any? = null   // io.livekit.android.room.Room

    // ──────────────────────────────────────────────────────────────
    // WebSocket / audio fields (Mode B)
    // ──────────────────────────────────────────────────────────────

    private var audioRecord: AudioRecord? = null
    private var playbackTrack: AudioTrack? = null
    private var webSocket: WebSocket? = null
    private val isRecording = AtomicBoolean(false)

    // Audio config — Whisper 16kHz mono PCM16
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).also { size ->
        check(size > 0) {
            "Unsupported audio config: AudioRecord.getMinBufferSize returned $size. " +
            "Check sampleRate=$sampleRate, channelConfig=$channelConfig, audioFormat=$audioFormat."
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private var selectedModel: String = "general"

    // ──────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────

    fun setAuthToken(token: String) { this.authToken = token }
    fun setModel(model: String) { this.selectedModel = model }

    /**
     * Negotiate transport mode by querying /system/capabilities, then start voice.
     * Prefers LiveKit when available; falls back to WebSocket automatically.
     */
    fun startRecording() {
        if (isRecording.getAndSet(true)) return
        reconnectAttempts = 0

        scope.launch {
            val useLiveKit = negotiateTransport()
            if (useLiveKit) {
                startLiveKitSession()
            } else {
                startWebSocketSession()
            }
        }
    }

    fun stopRecordingAndSend() {
        isRecording.set(false)
        when (transportMode) {
            TransportMode.LIVEKIT -> stopLiveKitSession()
            TransportMode.WEBSOCKET -> stopWebSocketSession()
        }
    }

    /**
     * Barge-in: user interrupts AI while it is speaking.
     * LiveKit mode → sends INTERRUPT data channel message.
     * WebSocket mode → server handles naturally (no special signal needed).
     */
    fun interruptAiSpeech() {
        when (transportMode) {
            TransportMode.LIVEKIT -> sendLiveKitInterrupt()
            TransportMode.WEBSOCKET -> {
                // Flush local playback buffer
                try { playbackTrack?.stop() } catch (_: Exception) {}
                initPlaybackTrack()
            }
        }
    }

    fun cleanup() {
        isRecording.set(false)
        scope.cancel()
        stopLiveKitSession()
        stopWebSocketSession()
        Log.d(TAG, "VoiceService cleanup complete")
    }

    // ──────────────────────────────────────────────────────────────
    // Transport Negotiation
    // ──────────────────────────────────────────────────────────────

    private suspend fun negotiateTransport(): Boolean {
        return try {
            val token = authToken ?: return false
            val req = okhttp3.Request.Builder()
                .url("$backendBaseUrl/system/capabilities")
                .addHeader("Authorization", "Bearer $token")
                .build()
            val response = kotlinx.coroutines.withContext(Dispatchers.IO) {
                client.newCall(req).execute()
            }
            if (!response.isSuccessful) return false
            val body = response.body?.string() ?: return false
            // Simple JSON parse for "voice_transport":"livekit"
            val useLiveKit = body.contains("\"voice_transport\":\"livekit\"")
            Log.d(TAG, "Transport negotiated: ${if (useLiveKit) "LiveKit" else "WebSocket"}")
            useLiveKit
        } catch (e: Exception) {
            Log.w(TAG, "Transport negotiation failed, defaulting to WebSocket: $e")
            false
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Mode A — LiveKit WebRTC
    // ──────────────────────────────────────────────────────────────

    private suspend fun startLiveKitSession() {
        transportMode = TransportMode.LIVEKIT
        Log.d(TAG, "Starting LiveKit session")

        try {
            // Fetch room token from backend
            val tokenResult = fetchLiveKitToken() ?: run {
                Log.w(TAG, "Failed to get LiveKit token — falling back to WebSocket")
                startWebSocketSession()
                return
            }

            val liveKitUrl = tokenResult.first
            val roomToken = tokenResult.second

            // Dynamically load LiveKit SDK classes to keep the import optional
            connectLiveKitRoom(liveKitUrl, roomToken)

        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "LiveKit SDK not in classpath — falling back to WebSocket. Add io.livekit:livekit-android to build.gradle")
            startWebSocketSession()
        } catch (e: Exception) {
            Log.e(TAG, "LiveKit session failed: $e — falling back to WebSocket")
            startWebSocketSession()
        }
    }

    private suspend fun fetchLiveKitToken(): Pair<String, String>? {
        return try {
            val token = authToken ?: return null
            val req = okhttp3.Request.Builder()
                .url("$backendBaseUrl/voice/token")
                .addHeader("Authorization", "Bearer $token")
                .build()
            val response = kotlinx.coroutines.withContext(Dispatchers.IO) {
                client.newCall(req).execute()
            }
            val body = response.body?.string() ?: return null

            // Parse { "token": "...", "url": "ws://..." }
            val json = JsonParser.parseString(body).asJsonObject
            val tokenValue = json.get("token")?.asString
            val urlValue = json.get("url")?.asString

            if (tokenValue != null && urlValue != null) Pair(urlValue, tokenValue) else null
        } catch (e: Exception) {
            Log.e(TAG, "Token fetch failed: $e")
            null
        }
    }

    private fun connectLiveKitRoom(url: String, token: String) {
        // LiveKit Android SDK connection via reflection (optional dependency)
        // When livekit-android is added to build.gradle, replace with direct API call:
        //
        //   liveKitRoom = LiveKit.create(context.applicationContext)
        //   (liveKitRoom as Room).connect(url, token)
        //   (liveKitRoom as Room).localParticipant.setMicrophoneEnabled(true)
        //
        // This reflection approach gracefully handles the SDK being absent.
        try {
            val liveKitClass = Class.forName("io.livekit.android.LiveKit")
            val createMethod = liveKitClass.getMethod("create", Context::class.java)
            val room = createMethod.invoke(null, context.applicationContext)
            liveKitRoom = room

            // room.connect(url, token)
            val connectMethod = room.javaClass.getMethod("connect", String::class.java, String::class.java)
            scope.launch {
                connectMethod.invoke(room, url, token)
                // Enable microphone
                val participantGetter = room.javaClass.getMethod("getLocalParticipant")
                val participant = participantGetter.invoke(room)
                val micMethod = participant.javaClass.getMethod("setMicrophoneEnabled", Boolean::class.java)
                micMethod.invoke(participant, true)
                Log.d(TAG, "LiveKit room connected: $url")
                onMessageReceived("PKY AI is ready (LiveKit mode)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "LiveKit reflection connect failed: $e")
            throw e
        }
    }

    private fun sendLiveKitInterrupt() {
        try {
            val room = liveKitRoom ?: return
            val participantGetter = room.javaClass.getMethod("getLocalParticipant")
            val participant = participantGetter.invoke(room)
            val publishDataMethod = participant.javaClass.getMethod(
                "publishData", ByteArray::class.java, Any::class.java
            )
            publishDataMethod.invoke(participant, "INTERRUPT".toByteArray(), null)
            Log.d(TAG, "INTERRUPT signal sent via LiveKit data channel")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send LiveKit interrupt: $e")
        }
    }

    private fun stopLiveKitSession() {
        try {
            val room = liveKitRoom ?: return
            val disconnectMethod = room.javaClass.getMethod("disconnect")
            disconnectMethod.invoke(room)
            liveKitRoom = null
            Log.d(TAG, "LiveKit room disconnected")
        } catch (e: Exception) {
            Log.w(TAG, "LiveKit disconnect error: $e")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Mode B — WebSocket (legacy fallback)
    // ──────────────────────────────────────────────────────────────

    private fun startWebSocketSession() {
        transportMode = TransportMode.WEBSOCKET
        Log.d(TAG, "Starting WebSocket session (fallback mode)")

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate, channelConfig, audioFormat, bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                isRecording.set(false)
                return
            }

            audioRecord?.startRecording()
            connectWebSocket()
            initPlaybackTrack()

            scope.launch {
                val batchSize = bufferSize * 5
                val batchBuffer = ByteArray(batchSize)
                var batchIndex = 0
                val readBuffer = ByteArray(bufferSize)

                try {
                    while (isRecording.get() && isActive) {
                        val bytesRead = audioRecord?.read(readBuffer, 0, bufferSize) ?: 0
                        if (bytesRead > 0) {
                            // Compute RMS amplitude for waveform visualizer
                            val rms = computeRms(readBuffer, bytesRead)
                            onAmplitudeChanged(rms)

                            val copyLen = minOf(bytesRead, batchSize - batchIndex)
                            System.arraycopy(readBuffer, 0, batchBuffer, batchIndex, copyLen)
                            batchIndex += copyLen

                            if (batchIndex >= batchSize) {
                                webSocket?.send(batchBuffer.toByteString(0, batchIndex))
                                batchIndex = 0
                            }
                        }
                        yield()
                    }
                    if (batchIndex > 0) {
                        webSocket?.send(batchBuffer.toByteString(0, batchIndex))
                    }
                } catch (e: Exception) {
                    LocalLogger.e(TAG, "Recording loop error", e)
                }
            }

            LocalLogger.d(TAG, "WebSocket recording started")

        } catch (e: Exception) {
            isRecording.set(false)
            LocalLogger.e(TAG, "Failed to start WebSocket session", e)
        }
    }

    private fun stopWebSocketSession() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        try { playbackTrack?.stop() } catch (_: Exception) {}
        playbackTrack?.release()
        playbackTrack = null
        webSocket?.close(1000, "Recording stopped")
        webSocket = null
    }

    private fun connectWebSocket() {
        val wsBase = backendBaseUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://")
        val url = "$wsBase/ws/voice?model=$selectedModel"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${authToken ?: ""}")
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempts = 0
                Log.d(TAG, "WebSocket opened")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                onMessageReceived(text)
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                playAudio(bytes.toByteArray())
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleReconnection()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: $t")
                handleReconnection()
            }
        })
    }

    private fun handleReconnection() {
        if (!isRecording.get()) return
        if (reconnectAttempts < maxReconnectAttempts) {
            val backoff = minOf((1000L * Math.pow(2.0, reconnectAttempts.toDouble())).toLong(), 60_000L)
            reconnectAttempts++
            scope.launch {
                kotlinx.coroutines.delay(backoff)
                if (isRecording.get()) connectWebSocket()
            }
        } else {
            Log.e(TAG, "Max reconnects reached")
            onMessageReceived("Connection lost. Please try again.")
            stopRecordingAndSend()
        }
    }

    private fun initPlaybackTrack() {
        playbackTrack?.release()
        playbackTrack = null
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        playbackTrack = AudioTrack.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        playbackTrack?.play()
    }

    private fun playAudio(audioData: ByteArray) {
        try {
            playbackTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback error: $e")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────

    /** Compute normalized RMS amplitude [0..1] from a PCM16 buffer. */
    private fun computeRms(buffer: ByteArray, length: Int): Float {
        var sum = 0.0
        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
            sum += sample.toDouble() * sample.toDouble()
            i += 2
        }
        val sampleCount = length / 2
        if (sampleCount == 0) return 0f
        val rms = Math.sqrt(sum / sampleCount)
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        private const val TAG = "VoiceService"
    }
}
