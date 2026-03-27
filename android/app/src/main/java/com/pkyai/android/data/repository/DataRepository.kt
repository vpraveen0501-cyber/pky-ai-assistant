package com.pkyai.android.data.repository

import com.pkyai.android.data.model.SystemStats
import com.pkyai.android.data.ChatHistoryItem
import com.pkyai.android.data.HistoryDao
import com.pkyai.android.data.network.PkyAiApiService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

class DataRepository @Inject constructor(
    private val apiService: PkyAiApiService,
    private val historyDao: HistoryDao
) {
    private val gson = Gson()

    suspend fun getSystemStats(): Result<SystemStats> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getSystemStats()
            val body = response.body()
            when {
                response.isSuccessful && body != null -> Result.success(body)
                response.isSuccessful -> Result.failure(Exception("Empty response body from /system/stats"))
                else -> Result.failure(Exception("Failed to load stats: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistory(): Result<List<ChatHistoryItem>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getHistory()
            val body = response.body()
            when {
                response.isSuccessful && body != null -> {
                    if (body.isNotEmpty()) historyDao.insertAll(body)
                    Result.success(body)
                }
                response.isSuccessful -> Result.failure(Exception("Empty response body from /user/history"))
                else -> Result.failure(Exception("Failed to load history: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAlerts(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getAlerts()
            val body = response.body()
            when {
                response.isSuccessful && body != null -> Result.success(body)
                response.isSuccessful -> Result.failure(Exception("Empty response body from /user/alerts"))
                else -> Result.failure(Exception("Failed to load alerts: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSystemHealth(): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getSystemHealth()
            val body = response.body()
            when {
                response.isSuccessful && body != null -> Result.success(body)
                response.isSuccessful -> Result.failure(Exception("Empty response body from /system/health"))
                else -> Result.failure(Exception("Health check failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPreferences(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPreferences()
            val body = response.body()
            when {
                response.isSuccessful && body != null -> {
                    val name = body["preferences"]?.get("name")?.toString() ?: "PKY AI Assistant"
                    Result.success(name)
                }
                response.isSuccessful -> Result.failure(Exception("Empty response body from /user/preferences"))
                else -> Result.failure(Exception("Failed to load preferences: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(personaName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = mapOf("preferences" to mapOf("name" to personaName))
            val json = gson.toJson(payload)
            val body = json.toRequestBody("application/json".toMediaType())
            val response = apiService.updatePreferences(body)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed to update preferences: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
