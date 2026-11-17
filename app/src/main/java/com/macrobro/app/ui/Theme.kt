package com.macrobro.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Lime = Color(0xFFC6FF3D)
val Orange = Color(0xFFFF7A1A)
val Sky = Color(0xFF4CC9F0)
val Pink = Color(0xFFFF5D8F)
val Bg = Color(0xFF0F1115)
val CardBg = Color(0xFF1A1D23)
val CardBg2 = Color(0xFF242833)
val TextMain = Color(0xFFECEFF4)
val TextDim = Color(0xFF9AA3AF)

private val scheme = darkColorScheme(
    primary = Lime,
    onPrimary = Color(0xFF111400),
    primaryContainer = Color(0xFF2E3A0C),
    onPrimaryContainer = Lime,
    secondary = Orange,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3A2410),
    onSecondaryContainer = Orange,
    background = Bg,
    onBackground = TextMain,
    surface = Bg,
    onSurface = TextMain,
    surfaceVariant = CardBg2,
    onSurfaceVariant = TextDim,
    surfaceContainer = CardBg,
    surfaceContainerLow = CardBg,
    surfaceContainerHigh = CardBg2,
    surfaceContainerHighest = CardBg2,
    outline = Color(0xFF3A3F4B),
    error = Color(0xFFFF5D5D),
)

@Composable
fun MacroBroTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
