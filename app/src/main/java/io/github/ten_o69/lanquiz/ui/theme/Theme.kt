package io.github.ten_o69.lanquiz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = ArcadeBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0A1A3A),
    secondary = ArcadeOrange,
    onSecondary = Color(0xFF1F1100),
    secondaryContainer = Color(0xFFFFE0C2),
    onSecondaryContainer = Color(0xFF1F1100),
    tertiary = ArcadeBlueBright,
    onTertiary = Color(0xFF0B1B2D),
    tertiaryContainer = Color(0xFFD8F0FF),
    onTertiaryContainer = Color(0xFF0B1B2D),
    background = LightBackground,
    onBackground = LightOn,
    surface = LightSurface,
    surfaceVariant = LightSurfaceAlt,
    onSurface = LightOn,
    onSurfaceVariant = LightMuted,
    outline = LightMuted,
    error = Color(0xFFFF5B5B),
    onError = Color(0xFF2D0000)
)

private val DarkColors = darkColorScheme(
    primary = ArcadeBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF233B75),
    onPrimaryContainer = Color(0xFFDFE7FF),
    secondary = ArcadeOrange,
    onSecondary = Color(0xFF1F1100),
    secondaryContainer = Color(0xFF5B3108),
    onSecondaryContainer = Color(0xFFFFE0C2),
    tertiary = ArcadeBlueBright,
    onTertiary = Color(0xFF0B1B2D),
    tertiaryContainer = Color(0xFF214061),
    onTertiaryContainer = Color(0xFFD8F0FF),
    background = DarkBackground,
    onBackground = DarkOn,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceAlt,
    onSurface = DarkOn,
    onSurfaceVariant = DarkMuted,
    outline = DarkMuted,
    error = Color(0xFFFF5B5B),
    onError = Color(0xFF2D0000)
)

@Composable
fun LanQuizTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
