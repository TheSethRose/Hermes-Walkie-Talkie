package com.hermes.voiceremote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = HermesYellow,
    onPrimary = Color(0xFF111111),
    secondary = HermesYellow,
    tertiary = HermesYellowMuted,
    background = HermesBg,
    onBackground = HermesTextPrimary,
    surface = HermesSurface,
    onSurface = HermesTextPrimary,
    primaryContainer = HermesSurfaceVariant,
    onPrimaryContainer = HermesYellow,
    surfaceVariant = HermesSurfaceVariant,
    onSurfaceVariant = HermesTextSecondary,
    outline = HermesBorder,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
  )

@Composable
fun HermesVoiceTheme(
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
