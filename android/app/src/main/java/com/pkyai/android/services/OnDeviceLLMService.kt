package com.pkyai.android.services

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OnDeviceLLMService — hybrid on-device intelligence for PKY AI Assistant.
 *
 * Tier 1 — Gemini Nano (via ML Kit AICore):
 *   - Fast, on-device inference using Google's AICore system service
 *   - Used for: quick summaries, smart replies, screen context analysis
 *   - Requires: Android 12+ with AICore support (Pixel 8+, Samsung S24+)
 *   - NEVER sends data to cloud — all processing local
 *
 * Tier 2 — MediaPipe LLM Inference (Gemma-3 1B 4-bit quantized):
 *   - Runs fully offline on any device with 4GB+ RAM
 *   - Used for: offline fallback, complex queries when network unavailable
 *   - Model file: stored in internal app storage (~800MB after download)
 *
 * Tier 3 — Cloud LLM (OpenRouter via backend):
 *   - Used for: complex multi-step reasoning, planning, coding
 *   - Requires internet connectivity
 *
 * The [processQuery] method automatically selects the appropriate tier.
 *
 * Setup:
 *   1. Add to build.gradle:
 *      implementation("com.google.android.gms:play-services-mlkit-language-id:17.0.0")
 *      implementation("com.google.ai.edge.aicore:aicore:0.0.1-exp")
 *      implementation("com.google.mediapipe:tasks-genai:0.10.14")
 *
 *   2. Download Gemma-3 1B model (one-time):
 *      ModelDownloadService.downloadGemma3()
 */
