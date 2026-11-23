package com.gymencasa.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand palette: red / black / greys. Light, soft and rounded by default; dark is optional.
// Use only through MaterialTheme.colorScheme or LocalGymExtras — never Color(0x…) outside ui/theme.
object GymColors {
    val Red = Color(0xFFE53935)
    val RedDeep = Color(0xFFB71C1C)
    val RedDark = Color(0xFFC62828)
    val RedSoft = Color(0xFFFDECEC)
    val Ink = Color(0xFF111214)
    val Black = Color(0xFF0D0D0D)
    val Graphite = Color(0xFF1A1A1A)
    val Charcoal = Color(0xFF26272B)
    val Slate = Color(0xFF34363C)
    val Muted = Color(0xFF6B6E76)
    val Steel = Color(0xFF9EA3AE)
    val Hairline = Color(0xFFE6E7EB)
    val ChipGrey = Color(0xFFF0F1F4)
    val Page = Color(0xFFF3F4F7)
    val PageTop = Color(0xFFEAECF2)
    val Smoke = Color(0xFFF2F2F2)
    val ErrorLight = Color(0xFFB00020)
    val ErrorDark = Color(0xFFFF6B6B)
}

/** Extra tokens Material 3 has no slot for: gradients, soft shadows, muscle dots. */
@Immutable
data class GymExtras(
    val heroGradient: Brush,
    val pageGradient: Brush,
    val shadow: Color,
    val chip: Color,
    val dotLegs: Color,
    val dotUpper: Color,
    val dotCore: Color,
    val dotCardio: Color,
)

private val LightExtras = GymExtras(
    heroGradient = Brush.linearGradient(listOf(GymColors.Red, GymColors.RedDeep)),
    pageGradient = Brush.verticalGradient(0f to GymColors.PageTop, 0.35f to GymColors.Page),
    shadow = Color(0x2914161E),
    chip = GymColors.ChipGrey,
    dotLegs = GymColors.Red,
    dotUpper = GymColors.Ink,
    dotCore = GymColors.Steel,
    dotCardio = GymColors.RedDeep,
)

private val DarkExtras = GymExtras(
    heroGradient = Brush.linearGradient(listOf(GymColors.Red, GymColors.RedDeep)),
    pageGradient = Brush.verticalGradient(0f to GymColors.Graphite, 0.35f to GymColors.Black),
    shadow = Color(0x66000000),
    chip = GymColors.Slate,
    dotLegs = GymColors.Red,
    dotUpper = GymColors.Smoke,
    dotCore = GymColors.Steel,
    dotCardio = GymColors.RedSoft,
)

val LocalGymExtras = staticCompositionLocalOf { LightExtras }

private val LightColors = lightColorScheme(
    primary = GymColors.Red,
    onPrimary = Color.White,
    primaryContainer = GymColors.RedSoft,
    onPrimaryContainer = GymColors.RedDeep,
    secondary = GymColors.Ink,
    onSecondary = Color.White,
    secondaryContainer = GymColors.ChipGrey,
    onSecondaryContainer = GymColors.Ink,
    tertiary = GymColors.Muted,
    background = GymColors.Page,
    onBackground = GymColors.Ink,
    surface = Color.White,
    onSurface = GymColors.Ink,
    surfaceVariant = GymColors.ChipGrey,
    onSurfaceVariant = GymColors.Muted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = GymColors.ChipGrey,
    surfaceContainerHighest = GymColors.Hairline,
    outline = GymColors.Steel,
    outlineVariant = GymColors.Hairline,
    error = GymColors.ErrorLight,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = GymColors.Red,
    onPrimary = Color.White,
    primaryContainer = GymColors.RedDeep,
    onPrimaryContainer = Color.White,
    secondary = GymColors.Smoke,
    onSecondary = GymColors.Black,
    secondaryContainer = GymColors.Slate,
    onSecondaryContainer = GymColors.Smoke,
    tertiary = GymColors.Steel,
    background = GymColors.Black,
    onBackground = GymColors.Smoke,
    surface = GymColors.Charcoal,
    onSurface = GymColors.Smoke,
    surfaceVariant = GymColors.Slate,
    onSurfaceVariant = GymColors.Steel,
    surfaceContainerLowest = GymColors.Black,
    surfaceContainerLow = GymColors.Charcoal,
    surfaceContainer = GymColors.Charcoal,
    surfaceContainerHigh = GymColors.Slate,
    surfaceContainerHighest = GymColors.Slate,
    outline = GymColors.Muted,
    outlineVariant = GymColors.Slate,
    error = GymColors.ErrorDark,
    onError = GymColors.Black,
)

/** Modern geometric look: the system sans in heavy weights with tight tracking for headlines. */
private val Sans = FontFamily.SansSerif
private val base = Typography()
private val GymTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
    displayMedium = base.displayMedium.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
    displaySmall = base.displaySmall.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineLarge = base.headlineLarge.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    headlineMedium = base.headlineMedium.copy(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Sans),
    bodyMedium = base.bodyMedium.copy(fontFamily = Sans),
    bodySmall = base.bodySmall.copy(fontFamily = Sans),
    labelLarge = base.labelLarge.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
)

/** Big tabular digits for the workout timer. */
val TimerStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 72.sp, letterSpacing = (-2).sp, fontFeatureSettings = "tnum")

/** Small uppercase section labels ("PARA TI"). */
val SectionStyle = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.6.sp)

@Composable
fun GymTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    androidx.compose.runtime.CompositionLocalProvider(LocalGymExtras provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(colorScheme = colors, typography = GymTypography) {
            // Root surface: sets the background and the default content colour for every screen.
            Surface(color = colors.background, contentColor = colors.onBackground, modifier = Modifier.fillMaxSize()) {
                content()
            }
        }
    }
}
