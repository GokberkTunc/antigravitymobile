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

object AntigravityPalette {
    val DeepSpace = Color(0xFF0A0D14)
    val SurfaceDark = Color(0xFF111622)
    val SurfaceElevated = Color(0xFF171F30)
    val SurfaceVariantDark = Color(0xFF1E283D)
    val OutlineDark = Color(0xFF2C3A56)
    val NeonCyan = Color(0xFF56CCF2)
    val HydrogenViolet = Color(0xFFA78BFA)
    val CarbonTeal = Color(0xFF2DD4BF)
    val SuccessGreen = Color(0xFF22C55E)
    val WarningAmber = Color(0xFFF59E0B)
    val CriticalRed = Color(0xFFEF4444)
    val DiffAddBg = Color(0xFF0F2C1C)
    val DiffAddFg = Color(0xFF86EFAC)
    val DiffRemoveBg = Color(0xFF33151B)
    val DiffRemoveFg = Color(0xFFFCA5A5)
    val CodeBlockBg = Color(0xFF0D1119)
    val TextPrimary = Color(0xFFE8EEF8)
    val TextSecondary = Color(0xFF9BA9BF)
}

val AntigravityDarkColorScheme = darkColorScheme(
    primary = AntigravityPalette.NeonCyan,
    onPrimary = Color(0xFF001F2A),
    primaryContainer = Color(0xFF0C364A),
    onPrimaryContainer = Color(0xFFC4EEFF),
    secondary = AntigravityPalette.HydrogenViolet,
    onSecondary = Color(0xFF1D0E42),
    secondaryContainer = Color(0xFF2F2157),
    onSecondaryContainer = Color(0xFFE5DCFF),
    tertiary = AntigravityPalette.CarbonTeal,
    onTertiary = Color(0xFF00201C),
    background = AntigravityPalette.DeepSpace,
    onBackground = AntigravityPalette.TextPrimary,
    surface = AntigravityPalette.SurfaceDark,
    onSurface = AntigravityPalette.TextPrimary,
    surfaceVariant = AntigravityPalette.SurfaceVariantDark,
    onSurfaceVariant = AntigravityPalette.TextSecondary,
    outline = AntigravityPalette.OutlineDark,
    error = AntigravityPalette.CriticalRed,
    onError = Color.White,
    errorContainer = Color(0xFF471317),
    onErrorContainer = Color(0xFFFFDAD9),
)

val AntigravityLightColorScheme = lightColorScheme(
    primary = Color(0xFF00658A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5E7FF),
    onPrimaryContainer = Color(0xFF001E2C),
    secondary = Color(0xFF5D46B8),
    onSecondary = Color.White,
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE6ECF5),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White,
)

val AntigravityTypography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
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
 * Antigravity Mobile Material 3 theme — dark-theme-first with Dynamic Color on Android 12+.
 */
@Composable
fun AntigravityTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    followSystemDarkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val useDark = if (followSystemDarkTheme) isSystemInDarkTheme() else darkTheme
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
