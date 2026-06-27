package com.hermes.voiceremote.audio

enum class VoiceActivityEvent {
    SPEECH_START,
    SPEECH_END
}

interface VoiceVad {
    fun accept(samples: ShortArray, count: Int): VoiceActivityEvent?
    fun isSpeaking(): Boolean
    fun reset()
}
