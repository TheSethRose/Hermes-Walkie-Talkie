package com.hermes.voiceremote.audio

import kotlin.math.sqrt

class RmsVad(
    private val sampleRate: Int = 16000,
    private val threshold: Double = 600.0,
    private val startMs: Int = 250,
    private val endSilenceMs: Int = 600,
    private val maxSpeechMs: Int = 20_000
) : VoiceVad {
    private var speaking = false
    private var voicedMs = 0
    private var silenceMs = 0
    private var speechMs = 0
    private var peakRms = 0.0

    override fun accept(samples: ShortArray, count: Int): VoiceActivityEvent? {
        val frameMs = ((count.toDouble() / sampleRate) * 1000).toInt().coerceAtLeast(1)
        val level = rms(samples, count)
        val quietThreshold = if (speaking) maxOf(300.0, peakRms * 0.55) else threshold
        val voice = level >= quietThreshold

        if (voice) {
            voicedMs += frameMs
            silenceMs = 0
            if (level > peakRms) peakRms = level
        } else {
            silenceMs += frameMs
            if (!speaking) voicedMs = 0
        }

        if (!speaking && voicedMs >= startMs) {
            speaking = true
            speechMs = voicedMs
            return VoiceActivityEvent.SPEECH_START
        }

        if (speaking) {
            speechMs += frameMs
            if (silenceMs >= endSilenceMs || speechMs >= maxSpeechMs) {
                reset()
                return VoiceActivityEvent.SPEECH_END
            }
        }

        return null
    }

    override fun isSpeaking(): Boolean = speaking

    override fun reset() {
        speaking = false
        voicedMs = 0
        silenceMs = 0
        speechMs = 0
        peakRms = 0.0
    }

    private fun rms(samples: ShortArray, count: Int): Double {
        if (count <= 0) return 0.0
        var sum = 0.0
        for (index in 0 until count) {
            val sample = samples[index].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / count)
    }
}
