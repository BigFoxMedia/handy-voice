package com.bigfoxmedia.handyvoice

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsStore(context: Context) {
    companion object {
        const val DEFAULT_BASE_URL = "https://litellm.apps-prod.enpraise.com/v1"
        const val DEFAULT_MODEL = "stt-whisper-local"
        private const val SECURE_FILE = "handy_voice_secure"
        private const val PLAIN_FILE = "handy_voice_settings"
    }

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (error: Exception) {
        throw IllegalStateException("Could not initialize encrypted storage", error)
    }

    var baseUrl: String
        get() = prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString("base_url", value.trim()).apply()

    var model: String
        get() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString("model", value.trim()).apply()

    var language: String
        get() = prefs.getString("language", "auto") ?: "auto"
        set(value) = prefs.edit().putString("language", value.trim()).apply()

    var fallbackEnabled: Boolean
        get() = prefs.getBoolean("fallback_enabled", false)
        set(value) = prefs.edit().putBoolean("fallback_enabled", value).apply()

    var apiKey: String
        get() = securePrefs.getString("api_key", "") ?: ""
        set(value) = securePrefs.edit().putString("api_key", value.trim()).apply()

    fun save(baseUrl: String, model: String, apiKey: String, language: String, fallback: Boolean) {
        this.baseUrl = baseUrl
        this.model = model
        this.apiKey = apiKey
        this.language = language
        this.fallbackEnabled = fallback
    }
}
