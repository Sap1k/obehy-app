package cz.obehy.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The web's colour tokens (obehy web/src/theme/tokens.css): petrol in light, lime in dark.
private val Light = lightColorScheme(
    primary = Color(0xFF246568),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDEEEEA),
    onPrimaryContainer = Color(0xFF1C5356),
    background = Color(0xFFF4F7EF),
    onBackground = Color(0xFF153438),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF153438),
    surfaceVariant = Color(0xFFF7F9F6),
    onSurfaceVariant = Color(0xFF526760),
    outline = Color(0xFF6D8079),
    outlineVariant = Color(0xFFD4DFD7),
    error = Color(0xFFAF3038),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFC5ED62),
    onPrimary = Color(0xFF102B2D),
    primaryContainer = Color(0xFF284D4C),
    onPrimaryContainer = Color(0xFFF4F7EF),
    background = Color(0xFF102B2D),
    onBackground = Color(0xFFF4F7EF),
    surface = Color(0xFF18383A),
    onSurface = Color(0xFFF4F7EF),
    surfaceVariant = Color(0xFF214447),
    onSurfaceVariant = Color(0xFFB6C8BD),
    outline = Color(0xFFB6C8BD),
    outlineVariant = Color(0xFF365352),
)

@Composable
fun ObehyTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
