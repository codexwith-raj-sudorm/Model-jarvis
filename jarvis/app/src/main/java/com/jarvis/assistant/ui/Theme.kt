package com.jarvis.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// STARK HUD — MCU film-accurate palette
// Iron Man helmet + lab hologram reference:
//   Primary cyan is electric #00D9FF (not teal #22D3EE)
//   Deep space is near-black navy #050A14
//   Coil fill #073C4B + glow #52FEFE
// ---------------------------------------------------------------------------
internal val StarkCyan = Color(0xFF00D9FF)
internal val StarkCyanGlow = Color(0xFF52FEFE)
internal val StarkCyanSoft = Color(0xFF67E8F9)
internal val StarkCyanDeep = Color(0xFF073C4B)
internal val StarkDeepSpace = Color(0xFF050A14)
internal val StarkPanel = Color(0xFF0F1E33)
internal val StarkPanelVariant = Color(0xFF16233A)
internal val StarkIce = Color(0xFFD6E4F0)
internal val StarkDim = Color(0xFF7A8CA3)
internal val StarkAlert = Color(0xFFFF3B30)

// Legacy aliases kept for compat — point at Stark
private val ReactorCyan = StarkCyan
private val ReactorGlow = StarkCyanSoft
private val DeepSpace = StarkDeepSpace
private val Panel = StarkPanel
private val PanelVariant = StarkPanelVariant
private val IceText = StarkIce
private val MutedText = StarkDim

private val JarvisColors = darkColorScheme(
    primary = ReactorCyan,
    onPrimary = DeepSpace,
    secondary = StarkCyanGlow,
    onSecondary = DeepSpace,
    background = DeepSpace,
    onBackground = IceText,
    surface = Panel,
    onSurface = IceText,
    surfaceVariant = PanelVariant,
    onSurfaceVariant = MutedText,
    outline = StarkDim.copy(alpha = 0.22f), // Stark lab bench outline — was #1E3A5F
    outlineVariant = StarkCyan.copy(alpha = 0.14f),
    error = StarkAlert,
)

/**
 * HUD typography — Mono for telemetry (condensed, tracked, CAPS),
 * Sans for voice transcript.
 */
internal object HudType {
    val monoLabel = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.1.sp,
    )
    val monoSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        letterSpacing = 0.9.sp,
    )
    val voiceLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    )
    val voiceChip = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        letterSpacing = 0.7.sp,
    )
}

/**
 * Arc-reactor dark Material3 theme. JARVIS is a voice assistant for dim
 * rooms and pockets — always dark, ignoring the system setting.
 * Now Stark-calibrated: electric cyan, deep navy, hexagon-ready.
 */
@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColors,
        typography = Typography(),
        content = content,
    )
}
