package com.hermes.voiceremote.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hermes.voiceremote.audio.AudioRouteManager
import com.hermes.voiceremote.network.HermesApiClient
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

    private val defaultSettings = HermesSettings("", "", "Vex Volt", ResponseMode.TEXT_AUDIO, AudioInputPreference.AUTO)

    private val _settings = MutableStateFlow(
        settingsRepository.getSettings().getOrDefault(defaultSettings)
    )
    val settings = _settings.asStateFlow()

    private val _storageError = MutableStateFlow<String?>(settingsRepository.initError)
    val storageError = _storageError.asStateFlow()

    val uiState: StateFlow<VoiceSessionUiState> = combine(
        listOf(
            VoiceSessionService.status,
            VoiceSessionService.sessionId,
            VoiceSessionService.agentProfile,
            VoiceSessionService.isConnected,
            VoiceSessionService.audioRoute,
            VoiceSessionService.transcript,
            VoiceSessionService.responseText,
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
        val errorMessage = array[7] as? String

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
            errorMessage = errorMessage,
            isBusy = isBusy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VoiceSessionUiState()
    )

    init {
        loadSettings()
    }

    fun loadSettings(): HermesSettings {
        val result = settingsRepository.getSettings()
        _storageError.value = settingsRepository.initError
        val currentSettings = result.getOrDefault(defaultSettings)
        _settings.value = currentSettings
        
        // Asynchronously check health on start to update the connection state dot
        viewModelScope.launch {
            if (currentSettings.baseUrl.isNotEmpty() && currentSettings.apiKey.isNotEmpty()) {
                val checkResult = apiClient.health(currentSettings)
                VoiceSessionService.setIsConnected(checkResult.isSuccess)
            }
        }
        return currentSettings
    }

    fun saveSettings(newSettings: HermesSettings): Boolean {
        val result = settingsRepository.saveSettings(newSettings)
        _storageError.value = settingsRepository.initError ?: result.exceptionOrNull()?.localizedMessage
        if (result.isSuccess) {
            _settings.value = newSettings
            // Reset existing session so a new one is built with updated config
            VoiceSessionService.setSessionId(null)
            viewModelScope.launch {
                val checkResult = apiClient.health(newSettings)
                VoiceSessionService.setIsConnected(checkResult.isSuccess)
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
}
