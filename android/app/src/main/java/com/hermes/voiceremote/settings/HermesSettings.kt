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

enum class VadEngine {
    RMS,
    SILERO_ONNX
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
    val talkInteractionMode: TalkInteractionMode,
    val selectedTtsVoiceId: String = "",
    val vadEngine: VadEngine = VadEngine.RMS,
    val vadSpeechThreshold: Float = 600f,
    val vadSilenceMs: Int = 600,
    val bargeInEnabled: Boolean = false,
    val bargeInMinSpeechMs: Int = 400
)

fun TalkInteractionMode.defaultVadSettings(): HermesVadSettings {
    return when (this) {
        TalkInteractionMode.PUSH_TO_TALK -> HermesVadSettings(
            vadEngine = VadEngine.RMS,
            vadSpeechThreshold = 600f,
            vadSilenceMs = 400,
            bargeInEnabled = false,
            bargeInMinSpeechMs = 400
        )
        TalkInteractionMode.TAP_TO_TALK -> HermesVadSettings(
            vadEngine = VadEngine.RMS,
            vadSpeechThreshold = 600f,
            vadSilenceMs = 400,
            bargeInEnabled = false,
            bargeInMinSpeechMs = 400
        )
        TalkInteractionMode.ALWAYS_LISTENING -> HermesVadSettings(
            vadEngine = VadEngine.RMS,
            vadSpeechThreshold = 600f,
            vadSilenceMs = 1200,
            bargeInEnabled = true,
            bargeInMinSpeechMs = 400
        )
    }
}

fun HermesSettings.withTalkInteractionModePreset(mode: TalkInteractionMode): HermesSettings {
    val preset = mode.defaultVadSettings()
    return copy(
        talkInteractionMode = mode,
        vadEngine = preset.vadEngine,
        vadSpeechThreshold = preset.vadSpeechThreshold,
        vadSilenceMs = preset.vadSilenceMs,
        bargeInEnabled = preset.bargeInEnabled,
        bargeInMinSpeechMs = preset.bargeInMinSpeechMs
    )
}

data class HermesVadSettings(
    val vadEngine: VadEngine,
    val vadSpeechThreshold: Float,
    val vadSilenceMs: Int,
    val bargeInEnabled: Boolean,
    val bargeInMinSpeechMs: Int
)
