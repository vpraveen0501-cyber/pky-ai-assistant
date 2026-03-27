package com.pkyai.android.data.model

data class SystemStats(
    val status: String = "",
    val version: String = "",
    val ram_usage: String = "",
    val cpu_usage: String = "",
    val documents_indexed: Int = 0,
    val llm_mode: String = "",
    val voice_engine: String = "",
    val active_brains: Int = 0,
    val uptime: String = ""
)

data class LoginResponse(
    val access_token: String,
    val token_type: String = "bearer"
)

enum class ApiError {
    NETWORK_ERROR,
    UNAUTHORIZED,
    SERVER_ERROR,
    NOT_FOUND,
    BAD_REQUEST,
    UNKNOWN_ERROR
}
