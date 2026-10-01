package com.mohammed.mosa.qrscanner.ui.theme


import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohammed.mosa.qrscanner.R

object Ui {
    val radiusXl: Dp = 12.dp
    val radius2xl: Dp = 16.dp
    val blurXl: Dp = 24.dp
    const val duration: Int = 150
    val easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

/** Accent color — snapshot-state backed so Settings can swap it live. */
var Brand by mutableStateOf(Color(0xFF0077B6))
val Ink = Color(0xFF0B0B0F)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val IBM_PLEX_SANS = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

private val AppTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.copy(fontFamily = IBM_PLEX_SANS),
        displayMedium = t.displayMedium.copy(fontFamily = IBM_PLEX_SANS),
        displaySmall = t.displaySmall.copy(fontFamily = IBM_PLEX_SANS),
        headlineLarge = t.headlineLarge.copy(fontFamily = IBM_PLEX_SANS),
        headlineMedium = t.headlineMedium.copy(fontFamily = IBM_PLEX_SANS),
        headlineSmall = t.headlineSmall.copy(fontFamily = IBM_PLEX_SANS),
        titleLarge = t.titleLarge.copy(fontFamily = IBM_PLEX_SANS),
        titleMedium = t.titleMedium.copy(fontFamily = IBM_PLEX_SANS),
        titleSmall = t.titleSmall.copy(fontFamily = IBM_PLEX_SANS),
        bodyLarge = t.bodyLarge.copy(fontFamily = IBM_PLEX_SANS),
        bodyMedium = t.bodyMedium.copy(fontFamily = IBM_PLEX_SANS),
        bodySmall = t.bodySmall.copy(fontFamily = IBM_PLEX_SANS),
        labelLarge = t.labelLarge.copy(fontFamily = IBM_PLEX_SANS),
        labelMedium = t.labelMedium.copy(fontFamily = IBM_PLEX_SANS),
        labelSmall = t.labelSmall.copy(fontFamily = IBM_PLEX_SANS),
    )
}

@Composable
fun QrScannerTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) darkPalette() else lightPalette()
    val scheme = if (dark) darkColorScheme(
        primary = Brand, onPrimary = Color.White,
        background = palette.bg, onBackground = palette.text,
        surface = palette.bg, onSurface = palette.text,
    ) else lightColorScheme(
        primary = Brand, onPrimary = Color.White,
        background = palette.bg, onBackground = palette.text,
        surface = palette.bg, onSurface = palette.text,
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}