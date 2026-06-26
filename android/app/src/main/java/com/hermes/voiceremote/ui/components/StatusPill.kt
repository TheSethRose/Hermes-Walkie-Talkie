package com.hermes.voiceremote.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.voiceremote.state.GatewayConnectionStatus
import com.hermes.voiceremote.state.VoiceSessionStatus
import com.hermes.voiceremote.ui.theme.HermesSuccess

@Composable
fun StatusPill(
    connectionStatus: GatewayConnectionStatus,
    status: VoiceSessionStatus,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("status_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val isSessionActive = status == VoiceSessionStatus.LISTENING || 
                             status == VoiceSessionStatus.UPLOADING || 
                             status == VoiceSessionStatus.THINKING || 
                             status == VoiceSessionStatus.SPEAKING

        val (dotColor, textColor, textValue) = when {
            status == VoiceSessionStatus.ERROR -> Triple(
                MaterialTheme.colorScheme.error.copy(alpha = dotAlpha),
                MaterialTheme.colorScheme.error,
                "ERROR"
            )
            connectionStatus == GatewayConnectionStatus.CONNECTING -> Triple(
                MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha),
                MaterialTheme.colorScheme.primary,
                "CONNECTING"
            )
            connectionStatus == GatewayConnectionStatus.ONLINE -> Triple(
                if (isSessionActive) MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha) else HermesSuccess.copy(alpha = dotAlpha),
                if (isSessionActive) MaterialTheme.colorScheme.primary else HermesSuccess,
                "ONLINE"
            )
            connectionStatus == GatewayConnectionStatus.ERROR -> Triple(
                MaterialTheme.colorScheme.error.copy(alpha = dotAlpha),
                MaterialTheme.colorScheme.error,
                "ERROR"
            )
            else -> Triple(
                MaterialTheme.colorScheme.error.copy(alpha = dotAlpha),
                MaterialTheme.colorScheme.error,
                "OFFLINE"
            )
        }

        // Connected Dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        Text(
            text = textValue,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )

        val statusColor = when (status) {
            VoiceSessionStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
            VoiceSessionStatus.LISTENING -> MaterialTheme.colorScheme.error
            VoiceSessionStatus.UPLOADING -> MaterialTheme.colorScheme.onSurface
            VoiceSessionStatus.THINKING -> MaterialTheme.colorScheme.onSurface
            VoiceSessionStatus.SPEAKING -> MaterialTheme.colorScheme.primary
            VoiceSessionStatus.ERROR -> MaterialTheme.colorScheme.error
        }

        val statusText = when (status) {
            VoiceSessionStatus.IDLE -> "IDLE"
            VoiceSessionStatus.LISTENING -> "LISTENING"
            VoiceSessionStatus.UPLOADING -> "SENDING"
            VoiceSessionStatus.THINKING -> "THINKING"
            VoiceSessionStatus.SPEAKING -> "SPEAKING"
            VoiceSessionStatus.ERROR -> "ERROR"
        }

        if (connectionStatus == GatewayConnectionStatus.ONLINE && status != VoiceSessionStatus.ERROR) {
            VerticalDivider(
                modifier = Modifier
                    .height(12.dp)
                    .width(1.dp),
                color = MaterialTheme.colorScheme.outline
            )

            Text(
                text = statusText,
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// Extension helper for M3 surface tone mapping
@Composable
private fun ColorScheme.surfaceColorAtElevation(elevation: androidx.compose.ui.unit.Dp): Color {
    return surface
}
