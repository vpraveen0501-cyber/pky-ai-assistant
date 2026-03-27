package com.pkyai.android

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AuthManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "pky_ai_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        sharedPreferences.edit().putString("auth_token", token).apply()
    }

    fun getToken(): String? {
        return sharedPreferences.getString("auth_token", null)
    }

    fun clearToken() {
        sharedPreferences.edit().remove("auth_token").apply()
    }

    fun hasToken(): Boolean {
        return getToken() != null
    }

    fun getDatabasePassword(): String {
        var password = sharedPreferences.getString("db_password", null)
        if (password == null) {
            // Use cryptographically secure random bytes encoded as hex for stronger encryption key
            val bytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(bytes)
            password = bytes.joinToString("") { "%02x".format(it) }
            sharedPreferences.edit().putString("db_password", password).apply()
        }
        return password
    }
}