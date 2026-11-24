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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.antigravity.mobile.feature.workspace.model.AppThemeMode

/**
 * Antigravity Desktop-inspired obsidian & vibrant cyan/indigo/emerald design tokens.
 */
object AntigravityPalette {
    val DeepSpace = Color(0xFF090C12)
    val SurfaceDark = Color(0xFF0F141F)
    val SurfaceElevated = Color(0xFF151C2B)
    val SurfaceVariantDark = Color(0xFF1D273B)
    val OutlineDark = Color(0xFF233047)
    val BorderHairline = Color(0xFF233047)
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
    val CodeBlockBg = Color(0xFF0B0F17)
    val TextPrimary = Color(0xFFEDF2FA)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
}

val AntigravityDarkColorScheme = darkColorScheme(
    primary = AntigravityPalette.NeonCyan,
    onPrimary = Color(0xFF031B26),
    primaryContainer = Color(0xFF0C3147),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = AntigravityPalette.HydrogenViolet,
    onSecondary = Color(0xFF151442),
    secondaryContainer = Color(0xFF25295C),
    onSecondaryContainer = Color(0xFFE0E7FF),
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
    surfaceContainerHighest = Color(0xFF243149),
    outline = AntigravityPalette.OutlineDark,
    outlineVariant = Color(0xFF1B263B),
    error = AntigravityPalette.CriticalRed,
    onError = Color(0xFF2D0A0E),
    errorContainer = Color(0xFF47151C),
    onErrorContainer = Color(0xFFFEE2E2),
)

val AntigravityLightColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

val AntigravityTypography = Typography(
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
 * Antigravity Mobile Material 3 theme — obsidian/cyan dark-theme-first with optional Dynamic Color
 * and explicit [AppThemeMode] support.
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AntigravityTypography,
        content = content,
    )
}
