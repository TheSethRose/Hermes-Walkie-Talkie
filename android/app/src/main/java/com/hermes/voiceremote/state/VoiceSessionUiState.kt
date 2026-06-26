package com.hermes.voiceremote.state

import com.hermes.voiceremote.settings.AudioRoute

data class VoiceTurnUi(
    val id: String,
    val userText: String,
    val assistantText: String,
    val audioUrl: String?,
    val createdAt: Long
)

data class VoiceSessionUiState(
    val status: VoiceSessionStatus = VoiceSessionStatus.IDLE,
    val sessionId: String? = null,
    val agentProfile: String = "Main",
    val isConnected: Boolean = false,
    val connectionStatus: GatewayConnectionStatus = GatewayConnectionStatus.OFFLINE,
    val lastHealthCheckedAt: Long? = null,
    val gatewayLatencyMs: Long? = null,
    val connectionErrorMessage: String? = null,
    val audioRoute: AudioRoute = AudioRoute.PHONE,
    val transcript: String = "",
    val responseText: String = "",
    val lastAudioUrl: String? = null,
    val isPlaybackAvailable: Boolean = false,
    val turnHistory: List<VoiceTurnUi> = emptyList(),
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)
