package io.github.bydavy.openliene.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563A8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3F5),
    onPrimaryContainer = Color(0xFF0B2B4A),
    secondary = Color(0xFF1F7A3D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F0DE),
    onSecondaryContainer = Color(0xFF073318),
    tertiary = Color(0xFF946200),
    onTertiary = Color.White,
    background = Color(0xFFF1F3F6),
    onBackground = Color(0xFF1A1C1F),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1F),
    surfaceVariant = Color(0xFFE6E9ED),
    onSurfaceVariant = Color(0xFF5B626B),
    outline = Color(0xFF7A828C),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEB),
    onErrorContainer = Color(0xFF8F1D18),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC7FF),
    onPrimary = Color(0xFF0B2B4A),
    primaryContainer = Color(0xFF2F5E8A),
    onPrimaryContainer = Color(0xFFD6E3F5),
    secondary = Color(0xFF7FD08E),
    onSecondary = Color(0xFF073318),
    secondaryContainer = Color(0xFF1F4D2B),
    onSecondaryContainer = Color(0xFFD8F0DE),
    tertiary = Color(0xFFFFC266),
    onTertiary = Color(0xFF3D2A00),
    background = Color(0xFF141619),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF1C1E22),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF2E3238),
    onSurfaceVariant = Color(0xFFA7ADB5),
    outline = Color(0xFF8A929B),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF3A1D20),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun LieneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
