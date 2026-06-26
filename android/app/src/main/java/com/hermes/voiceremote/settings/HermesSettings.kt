package com.hermes.voiceremote.settings

enum class ResponseMode {
    TEXT,
    TEXT_AUDIO
}

enum class AudioInputPreference {
    AUTO,
    PHONE_MIC,
    BLUETOOTH_HEADSET
}

enum class TalkInteractionMode {
    TAP_TO_TALK,
    PUSH_TO_TALK,
    ALWAYS_LISTENING
}

enum class AudioRoute {
    BLUETOOTH_HEADSET,
    PHONE,
    UNKNOWN
}

data class HermesSettings(
    val baseUrl: String,
    val apiKey: String,
    val selectedProfileId: String,
    val selectedProfileName: String,
    val responseMode: ResponseMode,
    val audioInputPreference: AudioInputPreference,
    val talkInteractionMode: TalkInteractionMode
)
