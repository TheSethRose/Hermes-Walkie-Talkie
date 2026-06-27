package com.hermes.voiceremote.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hermes.voiceremote.MainActivity
import com.hermes.voiceremote.audio.AudioPlayer
import com.hermes.voiceremote.audio.AudioRecorder
import com.hermes.voiceremote.audio.AudioRouteManager
import com.hermes.voiceremote.audio.RmsVad
import com.hermes.voiceremote.audio.SileroOnnxVad
import com.hermes.voiceremote.audio.StreamingAudioRecorder
import com.hermes.voiceremote.audio.VoiceVad
import com.hermes.voiceremote.network.HermesApiClient
import com.hermes.voiceremote.network.StreamingVoiceClient
import com.hermes.voiceremote.settings.AudioRoute
import com.hermes.voiceremote.settings.HermesSettings
import com.hermes.voiceremote.settings.ResponseMode
import com.hermes.voiceremote.settings.SettingsRepository
import com.hermes.voiceremote.settings.VadEngine
import com.hermes.voiceremote.state.GatewayConnectionStatus
import com.hermes.voiceremote.state.VoiceSessionStatus
import com.hermes.voiceremote.state.VoiceTurnUi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VoiceSessionService : Service() {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var streamingAudioRecorder: StreamingAudioRecorder
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var audioRouteManager: AudioRouteManager
    private lateinit var apiClient: HermesApiClient
    private lateinit var streamingClient: StreamingVoiceClient
    private lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeJob: Job? = null
    private var playbackJob: Job? = null
    private var recordedFile: File? = null
    private var playbackStartedAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(this)
        streamingAudioRecorder = StreamingAudioRecorder(this)
        audioPlayer = AudioPlayer(this)
        audioRouteManager = AudioRouteManager(this)
        apiClient = HermesApiClient()
        streamingClient = StreamingVoiceClient(apiClient)
        settingsRepository = SettingsRepository(this)

        createNotificationChannel()
        
        audioPlayer.setOnPlaybackCompleteListener {
            if (_status.value == VoiceSessionStatus.SPEAKING) {
                _status.value = VoiceSessionStatus.IDLE
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY
        Log.d("VoiceSessionService", "Received action: $action")

        when (action) {
            ACTION_START_RECORDING -> handleStartRecording()
            ACTION_STOP_RECORDING -> handleStopRecording()
            ACTION_CANCEL -> handleCancel()
            ACTION_INTERRUPT -> handleInterrupt()
            ACTION_RESET -> handleReset()
            ACTION_REPLAY_LAST_RESPONSE -> handleReplayLastResponse()
            ACTION_NEW_SESSION -> handleNewSession()
            ACTION_TOGGLE_ALWAYS_LISTENING -> handleToggleAlwaysListening()
            ACTION_SEND_STREAMING_UTTERANCE -> handleSendStreamingUtterance()
        }

        return START_STICKY
    }

    private fun handleStartRecording() {
        activeJob?.cancel()
        audioPlayer.stop()

        activeJob = serviceScope.launch {
            val settingsResult = settingsRepository.getSettings()
            if (settingsResult.isFailure) {
                _status.value = VoiceSessionStatus.ERROR
                _errorMessage.value = "Settings storage error: ${settingsResult.exceptionOrNull()?.message}"
                updateNotification()
                return@launch
            }
            val settings = settingsResult.getOrThrow()
            _agentProfile.value = settings.selectedProfileName

            val route = audioRouteManager.preferConfiguredRoute(settings.audioInputPreference)
            _audioRoute.value = route

            _status.value = VoiceSessionStatus.LISTENING
            updateNotification()

            // Wait for legacy SCO connection if using Bluetooth routing
            if (route == AudioRoute.BLUETOOTH_HEADSET) {
                audioRouteManager.awaitLegacyScoConnection()
            }

            // Verify we are still in the LISTENING state before starting actual recording
            if (_status.value != VoiceSessionStatus.LISTENING) return@launch

            val recordResult = audioRecorder.startRecording()
            if (recordResult.isSuccess) {
                recordedFile = recordResult.getOrNull()
            } else {
                _status.value = VoiceSessionStatus.ERROR
                _errorMessage.value = "Failed to start microphone: ${recordResult.exceptionOrNull()?.message}"
                updateNotification()
            }
        }
    }

    private fun handleStopRecording() {
        if (_status.value != VoiceSessionStatus.LISTENING) return

        _status.value = VoiceSessionStatus.UPLOADING
        updateNotification()

        val stopResult = audioRecorder.stopRecording()
        if (stopResult.isFailure) {
            _status.value = VoiceSessionStatus.ERROR
            _errorMessage.value = "Recording failed: ${stopResult.exceptionOrNull()?.message}"
            updateNotification()
            recordedFile = null
            return
        }

        val file = stopResult.getOrNull()
        if (file == null || file.length() == 0L) {
            _status.value = VoiceSessionStatus.ERROR
            _errorMessage.value = "Microphone recorded no audio data."
            updateNotification()
            recordedFile = null
            return
        }

        // Successfully stopped recording, reference to file transferred to uploader
        recordedFile = null

        activeJob = serviceScope.launch {
            try {
                val settingsResult = settingsRepository.getSettings()
                if (settingsResult.isFailure) {
                    _status.value = VoiceSessionStatus.ERROR
                    _errorMessage.value = "Settings storage error: ${settingsResult.exceptionOrNull()?.message}"
                    updateNotification()
                    audioRecorder.cleanup(file)
                    recordedFile = null
                    return@launch
                }
                val settings = settingsResult.getOrThrow()
                
                // 1. Create session if we don't have one
                var sId = _sessionId.value
                if (sId.isNullOrEmpty()) {
                    _status.value = VoiceSessionStatus.THINKING
                    updateNotification()
                    val sessionResult = apiClient.createSession(settings)
                    if (sessionResult.isSuccess) {
                        sId = sessionResult.getOrThrow()
                        _sessionId.value = sId
                        setIsConnected(true)
                    } else {
                        val ex = sessionResult.exceptionOrNull()
                        if (ex?.message?.contains("Unauthorized", ignoreCase = true) == true) {
                            setGatewayConnection(GatewayConnectionStatus.ERROR, errorMessage = "Unauthorized")
                            _status.value = VoiceSessionStatus.ERROR
                            _errorMessage.value = "Unauthorized: Invalid Base URL or API Key."
                        } else {
                            setGatewayConnection(GatewayConnectionStatus.OFFLINE, errorMessage = ex?.message)
                            _status.value = VoiceSessionStatus.ERROR
                            _errorMessage.value = "Failed to open gateway session: ${ex?.message}"
                        }
                        updateNotification()
                        audioRecorder.cleanup(file)
                        recordedFile = null
                        return@launch
                    }
                }

                // 2. Submit Turn
                _status.value = VoiceSessionStatus.THINKING
                updateNotification()
                var turnResult = apiClient.submitTurn(settings, sId, file)

                // If session is expired (404), clear sessionId, create a new one, and retry the turn immediately
                if (turnResult.isFailure && turnResult.exceptionOrNull()?.message?.contains("404") == true) {
                    Log.d("VoiceSessionService", "Session expired (404), creating a new session and retrying turn...")
                    _sessionId.value = null
                    val sessionResult = apiClient.createSession(settings)
                    if (sessionResult.isSuccess) {
                        sId = sessionResult.getOrThrow()
                        _sessionId.value = sId
                        setIsConnected(true)
                        turnResult = apiClient.submitTurn(settings, sId, file)
                    } else {
                        val sessionEx = sessionResult.exceptionOrNull()
                        turnResult = Result.failure(sessionEx ?: java.io.IOException("Failed to recreate session"))
                    }
                }

                audioRecorder.cleanup(file)
                recordedFile = null

                if (turnResult.isSuccess) {
                    val response = turnResult.getOrThrow()
                    _transcript.value = response.transcript ?: ""
                    _responseText.value = response.responseText ?: ""
                    _lastAudioUrl.value = response.audioUrl
                    _turnHistory.value = _turnHistory.value + VoiceTurnUi(
                        id = "${System.currentTimeMillis()}_${_turnHistory.value.size}",
                        userText = _transcript.value,
                        assistantText = _responseText.value,
                        audioUrl = response.audioUrl,
                        createdAt = System.currentTimeMillis()
                    )
                    
                    val audioUrl = response.audioUrl
                    if (settings.responseMode == ResponseMode.TEXT_AUDIO && !audioUrl.isNullOrEmpty()) {
                        _status.value = VoiceSessionStatus.SPEAKING
                        updateNotification()
                        
                        val resolvedUrl = if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
                            audioUrl
                        } else {
                            "${settings.baseUrl.trim().removeSuffix("/")}/${audioUrl.trim().removePrefix("/")}"
                        }
                        
                        val playResult = audioPlayer.play(resolvedUrl, settings.apiKey)
                        if (playResult.isFailure) {
                            _status.value = VoiceSessionStatus.ERROR
                            _errorMessage.value = "Audio playback failed, but response received: ${playResult.exceptionOrNull()?.message}"
                            updateNotification()
                        } else {
                            _status.value = VoiceSessionStatus.IDLE
                            updateNotification()
                        }
                    } else {
                        _status.value = VoiceSessionStatus.IDLE
                        updateNotification()
                    }
                } else {
                    val ex = turnResult.exceptionOrNull()
                    if (ex?.message?.contains("Unauthorized", ignoreCase = true) == true) {
                        setGatewayConnection(GatewayConnectionStatus.ERROR, errorMessage = "Unauthorized")
                        _status.value = VoiceSessionStatus.ERROR
                        _errorMessage.value = "Unauthorized: Invalid API Key."
                    } else {
                        setGatewayConnection(GatewayConnectionStatus.OFFLINE, errorMessage = ex?.message)
                        _status.value = VoiceSessionStatus.ERROR
                        _errorMessage.value = "Failed to upload voice turn: ${ex?.message}"
                    }
                    if (ex?.message?.contains("404") == true) {
                        _sessionId.value = null
                    }
                    updateNotification()
                }

            } catch (e: CancellationException) {
                if (file.exists()) {
                    audioRecorder.cleanup(file)
                }
                recordedFile = null
            } catch (e: Exception) {
                _status.value = VoiceSessionStatus.ERROR
                _errorMessage.value = "Unexpected session error: ${e.message}"
                updateNotification()
                if (file.exists()) {
                    audioRecorder.cleanup(file)
                }
                recordedFile = null
            }
        }
    }

    private fun handleCancel() {
        activeJob?.cancel()
        playbackJob?.cancel()
        stopAlwaysListening(endSession = true)
        audioPlayer.stop()
        if (_status.value == VoiceSessionStatus.LISTENING) {
            audioRecorder.cancelRecording()
        }
        recordedFile?.let {
            audioRecorder.cleanup(it)
            recordedFile = null
        }
        audioRouteManager.enableBluetoothRouting(false)
        
        val settings = settingsRepository.getSettings().getOrNull()
        val sId = _sessionId.value
        if (settings != null && !sId.isNullOrEmpty()) {
            serviceScope.launch {
                apiClient.cancel(settings, sId)
            }
        }
        
        _status.value = VoiceSessionStatus.IDLE
        _errorMessage.value = null
        updateNotification()
    }

    private fun handleInterrupt() {
        activeJob?.cancel()
        playbackJob?.cancel()
        audioPlayer.stop()
        if (_isAlwaysListeningActive.value) {
            streamingClient.interrupt()
            streamingAudioRecorder.setPaused(false)
            streamingAudioRecorder.resetVad()
            _status.value = VoiceSessionStatus.LISTENING
            updateNotification()
            return
        }
        val settings = settingsRepository.getSettings().getOrNull()
        val sId = _sessionId.value
        if (settings != null && !sId.isNullOrEmpty() && _status.value == VoiceSessionStatus.SPEAKING) {
            serviceScope.launch {
                apiClient.cancel(settings, sId)
            }
        }
        _status.value = VoiceSessionStatus.IDLE
        updateNotification()
    }

    private fun handleReset() {
        handleCancel()
        _errorMessage.value = null
        _status.value = VoiceSessionStatus.IDLE
        updateNotification()
    }

    private fun handleReplayLastResponse() {
        val audioUrl = _lastAudioUrl.value ?: return
        activeJob?.cancel()
        activeJob = serviceScope.launch {
            val settings = settingsRepository.getSettings().getOrNull() ?: return@launch
            _status.value = VoiceSessionStatus.SPEAKING
            updateNotification()
            val resolvedUrl = resolveAudioUrl(audioUrl, settings.baseUrl)
            val playResult = audioPlayer.play(resolvedUrl, settings.apiKey)
            if (playResult.isFailure && _status.value == VoiceSessionStatus.SPEAKING) {
                _status.value = VoiceSessionStatus.ERROR
                _errorMessage.value = "Replay failed: ${playResult.exceptionOrNull()?.message}"
            } else if (_status.value == VoiceSessionStatus.SPEAKING) {
                _status.value = VoiceSessionStatus.IDLE
            }
            updateNotification()
        }
    }

    private fun handleNewSession() {
        activeJob?.cancel()
        playbackJob?.cancel()
        stopAlwaysListening(endSession = true)
        audioPlayer.stop()
        val settings = settingsRepository.getSettings().getOrNull()
        val sId = _sessionId.value
        if (settings != null && !sId.isNullOrEmpty()) {
            serviceScope.launch {
                apiClient.endSession(settings, sId)
            }
        }
        _sessionId.value = null
        clearHistory()
        _status.value = VoiceSessionStatus.IDLE
        updateNotification()
    }

    private fun handleToggleAlwaysListening() {
        if (_isAlwaysListeningActive.value) {
            stopAlwaysListening(endSession = true)
        } else {
            startAlwaysListening()
        }
    }

    private fun handleSendStreamingUtterance() {
        if (!_isAlwaysListeningActive.value) return
        streamingAudioRecorder.forceSpeechEnd()
        if (_status.value == VoiceSessionStatus.LISTENING) {
            _status.value = VoiceSessionStatus.THINKING
            updateNotification()
        }
    }

    private fun startAlwaysListening() {
        activeJob?.cancel()
        playbackJob?.cancel()
        audioPlayer.stop()

        activeJob = serviceScope.launch {
            val settingsResult = settingsRepository.getSettings()
            if (settingsResult.isFailure) {
                _status.value = VoiceSessionStatus.ERROR
                _errorMessage.value = "Settings storage error: ${settingsResult.exceptionOrNull()?.message}"
                updateNotification()
                return@launch
            }
            val settings = settingsResult.getOrThrow()
            _agentProfile.value = settings.selectedProfileName

            val route = audioRouteManager.preferConfiguredRoute(settings.audioInputPreference)
            _audioRoute.value = route
            if (route == AudioRoute.BLUETOOTH_HEADSET) {
                audioRouteManager.awaitLegacyScoConnection()
            }

            val connectResult = streamingClient.connect(settings, _sessionId.value, streamingListener(settings))
            if (connectResult.isFailure) {
                _status.value = VoiceSessionStatus.ERROR
                _errorMessage.value = "Streaming connection failed: ${connectResult.exceptionOrNull()?.message}"
                updateNotification()
                return@launch
            }

            _isAlwaysListeningActive.value = true
            val recordResult = streamingAudioRecorder.start(
                scope = serviceScope,
                vadEngine = createVad(settings),
                onSpeechStart = speechStart@{ debug ->
                    if (!_isAlwaysListeningActive.value) return@speechStart false
                    if (_status.value == VoiceSessionStatus.SPEAKING) {
                        if (!settings.bargeInEnabled) return@speechStart false
                        val elapsedPlaybackMs = System.currentTimeMillis() - playbackStartedAtMs
                        if (elapsedPlaybackMs < BARGE_IN_PLAYBACK_GUARD_MS) return@speechStart false
                        if (debug.rms < BARGE_IN_MIN_RMS) return@speechStart false
                        serviceScope.launch {
                            audioPlayer.fadeOutAndStop(BARGE_IN_FADE_MS)
                            streamingClient.interrupt()
                            _status.value = VoiceSessionStatus.LISTENING
                            updateNotification()
                        }
                        true
                    } else {
                        serviceScope.launch {
                            _status.value = VoiceSessionStatus.LISTENING
                            updateNotification()
                        }
                        true
                    }
                },
                onAudioChunk = { chunk ->
                    if (_isAlwaysListeningActive.value) streamingClient.sendAudioChunk(chunk)
                },
                onSpeechEnd = {
                    serviceScope.launch {
                        if (!_isAlwaysListeningActive.value) return@launch
                        streamingClient.speechEnd()
                        if (_status.value == VoiceSessionStatus.LISTENING) {
                            _status.value = VoiceSessionStatus.THINKING
                            updateNotification()
                        }
                    }
                },
                onVadDebug = { debug ->
                    if (_isAlwaysListeningActive.value) {
                        streamingClient.sendVadDebug(
                            event = debug.event,
                            speaking = debug.speaking,
                            rms = debug.rms,
                            bufferedBytes = debug.bufferedBytes
                        )
                    }
                }
            )
            if (recordResult.isFailure) {
                _isAlwaysListeningActive.value = false
                streamingClient.close()
                _status.value = VoiceSessionStatus.ERROR
                _errorMessage.value = "Failed to start streaming microphone: ${recordResult.exceptionOrNull()?.message}"
                updateNotification()
                return@launch
            }

            _status.value = VoiceSessionStatus.IDLE
            _errorMessage.value = null
            updateNotification()
        }
    }

    private fun stopAlwaysListening(endSession: Boolean) {
        if (!_isAlwaysListeningActive.value) return
        _isAlwaysListeningActive.value = false
        streamingAudioRecorder.stop()
        if (endSession) {
            streamingClient.endSession()
        } else {
            streamingClient.close()
        }
        playbackJob?.cancel()
        audioPlayer.stop()
        audioRouteManager.enableBluetoothRouting(false)
        _status.value = VoiceSessionStatus.IDLE
        updateNotification()
    }

    private fun streamingListener(settings: HermesSettings) = object : StreamingVoiceClient.Listener {
        override fun onOpen() {
            serviceScope.launch {
                setIsConnected(true)
            }
        }

        override fun onSession(sessionId: String, profileName: String) {
            serviceScope.launch {
                _sessionId.value = sessionId
                _agentProfile.value = profileName.ifBlank { settings.selectedProfileName }
            }
        }

        override fun onState(status: String) {
            serviceScope.launch {
                if (!_isAlwaysListeningActive.value) return@launch
                _status.value = when (status) {
                    "thinking" -> VoiceSessionStatus.THINKING
                    "speaking" -> VoiceSessionStatus.THINKING
                    "interrupted", "idle", "listening" -> VoiceSessionStatus.IDLE
                    else -> _status.value
                }
                updateNotification()
            }
        }

        override fun onFinalTranscript(text: String) {
            serviceScope.launch {
                _transcript.value = text
            }
        }

        override fun onAssistantTextFinal(text: String) {
            serviceScope.launch {
                _responseText.value = text
                _turnHistory.value = _turnHistory.value + VoiceTurnUi(
                    id = "${System.currentTimeMillis()}_${_turnHistory.value.size}",
                    userText = _transcript.value,
                    assistantText = text,
                    audioUrl = null,
                    createdAt = System.currentTimeMillis()
                )
            }
        }

        override fun onAudioUrl(url: String) {
            serviceScope.launch {
                _lastAudioUrl.value = url
                val history = _turnHistory.value
                if (history.isNotEmpty()) {
                    _turnHistory.value = history.dropLast(1) + history.last().copy(audioUrl = url)
                }
                playStreamingAudio(url, settings)
            }
        }

        override fun onError(message: String) {
            serviceScope.launch {
                _status.value = VoiceSessionStatus.ERROR
                _errorMessage.value = message
                updateNotification()
            }
        }

        override fun onClosed() {
            serviceScope.launch {
                if (_isAlwaysListeningActive.value) {
                    _isAlwaysListeningActive.value = false
                    streamingAudioRecorder.stop()
                    _status.value = VoiceSessionStatus.ERROR
                    _errorMessage.value = "Streaming connection closed"
                    updateNotification()
                }
            }
        }
    }

    private fun playStreamingAudio(audioUrl: String, settings: HermesSettings) {
        playbackJob?.cancel()
        playbackJob = serviceScope.launch {
            _status.value = VoiceSessionStatus.SPEAKING
            playbackStartedAtMs = System.currentTimeMillis()
            streamingAudioRecorder.setPaused(!settings.bargeInEnabled)
            updateNotification()
            val playResult = audioPlayer.play(resolveAudioUrl(audioUrl, settings.baseUrl), settings.apiKey)
            streamingAudioRecorder.setPaused(false)
            if (_isAlwaysListeningActive.value && _status.value == VoiceSessionStatus.SPEAKING) {
                _status.value = VoiceSessionStatus.IDLE
            } else if (!_isAlwaysListeningActive.value && playResult.isSuccess) {
                _status.value = VoiceSessionStatus.IDLE
            }
            if (playResult.isFailure && _status.value == VoiceSessionStatus.SPEAKING) {
                _status.value = VoiceSessionStatus.ERROR
                _errorMessage.value = "Audio playback failed, but response received: ${playResult.exceptionOrNull()?.message}"
            }
            updateNotification()
        }
    }

    private fun createVad(settings: HermesSettings): VoiceVad {
        if (settings.talkInteractionMode == com.hermes.voiceremote.settings.TalkInteractionMode.ALWAYS_LISTENING) {
            // ponytail: Silero ONNX is hearing high RMS but not activating on device; RMS keeps the live mode usable while we tune the model path.
            return RmsVad(
                sampleRate = StreamingAudioRecorder.SAMPLE_RATE,
                threshold = 600.0,
                startMs = 250,
                endSilenceMs = maxOf(settings.vadSilenceMs, ALWAYS_LISTENING_MIN_SILENCE_MS)
            )
        }
        return when (settings.vadEngine) {
            VadEngine.RMS -> RmsVad(
                sampleRate = StreamingAudioRecorder.SAMPLE_RATE,
                threshold = settings.vadSpeechThreshold.toDouble(),
                startMs = if (settings.bargeInEnabled) settings.bargeInMinSpeechMs else 250,
                endSilenceMs = settings.vadSilenceMs
            )
            VadEngine.SILERO_ONNX -> SileroOnnxVad(
                context = this,
                activationThreshold = settings.vadSpeechThreshold,
                minSpeechMs = if (settings.bargeInEnabled) settings.bargeInMinSpeechMs else 100,
                minSilenceMs = settings.vadSilenceMs
            )
        }
    }

    private fun resolveAudioUrl(audioUrl: String, baseUrl: String): String {
        return if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
            audioUrl
        } else {
            "${baseUrl.trim().removeSuffix("/")}/${audioUrl.trim().removePrefix("/")}"
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hermes Active Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Protects active recording, uploading, and playback work"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification(_status.value)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
        
        if (_status.value != VoiceSessionStatus.IDLE || _isAlwaysListeningActive.value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildNotification(status: VoiceSessionStatus): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceSessionService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, VoiceSessionService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 2, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Hermes Voice Remote"
        val text = when (status) {
            VoiceSessionStatus.IDLE -> "Gateway is connected"
            VoiceSessionStatus.LISTENING -> "Listening to your voice..."
            VoiceSessionStatus.UPLOADING -> "Uploading audio turn..."
            VoiceSessionStatus.THINKING -> "Thinking..."
            VoiceSessionStatus.SPEAKING -> "Hermes is responding..."
            VoiceSessionStatus.ERROR -> "Session error encountered."
        }
        val effectiveText = if (_isAlwaysListeningActive.value && status == VoiceSessionStatus.IDLE) {
            "Always listening is active"
        } else {
            text
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(effectiveText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(status != VoiceSessionStatus.IDLE || _isAlwaysListeningActive.value)

         if (status == VoiceSessionStatus.LISTENING) {
            builder.addAction(android.R.drawable.ic_media_pause, "Stop & Send", stopPendingIntent)
         }
         if (status == VoiceSessionStatus.UPLOADING || status == VoiceSessionStatus.THINKING || status == VoiceSessionStatus.SPEAKING) {
            builder.addAction(android.R.drawable.ic_delete, "Cancel", cancelPendingIntent)
         }

        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (_status.value == VoiceSessionStatus.LISTENING && recordedFile != null) {
            audioRecorder.stopRecording()
        }
        streamingAudioRecorder.stop()
        streamingClient.close()
        audioPlayer.release()
    }

    companion object {
        private const val CHANNEL_ID = "hermes_voice_channel"
        private const val NOTIFICATION_ID = 484

        const val ACTION_START_RECORDING = "com.hermes.voiceremote.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.hermes.voiceremote.STOP_RECORDING"
        const val ACTION_CANCEL = "com.hermes.voiceremote.CANCEL"
        const val ACTION_INTERRUPT = "com.hermes.voiceremote.INTERRUPT"
        const val ACTION_RESET = "com.hermes.voiceremote.RESET"
        const val ACTION_REPLAY_LAST_RESPONSE = "com.hermes.voiceremote.REPLAY_LAST_RESPONSE"
        const val ACTION_NEW_SESSION = "com.hermes.voiceremote.NEW_SESSION"
        const val ACTION_TOGGLE_ALWAYS_LISTENING = "com.hermes.voiceremote.TOGGLE_ALWAYS_LISTENING"
        const val ACTION_SEND_STREAMING_UTTERANCE = "com.hermes.voiceremote.SEND_STREAMING_UTTERANCE"

        private const val BARGE_IN_PLAYBACK_GUARD_MS = 500L
        private const val BARGE_IN_FADE_MS = 100L
        private const val BARGE_IN_MIN_RMS = 8000
        private const val ALWAYS_LISTENING_MIN_SILENCE_MS = 1200

        private val _status = MutableStateFlow(VoiceSessionStatus.IDLE)
        val status = _status.asStateFlow()

        private val _sessionId = MutableStateFlow<String?>(null)
        val sessionId = _sessionId.asStateFlow()

        private val _agentProfile = MutableStateFlow("Main")
        val agentProfile = _agentProfile.asStateFlow()

        private val _isConnected = MutableStateFlow(false)
        val isConnected = _isConnected.asStateFlow()

        private val _connectionStatus = MutableStateFlow(GatewayConnectionStatus.OFFLINE)
        val connectionStatus = _connectionStatus.asStateFlow()

        private val _lastHealthCheckedAt = MutableStateFlow<Long?>(null)
        val lastHealthCheckedAt = _lastHealthCheckedAt.asStateFlow()

        private val _gatewayLatencyMs = MutableStateFlow<Long?>(null)
        val gatewayLatencyMs = _gatewayLatencyMs.asStateFlow()

        private val _connectionErrorMessage = MutableStateFlow<String?>(null)
        val connectionErrorMessage = _connectionErrorMessage.asStateFlow()

        private val _audioRoute = MutableStateFlow(AudioRoute.PHONE)
        val audioRoute = _audioRoute.asStateFlow()

        private val _transcript = MutableStateFlow("")
        val transcript = _transcript.asStateFlow()

        private val _responseText = MutableStateFlow("")
        val responseText = _responseText.asStateFlow()

        private val _lastAudioUrl = MutableStateFlow<String?>(null)
        val lastAudioUrl = _lastAudioUrl.asStateFlow()

        private val _isAlwaysListeningActive = MutableStateFlow(false)
        val isAlwaysListeningActive = _isAlwaysListeningActive.asStateFlow()

        private val _turnHistory = MutableStateFlow<List<VoiceTurnUi>>(emptyList())
        val turnHistory = _turnHistory.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage = _errorMessage.asStateFlow()

        fun setSessionId(id: String?) {
            _sessionId.value = id
        }

        fun setIsConnected(connected: Boolean) {
            _isConnected.value = connected
            _connectionStatus.value = if (connected) GatewayConnectionStatus.ONLINE else GatewayConnectionStatus.OFFLINE
            if (connected) {
                _connectionErrorMessage.value = null
            }
        }

        fun setGatewayConnection(
            status: GatewayConnectionStatus,
            checkedAt: Long? = null,
            latencyMs: Long? = null,
            errorMessage: String? = null
        ) {
            _connectionStatus.value = status
            _isConnected.value = status == GatewayConnectionStatus.ONLINE
            checkedAt?.let { _lastHealthCheckedAt.value = it }
            _gatewayLatencyMs.value = latencyMs
            _connectionErrorMessage.value = errorMessage
        }

        fun setAgentProfile(profileName: String) {
            _agentProfile.value = profileName.ifBlank { "Main" }
        }

        fun clearHistory() {
            _transcript.value = ""
            _responseText.value = ""
            _lastAudioUrl.value = null
            _turnHistory.value = emptyList()
            _errorMessage.value = null
        }
    }
}
