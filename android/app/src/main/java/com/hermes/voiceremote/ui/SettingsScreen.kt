package com.hermes.voiceremote.ui

import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
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
    val profileLoadError by viewModel.profileLoadError.collectAsState()

    var baseUrl by remember { mutableStateOf(currentSettings.baseUrl) }
    var apiKeyInput by remember { mutableStateOf("") }
    var selectedProfileId by remember { mutableStateOf(currentSettings.selectedProfileId) }
    var selectedProfileName by remember { mutableStateOf(currentSettings.selectedProfileName) }
    var responseMode by remember { mutableStateOf(currentSettings.responseMode) }
    var audioInputPref by remember { mutableStateOf(currentSettings.audioInputPreference) }

    val hasSavedKey = currentSettings.apiKey.isNotEmpty()
    var apiKeyPlaceholder = if (hasSavedKey) "•••••••••••••••• (Saved)" else "Enter API Key"
    var showApiKey by remember { mutableStateOf(false) }

    // Error States
    var baseUrlError by remember { mutableStateOf<String?>(null) }
    var apiKeyError by remember { mutableStateOf<String?>(null) }

    // Dropdown States
    var responseModeExpanded by remember { mutableStateOf(false) }
    var audioPrefExpanded by remember { mutableStateOf(false) }
    var profileExpanded by remember { mutableStateOf(false) }
    var isLoadingProfiles by remember { mutableStateOf(false) }

    // Test Connection status
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testResultSuccess by remember { mutableStateOf<Boolean?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (viewModel.hasValidSettings()) {
                        IconButton(onClick = onBack) {
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
                            audioInputPreference = audioInputPref
                        )
                        viewModel.loadProfiles(tempSettings) { success, message ->
                            isLoadingProfiles = false
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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

            // Audio Route Preferences Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = when (audioInputPref) {
                        AudioInputPreference.AUTO -> "Auto"
                        AudioInputPreference.PHONE_MIC -> "Phone mic"
                        AudioInputPreference.BLUETOOTH_HEADSET -> "Bluetooth headset"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Preferred Audio Route") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Open Dropdown") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    enabled = false,
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
                        .clickable { audioPrefExpanded = true }
                )
                DropdownMenu(
                    expanded = audioPrefExpanded,
                    onDismissRequest = { audioPrefExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("Auto (Recommended)") },
                        onClick = {
                            audioInputPref = AudioInputPreference.AUTO
                            audioPrefExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Phone mic") },
                        onClick = {
                            audioInputPref = AudioInputPreference.PHONE_MIC
                            audioPrefExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Bluetooth headset") },
                        onClick = {
                            audioInputPref = AudioInputPreference.BLUETOOTH_HEADSET
                            audioPrefExpanded = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Health Status Box if any
            testResultText?.let { text ->
                val cardColor = if (testResultSuccess == true) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                val textColor = Color.White
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = text,
                        color = textColor,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Actions Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Test Connection Button
                OutlinedButton(
                    onClick = {
                        if (validateInputs()) {
                            isTestingConnection = true
                            testResultText = "Testing connection to health endpoint..."
                            testResultSuccess = null
                            
                            val resolvedApiKey = if (apiKeyInput.trim().isEmpty()) currentSettings.apiKey else apiKeyInput.trim()
                            val tempSettings = HermesSettings(
                                baseUrl = baseUrl.trim(),
                                apiKey = resolvedApiKey,
                                selectedProfileId = selectedProfileId.trim(),
                                selectedProfileName = selectedProfileName.trim(),
                                responseMode = responseMode,
                                audioInputPreference = audioInputPref
                            )
                            
                            viewModel.onTestConnection(tempSettings) { success, message ->
                                isTestingConnection = false
                                testResultSuccess = success
                                testResultText = message
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("test_connection_button"),
                    shape = RoundedCornerShape(24.dp),
                    enabled = !isTestingConnection
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Test Connection", fontSize = 14.sp)
                    }
                }

                // Save Button
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
                                audioInputPreference = audioInputPref
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
                        .weight(1f)
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
}
