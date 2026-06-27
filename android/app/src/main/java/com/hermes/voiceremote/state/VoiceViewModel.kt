package com.hermes.voiceremote.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.voiceremote.audio.AudioRouteManager
import com.hermes.voiceremote.network.HermesApiClient
import com.hermes.voiceremote.network.HermesProfileDto
import com.hermes.voiceremote.network.TtsVoiceDto
import com.hermes.voiceremote.service.VoiceServiceController
import com.hermes.voiceremote.service.VoiceSessionService
import com.hermes.voiceremote.settings.AudioInputPreference
import com.hermes.voiceremote.settings.AudioRoute
import com.hermes.voiceremote.settings.HermesSettings
import com.hermes.voiceremote.settings.ResponseMode
import com.hermes.voiceremote.settings.SettingsRepository
import com.hermes.voiceremote.settings.TalkInteractionMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val serviceController = VoiceServiceController(application)
    private val apiClient = HermesApiClient()
    private val audioRouteManager = AudioRouteManager(application)

    private val defaultSettings = HermesSettings("", "", "main", "Main", ResponseMode.TEXT_AUDIO, AudioInputPreference.AUTO, TalkInteractionMode.TAP_TO_TALK)
    private var healthCheckJob: Job? = null

    private val _settings = MutableStateFlow(
        settingsRepository.getSettings().getOrDefault(defaultSettings)
    )
    val settings = _settings.asStateFlow()

    private val _storageError = MutableStateFlow<String?>(settingsRepository.initError)
    val storageError = _storageError.asStateFlow()

    private val _profiles = MutableStateFlow(settingsRepository.getCachedProfiles())
    val profiles = _profiles.asStateFlow()

    private val _profileLoadError = MutableStateFlow<String?>(null)
    val profileLoadError = _profileLoadError.asStateFlow()

    private val _ttsVoices = MutableStateFlow(settingsRepository.getCachedTtsVoices())
    val ttsVoices = _ttsVoices.asStateFlow()

    val uiState: StateFlow<VoiceSessionUiState> = combine(
        listOf(
            VoiceSessionService.status,
            VoiceSessionService.sessionId,
            VoiceSessionService.agentProfile,
            VoiceSessionService.isConnected,
            VoiceSessionService.connectionStatus,
            VoiceSessionService.lastHealthCheckedAt,
            VoiceSessionService.gatewayLatencyMs,
            VoiceSessionService.connectionErrorMessage,
            VoiceSessionService.audioRoute,
            VoiceSessionService.transcript,
            VoiceSessionService.responseText,
            VoiceSessionService.lastAudioUrl,
            VoiceSessionService.isAlwaysListeningActive,
            VoiceSessionService.turnHistory,
            VoiceSessionService.errorMessage
        )
    ) { array ->
        val status = array[0] as VoiceSessionStatus
        val sessionId = array[1] as? String
        val agentProfile = array[2] as String
        val isConnected = array[3] as Boolean
        val connectionStatus = array[4] as GatewayConnectionStatus
        val lastHealthCheckedAt = array[5] as? Long
        val gatewayLatencyMs = array[6] as? Long
        val connectionErrorMessage = array[7] as? String
        val audioRoute = array[8] as AudioRoute
        val transcript = array[9] as String
        val responseText = array[10] as String
        val lastAudioUrl = array[11] as? String
        val isAlwaysListeningActive = array[12] as Boolean
        @Suppress("UNCHECKED_CAST")
        val turnHistory = array[13] as List<VoiceTurnUi>
        val errorMessage = array[14] as? String

        val isBusy = status == VoiceSessionStatus.UPLOADING ||
                     status == VoiceSessionStatus.THINKING ||
                     status == VoiceSessionStatus.SPEAKING

        VoiceSessionUiState(
            status = status,
            sessionId = sessionId,
            agentProfile = agentProfile,
            isConnected = isConnected,
            connectionStatus = connectionStatus,
            lastHealthCheckedAt = lastHealthCheckedAt,
            gatewayLatencyMs = gatewayLatencyMs,
            connectionErrorMessage = connectionErrorMessage,
            audioRoute = audioRoute,
            transcript = transcript,
            responseText = responseText,
            lastAudioUrl = lastAudioUrl,
            isPlaybackAvailable = !lastAudioUrl.isNullOrEmpty(),
            isAlwaysListeningActive = isAlwaysListeningActive,
            turnHistory = turnHistory,
            errorMessage = errorMessage,
            isBusy = isBusy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VoiceSessionUiState(agentProfile = _settings.value.selectedProfileName)
    )

    init {
        loadSettings()
        startPeriodicHealthChecks()
    }

    fun loadSettings(): HermesSettings {
        val result = settingsRepository.getSettings()
        _storageError.value = settingsRepository.initError
        val currentSettings = result.getOrDefault(defaultSettings)
        _settings.value = currentSettings
        VoiceSessionService.setAgentProfile(currentSettings.selectedProfileName)
        
        viewModelScope.launch {
            val connected = refreshConnectionHealth(currentSettings)
            if (connected) {
                loadProfiles(currentSettings)
                loadTtsVoices(currentSettings)
            }
        }
        return currentSettings
    }

    fun saveSettings(newSettings: HermesSettings): Boolean {
        val result = settingsRepository.saveSettings(newSettings)
        _storageError.value = settingsRepository.initError ?: result.exceptionOrNull()?.localizedMessage
        if (result.isSuccess) {
            _settings.value = newSettings
            VoiceSessionService.setAgentProfile(newSettings.selectedProfileName)
            // Reset existing session so a new one is built with updated config
            VoiceSessionService.setSessionId(null)
            VoiceSessionService.clearHistory()
            viewModelScope.launch {
                if (refreshConnectionHealth(newSettings)) {
                    loadProfiles(newSettings)
                    loadTtsVoices(newSettings)
                }
            }
            return true
        }
        return false
    }

    fun hasValidSettings(): Boolean {
        return settingsRepository.hasSettings()
    }

    fun onTalkButtonPressed() {
        val state = uiState.value
        if (settings.value.talkInteractionMode == TalkInteractionMode.ALWAYS_LISTENING) {
            when (state.status) {
                VoiceSessionStatus.ERROR -> serviceController.reset()
                VoiceSessionStatus.SPEAKING -> serviceController.interrupt()
                else -> serviceController.toggleAlwaysListening()
            }
            return
        }

        when (state.status) {
            VoiceSessionStatus.IDLE -> {
                serviceController.startRecording()
            }
            VoiceSessionStatus.LISTENING -> {
                serviceController.stopRecording()
            }
            VoiceSessionStatus.UPLOADING -> {
                // Disabled state or cancel if safe
                serviceController.cancel()
            }
            VoiceSessionStatus.THINKING -> {
                serviceController.cancel()
            }
            VoiceSessionStatus.SPEAKING -> {
                serviceController.interrupt()
            }
            VoiceSessionStatus.ERROR -> {
                serviceController.reset()
            }
        }
    }

    fun onTalkPressStart() {
        if (uiState.value.status == VoiceSessionStatus.IDLE) {
            serviceController.startRecording()
        }
    }

    fun onTalkPressEnd() {
        if (uiState.value.status == VoiceSessionStatus.LISTENING) {
            serviceController.stopRecording()
        }
    }

    fun onCancel() {
        serviceController.cancel()
    }

    fun onInterrupt() {
        serviceController.interrupt()
    }

    fun onClear() {
        VoiceSessionService.clearHistory()
    }

    fun onReplayLastResponse() {
        serviceController.replayLastResponse()
    }

    fun onNewSession() {
        serviceController.newSession()
    }

    fun loadProfiles(customSettings: HermesSettings? = null, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val targetSettings = customSettings ?: settings.value
            val result = apiClient.getProfiles(targetSettings)
            if (result.isSuccess) {
                val response = result.getOrThrow()
                _profiles.value = response.profiles
                settingsRepository.saveCachedProfiles(response.profiles)
                _profileLoadError.value = null
                val selected = chooseSelectedProfile(response.profiles, response.defaultProfileId, targetSettings)
                if (selected != null && (selected.id != targetSettings.selectedProfileId || selected.name != targetSettings.selectedProfileName)) {
                    val updatedSettings = targetSettings.copy(
                        selectedProfileId = selected.id,
                        selectedProfileName = selected.name
                    )
                    settingsRepository.saveSettings(updatedSettings)
                    _settings.value = updatedSettings
                    VoiceSessionService.setAgentProfile(updatedSettings.selectedProfileName)
                    VoiceSessionService.setSessionId(null)
                    VoiceSessionService.clearHistory()
                }
                onResult?.invoke(true, "Loaded ${response.profiles.size} profiles")
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unknown error"
                _profileLoadError.value = message
                onResult?.invoke(false, "Profile load failed: $message")
            }
        }
    }

    fun loadTtsVoices(customSettings: HermesSettings? = null, onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            val targetSettings = customSettings ?: settings.value
            val result = apiClient.getTtsVoices(targetSettings)
            if (result.isSuccess) {
                val response = result.getOrThrow()
                _ttsVoices.value = response.voices
                settingsRepository.saveCachedTtsVoices(response.voices)
                onResult?.invoke(true, "Loaded ${response.voices.size} voices")
            } else {
                val message = result.exceptionOrNull()?.message ?: "Unknown error"
                onResult?.invoke(false, "Voice load failed: $message")
            }
        }
    }

    fun startPeriodicHealthChecks() {
        healthCheckJob?.cancel()
        healthCheckJob = viewModelScope.launch {
            while (true) {
                refreshConnectionHealth(settings.value)
                delay(45_000)
            }
        }
    }

    fun stopPeriodicHealthChecks() {
        healthCheckJob?.cancel()
        healthCheckJob = null
    }

    private suspend fun refreshConnectionHealth(settings: HermesSettings): Boolean {
        if (settings.baseUrl.isEmpty() || settings.apiKey.isEmpty()) {
            VoiceSessionService.setGatewayConnection(
                status = GatewayConnectionStatus.OFFLINE,
                checkedAt = System.currentTimeMillis(),
                errorMessage = "Gateway URL or API key is missing"
            )
            return false
        }

        VoiceSessionService.setGatewayConnection(GatewayConnectionStatus.CONNECTING)
        var result: Result<Boolean>? = null
        val latencyMs = measureTimeMillis {
            result = apiClient.health(settings)
        }
        val checkedAt = System.currentTimeMillis()
        val healthResult = result ?: Result.failure(IllegalStateException("Health check did not run"))
        return if (healthResult.isSuccess && healthResult.getOrThrow()) {
            VoiceSessionService.setGatewayConnection(
                status = GatewayConnectionStatus.ONLINE,
                checkedAt = checkedAt,
                latencyMs = latencyMs
            )
            true
        } else {
            VoiceSessionService.setGatewayConnection(
                status = GatewayConnectionStatus.OFFLINE,
                checkedAt = checkedAt,
                latencyMs = latencyMs,
                errorMessage = healthResult.exceptionOrNull()?.message ?: "Gateway health check failed"
            )
            false
        }
    }

    fun onTestConnection(customSettings: HermesSettings? = null, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val targetSettings = customSettings ?: settings.value
            if (targetSettings.baseUrl.isEmpty()) {
                onResult(false, "Base URL is missing")
                return@launch
            }
            if (targetSettings.apiKey.isEmpty()) {
                onResult(false, "API Key is missing")
                return@launch
            }

            val isOk = refreshConnectionHealth(targetSettings)
            if (isOk) {
                loadProfiles(targetSettings)
                loadTtsVoices(targetSettings)
                onResult(true, "Successfully connected to Hermes Voice Gateway!")
            } else {
                val msg = VoiceSessionService.connectionErrorMessage.value ?: "Unknown error"
                onResult(false, "Connection failed: $msg")
            }
        }
    }

    private fun chooseSelectedProfile(
        profiles: List<HermesProfileDto>,
        defaultProfileId: String,
        settings: HermesSettings
    ): HermesProfileDto? {
        if (profiles.isEmpty()) return null
        val current = profiles.firstOrNull { it.id == settings.selectedProfileId }
        if (current != null && settings.selectedProfileId != "main") return current
        return profiles.firstOrNull { it.id == "main" || it.name.equals("Main", ignoreCase = true) }
            ?: profiles.firstOrNull { it.id == defaultProfileId }
            ?: profiles.firstOrNull { it.isDefault }
            ?: profiles.first()
    }

    override fun onCleared() {
        stopPeriodicHealthChecks()
        super.onCleared()
    }
}
