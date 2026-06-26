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
import com.hermes.voiceremote.network.HermesApiClient
import com.hermes.voiceremote.settings.AudioRoute
import com.hermes.voiceremote.settings.ResponseMode
import com.hermes.voiceremote.settings.SettingsRepository
import com.hermes.voiceremote.state.VoiceSessionStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VoiceSessionService : Service() {

    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var audioRouteManager: AudioRouteManager
    private lateinit var apiClient: HermesApiClient
    private lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeJob: Job? = null
    private var recordedFile: File? = null

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorder(this)
        audioPlayer = AudioPlayer(this)
        audioRouteManager = AudioRouteManager(this)
        apiClient = HermesApiClient()
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
            _agentProfile.value = settings.agentProfile

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
                        _isConnected.value = true
                    } else {
                        val ex = sessionResult.exceptionOrNull()
                        if (ex?.message?.contains("Unauthorized", ignoreCase = true) == true) {
                            _isConnected.value = false
                            _status.value = VoiceSessionStatus.ERROR
                            _errorMessage.value = "Unauthorized: Invalid Base URL or API Key."
                        } else {
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
                        _isConnected.value = true
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
                        _isConnected.value = false
                        _status.value = VoiceSessionStatus.ERROR
                        _errorMessage.value = "Unauthorized: Invalid API Key."
                    } else {
                        _status.value = VoiceSessionStatus.ERROR
                        _errorMessage.value = "Failed to upload voice turn: ${ex?.message}"
                    }
                    if (ex?.message?.contains("404") == true) {
                        _sessionId.value = null
                    }
                    updateNotification()
                }

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
        audioPlayer.stop()
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
        
        if (_status.value != VoiceSessionStatus.IDLE) {
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(status != VoiceSessionStatus.IDLE)

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
        if (_status.value == VoiceSessionStatus.LISTENING) {
            audioRecorder.stopRecording()
        }
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

        private val _status = MutableStateFlow(VoiceSessionStatus.IDLE)
        val status = _status.asStateFlow()

        private val _sessionId = MutableStateFlow<String?>(null)
        val sessionId = _sessionId.asStateFlow()

        private val _agentProfile = MutableStateFlow("Vex Volt")
        val agentProfile = _agentProfile.asStateFlow()

        private val _isConnected = MutableStateFlow(false)
        val isConnected = _isConnected.asStateFlow()

        private val _audioRoute = MutableStateFlow(AudioRoute.PHONE)
        val audioRoute = _audioRoute.asStateFlow()

        private val _transcript = MutableStateFlow("")
        val transcript = _transcript.asStateFlow()

        private val _responseText = MutableStateFlow("")
        val responseText = _responseText.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage = _errorMessage.asStateFlow()

        fun setSessionId(id: String?) {
            _sessionId.value = id
        }

        fun setIsConnected(connected: Boolean) {
            _isConnected.value = connected
        }

        fun clearHistory() {
            _transcript.value = ""
            _responseText.value = ""
            _errorMessage.value = null
        }
    }
}
