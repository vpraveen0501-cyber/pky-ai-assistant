package com.pkyai.android

import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

enum class ApiError {
    NETWORK_ERROR,
    UNAUTHORIZED,
    SERVER_ERROR,
    NOT_FOUND,
    BAD_REQUEST,
    UNKNOWN_ERROR
}

data class SystemStats(
    val status: String,
    val version: String,
    val ram_usage: String,
    val documents_indexed: Int,
    val llm_mode: String,
    val voice_engine: String,
    val active_brains: Int,
    val uptime: String
)

data class LoginResponse(
    val access_token: String,
    val token_type: String
)

class PkyAiApiClient(private val baseUrl: String, private var authToken: String? = null) {
    var isConnected: Boolean = true
        private set
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onAuthFailed: (() -> Unit)? = null

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
        
        authToken?.let {
            requestBuilder.addHeader("Authorization", "Bearer $it")
        }
        
        try {
            val response = chain.proceed(requestBuilder.build())
            if (!isConnected) {
                isConnected = true
                onConnectionStateChanged?.invoke(true)
            }
            response
        } catch (e: IOException) {
            if (isConnected) {
                isConnected = false
                onConnectionStateChanged?.invoke(false)
            }
            throw e
        }
    }

    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var response: Response? = null
        var exception: IOException? = null
        var tryCount = 0
        val maxRetries = 3

        while (tryCount < maxRetries) {
            try {
                if (response != null) response.close()
                response = chain.proceed(request)
                // Do not retry on successful requests or client errors (except 408)
                if (response.isSuccessful || (response.code < 500 && response.code != 408)) {
                    break
                }
            } catch (e: IOException) {
                exception = e
            }
            tryCount++
            if (tryCount < maxRetries) {
                try { Thread.sleep(minOf(1000L * tryCount, 5000L)) } catch (_: InterruptedException) { break }
            }
        }
        
        response ?: throw (exception ?: IOException("Network error after $maxRetries retries"))
    }

    private val authenticator = Authenticator { _, response ->
        if (response.request.header("Authorization") != null) {
            // Unauthenticated even with a token, invoke failure and give up
            onAuthFailed?.invoke()
            return@Authenticator null
        }
        null
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(retryInterceptor)
        .addInterceptor(authInterceptor)
        .authenticator(authenticator)
        .build()
        
    private val gson = Gson()

    fun setAuthToken(token: String?) {
        this.authToken = token
    }

    fun login(email: String, password: String, callback: (String?, ApiError?) -> Unit) {
        val formBody = FormBody.Builder()
            .add("username", email)
            .add("password", password)
            .build()
            
        val request = Request.Builder()
            .url("$baseUrl/auth/token")
            .post(formBody)
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, ApiError.NETWORK_ERROR)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val loginResponse = gson.fromJson(body, LoginResponse::class.java)
                    authToken = loginResponse.access_token
                    callback(loginResponse.access_token, null)
                } else {
                    val error = if (response.code == 401) ApiError.UNAUTHORIZED else ApiError.SERVER_ERROR
                    callback(null, error)
                }
            }
        })
    }

    fun getSystemStats(callback: (SystemStats?, ApiError?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/system/stats")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, ApiError.NETWORK_ERROR)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val stats = gson.fromJson(body, SystemStats::class.java)
                    callback(stats, null)
                } else {
                    val error = if (response.code == 401) ApiError.UNAUTHORIZED else ApiError.SERVER_ERROR
                    callback(null, error)
                }
            }
        })
    }

    fun updatePreferences(personaName: String) {
        val payload = mapOf("preferences" to mapOf("name" to personaName))
        val json = gson.toJson(payload)
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl/user/preferences")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    fun getPreferences(callback: (String?, ApiError?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/user/preferences")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, ApiError.NETWORK_ERROR)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val name = try {
                        val obj = gson.fromJson(body, Map::class.java)
                        val prefs = obj["preferences"] as? Map<*, *>
                        prefs?.get("name") as? String ?: "PKY AI Assistant"
                    } catch (e: Exception) { "PKY AI Assistant" }
                    callback(name, null)
                } else {
                    val error = if (response.code == 401) ApiError.UNAUTHORIZED else ApiError.SERVER_ERROR
                    callback(null, error)
                }
            }
        })
    }

    fun getHistory(callback: (List<com.pkyai.android.data.ChatHistoryItem>?, ApiError?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/user/history")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, ApiError.NETWORK_ERROR)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val type = object : com.google.gson.reflect.TypeToken<List<com.pkyai.android.data.ChatHistoryItem>>() {}.type
                    val history = try {
                        gson.fromJson<List<com.pkyai.android.data.ChatHistoryItem>>(body, type)
                    } catch (e: Exception) { emptyList() }
                    callback(history, null)
                } else {
                    val error = if (response.code == 401) ApiError.UNAUTHORIZED else ApiError.SERVER_ERROR
                    callback(null, error)
                }
            }
        })
    }

    fun getAlerts(callback: (Map<String, Any?>?, ApiError?) -> Unit) {
        val request = Request.Builder()
            .url("$baseUrl/user/alerts")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, ApiError.NETWORK_ERROR)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val alerts = try {
                        gson.fromJson(body, Map::class.java) as Map<String, Any?>
                    } catch (e: Exception) { null }
                    callback(alerts, null)
                } else {
                    val error = if (response.code == 401) ApiError.UNAUTHORIZED else ApiError.SERVER_ERROR
                    callback(null, error)
                }
            }
        })
    }
}
