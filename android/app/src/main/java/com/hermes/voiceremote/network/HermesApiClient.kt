package com.hermes.voiceremote.network

import com.hermes.voiceremote.settings.HermesSettings
import com.hermes.voiceremote.settings.ResponseMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class HermesApiClient {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val baseOkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    fun getUrl(baseUrl: String, path: String): HttpUrl {
        val normalizedBaseUrl = baseUrl.trim().removeSuffix("/")
        val finalUrl = "$normalizedBaseUrl$path"
        return finalUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("Malformed Base URL: $finalUrl")
    }

    fun getStreamUrl(settings: HermesSettings, sessionId: String? = null): String {
        val httpUrl = getUrl(settings.baseUrl, HermesGatewayRoutes.stream())
        val scheme = when (httpUrl.scheme) {
            "https" -> "wss"
            "http" -> "ws"
            else -> throw IllegalArgumentException("Streaming requires http:// or https:// Base URL")
        }
        val responseModeStr = when (settings.responseMode) {
            ResponseMode.TEXT -> "text"
            ResponseMode.TEXT_AUDIO -> "text_audio"
        }
        val builder = httpUrl.newBuilder()
            .addQueryParameter("profileId", settings.selectedProfileId)
            .addQueryParameter("agent", settings.selectedProfileName)
            .addQueryParameter("responseMode", responseModeStr)
        if (settings.selectedTtsVoiceId.isNotBlank()) {
            builder.addQueryParameter("ttsVoiceId", settings.selectedTtsVoiceId)
        }
        if (!sessionId.isNullOrEmpty()) {
            builder.addQueryParameter("sessionId", sessionId)
        }
        val url = builder.build().toString()
        return "$scheme://${url.substringAfter("://")}"
    }

    private suspend fun executeCancellable(request: Request): Result<String> =
        suspendCancellableCoroutine { cont ->
            val call = baseOkHttpClient.newCall(request)

            cont.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resume(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        if (!cont.isActive) return
                        
                        if (resp.code == 401) {
                            cont.resume(Result.failure(IOException("Unauthorized")))
                            return
                        }
                        if (!resp.isSuccessful) {
                            cont.resume(Result.failure(IOException("Server returned error: ${resp.code}")))
                            return
                        }
                        
                        val bodyStr = resp.body?.string()
                        if (bodyStr == null) {
                            cont.resume(Result.failure(IOException("Empty response body")))
                        } else {
                            cont.resume(Result.success(bodyStr))
                        }
                    }
                }
            })
        }

    suspend fun health(settings: HermesSettings): Result<Boolean> {
        try {
            if (settings.baseUrl.isEmpty()) {
                return Result.failure(IllegalArgumentException("Base URL is empty"))
            }
            if (settings.apiKey.isEmpty()) {
                return Result.failure(IllegalArgumentException("API Key is empty"))
            }

            val url = getUrl(settings.baseUrl, HermesGatewayRoutes.HEALTH)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer ${settings.apiKey}")
                .build()

            val resultStr = executeCancellable(request)
            return resultStr.map { bodyStr ->
                val adapter = moshi.adapter(HealthResponse::class.java)
                val healthResponse = adapter.fromJson(bodyStr)
                healthResponse?.ok ?: throw IOException("Invalid JSON response")
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun createSession(settings: HermesSettings): Result<String> {
        try {
            if (settings.baseUrl.isEmpty()) {
                return Result.failure(IllegalArgumentException("Base URL is empty"))
            }
            if (settings.apiKey.isEmpty()) {
                return Result.failure(IllegalArgumentException("API Key is empty"))
            }

            val url = getUrl(settings.baseUrl, HermesGatewayRoutes.VOICE_SESSION)
            val responseModeStr = when (settings.responseMode) {
                ResponseMode.TEXT -> "text"
                ResponseMode.TEXT_AUDIO -> "text_audio"
            }
            
            val requestBodyObj = CreateSessionRequest(
                profileId = settings.selectedProfileId,
                agent = settings.selectedProfileName,
                responseMode = responseModeStr,
                ttsVoiceId = settings.selectedTtsVoiceId.takeIf { it.isNotBlank() }
            )
            val jsonAdapter = moshi.adapter(CreateSessionRequest::class.java)
            val jsonBody = jsonAdapter.toJson(requestBodyObj)
            
            val requestBody = jsonBody.toRequestBody("application/json".toMediaTypeOrNull())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Authorization", "Bearer ${settings.apiKey}")
                .header("Content-Type", "application/json")
                .build()

            val resultStr = executeCancellable(request)
            return resultStr.map { bodyStr ->
                val adapter = moshi.adapter(CreateSessionResponse::class.java)
                val createSessionResponse = adapter.fromJson(bodyStr)
                createSessionResponse?.sessionId ?: throw IOException("Invalid JSON response")
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun submitTurn(
        settings: HermesSettings,
        sessionId: String,
        audioFile: File
    ): Result<VoiceTurnResponse> {
        try {
            if (settings.baseUrl.isEmpty()) {
                return Result.failure(IllegalArgumentException("Base URL is empty"))
            }
            if (settings.apiKey.isEmpty()) {
                return Result.failure(IllegalArgumentException("API Key is empty"))
            }
            if (sessionId.isEmpty()) {
                return Result.failure(IllegalArgumentException("Session ID is empty"))
            }
            if (!audioFile.exists() || audioFile.length() == 0L) {
                return Result.failure(IllegalArgumentException("Empty or missing recording file"))
            }

            val url = getUrl(settings.baseUrl, HermesGatewayRoutes.voiceTurn(sessionId))
            
            val responseModeStr = when (settings.responseMode) {
                ResponseMode.TEXT -> "text"
                ResponseMode.TEXT_AUDIO -> "text_audio"
            }

            val audioBody = audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
            
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", "audio.m4a", audioBody)
                .addFormDataPart("format", "m4a")
                .addFormDataPart("profileId", settings.selectedProfileId)
                .addFormDataPart("agent", settings.selectedProfileName)
                .addFormDataPart("responseMode", responseModeStr)
                .apply {
                    if (settings.selectedTtsVoiceId.isNotBlank()) {
                        addFormDataPart("ttsVoiceId", settings.selectedTtsVoiceId)
                    }
                }
                .build()

            val request = Request.Builder()
                .url(url)
                .post(multipartBody)
                .header("Authorization", "Bearer ${settings.apiKey}")
                .build()

            val resultStr = executeCancellable(request)
            return resultStr.map { bodyStr ->
                val adapter = moshi.adapter(VoiceTurnResponse::class.java)
                val voiceTurnResponse = adapter.fromJson(bodyStr)
                voiceTurnResponse ?: throw IOException("Invalid JSON response")
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun cancel(settings: HermesSettings, sessionId: String): Result<Unit> {
        return postSessionAction(settings, HermesGatewayRoutes.cancel(sessionId), sessionId)
    }

    suspend fun resetSession(settings: HermesSettings, sessionId: String): Result<Unit> {
        return postSessionAction(settings, HermesGatewayRoutes.reset(sessionId), sessionId)
    }

    suspend fun endSession(settings: HermesSettings, sessionId: String): Result<Unit> {
        return postSessionAction(settings, HermesGatewayRoutes.end(sessionId), sessionId)
    }

    suspend fun getProfiles(settings: HermesSettings): Result<ProfilesResponse> {
        try {
            if (settings.baseUrl.isEmpty()) {
                return Result.failure(IllegalArgumentException("Base URL is empty"))
            }
            if (settings.apiKey.isEmpty()) {
                return Result.failure(IllegalArgumentException("API Key is empty"))
            }

            val url = getUrl(settings.baseUrl, HermesGatewayRoutes.PROFILES)
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer ${settings.apiKey}")
                .build()

            val resultStr = executeCancellable(request)
            return resultStr.map { bodyStr ->
                val adapter = moshi.adapter(ProfilesResponse::class.java)
                adapter.fromJson(bodyStr) ?: throw IOException("Invalid JSON response")
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun getTtsVoices(settings: HermesSettings): Result<TtsVoicesResponse> {
        try {
            if (settings.baseUrl.isEmpty()) {
                return Result.failure(IllegalArgumentException("Base URL is empty"))
            }
            if (settings.apiKey.isEmpty()) {
                return Result.failure(IllegalArgumentException("API Key is empty"))
            }

            val url = getUrl(settings.baseUrl, HermesGatewayRoutes.TTS_VOICES).newBuilder()
                .addQueryParameter("profileId", settings.selectedProfileId)
                .addQueryParameter("agent", settings.selectedProfileName)
                .build()
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer ${settings.apiKey}")
                .build()

            val resultStr = executeCancellable(request)
            return resultStr.map { bodyStr ->
                val adapter = moshi.adapter(TtsVoicesResponse::class.java)
                adapter.fromJson(bodyStr) ?: throw IOException("Invalid JSON response")
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    private suspend fun postSessionAction(settings: HermesSettings, path: String, sessionId: String): Result<Unit> {
        try {
            if (settings.baseUrl.isEmpty()) {
                return Result.failure(IllegalArgumentException("Base URL is empty"))
            }
            if (settings.apiKey.isEmpty()) {
                return Result.failure(IllegalArgumentException("API Key is empty"))
            }
            if (sessionId.isEmpty()) {
                return Result.failure(IllegalArgumentException("Session ID is empty"))
            }

            val requestBody = "".toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder()
                .url(getUrl(settings.baseUrl, path))
                .post(requestBody)
                .header("Authorization", "Bearer ${settings.apiKey}")
                .build()

            return executeCancellable(request).map { }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
