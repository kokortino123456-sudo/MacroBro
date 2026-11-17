package com.macrobro.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.macrobro.app.R

// ---------- Color tokens ----------
// Dark-mode depth: the higher a surface sits, the lighter it is.
val Bg = Color(0xFF0F1115)        // page plane
val Surface1 = Color(0xFF1A1D23)  // cards, list rows
val Surface2 = Color(0xFF232731)  // raised: dialogs, menus
val Surface3 = Color(0xFF2C313C)  // pressed / selected
val Hairline = Color(0xFF2A2F39)  // gridlines, dividers

val TextMain = Color(0xFFECEFF4)
val TextDim = Color(0xFFB1B9C4)   // secondary text, APCA Lc ~62 on cards
val TextMuted = Color(0xFF868E9B) // axis labels, tertiary

val Ink = Color(0xFF111400)       // text on lime
val Lime = Color(0xFFC6FF3D)      // brand accent + calories

// Macro series: validated categorical slots 1-3 (dark steps), CVD-safe on Surface1.
val Protein = Color(0xFF3987E5)
val Carbs = Color(0xFFD95926)
val Fat = Color(0xFF199E70)

val WeightLine = Color(0xFF9085E9)
val Water = Color(0xFF86B6EF)

// Status (reserved meaning, always with an icon or label)
val Good = Color(0xFF0CA30C)
val Serious = Color(0xFFEC835A)
val ErrorRed = Color(0xFFFF6B6B)

// Alpha borders work on any surface: brighter on top = light from above.
val BorderTop = Color.White.copy(alpha = 0.10f)
val BorderBottom = Color.White.copy(alpha = 0.03f)
val BorderFlat = Color.White.copy(alpha = 0.08f)

// ---------- Type ----------
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

private fun TextStyle.barlow() = copy(fontFamily = Barlow)

private val base = Typography()
private val AppTypography = Typography(
    displayLarge = base.displayLarge.barlow(),
    displayMedium = base.displayMedium.barlow(),
    displaySmall = base.displaySmall.barlow(),
    headlineLarge = base.headlineLarge.barlow(),
    headlineMedium = base.headlineMedium.barlow(),
    headlineSmall = base.headlineSmall.barlow(),
    titleLarge = base.titleLarge.barlow().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.barlow().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.barlow(),
    bodyLarge = base.bodyLarge.barlow(),
    bodyMedium = base.bodyMedium.barlow(),
    bodySmall = base.bodySmall.barlow(),
    labelLarge = base.labelLarge.barlow().copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.barlow(),
    labelSmall = base.labelSmall.barlow(),
)

// ---------- Shape: concentric radii ----------
// A card is 28dp with 16dp padding, so anything inside it uses 28 - 16 = 12dp.
object Radius {
    val card = 28.dp
    val page = 16.dp   // standalone rows and buttons on the page
    val inner = 12.dp  // controls inside a card or dialog
    val tiny = 8.dp
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

private val scheme = darkColorScheme(
    primary = Lime,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2E3A0C),
    onPrimaryContainer = Lime,
    secondary = Carbs,
    onSecondary = Color.White,
    background = Bg,
    onBackground = TextMain,
    surface = Bg,
    onSurface = TextMain,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim,
    surfaceContainerLowest = Bg,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    surfaceBright = Surface3,
    outline = Color(0xFF3A404C),
    outlineVariant = Hairline,
    error = ErrorRed,
    onError = Color.Black,
)

@Composable
fun MacroBroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(Radius.tiny),
            small = RoundedCornerShape(Radius.inner),
            medium = RoundedCornerShape(Radius.page),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(Radius.card),
        ),
        content = content,
    )
}

