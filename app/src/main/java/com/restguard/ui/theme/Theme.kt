package com.restguard.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Stress-level colors ────────────────────────────────────
val StressLow = Color(0xFF4CAF50)       // green
val StressModerate = Color(0xFFFFC107)  // amber
val StressHigh = Color(0xFFFF9800)      // orange
val StressExtreme = Color(0xFFF44336)   // red

val StressLowBg = Color(0xFF1B3A2A)
val StressModerateBg = Color(0xFF3A3520)
val StressHighBg = Color(0xFF3A2A1B)
val StressExtremeBg = Color(0xFF3A1B1B)

// ─── Brand colors ───────────────────────────────────────────
val Teal = Color(0xFF3CCBAA)
val TealDark = Color(0xFF2BA88C)
val DarkBg = Color(0xFF0F1419)
val DarkSurface = Color(0xFF1A1F2B)
val DarkCard = Color(0xFF222834)
val DarkCardBorder = Color(0xFF2E3542)
val TextPrimary = Color(0xFFE8ECF0)
val TextSecondary = Color(0xFF8B95A5)

// ─── App color scheme ───────────────────────────────────────
private val DarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color.Black,
    primaryContainer = TealDark,
    onPrimaryContainer = Color.White,
    secondary = Teal,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1E3A35),
    onSecondaryContainer = Teal,
    background = DarkBg,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DarkCardBorder,
    error = Color(0xFFEF5350),
    onError = Color.White,
    errorContainer = Color(0xFF3A1B1B),
    onErrorContainer = Color(0xFFEF9A9A),
)

@Composable
fun RestGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content,
    )
}
