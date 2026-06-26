package com.hermes.voiceremote.settings

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException

class SettingsRepository(private val context: Context) {

    var initError: String? = null
        private set

    private val sharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "hermes_secure_settings",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e("SettingsRepository", "Error initializing EncryptedSharedPreferences.", e)
            initError = "Encrypted storage failure: ${e.localizedMessage ?: e.javaClass.simpleName}. Plaintext storage fallback is disabled for security."
            null
        }
    }

    fun getSettings(): Result<HermesSettings> {
        val prefs = sharedPreferences
        val err = initError
        if (prefs == null || err != null) {
            return Result.failure(IllegalStateException(err ?: "Secure storage is not initialized"))
        }
        return try {
            val baseUrl = prefs.getString(KEY_BASE_URL, "") ?: ""
            val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
            val agentProfile = prefs.getString(KEY_AGENT_PROFILE, "Vex Volt") ?: "Vex Volt"
            
            val responseModeStr = prefs.getString(KEY_RESPONSE_MODE, ResponseMode.TEXT_AUDIO.name)
            val responseMode = try {
                ResponseMode.valueOf(responseModeStr ?: ResponseMode.TEXT_AUDIO.name)
            } catch (e: Exception) {
                ResponseMode.TEXT_AUDIO
            }

            val audioPrefStr = prefs.getString(KEY_AUDIO_INPUT_PREF, AudioInputPreference.AUTO.name)
            val audioPref = try {
                AudioInputPreference.valueOf(audioPrefStr ?: AudioInputPreference.AUTO.name)
            } catch (e: Exception) {
                AudioInputPreference.AUTO
            }

            Result.success(
                HermesSettings(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    agentProfile = agentProfile,
                    responseMode = responseMode,
                    audioInputPreference = audioPref
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveSettings(settings: HermesSettings): Result<Unit> {
        val prefs = sharedPreferences
        val err = initError
        if (prefs == null || err != null) {
            return Result.failure(IllegalStateException(err ?: "Secure storage is not initialized"))
        }
        return try {
            val normalizedUrl = normalizeBaseUrl(settings.baseUrl)
            val success = prefs.edit()
                .putString(KEY_BASE_URL, normalizedUrl)
                .putString(KEY_API_KEY, settings.apiKey.trim())
                .putString(KEY_AGENT_PROFILE, settings.agentProfile.trim())
                .putString(KEY_RESPONSE_MODE, settings.responseMode.name)
                .putString(KEY_AUDIO_INPUT_PREF, settings.audioInputPreference.name)
                .commit()
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Failed to write settings to secure storage"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hasSettings(): Boolean {
        val settingsResult = getSettings()
        if (settingsResult.isFailure) return false
        val settings = settingsResult.getOrNull() ?: return false
        return settings.baseUrl.isNotEmpty() && settings.apiKey.isNotEmpty() && settings.agentProfile.isNotEmpty()
    }

    fun normalizeBaseUrl(url: String): String {
        return url.trim().removeSuffix("/")
    }

    companion object {
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_AGENT_PROFILE = "agent_profile"
        private const val KEY_RESPONSE_MODE = "response_mode"
        private const val KEY_AUDIO_INPUT_PREF = "audio_input_pref"
    }
}
