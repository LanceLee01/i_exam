package com.examhelper.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// ── Design Token System ──────────────────────────────────────────

object ExamHelperColors {
    // Primary
    val Primary = Color(0xFF2563EB)
    val PrimaryVariant = Color(0xFF1E40AF)

    // Surface
    val Surface = Color(0xFF121220)
    val SurfaceCard = Color.White.copy(alpha = 0.06f)
    val SurfaceCardHover = Color.White.copy(alpha = 0.10f)

    // Semantic
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)

    // On-surface (text)
    val OnSurface = Color.White
    val OnSurfaceSecondary = Color(0xFF9CA3AF)
    val OnSurfaceMuted = Color.White.copy(alpha = 0.40f)

    // Outline
    val Outline = Color.White.copy(alpha = 0.08f)
    val OutlineInput = Color.White.copy(alpha = 0.15f)

    // Edge handle (special)
    val EdgeHandle = Color(0x40FFFFFF)
}

val LocalExamHelperColors = staticCompositionLocalOf { ExamHelperColors }

// 向后兼容别名（旧代码引用仍有效）
val Blue600 @Composable get() = LocalExamHelperColors.current.Primary
val Blue800 @Composable get() = LocalExamHelperColors.current.PrimaryVariant
val Blue900 = Color(0xFF1E3A8A)
val SidebarBg @Composable get() = LocalExamHelperColors.current.Surface
val SurfaceDark = Color(0xFF1E1E30)
val TextCorrect @Composable get() = LocalExamHelperColors.current.Success
val TextError @Composable get() = LocalExamHelperColors.current.Error
val TextSecondary @Composable get() = LocalExamHelperColors.current.OnSurfaceSecondary
val EdgeWhite @Composable get() = LocalExamHelperColors.current.EdgeHandle

private val DarkColorScheme = darkColorScheme(
    primary = ExamHelperColors.Primary,
    onPrimary = Color.White,
    secondary = ExamHelperColors.PrimaryVariant,
    background = ExamHelperColors.Surface,
    surface = SurfaceDark,
    onBackground = Color.White,
    onSurface = Color.White,
    error = ExamHelperColors.Error,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ExamHelperColors.Primary,
    onPrimary = Color.White,
    secondary = ExamHelperColors.PrimaryVariant,
    background = Color.White,
    surface = Color(0xFFF3F4F6),
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    error = ExamHelperColors.Error,
    onError = Color.White
)

@Composable
fun ExamHelperTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalExamHelperColors provides ExamHelperColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = ExamHelperTypography,
            content = content
        )
    }
}
