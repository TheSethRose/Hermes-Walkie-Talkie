package com.hermes.voiceremote.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class StreamingVadDebug(
    val event: String,
    val speaking: Boolean,
    val rms: Int,
    val bufferedBytes: Int
)

class StreamingAudioRecorder(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var job: Job? = null
    private var onSpeechEndCallback: (() -> Unit)? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var vad: VoiceVad = RmsVad(sampleRate = SAMPLE_RATE)
    private val preRoll = ArrayDeque<ByteArray>()
    @Volatile private var paused = false

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        vadEngine: VoiceVad = RmsVad(sampleRate = SAMPLE_RATE),
        onSpeechStart: (StreamingVadDebug) -> Boolean,
        onAudioChunk: (ByteArray) -> Unit,
        onSpeechEnd: () -> Unit,
        onVadDebug: (StreamingVadDebug) -> Unit = {}
    ): Result<Unit> {
        stop()
        vad = vadEngine
        onSpeechEndCallback = onSpeechEnd
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("Microphone permission is required"))
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return Result.failure(IllegalStateException("Unable to initialize streaming microphone"))

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return Result.failure(IllegalStateException("Streaming microphone failed to initialize"))
        }

        recorder = audioRecord
        enableAudioEffects(audioRecord.audioSessionId)
        audioRecord.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val samples = ShortArray(CHUNK_SAMPLES)
            var bufferedBytes = 0
            while (recorder === audioRecord) {
                val read = audioRecord.read(samples, 0, samples.size)
                if (read <= 0) continue
                if (paused) {
                    vad.reset()
                    preRoll.clear()
                    bufferedBytes = 0
                    continue
                }

                val bytes = shortsToBytes(samples, read)
                val rms = rms(samples, read).toInt()
                val wasSpeaking = vad.isSpeaking()
                if (!wasSpeaking) {
                    preRoll.addLast(bytes)
                    while (preRoll.size > PRE_ROLL_CHUNKS) preRoll.removeFirst()
                }

                when (vad.accept(samples, read)) {
                    VoiceActivityEvent.SPEECH_START -> {
                        val debug = StreamingVadDebug("speech_start", true, rms, bufferedBytes)
                        if (onSpeechStart(debug)) {
                            while (preRoll.isNotEmpty()) {
                                val preRollChunk = preRoll.removeFirst()
                                bufferedBytes += preRollChunk.size
                                onAudioChunk(preRollChunk)
                            }
                            onVadDebug(StreamingVadDebug("speech_start", true, rms, bufferedBytes))
                        } else {
                            resetVad()
                            bufferedBytes = 0
                            onVadDebug(StreamingVadDebug("speech_start_ignored", false, rms, bufferedBytes))
                        }
                    }
                    VoiceActivityEvent.SPEECH_END -> {
                        onVadDebug(StreamingVadDebug("speech_end", false, rms, bufferedBytes))
                        bufferedBytes = 0
                        onSpeechEnd()
                    }
                    null -> {
                        if (vad.isSpeaking() && wasSpeaking) {
                            bufferedBytes += bytes.size
                            onAudioChunk(bytes)
                        }
                    }
                }
            }
        }
        return Result.success(Unit)
    }

    fun forceSpeechEnd() {
        vad.reset()
        preRoll.clear()
        onSpeechEndCallback?.invoke()
    }

    fun setPaused(paused: Boolean) {
        this.paused = paused
        if (paused) {
            resetVad()
        }
    }

    fun resetVad() {
        vad.reset()
        preRoll.clear()
    }

    fun stop() {
        job?.cancel()
        job = null
        vad.reset()
        preRoll.clear()
        onSpeechEndCallback = null
        paused = false
        releaseEffects()
        recorder?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        recorder = null
    }

    private fun enableAudioEffects(audioSessionId: Int) {
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
        }
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
        }
    }

    private fun releaseEffects() {
        echoCanceler?.release()
        noiseSuppressor?.release()
        echoCanceler = null
        noiseSuppressor = null
    }

    private fun shortsToBytes(samples: ShortArray, count: Int): ByteArray {
        val buffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (index in 0 until count) buffer.putShort(samples[index])
        return buffer.array()
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

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHUNK_SAMPLES = 512
        private const val PRE_ROLL_CHUNKS = 10
    }
}
