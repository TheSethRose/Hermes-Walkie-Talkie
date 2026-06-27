package com.hermes.voiceremote.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.voiceremote.settings.AudioInputPreference
import com.hermes.voiceremote.settings.HermesSettings
import com.hermes.voiceremote.settings.ResponseMode
import com.hermes.voiceremote.settings.TalkInteractionMode
import com.hermes.voiceremote.settings.defaultVadSettings
import com.hermes.voiceremote.state.VoiceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: VoiceViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentSettings = remember { viewModel.loadSettings() }
    val storageError by viewModel.storageError.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val ttsVoices by viewModel.ttsVoices.collectAsState()
    val profileLoadError by viewModel.profileLoadError.collectAsState()
    val currentModePreset = remember(currentSettings.talkInteractionMode) {
        currentSettings.talkInteractionMode.defaultVadSettings()
    }

    var baseUrl by remember { mutableStateOf(currentSettings.baseUrl) }
    var apiKeyInput by remember { mutableStateOf("") }
    var selectedProfileId by remember { mutableStateOf(currentSettings.selectedProfileId) }
    var selectedProfileName by remember { mutableStateOf(currentSettings.selectedProfileName) }
    var responseMode by remember { mutableStateOf(currentSettings.responseMode) }
    var talkInteractionMode by remember { mutableStateOf(currentSettings.talkInteractionMode) }
    var selectedTtsVoiceId by remember { mutableStateOf(currentSettings.selectedTtsVoiceId) }
    var vadEngine by remember { mutableStateOf(currentModePreset.vadEngine) }
    var vadSpeechThreshold by remember { mutableStateOf(currentModePreset.vadSpeechThreshold.toString()) }
    var vadSilenceMs by remember { mutableStateOf(currentModePreset.vadSilenceMs.toString()) }
    var bargeInEnabled by remember { mutableStateOf(currentSettings.bargeInEnabled) }
    var bargeInMinSpeechMs by remember { mutableStateOf(currentModePreset.bargeInMinSpeechMs.toString()) }

    val hasSavedKey = currentSettings.apiKey.isNotEmpty()
    val apiKeyPlaceholder = if (hasSavedKey) "•••••••• (Key saved)" else "Enter API Key"
    var showApiKey by remember { mutableStateOf(false) }

    // Error States
    var baseUrlError by remember { mutableStateOf<String?>(null) }
    var apiKeyError by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Dropdown States
    var responseModeExpanded by remember { mutableStateOf(false) }
    var talkModeExpanded by remember { mutableStateOf(false) }
    var ttsVoiceExpanded by remember { mutableStateOf(false) }
    var profileExpanded by remember { mutableStateOf(false) }
    var isLoadingProfiles by remember { mutableStateOf(false) }
    var isLoadingVoices by remember { mutableStateOf(false) }

    LaunchedEffect(profiles) {
        if (profiles.isNotEmpty()) {
            val current = profiles.firstOrNull { it.id == selectedProfileId }
            val preferred = current
                ?: profiles.firstOrNull { it.id == "main" || it.name.equals("Main", ignoreCase = true) }
                ?: profiles.firstOrNull { it.isDefault }
                ?: profiles.first()
            selectedProfileId = preferred.id
            selectedProfileName = preferred.name
        }
    }

    fun hasUnsavedChanges(): Boolean {
        return baseUrl.trim() != currentSettings.baseUrl ||
            apiKeyInput.trim().isNotEmpty() ||
            selectedProfileId != currentSettings.selectedProfileId ||
            selectedProfileName != currentSettings.selectedProfileName ||
            responseMode != currentSettings.responseMode ||
            talkInteractionMode != currentSettings.talkInteractionMode ||
            selectedTtsVoiceId != currentSettings.selectedTtsVoiceId ||
            bargeInEnabled != currentSettings.bargeInEnabled
    }

    fun applyTalkModePreset(mode: TalkInteractionMode) {
        val preset = mode.defaultVadSettings()
        talkInteractionMode = mode
        vadEngine = preset.vadEngine
        vadSpeechThreshold = preset.vadSpeechThreshold.toString()
        vadSilenceMs = preset.vadSilenceMs.toString()
        bargeInEnabled = preset.bargeInEnabled
        bargeInMinSpeechMs = preset.bargeInMinSpeechMs.toString()
    }

    fun navigateBackOrConfirmDiscard() {
        if (hasUnsavedChanges()) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    fun validateInputs(): Boolean {
        var isValid = true
        if (baseUrl.trim().isEmpty()) {
            baseUrlError = "Base URL is required"
            isValid = false
        } else if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrlError = "URL must start with http:// or https://"
            isValid = false
        } else {
            baseUrlError = null
        }

        if (apiKeyInput.trim().isEmpty() && !hasSavedKey) {
            apiKeyError = "API key is required"
            isValid = false
        } else {
            apiKeyError = null
        }

        return isValid
    }

    BackHandler(enabled = viewModel.hasValidSettings()) {
        navigateBackOrConfirmDiscard()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved settings changes. If you go back now, they will be discarded.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (viewModel.hasValidSettings()) {
                        IconButton(onClick = { navigateBackOrConfirmDiscard() }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            storageError?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Storage Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Text(
                text = "Connection Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            // Base URL Input
            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    baseUrlError = null
                },
                label = { Text("Hermes Voice Gateway URL") },
                placeholder = { Text("http://192.168.1.50:8789") },
                isError = baseUrlError != null,
                supportingText = baseUrlError?.let { { Text(it) } } ?: { Text("Use your computer LAN IP for local gateway testing.") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("base_url_input"),
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = "Link") },
                shape = RoundedCornerShape(12.dp)
            )

            // API Key Input
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = {
                    apiKeyInput = it
                    apiKeyError = null
                },
                label = { Text("API Key") },
                placeholder = { Text(apiKeyPlaceholder) },
                isError = apiKeyError != null,
                supportingText = apiKeyError?.let { { Text(it) } } 
                    ?: if (hasSavedKey) { { Text("Leave blank to keep existing secure key.") } } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input"),
                leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = "API Key") },
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle visibility"
                        )
                    }
                },
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedButton(
                onClick = {
                    if (baseUrl.trim().isNotEmpty()) {
                        isLoadingProfiles = true
                        val resolvedApiKey = if (apiKeyInput.trim().isEmpty()) currentSettings.apiKey else apiKeyInput.trim()
                        val tempSettings = HermesSettings(
                            baseUrl = baseUrl.trim(),
                            apiKey = resolvedApiKey,
                            selectedProfileId = selectedProfileId.trim(),
                            selectedProfileName = selectedProfileName.trim(),
                            responseMode = responseMode,
                            audioInputPreference = AudioInputPreference.AUTO,
                            talkInteractionMode = talkInteractionMode,
                            selectedTtsVoiceId = selectedTtsVoiceId,
                            vadEngine = vadEngine,
                            vadSpeechThreshold = vadSpeechThreshold.toFloatOrNull() ?: currentSettings.vadSpeechThreshold,
                            vadSilenceMs = vadSilenceMs.toIntOrNull() ?: currentSettings.vadSilenceMs,
                            bargeInEnabled = bargeInEnabled,
                            bargeInMinSpeechMs = bargeInMinSpeechMs.toIntOrNull() ?: currentSettings.bargeInMinSpeechMs
                        )
                        viewModel.loadProfiles(tempSettings) { success, message ->
                            isLoadingProfiles = false
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            if (success) {
                                isLoadingVoices = true
                                viewModel.loadTtsVoices(tempSettings) { _, voiceMessage ->
                                    isLoadingVoices = false
                                    Toast.makeText(context, voiceMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("load_profiles_button"),
                enabled = !isLoadingProfiles
            ) {
                if (isLoadingProfiles) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (profiles.isEmpty()) "Load Profiles" else "Refresh Profiles")
            }

            if (profiles.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedProfileName.ifBlank { selectedProfileId },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Hermes Profile") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Open Dropdown") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Box(
                        modifier = Modifier.matchParentSize().clickable { profileExpanded = true }
                    )
                    DropdownMenu(
                        expanded = profileExpanded,
                        onDismissRequest = { profileExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        profiles.forEach { profile ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                                        profile.description?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(
                                            listOfNotNull(profile.sttLabel, profile.ttsLabel).joinToString(" / "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedProfileId = profile.id
                                    selectedProfileName = profile.name
                                    profileExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            profileLoadError?.let {
                Text(
                    text = "Profiles will load after the gateway connection succeeds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            if (ttsVoices.isEmpty()) {
                OutlinedButton(
                    onClick = {
                        val resolvedApiKey = if (apiKeyInput.trim().isEmpty()) currentSettings.apiKey else apiKeyInput.trim()
                        val tempSettings = HermesSettings(
                            baseUrl = baseUrl.trim(),
                            apiKey = resolvedApiKey,
                            selectedProfileId = selectedProfileId.trim(),
                            selectedProfileName = selectedProfileName.trim(),
                            responseMode = responseMode,
                            audioInputPreference = AudioInputPreference.AUTO,
                            talkInteractionMode = talkInteractionMode,
                            selectedTtsVoiceId = selectedTtsVoiceId,
                            vadEngine = vadEngine,
                            vadSpeechThreshold = vadSpeechThreshold.toFloatOrNull() ?: currentSettings.vadSpeechThreshold,
                            vadSilenceMs = vadSilenceMs.toIntOrNull() ?: currentSettings.vadSilenceMs,
                            bargeInEnabled = bargeInEnabled,
                            bargeInMinSpeechMs = bargeInMinSpeechMs.toIntOrNull() ?: currentSettings.bargeInMinSpeechMs
                        )
                        isLoadingVoices = true
                        viewModel.loadTtsVoices(tempSettings) { _, message ->
                            isLoadingVoices = false
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("load_tts_voices_button"),
                    enabled = !isLoadingVoices
                ) {
                    if (isLoadingVoices) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Load Voices")
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val selectedVoice = ttsVoices.firstOrNull { it.id == selectedTtsVoiceId }
                    OutlinedTextField(
                        value = selectedVoice?.let { voice ->
                            listOfNotNull(voice.name, voice.locale).joinToString(" · ")
                        } ?: if (selectedTtsVoiceId.isBlank()) "Gateway default" else selectedTtsVoiceId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("TTS Voice") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Open Dropdown") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { ttsVoiceExpanded = true }
                            .testTag("tts_voice_dropdown")
                    )
                    DropdownMenu(
                        expanded = ttsVoiceExpanded,
                        onDismissRequest = { ttsVoiceExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Gateway default") },
                            onClick = {
                                selectedTtsVoiceId = ""
                                ttsVoiceExpanded = false
                            }
                        )
                        ttsVoices.forEach { voice ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(voice.name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            listOfNotNull(voice.locale, voice.gender, voice.provider).joinToString(" / "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedTtsVoiceId = voice.id
                                    ttsVoiceExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Response Mode Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = when (responseMode) {
                        ResponseMode.TEXT -> "Text only"
                        ResponseMode.TEXT_AUDIO -> "Text + audio"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Response Mode") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Open Dropdown") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { responseModeExpanded = true },
                    enabled = false, // disabled to let box capture tap
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                // Overlay to capture clicks
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { responseModeExpanded = true }
                )
                DropdownMenu(
                    expanded = responseModeExpanded,
                    onDismissRequest = { responseModeExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("Text only") },
                        onClick = {
                            responseMode = ResponseMode.TEXT
                            responseModeExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Text + audio") },
                        onClick = {
                            responseMode = ResponseMode.TEXT_AUDIO
                            responseModeExpanded = false
                        }
                    )
                }
            }

            // Talk Interaction Mode Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = when (talkInteractionMode) {
                        TalkInteractionMode.TAP_TO_TALK -> "Tap to talk"
                        TalkInteractionMode.PUSH_TO_TALK -> "Push to talk"
                        TalkInteractionMode.ALWAYS_LISTENING -> "Always listening"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Talk Interaction Mode") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Open Dropdown") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { talkModeExpanded = true }
                        .testTag("talk_interaction_mode_dropdown")
                )
                DropdownMenu(
                    expanded = talkModeExpanded,
                    onDismissRequest = { talkModeExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("Tap to talk") },
                        onClick = {
                            applyTalkModePreset(TalkInteractionMode.TAP_TO_TALK)
                            talkModeExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Push to talk") },
                        onClick = {
                            applyTalkModePreset(TalkInteractionMode.PUSH_TO_TALK)
                            talkModeExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text("Always listening")
                                Text(
                                    "Use the main button to turn continuous listening on or off.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            applyTalkModePreset(TalkInteractionMode.ALWAYS_LISTENING)
                            talkModeExpanded = false
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Voice barge-in", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Always Listening only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = bargeInEnabled,
                    onCheckedChange = { bargeInEnabled = it }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    if (validateInputs()) {
                        val resolvedApiKey = if (apiKeyInput.trim().isEmpty()) currentSettings.apiKey else apiKeyInput.trim()
                        val updatedSettings = HermesSettings(
                            baseUrl = baseUrl.trim(),
                            apiKey = resolvedApiKey,
                            selectedProfileId = selectedProfileId.trim(),
                            selectedProfileName = selectedProfileName.trim().ifBlank { selectedProfileId.trim() },
                            responseMode = responseMode,
                            audioInputPreference = AudioInputPreference.AUTO,
                            talkInteractionMode = talkInteractionMode,
                            selectedTtsVoiceId = selectedTtsVoiceId,
                            vadEngine = vadEngine,
                            vadSpeechThreshold = vadSpeechThreshold.toFloatOrNull() ?: currentSettings.vadSpeechThreshold,
                            vadSilenceMs = vadSilenceMs.toIntOrNull() ?: currentSettings.vadSilenceMs,
                            bargeInEnabled = bargeInEnabled,
                            bargeInMinSpeechMs = bargeInMinSpeechMs.toIntOrNull() ?: currentSettings.bargeInMinSpeechMs
                        )

                        val success = viewModel.saveSettings(updatedSettings)
                        if (success) {
                            Toast.makeText(context, "Settings saved securely!", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, "Error saving settings securely.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_settings_button"),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save Settings", fontSize = 14.sp)
            }
        }
    }
}
