package com.hermes.voiceremote.audio

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.max

class SileroOnnxVad(
    context: Context,
    private val activationThreshold: Float = 0.5f,
    deactivationThreshold: Float = max(activationThreshold - 0.15f, 0.01f),
    private val sampleRate: Int = 16000,
    private val minSpeechMs: Int = 100,
    private val minSilenceMs: Int = 550,
    private val maxSpeechMs: Int = 20_000
) : VoiceVad {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val deactivationThreshold = deactivationThreshold.coerceIn(0.01f, activationThreshold)
    private val inputNames: Set<String>
    private val outputNames: List<String>
    private var state = Array(2) { Array(1) { FloatArray(128) } }
    private var speaking = false
    private var voicedMs = 0
    private var silenceMs = 0
    private var speechMs = 0

    init {
        val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        session = environment.createSession(modelBytes, OrtSession.SessionOptions())
        inputNames = session.inputNames
        outputNames = session.outputNames.toList()
    }

    override fun accept(samples: ShortArray, count: Int): VoiceActivityEvent? {
        val safeCount = count.coerceIn(0, samples.size)
        val frameMs = ((safeCount.toDouble() / sampleRate) * 1000).toInt().coerceAtLeast(1)
        val probability = runModel(samples, safeCount)
        val voice = if (speaking) probability >= deactivationThreshold else probability >= activationThreshold

        if (voice) {
            voicedMs += frameMs
            silenceMs = 0
        } else {
            silenceMs += frameMs
            if (!speaking) voicedMs = 0
        }

        if (!speaking && voicedMs >= minSpeechMs) {
            speaking = true
            speechMs = voicedMs
            return VoiceActivityEvent.SPEECH_START
        }

        if (speaking) {
            speechMs += frameMs
            if (silenceMs >= minSilenceMs || speechMs >= maxSpeechMs) {
                resetCounters()
                return VoiceActivityEvent.SPEECH_END
            }
        }

        return null
    }

    override fun isSpeaking(): Boolean = speaking

    override fun reset() {
        resetCounters()
        state = Array(2) { Array(1) { FloatArray(128) } }
    }

    private fun runModel(samples: ShortArray, count: Int): Float {
        val window = FloatArray(CHUNK_SAMPLES)
        for (index in 0 until minOf(count, CHUNK_SAMPLES)) {
            window[index] = samples[index] / 32768f
        }

        val tensors = mutableListOf<OnnxTensor>()
        val inputs = linkedMapOf<String, OnnxTensor>()
        try {
            val audioTensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(window),
                longArrayOf(1, CHUNK_SAMPLES.toLong())
            )
            tensors += audioTensor
            inputs["input"] = audioTensor

            if ("state" in inputNames) {
                val stateTensor = OnnxTensor.createTensor(environment, state)
                tensors += stateTensor
                inputs["state"] = stateTensor
            }

            if ("sr" in inputNames) {
                val sampleRateTensor = OnnxTensor.createTensor(
                    environment,
                    LongBuffer.wrap(longArrayOf(sampleRate.toLong())),
                    longArrayOf(1)
                )
                tensors += sampleRateTensor
                inputs["sr"] = sampleRateTensor
            }

            session.run(inputs).use { result ->
                val probability = firstFloat(result.get(0).value)
                if (result.size() > 1) {
                    state = copyState(result.get(1).value) ?: state
                } else {
                    val stateOutputName = outputNames.firstOrNull { it.contains("state", ignoreCase = true) }
                    if (stateOutputName != null) {
                        val stateValue = result.get(stateOutputName).orElse(null)?.value
                        state = copyState(stateValue) ?: state
                    }
                }
                return probability
            }
        } finally {
            tensors.forEach { it.close() }
        }
    }

    private fun resetCounters() {
        speaking = false
        voicedMs = 0
        silenceMs = 0
        speechMs = 0
    }

    private fun firstFloat(value: Any?): Float {
        return when (value) {
            is Float -> value
            is FloatArray -> value.firstOrNull() ?: 0f
            is Array<*> -> value.firstOrNull()?.let { firstFloat(it) } ?: 0f
            else -> 0f
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun copyState(value: Any?): Array<Array<FloatArray>>? {
        return value as? Array<Array<FloatArray>>
    }

    companion object {
        private const val MODEL_ASSET = "silero_vad.onnx"
        const val CHUNK_SAMPLES = 512
    }
}
