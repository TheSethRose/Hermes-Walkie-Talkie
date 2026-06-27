package com.hermes.voiceremote.network

import android.util.Base64
import com.hermes.voiceremote.settings.HermesSettings
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class StreamingVoiceClient(
    private val apiClient: HermesApiClient = HermesApiClient(),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) {
    interface Listener {
        fun onOpen() {}
        fun onSession(sessionId: String, profileName: String) {}
        fun onState(status: String) {}
        fun onFinalTranscript(text: String) {}
        fun onAssistantTextFinal(text: String) {}
        fun onAudioUrl(url: String) {}
        fun onError(message: String) {}
        fun onClosed() {}
    }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val audioChunkAdapter = moshi.adapter(StreamAudioChunkEvent::class.java)
    private val controlAdapter = moshi.adapter(StreamControlEvent::class.java)
    private val vadDebugAdapter = moshi.adapter(StreamVadDebugEvent::class.java)
    private var webSocket: WebSocket? = null
    private var seq = 0

    fun connect(settings: HermesSettings, sessionId: String?, listener: Listener): Result<Unit> {
        if (settings.baseUrl.isEmpty()) return Result.failure(IllegalArgumentException("Base URL is empty"))
        if (settings.apiKey.isEmpty()) return Result.failure(IllegalArgumentException("API Key is empty"))

        return try {
            val request = Request.Builder()
                .url(apiClient.getStreamUrl(settings, sessionId))
                .header("Authorization", "Bearer ${settings.apiKey}")
                .build()
            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text, listener)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    listener.onError(t.message ?: "Streaming connection failed")
                    listener.onClosed()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onClosed()
                }
            })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendAudioChunk(pcm: ByteArray): Boolean {
        val socket = webSocket ?: return false
        val payload = StreamAudioChunkEvent(
            seq = seq++,
            data = Base64.encodeToString(pcm, Base64.NO_WRAP)
        )
        return socket.send(audioChunkAdapter.toJson(payload))
    }

    fun speechEnd(): Boolean = sendControl("speech_end")

    fun sendVadDebug(event: String, speaking: Boolean, rms: Int, bufferedBytes: Int): Boolean {
        val payload = StreamVadDebugEvent(
            event = event,
            speaking = speaking,
            rms = rms,
            bufferedBytes = bufferedBytes
        )
        return webSocket?.send(vadDebugAdapter.toJson(payload)) ?: false
    }

    fun interrupt(): Boolean = sendControl("interrupt")

    fun endSession() {
        sendControl("end_session")
        webSocket?.close(1000, "Session ended")
        webSocket = null
    }

    fun close() {
        webSocket?.close(1000, "Closed")
        webSocket = null
    }

    private fun sendControl(type: String): Boolean {
        return webSocket?.send(controlAdapter.toJson(StreamControlEvent(type))) ?: false
    }

    private fun handleMessage(text: String, listener: Listener) {
        try {
            val body = JSONObject(text)
            when (body.optString("type")) {
                "session" -> listener.onSession(
                    body.optString("sessionId"),
                    body.optString("profileName")
                )
                "state" -> listener.onState(body.optString("status"))
                "final_transcript" -> listener.onFinalTranscript(body.optString("text"))
                "assistant_text_final" -> listener.onAssistantTextFinal(body.optString("text"))
                "audio_url" -> listener.onAudioUrl(body.optString("url"))
                "error" -> listener.onError(body.optString("message", "Streaming error"))
            }
        } catch (e: Exception) {
            listener.onError("Invalid streaming event")
        }
    }
}
