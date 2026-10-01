package com.mohammed.mosa.qrscanner.ui.theme


import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class Palette(
    val bg: Color,
    val card: Color,
    val border: Color,
    val borderSoft: Color,
    val well: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val text4: Color,
    val scrim: Color,
    val glassTint: Color,
    val glassFill: Color,
    val glassStroke: Color,
    val dialog: Color,
    val sheet: Color,
    val switchOff: Color,
    val navBar: Color,
    val isDark: Boolean,
)

fun darkPalette() = Palette(
    bg = Color(0xFF0B0B0F),
    card = Color(0xFF14141A),
    border = Color.White.copy(alpha = 0.13f),
    borderSoft = Color.White.copy(alpha = 0.08f),
    well = Color.Black.copy(alpha = 0.35f),
    text = Color.White,
    text2 = Color.White.copy(alpha = 0.65f),
    text3 = Color.White.copy(alpha = 0.5f),
    text4 = Color.White.copy(alpha = 0.38f),
    scrim = Color.Black.copy(alpha = 0.55f),
    glassTint = Color(0xFF14141A).copy(alpha = 0.55f),
    glassFill = Color(0xFF14141A).copy(alpha = 0.55f),
    glassStroke = Color.White.copy(alpha = 0.14f),
    dialog = Color(0xFF1B1B22),
    sheet = Color(0xFF17171D),
    switchOff = Color(0xFF2A2A33),
    navBar = Color(0xFF14141A),
    isDark = true,
)

fun lightPalette() = Palette(
    bg = Color(0xFFF4F5F7),
    card = Color.White,
    border = Color.Black.copy(alpha = 0.12f),
    borderSoft = Color.Black.copy(alpha = 0.07f),
    well = Color(0xFFEDEFF3),
    text = Color(0xFF15161A),
    text2 = Color.Black.copy(alpha = 0.62f),
    text3 = Color.Black.copy(alpha = 0.48f),
    text4 = Color.Black.copy(alpha = 0.35f),
    scrim = Color.Black.copy(alpha = 0.45f),
    glassTint = Color.White.copy(alpha = 0.65f),
    glassFill = Color.White.copy(alpha = 0.65f),
    glassStroke = Color.White.copy(alpha = 0.55f),
    dialog = Color.White,
    sheet = Color.White,
    switchOff = Color(0xFFE1E3E8),
    navBar = Color.White,
    isDark = false,
)

val LocalPalette = staticCompositionLocalOf { darkPalette() }
val PC: Palette @Composable get() = LocalPalette.current