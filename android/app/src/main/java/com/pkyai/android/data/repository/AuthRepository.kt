package com.pkyai.android.data.repository

import com.pkyai.android.AuthManager
import com.pkyai.android.data.model.LoginResponse
import com.pkyai.android.data.network.PkyAiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val apiService: PkyAiApiService,
    private val authManager: AuthManager
) {
    suspend fun login(email: String, password: String): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.login(email, password)
            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!
                authManager.saveToken(loginResponse.access_token)
                Result.success(loginResponse)
            } else {
                Result.failure(Exception("Login Failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hasToken(): Boolean = authManager.hasToken()
    fun getToken(): String? = authManager.getToken()

    fun logout() {
        authManager.clearToken()
    }
}
