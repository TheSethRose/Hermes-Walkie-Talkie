package com.hermes.voiceremote.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.Wearable
import com.hermes.voiceremote.wear.ui.theme.HermesWearTheme

class MainActivity : ComponentActivity() {
    private var statusText by mutableStateOf("Ready")
    private var isPressed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HermesWearTheme {
                PttScreen(
                    statusText = statusText,
                    isPressed = isPressed,
                    onPress = {
                        isPressed = true
                        sendPtt(PATH_PTT_START)
                    },
                    onRelease = {
                        isPressed = false
                        sendPtt(PATH_PTT_STOP)
                    }
                )
            }
        }
    }

    private fun sendPtt(path: String) {
        Wearable.getNodeClient(this).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    statusText = "Phone offline"
                    return@addOnSuccessListener
                }

                nodes.forEach { node ->
                    Wearable.getMessageClient(this)
                        .sendMessage(node.id, path, ByteArray(0))
                        .addOnSuccessListener {
                            statusText = if (path == PATH_PTT_START) "Talking" else "Sent"
                        }
                        .addOnFailureListener {
                            statusText = "Send failed"
                        }
                }
            }
            .addOnFailureListener {
                statusText = "Phone offline"
            }
    }

    private companion object {
        const val PATH_PTT_START = "/hermes/ptt/start"
        const val PATH_PTT_STOP = "/hermes/ptt/stop"
    }
}

@Composable
private fun PttScreen(
    statusText: String,
    isPressed: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(
                    if (isPressed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer
                )
                .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .semantics {
                    role = Role.Button
                    contentDescription = "Hold to talk"
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onPress()
                        waitForUpOrCancellation()
                        onRelease()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isPressed) "TALKING" else "HOLD",
                color = if (isPressed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = statusText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}
