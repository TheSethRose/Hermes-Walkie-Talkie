package com.hermes.voiceremote.state

import com.hermes.voiceremote.settings.AudioRoute

data class VoiceSessionUiState(
    val status: VoiceSessionStatus = VoiceSessionStatus.IDLE,
    val sessionId: String? = null,
    val agentProfile: String = "Vex Volt",
    val isConnected: Boolean = false,
    val audioRoute: AudioRoute = AudioRoute.PHONE,
    val transcript: String = "",
    val responseText: String = "",
    val errorMessage: String? = null,
    val isBusy: Boolean = false
)
