package com.restguard.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Stress-level colors ────────────────────────────────────
val StressLow = Color(0xFF4CAF50)       // green
val StressModerate = Color(0xFFFFC107)  // amber
val StressHigh = Color(0xFFFF9800)      // orange
val StressExtreme = Color(0xFFF44336)   // red

val StressLowBg = Color(0xFFE8F5E9)
val StressModerateBg = Color(0xFFFFF8E1)
val StressHighBg = Color(0xFFFFF3E0)
val StressExtremeBg = Color(0xFFFFEBEE)

// ─── App color scheme ───────────────────────────────────────
private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    error = Color(0xFFD32F2F),
)

@Composable
fun RestGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content,
    )
}
