package com.seenslide.teacher.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Warm, friendly colors — green for Bangladesh, approachable for teachers
private val Green700 = Color(0xFF388E3C)
private val Green800 = Color(0xFF2E7D32)
private val Green50 = Color(0xFFE8F5E9)
private val Orange600 = Color(0xFFFB8C00)
private val Red600 = Color(0xFFE53935)
private val White = Color(0xFFFFFFFF)
private val Gray50 = Color(0xFFFAFAFA)
private val Gray900 = Color(0xFF212121)

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

@Composable
fun SeenSlideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
