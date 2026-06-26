package com.hermes.voiceremote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.voiceremote.settings.TalkInteractionMode
import com.hermes.voiceremote.state.VoiceSessionStatus

@Composable
fun TalkButton(
    status: VoiceSessionStatus,
    talkInteractionMode: TalkInteractionMode,
    onTap: () -> Unit,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    // Scale animation for LISTENING and SPEAKING states
    val scaleValue by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val scaleModifier = if (status == VoiceSessionStatus.LISTENING || status == VoiceSessionStatus.SPEAKING) {
        Modifier.scale(scaleValue)
    } else {
        Modifier
    }

    val backgroundColor by animateColorAsState(
        targetValue = when (status) {
            VoiceSessionStatus.IDLE -> MaterialTheme.colorScheme.primaryContainer
            VoiceSessionStatus.LISTENING -> MaterialTheme.colorScheme.error
            VoiceSessionStatus.UPLOADING -> MaterialTheme.colorScheme.surfaceVariant
            VoiceSessionStatus.THINKING -> MaterialTheme.colorScheme.surfaceVariant
            VoiceSessionStatus.SPEAKING -> MaterialTheme.colorScheme.primary
            VoiceSessionStatus.ERROR -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(300),
        label = "button_color"
    )

    val contentColor by animateColorAsState(
        targetValue = when (status) {
            VoiceSessionStatus.IDLE -> MaterialTheme.colorScheme.onPrimaryContainer
            VoiceSessionStatus.LISTENING -> MaterialTheme.colorScheme.onError
            VoiceSessionStatus.UPLOADING -> MaterialTheme.colorScheme.onSurfaceVariant
            VoiceSessionStatus.THINKING -> MaterialTheme.colorScheme.onSurfaceVariant
            VoiceSessionStatus.SPEAKING -> MaterialTheme.colorScheme.onPrimary
            VoiceSessionStatus.ERROR -> MaterialTheme.colorScheme.onError
        },
        animationSpec = tween(300),
        label = "content_color"
    )

    val label = when (status) {
        VoiceSessionStatus.IDLE -> "Talk"
        VoiceSessionStatus.LISTENING -> "Listening…"
        VoiceSessionStatus.UPLOADING -> "Sending…"
        VoiceSessionStatus.THINKING -> "Thinking…"
        VoiceSessionStatus.SPEAKING -> "Speaking…"
        VoiceSessionStatus.ERROR -> "Reset"
    }

    val description = when (status) {
        VoiceSessionStatus.IDLE -> when (talkInteractionMode) {
            TalkInteractionMode.PUSH_TO_TALK -> "Hold to speak to Hermes"
            else -> "Tap to speak to Hermes"
        }
        VoiceSessionStatus.LISTENING -> when (talkInteractionMode) {
            TalkInteractionMode.PUSH_TO_TALK -> "Release to send"
            else -> "Tap again to send"
        }
        VoiceSessionStatus.UPLOADING -> "Uploading voice"
        VoiceSessionStatus.THINKING -> "Hermes is processing"
        VoiceSessionStatus.SPEAKING -> "Hermes is responding"
        VoiceSessionStatus.ERROR -> "Tap to clear error"
    }

    Box(
        modifier = modifier
            .then(scaleModifier)
            .size(180.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(4.dp, contentColor.copy(alpha = 0.4f), CircleShape)
            .pointerInput(talkInteractionMode, status) {
                detectTapGestures(
                    onTap = {
                        if (talkInteractionMode == TalkInteractionMode.TAP_TO_TALK ||
                            status != VoiceSessionStatus.IDLE && status != VoiceSessionStatus.LISTENING
                        ) {
                            onTap()
                        }
                    },
                    onPress = {
                        if (talkInteractionMode == TalkInteractionMode.PUSH_TO_TALK && status == VoiceSessionStatus.IDLE) {
                            onPressStart()
                            tryAwaitRelease()
                            onPressEnd()
                        }
                    }
                )
            }
            .testTag("talk_button"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(16.dp)
        ) {
            // Loader circle
            if (status == VoiceSessionStatus.UPLOADING || status == VoiceSessionStatus.THINKING) {
                CircularProgressIndicator(
                    color = contentColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(36.dp)
                        .padding(bottom = 8.dp)
                )
            }

            Text(
                text = label,
                color = contentColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                color = contentColor.copy(alpha = 0.7f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}