@Singleton
class OnDeviceLLMService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "OnDeviceLLMService"
        private const val GEMMA3_MODEL_FILENAME = "gemma3_1b_it_q4.task"
        private const val MAX_ONDEVICE_TOKENS = 512
    }

    // Gemini Nano handle (null when AICore is unavailable on this device)
    private var geminiNanoModel: Any? = null

    // MediaPipe LLM Inference handle (null when model not downloaded)
    private var mediaPipeLlm: Any? = null

    private var isInitialized = false

    // ──────────────────────────────────────────────────────────────
    // Initialization
    // ──────────────────────────────────────────────────────────────

    /**
     * Initialize available on-device models.
     * Call once from Application.onCreate() or a background coroutine.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Initializing on-device LLM service")
        initGeminiNano()
        initMediaPipe()
        isInitialized = true
        Log.i(TAG, "On-device LLM ready: nano=${geminiNanoModel != null}, mediapipe=${mediaPipeLlm != null}")
    }

    private fun initGeminiNano() {
        // Gemini Nano via ML Kit GenAI APIs
        // Direct instantiation used here; replace with Hilt injection if preferred.
        //
        // Required dependency in build.gradle:
        //   implementation("com.google.ai.edge.aicore:aicore:0.0.1-exp")
        //
        try {
            val configClass = Class.forName("com.google.ai.edge.aicore.GenerationConfig")
            val configBuilder = configClass.getMethod("builder").invoke(null)
            configBuilder.javaClass.getMethod("temperature", Float::class.java).invoke(configBuilder, 0.7f)
            configBuilder.javaClass.getMethod("topK", Int::class.java).invoke(configBuilder, 40)
            configBuilder.javaClass.getMethod("maxOutputTokens", Int::class.java).invoke(configBuilder, MAX_ONDEVICE_TOKENS)
            val config = configBuilder.javaClass.getMethod("build").invoke(configBuilder)

            val modelClass = Class.forName("com.google.ai.edge.aicore.GenerativeModel")
            geminiNanoModel = modelClass.getConstructor(String::class.java, configClass)
                .newInstance("gemini-nano", config)

            Log.i(TAG, "Gemini Nano initialized via AICore")
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "AICore not available — add com.google.ai.edge.aicore:aicore to build.gradle")
        } catch (e: Exception) {
            Log.w(TAG, "Gemini Nano init failed (device may not support AICore): $e")
        }
    }

    private fun initMediaPipe() {
        // MediaPipe LLM Inference — Gemma-3 1B 4-bit quantized
        //
        // Required dependency in build.gradle:
        //   implementation("com.google.mediapipe:tasks-genai:0.10.14")
        //
        val modelFile = File(context.filesDir, GEMMA3_MODEL_FILENAME)
        if (!modelFile.exists()) {
            Log.i(TAG, "Gemma-3 model not downloaded yet. Call ModelDownloadService.downloadGemma3() first.")
            return
        }

        try {
            val optionsClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions")
            val optionsBuilder = optionsClass.getMethod("builder").invoke(null)
            optionsBuilder.javaClass.getMethod("setModelPath", String::class.java)
                .invoke(optionsBuilder, modelFile.absolutePath)
            optionsBuilder.javaClass.getMethod("setMaxTokens", Int::class.java)
                .invoke(optionsBuilder, MAX_ONDEVICE_TOKENS)
            optionsBuilder.javaClass.getMethod("setTopK", Int::class.java).invoke(optionsBuilder, 40)
            optionsBuilder.javaClass.getMethod("setTemperature", Float::class.java).invoke(optionsBuilder, 0.7f)
            val options = optionsBuilder.javaClass.getMethod("build").invoke(optionsBuilder)

            val llmClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
            mediaPipeLlm = llmClass.getMethod("createFromOptions", Context::class.java, optionsClass)
                .invoke(null, context, options)

            Log.i(TAG, "MediaPipe LLM (Gemma-3 1B) initialized from ${modelFile.absolutePath}")
        } catch (e: ClassNotFoundException) {
            Log.w(TAG, "MediaPipe GenAI not available — add com.google.mediapipe:tasks-genai to build.gradle")
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe LLM init failed: $e")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Inference Router
    // ──────────────────────────────────────────────────────────────

    /**
     * Determine the best inference tier for a given query.
     *
     * Selection logic:
     *   - Screen context / privacy-sensitive → Gemini Nano (on-device only)
     *   - Short summaries / smart replies → Gemini Nano
     *   - Offline mode → MediaPipe (Gemma-3)
     *   - Complex reasoning → Cloud (caller falls through to backend)
     */
    fun selectTier(query: String, isOffline: Boolean, isScreenContext: Boolean): InferenceTier {
        if (isScreenContext) return InferenceTier.GEMINI_NANO   // TRAPS: screen data never leaves device
        if (isOffline) return if (mediaPipeLlm != null) InferenceTier.MEDIAPIPE else InferenceTier.UNAVAILABLE
        if (query.length < 200 && geminiNanoModel != null) return InferenceTier.GEMINI_NANO
        return InferenceTier.CLOUD
    }

    enum class InferenceTier { GEMINI_NANO, MEDIAPIPE, CLOUD, UNAVAILABLE }

    // ──────────────────────────────────────────────────────────────
    // Gemini Nano Inference
    // ──────────────────────────────────────────────────────────────

    /**
     * Run inference via Gemini Nano (AICore).
     * All processing on-device. Never sends data to any network.
     */
    suspend fun generateNano(prompt: String): String = withContext(Dispatchers.Default) {
        val model = geminiNanoModel ?: return@withContext ""
        try {
            val response = model.javaClass
                .getMethod("generateContent", String::class.java)
                .invoke(model, prompt)
            response?.javaClass?.getMethod("getText")?.invoke(response) as? String ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Nano inference failed: $e")
            ""
        }
    }

    /**
     * Summarize text on-device via Gemini Nano.
     */
    suspend fun summarize(text: String): String {
        val prompt = "Summarize this concisely in 2-3 sentences:\n\n$text"
        return generateNano(prompt)
    }

    /**
     * Process screen context on-device. TRAPS-compliant — data never leaves device.
     *
     * @param screenText Text extracted from AssistStructure (what's visible on screen).
     * @param userQuery  What the user asked about the screen content.
     */
    suspend fun processScreenContext(screenText: String, userQuery: String): String {
        val prompt = buildString {
            append("You are a privacy-first AI assistant. The user is looking at their screen.\n\n")
            append("VISIBLE SCREEN CONTENT:\n${screenText.take(2000)}\n\n")
            append("USER REQUEST: $userQuery\n\n")
            append("Provide a helpful, concise response. Do not mention that you can see the screen.")
        }
        return generateNano(prompt)
    }

    /**
     * Generate a smart reply suggestion for a given message thread.
     */
    suspend fun generateSmartReply(conversationContext: String): String {
        val prompt = "Given this conversation, suggest one short natural reply (max 15 words):\n\n$conversationContext"
        return generateNano(prompt)
    }

    // ──────────────────────────────────────────────────────────────
    // MediaPipe (Gemma-3 1B) Inference — Offline Fallback
    // ──────────────────────────────────────────────────────────────

    /**
     * Run inference via MediaPipe Gemma-3 1B. Works completely offline.
     */
    suspend fun generateOffline(prompt: String): String = withContext(Dispatchers.Default) {
        val llm = mediaPipeLlm ?: return@withContext "Offline AI model not available. Please download it from Settings."
        try {
            val response = llm.javaClass
                .getMethod("generateResponse", String::class.java)
                .invoke(llm, prompt)
            response as? String ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe inference failed: $e")
            ""
        }
    }

    // ──────────────────────────────────────────────────────────────
    // LoRA Weight Hot-Swap (MediaPipe only)
    // ──────────────────────────────────────────────────────────────

    /**
     * Load a LoRA adapter to specialize the base model for a domain.
     * Enables instant brain-mode switching without reloading the full model.
     *
     * @param loraPath Path to .flatbuffer LoRA weights in internal storage.
     */
    fun loadLoRAWeights(loraPath: String) {
        val llm = mediaPipeLlm ?: run {
            Log.w(TAG, "MediaPipe LLM not initialized — cannot load LoRA weights")
            return
        }
        val loraFile = File(context.filesDir, loraPath)
        if (!loraFile.exists()) {
            Log.w(TAG, "LoRA file not found: $loraPath")
            return
        }
        try {
            llm.javaClass.getMethod("loadLoRAWeights", String::class.java)
                .invoke(llm, loraFile.absolutePath)
            Log.i(TAG, "LoRA weights loaded: $loraPath")
        } catch (e: Exception) {
            Log.e(TAG, "LoRA load failed: $e")
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Cleanup
    // ──────────────────────────────────────────────────────────────

    fun release() {
        try {
            mediaPipeLlm?.javaClass?.getMethod("close")?.invoke(mediaPipeLlm)
        } catch (_: Exception) {}
        geminiNanoModel = null
        mediaPipeLlm = null
        isInitialized = false
        Log.d(TAG, "OnDeviceLLMService released")
    }
}
