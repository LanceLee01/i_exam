package com.examhelper.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// ── Design Token System ──────────────────────────────────────────

// Sidebar dark palette (unchanged, used by floating overlay)
object ExamHelperColors {
    val Primary = Color(0xFF2563EB)
    val PrimaryVariant = Color(0xFF1E40AF)
    val Surface = Color(0xFF121220)
    val SurfaceCard = Color.White.copy(alpha = 0.06f)
    val SurfaceCardHover = Color.White.copy(alpha = 0.10f)
    val Success = Color(0xFF22C55E)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)
    val Info = Color(0xFF3B82F6)
    val OnSurface = Color.White
    val OnSurfaceSecondary = Color(0xFF9CA3AF)
    val OnSurfaceMuted = Color.White.copy(alpha = 0.40f)
    val Outline = Color.White.copy(alpha = 0.08f)
    val OutlineInput = Color.White.copy(alpha = 0.15f)
    val EdgeHandle = Color(0x40FFFFFF)
}

val LocalExamHelperColors = staticCompositionLocalOf { ExamHelperColors }

// ── Main App Color System (theme-switchable) ─────────────────────

data class AppColors(
    val primary: Color,
    val primaryDark: Color,
    val primaryVariant: Color,
    val surface: Color,
    val surfaceCard: Color,
    val surfaceAccent: Color,
    val onSurface: Color,
    val onSurfaceSecondary: Color,
    val onSurfaceMuted: Color,
    val outline: Color,
    val outlineInput: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
    val navBarBg: Color,
    val navBarBorder: Color,
    val navBarGlow: Color,
    val navBarInnerGlow: Color,
    val navBarShadow: Color,
)

/** G · 暖橙白 — default light theme */
val WarmOrangeLight = AppColors(
    primary = Color(0xFFF27A3E),
    primaryDark = Color(0xFFD6652D),
    primaryVariant = Color(0xFFD6652D),
    surface = Color(0xFFFDFCFB),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceAccent = Color(0xFFFFF8F4),
    onSurface = Color(0xFF1C1C1E),
    onSurfaceSecondary = Color(0xFF8E8E93),
    onSurfaceMuted = Color(0x40000000),
    outline = Color(0x0A000000),
    outlineInput = Color(0x0F000000),
    success = Color(0xFF10B981),
    warning = Color(0xFFF59E0B),
    error = Color(0xFFEF4444),
    info = Color(0xFF3B82F6),
    navBarBg = Color(0xEBFFFFFF),
    navBarBorder = Color(0x0A000000),
    navBarGlow = Color(0x0FFFFFFF),
    navBarInnerGlow = Color(0xCCFFFFFF),
    navBarShadow = Color(0x0F000000),
)

/** A · 紫蓝暗色 — dark theme */
val VioletDark = AppColors(
    primary = Color(0xFF7C3AED),
    primaryDark = Color(0xFF6D28D9),
    primaryVariant = Color(0xFF2563EB),
    surface = Color(0xFF0F0F1A),
    surfaceCard = Color(0x0FFFFFFF),
    surfaceAccent = Color(0x0A7C3AED),
    onSurface = Color.White,
    onSurfaceSecondary = Color(0x80FFFFFF),
    onSurfaceMuted = Color(0x40FFFFFF),
    outline = Color(0x0FFFFFFF),
    outlineInput = Color(0x1AFFFFFF),
    success = Color(0xFF10B981),
    warning = Color(0xFFF59E0B),
    error = Color(0xFFEF4444),
    info = Color(0xFF3B82F6),
    navBarBg = Color(0xEB121222),
    navBarBorder = Color(0x0FFFFFFF),
    navBarGlow = Color(0x0A7C3AED),
    navBarInnerGlow = Color(0x14FFFFFF),
    navBarShadow = Color(0x66000000),
)

val LocalAppColors = staticCompositionLocalOf { WarmOrangeLight }

// ── Backward compat aliases ──────────────────────────────────────

val Blue600 @Composable get() = LocalExamHelperColors.current.Primary
val Blue800 @Composable get() = LocalExamHelperColors.current.PrimaryVariant
val Blue900 = Color(0xFF1E3A8A)
val SidebarBg @Composable get() = LocalExamHelperColors.current.Surface
val SurfaceDark = Color(0xFF1E1E30)
val TextCorrect @Composable get() = LocalExamHelperColors.current.Success
val TextError @Composable get() = LocalExamHelperColors.current.Error
val TextSecondary @Composable get() = LocalExamHelperColors.current.OnSurfaceSecondary
val EdgeWhite @Composable get() = LocalExamHelperColors.current.EdgeHandle

// ── M3 Color Schemes ─────────────────────────────────────────────

private fun appDarkColorScheme(app: AppColors) = darkColorScheme(
    primary = app.primary,
    onPrimary = Color.White,
    secondary = app.primaryVariant,
    background = app.surface,
    surface = app.surfaceCard,
    onBackground = app.onSurface,
    onSurface = app.onSurface,
    error = app.error,
    onError = Color.White,
)

private fun appLightColorScheme(app: AppColors) = lightColorScheme(
    primary = app.primary,
    onPrimary = Color.White,
    secondary = app.primaryDark,
    background = app.surface,
    surface = app.surfaceCard,
    onBackground = app.onSurface,
    onSurface = app.onSurface,
    error = app.error,
    onError = Color.White,
)

// ── Static dark scheme for sidebar ───────────────────────────────

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

// ── Theme Entry Point ────────────────────────────────────────────

/**
 * @param appDarkTheme false = G·暖橙白 (default), true = A·紫蓝暗色
 * @param sidebarMode true = always use dark sidebar palette (for SidebarService)
 */
@Composable
fun ExamHelperTheme(
    appDarkTheme: Boolean = false,
    sidebarMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val appColors = if (appDarkTheme) VioletDark else WarmOrangeLight
    CompositionLocalProvider(
        LocalExamHelperColors provides ExamHelperColors,
        LocalAppColors provides appColors,
    ) {
        MaterialTheme(
            colorScheme = when {
                sidebarMode -> DarkColorScheme
                appDarkTheme -> appDarkColorScheme(appColors)
                else -> appLightColorScheme(appColors)
            },
            typography = ExamHelperTypography,
            content = content
        )
    }
}
