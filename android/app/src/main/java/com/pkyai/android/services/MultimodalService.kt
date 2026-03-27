package com.pkyai.android.services

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MultimodalService — camera vision + file analysis for PKY AI Assistant.
 *
 * Capabilities:
 *   1. Image + text → AI analysis via /chat/multimodal (Gemini Flash free)
 *   2. File upload → summary + ChromaDB storage via /user/analyze-document
 *   3. Bitmap compression for efficient upload
 *
 * Privacy:
 *   - Images sent to backend (and via backend to OpenRouter cloud model)
 *   - PrivacyAuditLog records the transmission
 *   - Future: route image queries through on-device vision model (MediaPipe)
 *     for privacy-sensitive image content
 */
@Singleton
class MultimodalService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "MultimodalService"
        private const val JPEG_QUALITY = 75       // balance quality vs upload size
        private const val MAX_IMAGE_DIMENSION = 1024  // downscale large images
    }

    /**
     * Analyze an image (from camera or gallery) with an optional text query.
     *
     * @param bitmap    The captured image.
     * @param query     User's question about the image (e.g. "What do you see?").
     * @param backendUrl Backend base URL (e.g. "http://10.0.2.2:8000").
     * @param authToken  JWT bearer token.
     * @return AI response string.
     */
    suspend fun analyzeImage(
        bitmap: Bitmap,
        query: String,
        backendUrl: String,
        authToken: String
    ): String = withContext(Dispatchers.IO) {

        // Log privacy audit event (image sent to cloud)
        PrivacyAuditLog.log(
            source = "Camera",
            dataType = "image",
            processedLocally = false,   // goes to OpenRouter via backend
            context = context
        )

        val base64Image = encodeImageToBase64(bitmap)

        val requestBody = JSONObject().apply {
            put("text", query.ifEmpty { "Describe what you see in this image" })
            put("image", base64Image)
            put("model", "google/gemini-2.0-flash-exp:free")
        }.toString()

        return@withContext try {
            val request = Request.Builder()
                .url("$backendUrl/chat/multimodal")
                .addHeader("Authorization", "Bearer $authToken")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                JSONObject(responseBody).optString("response", "No response from AI")
            } else {
                Log.e(TAG, "Multimodal API error ${response.code}: $responseBody")
                "Failed to analyze image (HTTP ${response.code})"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image analysis failed: $e")
            "Failed to analyze image: ${e.message}"
        }
    }

    /**
     * Upload a document file for AI summarization and memory storage.
     *
     * @param fileBytes  Raw file bytes.
     * @param filename   File name with extension (e.g. "report.pdf").
     * @param backendUrl Backend base URL.
     * @param authToken  JWT bearer token.
     * @return [DocumentAnalysisResult] with summary and word count.
     */
    suspend fun analyzeDocument(
        fileBytes: ByteArray,
        filename: String,
        backendUrl: String,
        authToken: String
    ): DocumentAnalysisResult = withContext(Dispatchers.IO) {

        PrivacyAuditLog.log(
            source = "FileUpload",
            dataType = "document",
            processedLocally = false,
            context = context,
            appPackage = filename
        )

        return@withContext try {
            val boundary = "PKYBoundary${System.currentTimeMillis()}"
            val body = buildMultipartBody(fileBytes, filename, boundary)

            val request = Request.Builder()
                .url("$backendUrl/user/analyze-document")
                .addHeader("Authorization", "Bearer $authToken")
                .addHeader("Content-Type", "multipart/form-data; boundary=$boundary")
                .post(body.toRequestBody("multipart/form-data; boundary=$boundary".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(responseBody)
                DocumentAnalysisResult(
                    summary = json.optString("summary", ""),
                    wordCount = json.optInt("word_count", 0),
                    stored = json.optBoolean("stored", false),
                    filename = filename
                )
            } else {
                Log.e(TAG, "Document analysis error ${response.code}: $responseBody")
                DocumentAnalysisResult(
                    summary = "Failed to analyze document (HTTP ${response.code})",
                    wordCount = 0,
                    stored = false,
                    filename = filename
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Document upload failed: $e")
            DocumentAnalysisResult(
                summary = "Upload failed: ${e.message}",
                wordCount = 0,
                stored = false,
                filename = filename
            )
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────────────────────

    /**
     * Encode a Bitmap to Base64 JPEG string.
     * Downscales large images to [MAX_IMAGE_DIMENSION] px to reduce upload size.
     */
    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val scaled = scaleBitmapIfNeeded(bitmap)
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDim = MAX_IMAGE_DIMENSION
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap

        val scale = maxDim.toFloat() / maxOf(w, h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun buildMultipartBody(fileBytes: ByteArray, filename: String, boundary: String): ByteArray {
        val header = "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: application/octet-stream\r\n\r\n"
        val footer = "\r\n--$boundary--\r\n"
        return header.toByteArray() + fileBytes + footer.toByteArray()
    }

    // ──────────────────────────────────────────────────────────────
    // Data classes
    // ──────────────────────────────────────────────────────────────

    data class DocumentAnalysisResult(
        val summary: String,
        val wordCount: Int,
        val stored: Boolean,
        val filename: String
    )
}
