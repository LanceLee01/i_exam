package com.examhelper.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Blue600 = Color(0xFF2563EB)
val Blue800 = Color(0xFF1E40AF)
val Blue900 = Color(0xFF1E3A8A)
val SidebarBg = Color(0xFF121220)
val SurfaceDark = Color(0xFF1E1E30)
val TextCorrect = Color(0xFF22C55E)
val TextError = Color(0xFFEF4444)
val TextSecondary = Color(0xFF9CA3AF)
val EdgeWhite = Color(0x40FFFFFF)

private val DarkColorScheme = darkColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    secondary = Blue800,
    background = SidebarBg,
    surface = SurfaceDark,
    onBackground = Color.White,
    onSurface = Color.White,
    error = TextError,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    secondary = Blue800,
    background = Color.White,
    surface = Color(0xFFF3F4F6),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    error = TextError,
    onError = Color.White
)

@Composable
fun ExamHelperTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = ExamHelperTypography,
        content = content
    )
}
