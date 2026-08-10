package com.portico.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Portico now uses an editorial off-white/orange light mode and a warm brown
// dark mode. The roles are deliberately semantic so every screen changes
// together without introducing isolated color decisions.
val PorticoNavy = Color(0xFF2A1710)
val PorticoInk = Color(0xFF2A1D17)
val PorticoPaper = Color(0xFFF8F1E7)
val PorticoOrange = Color(0xFFC85A20)
val PorticoTeal = Color(0xFFC85A20)
val PorticoLilac = Color(0xFF8B5E45)
val PorticoMist = Color(0xFFEDE2D4)
val PorticoLine = Color(0xFFD7C6B5)
val PorticoMuted = Color(0xFF78685D)
val PorticoDarkSurface = Color(0xFF33211A)
val PorticoDarkSurfaceVariant = Color(0xFF432B21)
val PorticoCritical = Color(0xFFB33B2E)

private val LightColors = lightColorScheme(
    primary = PorticoOrange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC8),
    onPrimaryContainer = Color(0xFF4F1C08),
    secondary = Color(0xFF8B5E45),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECD8C8),
    onSecondaryContainer = Color(0xFF321B11),
    tertiary = Color(0xFFA86F32),
    onTertiary = Color.White,
    background = PorticoPaper,
    onBackground = PorticoInk,
    surface = Color(0xFFFFFCF7),
    onSurface = PorticoInk,
    surfaceVariant = PorticoMist,
    onSurfaceVariant = Color(0xFF5E5148),
    outline = Color(0xFFAD9582),
    outlineVariant = PorticoLine,
    error = PorticoCritical,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFAD78),
    onPrimary = Color(0xFF4A1A08),
    primaryContainer = Color(0xFF753719),
    onPrimaryContainer = Color(0xFFFFDCC8),
    secondary = Color(0xFFE1AA8C),
    onSecondary = Color(0xFF3E2116),
    secondaryContainer = Color(0xFF684333),
    onSecondaryContainer = Color(0xFFFFDCC8),
    tertiary = Color(0xFFE3B969),
    onTertiary = Color(0xFF3A2507),
    background = Color(0xFF21130F),
    onBackground = Color(0xFFFFF1E8),
    surface = PorticoDarkSurface,
    onSurface = Color(0xFFFFF1E8),
    surfaceVariant = PorticoDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFD8BFB0),
    outline = Color(0xFF8C6350),
    outlineVariant = Color(0xFF5E3C2E),
    error = Color(0xFFFF9C8C),
    onError = Color(0xFF4B0D08)
)

private val PorticoTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 42.sp, lineHeight = 46.sp, letterSpacing = (-1.6).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1.1).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.35.sp)
)

@Composable
fun PorticoTheme(appearance: String = "Light mode", content: @Composable () -> Unit) {
    val dark = when (appearance) {
        "Dark mode" -> true
        "Light mode" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = PorticoTypography, content = content)
}
