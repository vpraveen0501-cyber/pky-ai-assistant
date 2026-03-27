package com.pkyai.android.data.network

import com.pkyai.android.data.model.LoginResponse
import com.pkyai.android.data.model.SystemStats
import com.pkyai.android.data.ChatHistoryItem
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface PkyAiApiService {

    @FormUrlEncoded
    @POST("auth/token")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): Response<LoginResponse>

    @GET("system/stats")
    suspend fun getSystemStats(): Response<SystemStats>

    @POST("user/preferences")
    suspend fun updatePreferences(@Body body: RequestBody): Response<ResponseBody>

    @GET("user/preferences")
    suspend fun getPreferences(): Response<Map<String, Map<String, String>>>

    @GET("user/history")
    suspend fun getHistory(): Response<List<ChatHistoryItem>>

    @GET("user/alerts")
    suspend fun getAlerts(): Response<Map<String, Any?>>

    @GET("system/health")
    suspend fun getSystemHealth(): Response<Map<String, String>>
}
