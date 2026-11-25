package com.antigravity.mobile.feature.workspace.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.feature.workspace.model.AppThemeMode

/**
 * Official Google Antigravity Mobile design tokens covering both:
 * - **Dark Mode:** Antigravity Desktop obsidian canvas (`#0B0E14`), card surfaces (`#131823`),
 *   crisp hairline borders (`#232E42`), and luminous blue/cyan accents (`#60A5FA` / `#38BDF8`).
 * - **Light Mode:** Clean Google white (`#FFFFFF`) & subtle slate canvas (`#F8FAFC`),
 *   card surfaces (`#F1F5F9`), crisp borders (`#E2E8F0`), Google Blue (`#1A73E8`),
 *   and deep slate ink (`#0F172A`).
 */
object AntigravityPalette {
    // Dark Mode — Antigravity Desktop obsidian tokens
    val DeepSpace = Color(0xFF0B0E14)
    val SurfaceDark = Color(0xFF0F141D)
    val SurfaceElevated = Color(0xFF131823)
    val SurfaceVariantDark = Color(0xFF1A2233)
    val OutlineDark = Color(0xFF232E42)
    val BorderHairline = Color(0xFF232E42)
    val AntigravityBlueDark = Color(0xFF60A5FA)
    val NeonCyan = Color(0xFF38BDF8)
    val HydrogenViolet = Color(0xFF818CF8)
    val CarbonTeal = Color(0xFF2DD4BF)
    val SuccessGreen = Color(0xFF34D399)
    val WarningAmber = Color(0xFFFBBF24)
    val CriticalRed = Color(0xFFF87171)
    val DiffAddBg = Color(0xFF0D281E)
    val DiffAddFg = Color(0xFF6EE7B7)
    val DiffRemoveBg = Color(0xFF31151B)
    val DiffRemoveFg = Color(0xFFFCA5A5)
    val CodeBlockBg = Color(0xFF090C12)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    // Light Mode — Official Google Material 3 clean light tokens
    val GoogleBlue = Color(0xFF1A73E8)
    val GoogleBlueContainer = Color(0xFFE8F0FE)
    val GoogleBlueOnContainer = Color(0xFF174EA6)
    val LightCanvas = Color(0xFFF8FAFC)
    val LightSurface = Color(0xFFFFFFFF)
    val LightCard = Color(0xFFF1F5F9)
    val LightCardElevated = Color(0xFFE2E8F0)
    val LightBorder = Color(0xFFE2E8F0)
    val LightOutlineStrong = Color(0xFFCBD5E1)
    val LightInkPrimary = Color(0xFF0F172A)
    val LightInkSecondary = Color(0xFF475569)
    val LightInkMuted = Color(0xFF64748B)
    val SuccessGreenLight = Color(0xFF137333)
    val WarningAmberLight = Color(0xFFB45309)
    val CriticalRedLight = Color(0xFFD93025)
    val DiffAddBgLight = Color(0xFFE6F4EA)
    val DiffAddFgLight = Color(0xFF0D652D)
    val DiffRemoveBgLight = Color(0xFFFCE8E6)
    val DiffRemoveFgLight = Color(0xFFA50E0E)
    val CodeBlockBgLight = Color(0xFFF1F5F9)
}

/**
 * Theme-adaptive semantic colors for code blocks, unified diffs, quota indicators,
 * and subtle hairline card borders across both Light and Dark themes.
 */
@Immutable
data class AntigravityExtendedColors(
    val isDark: Boolean,
    val cardBackground: Color,
    val cardBorder: Color,
    val codeBlockBackground: Color,
    val accentCyan: Color,
    val success: Color,
    val warning: Color,
    val critical: Color,
    val diffAddBackground: Color,
    val diffAddForeground: Color,
    val diffRemoveBackground: Color,
    val diffRemoveForeground: Color,
    val textMuted: Color,
)

val DarkAntigravityExtendedColors = AntigravityExtendedColors(
    isDark = true,
    cardBackground = AntigravityPalette.SurfaceElevated,
    cardBorder = AntigravityPalette.BorderHairline,
    codeBlockBackground = AntigravityPalette.CodeBlockBg,
    accentCyan = AntigravityPalette.NeonCyan,
    success = AntigravityPalette.SuccessGreen,
    warning = AntigravityPalette.WarningAmber,
    critical = AntigravityPalette.CriticalRed,
    diffAddBackground = AntigravityPalette.DiffAddBg,
    diffAddForeground = AntigravityPalette.DiffAddFg,
    diffRemoveBackground = AntigravityPalette.DiffRemoveBg,
    diffRemoveForeground = AntigravityPalette.DiffRemoveFg,
    textMuted = AntigravityPalette.TextMuted,
)

