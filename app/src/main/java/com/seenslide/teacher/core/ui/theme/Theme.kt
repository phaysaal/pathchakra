package com.seenslide.teacher.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warm, friendly colors — green for Bangladesh, approachable for teachers
private val Green700 = Color(0xFF388E3C)
private val Green800 = Color(0xFF2E7D32)
private val Green50 = Color(0xFFE8F5E9)
private val Green300 = Color(0xFF81C784)
private val Green200 = Color(0xFFA5D6A7)
private val Orange600 = Color(0xFFFB8C00)
private val Orange300 = Color(0xFFFFB74D)
private val Red600 = Color(0xFFE53935)
private val Red300 = Color(0xFFEF9A9A)
private val White = Color(0xFFFFFFFF)
private val Gray50 = Color(0xFFFAFAFA)
private val Gray900 = Color(0xFF212121)
private val DarkSurface = Color(0xFF1C1B1F)
private val DarkBackground = Color(0xFF121212)
private val DarkSurfaceContainer = Color(0xFF2B2930)

private val LightColors = lightColorScheme(
    primary = Green700,
    onPrimary = White,
    primaryContainer = Green50,
    onPrimaryContainer = Green800,
    secondary = Orange600,
    onSecondary = White,
    error = Red600,
    onError = White,
    background = Gray50,
    onBackground = Gray900,
    surface = White,
    onSurface = Gray900,
)

private val DarkColors = darkColorScheme(
    primary = Green300,
    onPrimary = Color(0xFF003909),
    primaryContainer = Green800,
    onPrimaryContainer = Green200,
    secondary = Orange300,
    onSecondary = Color(0xFF462A00),
    error = Red300,
    onError = Color(0xFF601410),
    background = DarkBackground,
    onBackground = Color(0xFFE6E1E5),
    surface = DarkSurface,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = Color(0xFFCAC4D0),
)

@Composable
fun SeenSlideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
