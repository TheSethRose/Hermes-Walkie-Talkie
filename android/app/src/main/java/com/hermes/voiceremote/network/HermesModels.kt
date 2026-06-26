package com.hermes.voiceremote.network

import com.squareup.moshi.Json

object HermesGatewayRoutes {
    const val HEALTH = "/health"
    const val PROFILES = "/profiles"
    const val VOICE_SESSION = "/voice/session"
    fun voiceTurn(sessionId: String) = "/voice/session/$sessionId/turn"
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
    @Json(name = "responseMode") val responseMode: String
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
