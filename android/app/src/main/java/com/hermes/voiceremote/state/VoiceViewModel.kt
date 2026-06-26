package com.hermes.voiceremote.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.voiceremote.audio.AudioRouteManager
import com.hermes.voiceremote.network.HermesApiClient
import com.hermes.voiceremote.network.HermesProfileDto
import com.hermes.voiceremote.service.VoiceServiceController
import com.hermes.voiceremote.service.VoiceSessionService
import com.hermes.voiceremote.settings.AudioInputPreference
import com.hermes.voiceremote.settings.AudioRoute
import com.hermes.voiceremote.settings.HermesSettings
import com.hermes.voiceremote.settings.ResponseMode
import com.hermes.voiceremote.settings.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val serviceController = VoiceServiceController(application)
    private val apiClient = HermesApiClient()
    private val audioRouteManager = AudioRouteManager(application)

    private val defaultSettings = HermesSettings("", "", "main", "Main", ResponseMode.TEXT_AUDIO, AudioInputPreference.AUTO)

    private val _settings = MutableStateFlow(
        settingsRepository.getSettings().getOrDefault(defaultSettings)
    )
    val settings = _settings.asStateFlow()

    private val _storageError = MutableStateFlow<String?>(settingsRepository.initError)
    val storageError = _storageError.asStateFlow()

    private val _profiles = MutableStateFlow<List<HermesProfileDto>>(emptyList())
    val profiles = _profiles.asStateFlow()

    private val _profileLoadError = MutableStateFlow<String?>(null)
    val profileLoadError = _profileLoadError.asStateFlow()

    val uiState: StateFlow<VoiceSessionUiState> = combine(
        listOf(
            VoiceSessionService.status,
            VoiceSessionService.sessionId,
            VoiceSessionService.agentProfile,
            VoiceSessionService.isConnected,
            VoiceSessionService.audioRoute,
            VoiceSessionService.transcript,
            VoiceSessionService.responseText,
            VoiceSessionService.lastAudioUrl,
            VoiceSessionService.turnHistory,
            VoiceSessionService.errorMessage
        )
    ) { array ->
        val status = array[0] as VoiceSessionStatus
        val sessionId = array[1] as? String
        val agentProfile = array[2] as String
        val isConnected = array[3] as Boolean
        val audioRoute = array[4] as AudioRoute
        val transcript = array[5] as String
        val responseText = array[6] as String
        val lastAudioUrl = array[7] as? String
        @Suppress("UNCHECKED_CAST")
        val turnHistory = array[8] as List<VoiceTurnUi>
        val errorMessage = array[9] as? String

        val isBusy = status == VoiceSessionStatus.UPLOADING ||
                     status == VoiceSessionStatus.THINKING ||
                     status == VoiceSessionStatus.SPEAKING

        VoiceSessionUiState(
            status = status,
            sessionId = sessionId,
            agentProfile = agentProfile,
            isConnected = isConnected,
            audioRoute = audioRoute,
            transcript = transcript,
            responseText = responseText,
            lastAudioUrl = lastAudioUrl,
            isPlaybackAvailable = !lastAudioUrl.isNullOrEmpty(),
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
    }

    fun loadSettings(): HermesSettings {
        val result = settingsRepository.getSettings()
        _storageError.value = settingsRepository.initError
        val currentSettings = result.getOrDefault(defaultSettings)
        _settings.value = currentSettings
        VoiceSessionService.setAgentProfile(currentSettings.selectedProfileName)
        
        // Asynchronously check health on start to update the connection state dot
        viewModelScope.launch {
            if (currentSettings.baseUrl.isNotEmpty() && currentSettings.apiKey.isNotEmpty()) {
                val checkResult = apiClient.health(currentSettings)
                VoiceSessionService.setIsConnected(checkResult.isSuccess)
                if (checkResult.isSuccess) {
                    loadProfiles(currentSettings)
                }
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
                val checkResult = apiClient.health(newSettings)
                VoiceSessionService.setIsConnected(checkResult.isSuccess)
                if (checkResult.isSuccess) {
                    loadProfiles(newSettings)
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

            val result = apiClient.health(targetSettings)
            if (result.isSuccess) {
                val isOk = result.getOrThrow()
                VoiceSessionService.setIsConnected(isOk)
                if (isOk) {
                    loadProfiles(targetSettings)
                    onResult(true, "Successfully connected to Hermes Voice Gateway!")
                } else {
                    onResult(false, "Gateway responded with health failure.")
                }
            } else {
                VoiceSessionService.setIsConnected(false)
                val msg = result.exceptionOrNull()?.message ?: "Unknown error"
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
}
