package com.hermes.voiceremote.network

import com.squareup.moshi.Json

object HermesGatewayRoutes {
    const val HEALTH = "/health"
    const val PROFILES = "/profiles"
    const val TTS_VOICES = "/tts/voices"
    const val VOICE_SESSION = "/voice/session"
    fun voiceTurn(sessionId: String) = "/voice/session/$sessionId/turn"
    fun stream() = "/voice/stream"
    fun cancel(sessionId: String) = "/voice/session/$sessionId/cancel"
    fun reset(sessionId: String) = "/voice/session/$sessionId/reset"
    fun end(sessionId: String) = "/voice/session/$sessionId/end"
}

data class HealthResponse(
    @Json(name = "ok") val ok: Boolean
)

data class CreateSessionRequest(
    @Json(name = "profileId") val profileId: String?,
    @Json(name = "agent") val agent: String,
    @Json(name = "responseMode") val responseMode: String,
    @Json(name = "ttsVoiceId") val ttsVoiceId: String?
)

data class CreateSessionResponse(
    @Json(name = "sessionId") val sessionId: String
)

data class VoiceTurnResponse(
    @Json(name = "transcript") val transcript: String?,
    @Json(name = "responseText") val responseText: String?,
    @Json(name = "audioUrl") val audioUrl: String?
)

data class HermesProfileDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String?,
    @Json(name = "isDefault") val isDefault: Boolean,
    @Json(name = "source") val source: String,
    @Json(name = "sttLabel") val sttLabel: String?,
    @Json(name = "ttsLabel") val ttsLabel: String?
)

data class ProfilesResponse(
    @Json(name = "profiles") val profiles: List<HermesProfileDto>,
    @Json(name = "defaultProfileId") val defaultProfileId: String
)

data class TtsVoiceDto(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "provider") val provider: String,
    @Json(name = "locale") val locale: String?,
    @Json(name = "gender") val gender: String?
)

data class TtsVoicesResponse(
    @Json(name = "provider") val provider: String,
    @Json(name = "defaultVoiceId") val defaultVoiceId: String?,
    @Json(name = "voices") val voices: List<TtsVoiceDto>
)

data class StreamAudioChunkEvent(
    @Json(name = "type") val type: String = "audio_chunk",
    @Json(name = "seq") val seq: Int,
    @Json(name = "format") val format: String = "pcm16",
    @Json(name = "sampleRate") val sampleRate: Int = 16000,
    @Json(name = "data") val data: String
)

data class StreamControlEvent(
    @Json(name = "type") val type: String
)

data class StreamVadDebugEvent(
    @Json(name = "type") val type: String = "vad_debug",
    @Json(name = "event") val event: String,
    @Json(name = "speaking") val speaking: Boolean,
    @Json(name = "rms") val rms: Int,
    @Json(name = "bufferedBytes") val bufferedBytes: Int
)
