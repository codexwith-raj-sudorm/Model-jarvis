package com.jarvis.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Arc-reactor palette (mirrors res/values/colors.xml)
private val ReactorCyan = Color(0xFF22D3EE)
private val ReactorGlow = Color(0xFF67E8F9)
private val DeepSpace = Color(0xFF0B1220)
private val Panel = Color(0xFF111C2E)
private val PanelVariant = Color(0xFF16233A)
private val IceText = Color(0xFFE2E8F0)
private val MutedText = Color(0xFF94A3B8)

private val JarvisColors = darkColorScheme(
    primary = ReactorCyan,
    onPrimary = DeepSpace,
    secondary = ReactorGlow,
    onSecondary = DeepSpace,
    background = DeepSpace,
    onBackground = IceText,
    surface = Panel,
    onSurface = IceText,
    surfaceVariant = PanelVariant,
    onSurfaceVariant = MutedText,
    outline = Color(0xFF2A3B55),
)

/**
 * Arc-reactor dark Material3 theme. JARVIS is a voice assistant for dim
 * rooms and pockets — always dark, ignoring the system setting.
 */
@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColors,
        typography = Typography(),
        content = content,
    )
}
