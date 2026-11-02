package cz.obehy.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand foundations (Figma "Oběhy – marketing", 03 Electric palette): Petrol gives structure,
// Lime marks the main action or a selected state, always with dark lettering. Paper and Night
// are the surfaces; Ink is text on light.
private val Petrol = Color(0xFF246568)
private val Lime = Color(0xFFC5ED62)
private val Night = Color(0xFF102B2D)
private val Paper = Color(0xFFF4F7EF)
private val Ink = Color(0xFF153438)

private val Light = lightColorScheme(
    primary = Petrol,
    onPrimary = Color.White,
    primaryContainer = Lime,
    onPrimaryContainer = Night,
    inversePrimary = Lime,
    secondary = Color(0xFF526760),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDEEEEA),
    onSecondaryContainer = Color(0xFF1C5356),
    tertiary = Color(0xFF1C5356),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDEEEEA),
    onTertiaryContainer = Color(0xFF1C5356),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE0E8DC),
    onSurfaceVariant = Color(0xFF526760),
    surfaceTint = Petrol,
    inverseSurface = Night,
    inverseOnSurface = Paper,
    error = Color(0xFFAF3038),
    onError = Color.White,
    errorContainer = Color(0xFFFADDDB),
    onErrorContainer = Color(0xFF6E1A20),
    outline = Color(0xFF6D8079),
    outlineVariant = Color(0xFFD4DFD7),
    surfaceBright = Paper,
    surfaceDim = Color(0xFFD7DFD2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBF7),
    surfaceContainer = Color(0xFFEEF3E9),
    surfaceContainerHigh = Color(0xFFE7EEE3),
    surfaceContainerHighest = Color(0xFFE0E8DC),
)

private val Dark = darkColorScheme(
    primary = Lime,
    onPrimary = Night,
    primaryContainer = Lime,
    onPrimaryContainer = Night,
    inversePrimary = Petrol,
    secondary = Color(0xFFB6C8BD),
    onSecondary = Night,
    secondaryContainer = Color(0xFF284D4C),
    onSecondaryContainer = Paper,
    tertiary = Color(0xFFDEEEEA),
    onTertiary = Night,
    tertiaryContainer = Color(0xFF284D4C),
    onTertiaryContainer = Paper,
    background = Night,
    onBackground = Paper,
    surface = Night,
    onSurface = Paper,
    surfaceVariant = Color(0xFF214447),
    onSurfaceVariant = Color(0xFFB6C8BD),
    surfaceTint = Lime,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    error = Color(0xFFFFB3B0),
    onError = Color(0xFF680E16),
    errorContainer = Color(0xFF8C1D25),
    onErrorContainer = Color(0xFFFFDAD7),
    outline = Color(0xFF7F958B),
    outlineVariant = Color(0xFF365352),
    surfaceBright = Color(0xFF2C5052),
    surfaceDim = Night,
    surfaceContainerLowest = Color(0xFF0C2426),
    surfaceContainerLow = Color(0xFF143235),
    surfaceContainer = Color(0xFF18383A),
    surfaceContainerHigh = Color(0xFF1E4144),
    surfaceContainerHighest = Color(0xFF254A4C),
)

/** Material 3 Expressive with the Oběhy palette, Manrope and the expressive (springy) motion. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ObehyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (dark) Dark else Light,
        motionScheme = MotionScheme.expressive(),
        typography = ObehyTypography,
        content = content,
    )
}
