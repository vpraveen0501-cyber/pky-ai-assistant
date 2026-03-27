package com.pkyai.android.data.repository

import android.content.Context
import com.pkyai.android.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("pky_ai_config", Context.MODE_PRIVATE)

    fun getHost(): String = prefs.getString("backend_host", BuildConfig.DEFAULT_BACKEND_HOST) ?: BuildConfig.DEFAULT_BACKEND_HOST
    fun getPort(): String = prefs.getString("backend_port", BuildConfig.DEFAULT_BACKEND_PORT.toString()) ?: BuildConfig.DEFAULT_BACKEND_PORT.toString()

    fun isConfigured(): Boolean = getHost().isNotBlank()

    fun updateConfig(host: String, port: String) {
        prefs.edit()
            .putString("backend_host", host)
            .putString("backend_port", port)
            .apply()
    }

    fun getScheme(): String = prefs.getString("backend_scheme", if (BuildConfig.DEBUG) "http" else "https") ?: "https"
    fun getWsScheme(): String = if (getScheme() == "https") "wss" else "ws"

    fun getBaseUrl(): String = "${getScheme()}://${getHost()}:${getPort()}"
    fun getWsBaseUrl(): String = "${getWsScheme()}://${getHost()}:${getPort()}"
}
