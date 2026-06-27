package com.hermes.voiceremote.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume

class AudioPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var onCompleteCallback: (() -> Unit)? = null
    private var currentCacheFile: File? = null
    private var currentCall: Call? = null
    @Volatile private var stopped = false
    private val httpClient = OkHttpClient.Builder().build()

    suspend fun play(url: String, apiKey: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            stop()
            stopped = false
            
            val mediaSource: String
            if (url.startsWith("http://") || url.startsWith("https://")) {
                val cacheFile = File.createTempFile("hermes_play_cache_", ".mp3", context.cacheDir)
                currentCacheFile = cacheFile
                
                val requestBuilder = Request.Builder().url(url)
                if (!apiKey.isNullOrEmpty()) {
                    requestBuilder.header("Authorization", "Bearer $apiKey")
                }
                
                val call = httpClient.newCall(requestBuilder.build())
                currentCall = call
                call.execute().use { response ->
                    if (stopped) throw IOException("Playback stopped")
                    if (!response.isSuccessful) {
                        throw IOException("Failed to download audio file: HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Empty response body from audio URL")
                    FileOutputStream(cacheFile).use { fos ->
                        body.byteStream().copyTo(fos)
                    }
                }
                currentCall = null
                if (stopped) throw IOException("Playback stopped")
                mediaSource = cacheFile.absolutePath
            } else {
                mediaSource = url
            }

            suspendCancellableCoroutine { continuation ->
                try {
                    val player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(mediaSource)
                    }

                    player.setOnPreparedListener {
                        if (stopped) {
                            it.release()
                            mediaPlayer = null
                            cleanCache()
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(IOException("Playback stopped")))
                            }
                        } else {
                            it.start()
                        }
                    }

                    player.setOnCompletionListener {
                        it.release()
                        mediaPlayer = null
                        cleanCache()
                        onCompleteCallback?.invoke()
                        if (continuation.isActive) {
                            continuation.resume(Result.success(Unit))
                        }
                    }

                    player.setOnErrorListener { mp, what, extra ->
                        mp.release()
                        mediaPlayer = null
                        cleanCache()
                        if (continuation.isActive) {
                            continuation.resume(Result.failure(Exception("MediaPlayer error: what=$what, extra=$extra")))
                        }
                        true
                    }

                    mediaPlayer = player
                    player.prepareAsync()

                    continuation.invokeOnCancellation {
                        stop()
                    }

                } catch (e: Exception) {
                    cleanCache()
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(e))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to setup MediaPlayer", e)
            cleanCache()
            Result.failure(e)
        }
    }

    fun setOnPlaybackCompleteListener(callback: () -> Unit) {
        onCompleteCallback = callback
    }

    fun stop() {
        stopped = true
        currentCall?.cancel()
        currentCall = null
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping MediaPlayer", e)
        } finally {
            mediaPlayer = null
            cleanCache()
        }
    }

    suspend fun fadeOutAndStop(durationMs: Long = 100) {
        val player = mediaPlayer
        if (player == null || !player.isPlaying) {
            stop()
            return
        }

        val steps = 5
        val stepDelay = (durationMs / steps).coerceAtLeast(1)
        for (step in steps downTo 1) {
            val volume = (step - 1).toFloat() / steps
            try {
                player.setVolume(volume, volume)
            } catch (_: Exception) {
                break
            }
            delay(stepDelay)
        }
        stop()
    }

    fun release() {
        stop()
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true || currentCall != null
    }

    private fun cleanCache() {
        currentCacheFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("AudioPlayer", "Failed to delete cache file", e)
            } finally {
                currentCacheFile = null
            }
        }
    }
}
