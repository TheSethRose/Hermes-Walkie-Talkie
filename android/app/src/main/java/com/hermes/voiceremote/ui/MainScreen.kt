package com.hermes.voiceremote.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hermes.voiceremote.settings.AudioInputPreference
import com.hermes.voiceremote.state.VoiceSessionStatus
import com.hermes.voiceremote.state.VoiceViewModel
import com.hermes.voiceremote.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: VoiceViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var fullTextTitle by remember { mutableStateOf<String?>(null) }
    var fullTextBody by remember { mutableStateOf("") }

    // Launcher for runtime permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] 
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        if (micGranted) {
            if (settings.talkInteractionMode == com.hermes.voiceremote.settings.TalkInteractionMode.ALWAYS_LISTENING) {
                viewModel.onTalkButtonPressed()
            } else {
                viewModel.onTalkPressStart()
            }
        } else {
            Toast.makeText(context, "Microphone permission is required to record audio.", Toast.LENGTH_LONG).show()
        }
    }

    fun startRecordingWithPermissions() {
        val requiredPermissions = mutableListOf<String>()
        
        // Microphone is strictly mandatory to start recording
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        // Bluetooth (S+): only request if configuration uses Bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (settings.audioInputPreference == AudioInputPreference.AUTO ||
                settings.audioInputPreference == AudioInputPreference.BLUETOOTH_HEADSET) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
        }
        
        // Post notifications (Tiramisu+): soft request
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (requiredPermissions.isEmpty()) {
            if (settings.talkInteractionMode == com.hermes.voiceremote.settings.TalkInteractionMode.ALWAYS_LISTENING) {
                viewModel.onTalkButtonPressed()
            } else {
                viewModel.onTalkPressStart()
            }
        } else {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    fun handleTalkTap() {
        if (uiState.status == VoiceSessionStatus.IDLE) {
            startRecordingWithPermissions()
        } else {
            viewModel.onTalkButtonPressed()
        }
    }

    fun handlePushTalkStart() {
        if (viewModel.uiState.value.status == VoiceSessionStatus.IDLE) {
            startRecordingWithPermissions()
        }
    }

    fun handlePushTalkEnd() {
        viewModel.onTalkPressEnd()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hermes Voice", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    StatusPill(
                        connectionStatus = uiState.connectionStatus,
                        status = uiState.status,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AGENT PROFILE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = uiState.agentProfile,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                AudioRouteLabel(route = uiState.audioRoute, onClick = onNavigateToSettings)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                TalkButton(
                    status = uiState.status,
                    talkInteractionMode = settings.talkInteractionMode,
                    isAlwaysListeningActive = uiState.isAlwaysListeningActive,
                    onTap = { handleTalkTap() },
                    onPressStart = { handlePushTalkStart() },
                    onPressEnd = { handlePushTalkEnd() }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                uiState.errorMessage?.let { msg ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("error_card"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.ErrorOutline, "Error", tint = MaterialTheme.colorScheme.onError)
                                    Text("Error", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(msg, color = MaterialTheme.colorScheme.onError.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }
                item {
                    TranscriptCard(
                        transcript = uiState.transcript,
                        onOpenFullText = {
                            fullTextTitle = "You said"
                            fullTextBody = uiState.transcript
                        }
                    )
                }
                item {
                    ResponseCard(
                        responseText = uiState.responseText,
                        audioUrl = uiState.lastAudioUrl,
                        onPlay = { viewModel.onReplayLastResponse() },
                        onOpenFullText = {
                            fullTextTitle = "Hermes responded"
                            fullTextBody = uiState.responseText
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.testTag("settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Settings", fontWeight = FontWeight.SemiBold)
                }

                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )

                TextButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.testTag("agent_button")
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Agent", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Agent", fontWeight = FontWeight.SemiBold)
                }

                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )

                TextButton(
                    onClick = { viewModel.onNewSession() },
                    modifier = Modifier.testTag("new_session_button")
                ) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = "New Session", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    fullTextTitle?.let { title ->
        FullTextDialog(
            title = title,
            text = fullTextBody,
            onDismiss = { fullTextTitle = null }
        )
    }
}
