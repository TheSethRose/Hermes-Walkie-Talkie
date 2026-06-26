package com.hermes.voiceremote.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var recordingStartTimeMs: Long = 0L

    fun startRecording(): Result<File> {
        releaseRecorder()
        
        return try {
            val cacheDir = context.cacheDir
            val outputFile = File.createTempFile("hermes_rec_", ".m4a", cacheDir)
            currentFile = outputFile
            recordingStartTimeMs = System.currentTimeMillis()

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            Result.success(outputFile)
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            cleanupAndReset()
            Result.failure(Exception("Could not start microphone.", e))
        }
    }

    fun stopRecording(): Result<File> {
        val file = currentFile
        val recorder = mediaRecorder
        if (recorder == null || file == null) {
            cleanupAndReset()
            return Result.failure(IllegalStateException("Not recording"))
        }

        val duration = System.currentTimeMillis() - recordingStartTimeMs
        if (duration < 500) {
            cleanupAndReset()
            return Result.failure(IOException("Recording was too short."))
        }

        return try {
            recorder.stop()
            recorder.release()
            mediaRecorder = null
            currentFile = null
            
            if (!file.exists() || file.length() < 512) {
                cleanup(file)
                Result.failure(IOException("Recording was too short."))
            } else {
                Result.success(file)
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop recording cleanly", e)
            cleanupAndReset()
            Result.failure(IOException("Recording was too short.", e))
        }
    }

    fun cancelRecording() {
        cleanupAndReset()
    }

    fun isRecording(): Boolean {
        return mediaRecorder != null
    }

    fun cleanup(file: File) {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to delete file ${file.absolutePath}", e)
        }
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error releasing recorder", e)
        } finally {
            mediaRecorder = null
        }
    }

    private fun cleanupAndReset() {
        releaseRecorder()
        currentFile?.let {
            cleanup(it)
            currentFile = null
        }
    }
}