val LightAntigravityExtendedColors = AntigravityExtendedColors(
    isDark = false,
    cardBackground = AntigravityPalette.LightCard,
    cardBorder = AntigravityPalette.LightBorder,
    codeBlockBackground = AntigravityPalette.CodeBlockBgLight,
    accentCyan = AntigravityPalette.GoogleBlue,
    success = AntigravityPalette.SuccessGreenLight,
    warning = AntigravityPalette.WarningAmberLight,
    critical = AntigravityPalette.CriticalRedLight,
    diffAddBackground = AntigravityPalette.DiffAddBgLight,
    diffAddForeground = AntigravityPalette.DiffAddFgLight,
    diffRemoveBackground = AntigravityPalette.DiffRemoveBgLight,
    diffRemoveForeground = AntigravityPalette.DiffRemoveFgLight,
    textMuted = AntigravityPalette.LightInkMuted,
)

val LocalAntigravityColors = staticCompositionLocalOf { DarkAntigravityExtendedColors }

object AntigravityThemeTokens {
    val colors: AntigravityExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAntigravityColors.current
}

val AntigravityDarkColorScheme = darkColorScheme(
    primary = AntigravityPalette.AntigravityBlueDark,
    onPrimary = Color(0xFF061A33),
    primaryContainer = Color(0xFF132A4A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = AntigravityPalette.NeonCyan,
    onSecondary = Color(0xFF031B26),
    secondaryContainer = Color(0xFF0E2F44),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AntigravityPalette.SuccessGreen,
    onTertiary = Color(0xFF032117),
    tertiaryContainer = Color(0xFF0D3326),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = AntigravityPalette.DeepSpace,
    onBackground = AntigravityPalette.TextPrimary,
    surface = AntigravityPalette.SurfaceDark,
    onSurface = AntigravityPalette.TextPrimary,
    surfaceVariant = AntigravityPalette.SurfaceVariantDark,
    onSurfaceVariant = AntigravityPalette.TextSecondary,
    surfaceContainerLowest = AntigravityPalette.DeepSpace,
    surfaceContainerLow = AntigravityPalette.SurfaceDark,
    surfaceContainer = AntigravityPalette.SurfaceElevated,
    surfaceContainerHigh = AntigravityPalette.SurfaceVariantDark,
    surfaceContainerHighest = AntigravityPalette.BorderHairline,
    outline = Color(0xFF334155),
    outlineVariant = AntigravityPalette.BorderHairline,
    error = AntigravityPalette.CriticalRed,
    onError = Color(0xFF2D0A0E),
    errorContainer = Color(0xFF47151C),
    onErrorContainer = Color(0xFFFEE2E2),
)

val AntigravityLightColorScheme = lightColorScheme(
    primary = AntigravityPalette.GoogleBlue,
    onPrimary = Color.White,
    primaryContainer = AntigravityPalette.GoogleBlueContainer,
    onPrimaryContainer = AntigravityPalette.GoogleBlueOnContainer,
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF2FF),
    onSecondaryContainer = Color(0xFF312E81),
    tertiary = AntigravityPalette.SuccessGreenLight,
    onTertiary = Color.White,
    tertiaryContainer = AntigravityPalette.DiffAddBgLight,
    onTertiaryContainer = AntigravityPalette.DiffAddFgLight,
    background = AntigravityPalette.LightCanvas,
    onBackground = AntigravityPalette.LightInkPrimary,
    surface = AntigravityPalette.LightSurface,
    onSurface = AntigravityPalette.LightInkPrimary,
    surfaceVariant = AntigravityPalette.LightCard,
    onSurfaceVariant = AntigravityPalette.LightInkSecondary,
    surfaceContainerLowest = AntigravityPalette.LightSurface,
    surfaceContainerLow = AntigravityPalette.LightCanvas,
    surfaceContainer = AntigravityPalette.LightCard,
    surfaceContainerHigh = AntigravityPalette.LightCardElevated,
    surfaceContainerHighest = AntigravityPalette.LightOutlineStrong,
    outline = AntigravityPalette.LightOutlineStrong,
    outlineVariant = AntigravityPalette.LightBorder,
    error = AntigravityPalette.CriticalRedLight,
    onError = Color.White,
    errorContainer = AntigravityPalette.DiffRemoveBgLight,
    onErrorContainer = AntigravityPalette.DiffRemoveFgLight,
)

val AntigravityTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

/**
 * Antigravity Mobile Material 3 theme — polished Google Light & Antigravity Desktop Dark palettes
 * with optional Android 12+ Dynamic Color and explicit [AppThemeMode] support.
 */
@Composable
fun AntigravityTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    followSystemDarkTheme: Boolean = false,
    themeMode: AppThemeMode? = null,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        null -> if (followSystemDarkTheme) isSystemInDarkTheme() else darkTheme
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        useDark -> AntigravityDarkColorScheme
        else -> AntigravityLightColorScheme
    }
    val extendedColors = if (useDark) DarkAntigravityExtendedColors else LightAntigravityExtendedColors

    CompositionLocalProvider(LocalAntigravityColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AntigravityTypography,
            content = content,
        )
    }
}
