package com.gymencasa.ui.theme

import android.graphics.Typeface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand palette: red / black / greys — energy and strength. Use only via MaterialTheme / GymColors.
object GymColors {
    val Red = Color(0xFFE53935)
    val RedDark = Color(0xFFB71C1C)
    val RedLight = Color(0xFFFFCDD2)
    val RedDeep = Color(0xFFC62828)
    val Black = Color(0xFF0D0D0D)
    val PureBlack = Color(0xFF000000)
    val Graphite = Color(0xFF1A1A1A)
    val Charcoal = Color(0xFF2C2C2C)
    val Steel = Color(0xFF9E9E9E)
    val SteelDark = Color(0xFF616161)
    val Smoke = Color(0xFFF2F2F2)
    val Paper = Color(0xFFF5F5F5)
    val Silver = Color(0xFFE0E0E0)
    val Ink = Color(0xFF121212)
    val ErrorDark = Color(0xFFFF5252)
    val ErrorLight = Color(0xFFB00020)
}

private val DarkColors = darkColorScheme(
    primary = GymColors.Red,
    onPrimary = Color.White,
    primaryContainer = GymColors.RedDark,
    onPrimaryContainer = Color.White,
    secondary = GymColors.Steel,
    onSecondary = GymColors.Black,
    secondaryContainer = GymColors.Charcoal,
    onSecondaryContainer = GymColors.Smoke,
    tertiary = GymColors.Steel,
    background = GymColors.Black,
    onBackground = GymColors.Smoke,
    surface = GymColors.Black,
    onSurface = GymColors.Smoke,
    surfaceVariant = GymColors.Charcoal,
    onSurfaceVariant = GymColors.Steel,
    surfaceContainerLowest = GymColors.PureBlack,
    surfaceContainerLow = GymColors.Graphite,
    surfaceContainer = GymColors.Graphite,
    surfaceContainerHigh = GymColors.Charcoal,
    surfaceContainerHighest = GymColors.Charcoal,
    outline = GymColors.SteelDark,
    outlineVariant = GymColors.Charcoal,
    error = GymColors.ErrorDark,
    onError = GymColors.Black,
)

private val LightColors = lightColorScheme(
    primary = GymColors.RedDeep,
    onPrimary = Color.White,
    primaryContainer = GymColors.RedLight,
    onPrimaryContainer = GymColors.RedDark,
    secondary = GymColors.SteelDark,
    onSecondary = Color.White,
    secondaryContainer = GymColors.Silver,
    onSecondaryContainer = GymColors.Ink,
    tertiary = GymColors.SteelDark,
    background = GymColors.Paper,
    onBackground = GymColors.Ink,
    surface = GymColors.Paper,
    onSurface = GymColors.Ink,
    surfaceVariant = GymColors.Silver,
    onSurfaceVariant = GymColors.SteelDark,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = GymColors.Silver,
    surfaceContainerHighest = GymColors.Silver,
    outline = GymColors.Steel,
    outlineVariant = GymColors.Silver,
    error = GymColors.ErrorLight,
    onError = Color.White,
)

/** Heavy condensed system font for headlines (no font download needed). */
val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

private val base = Typography()
private val GymTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Black),
    displayMedium = base.displayMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Black),
    displaySmall = base.displaySmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    titleLarge = base.titleLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
)

/** Giant tabular digits for the workout timer. */
val TimerStyle = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Black, fontSize = 96.sp, fontFeatureSettings = "tnum")

@Composable
fun GymTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = GymTypography) {
        // Root surface: sets the background and the default content colour for every screen.
        Surface(color = colors.background, contentColor = colors.onBackground, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
