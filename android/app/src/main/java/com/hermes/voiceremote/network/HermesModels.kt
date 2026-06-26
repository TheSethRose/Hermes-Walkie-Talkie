package com.hermes.voiceremote.network

import com.squareup.moshi.Json

object HermesGatewayRoutes {
    const val HEALTH = "/health"
    const val VOICE_SESSION = "/voice/session"
    fun voiceTurn(sessionId: String) = "/voice/session/$sessionId/turn"
    fun cancel(sessionId: String) = "/voice/session/$sessionId/cancel"
}

data class HealthResponse(
    @Json(name = "ok") val ok: Boolean
)

data class CreateSessionRequest(
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
