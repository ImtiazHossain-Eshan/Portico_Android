package com.portico.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Portico reads as a financial instrument, not a property brochure.
 *
 * Grounds are neutral graphite (dark) and bone (light) so that the only
 * saturated colour on screen is carrying information. One amber accent marks
 * action and selection at two temperatures (bright on graphite, bronze on
 * bone), which leaves green and red free to mean exactly one thing each:
 * money gained and money lost. Nothing here gradients, glows, or tints for
 * decoration; depth comes from tonal steps and hairlines.
 */

// ---------------------------------------------------------------- dark scheme

/** Instrument ground. Neutral-warm graphite, never blue-black. */
val InkVoid = Color(0xFF0B0B0C)
val InkPanel = Color(0xFF141416)
val InkPanelRaised = Color(0xFF1C1C1F)
val InkHairline = Color(0xFF2A2A2E)
val InkRule = Color(0xFF3A3A40)
val InkPrimaryText = Color(0xFFF5F4F2)
val InkSecondaryText = Color(0xFFA8A5A0)
val InkTertiaryText = Color(0xFF726F6A)

/** The terminal amber. Portico's single accent, night form. */
val AmberSignal = Color(0xFFE8A33D)
val AmberDeepContainer = Color(0xFF4A3208)
val AmberOnContainer = Color(0xFFFFD9A0)

// --------------------------------------------------------------- light scheme

/** Bone. A warm-neutral near-white, not paper cream, not blue-white. */
val BoneGround = Color(0xFFF6F5F3)
val BonePanel = Color(0xFFFFFFFF)
val BonePanelSunk = Color(0xFFEDEBE8)
val BoneHairline = Color(0xFFDDDAD5)
val BoneRule = Color(0xFFB8B4AE)
val BonePrimaryText = Color(0xFF1A1917)
val BoneSecondaryText = Color(0xFF5E5B56)
val BoneTertiaryText = Color(0xFF8A867F)

/** The same accent in daylight: bronze, so it holds contrast on white. */
val BronzeSignal = Color(0xFF9A6212)
val BronzeContainer = Color(0xFFF6E3C6)
val BronzeOnContainer = Color(0xFF3D2604)

// ------------------------------------------------------- performance semantics

/*
 * These two are the only colours in the system that carry meaning on their own,
 * and both are always paired with a sign or an arrow so the meaning never rests
 * on hue alone. Tuned per theme to clear 4.5:1 against their own ground.
 */
val GainDark = Color(0xFF5FB981)
val LossDark = Color(0xFFE5675C)
val GainLight = Color(0xFF1F7A4D)
val LossLight = Color(0xFFB3392E)

/*
 * Neither gain nor loss: unchanged values, gross totals, comparison marks.
 * Deliberately a warm grey. A cool grey here picks up a blue cast next to the
 * amber accent and drags the whole surface toward generic fintech.
 */
val NeutralDark = Color(0xFF9A948B)
val NeutralLight = Color(0xFF6B665E)

/**
 * Roles Material 3 has no slot for. Read these through [PorticoTheme.semantic]
 * rather than reaching for the raw colours above, so light and dark resolve
 * together.
 */
@Immutable
data class PorticoSemantic(
    val gain: Color,
    val loss: Color,
    val neutral: Color,
    /** Hairline between rows inside a panel. */
    val hairline: Color,
    /** Heavier rule that closes a total or separates a section. */
    val rule: Color,
    /** Panel interior, one tonal step off the page ground. */
    val panel: Color,
    /** Inset wells: fields, code entry, sunken table headers. */
    val panelSunk: Color,
    /** De-emphasised metadata: timestamps, units, row counts. */
    val tertiaryText: Color,
    val isDark: Boolean
) {
    /** Sign-aware accessor so callers never branch on colour themselves. */
    fun forDelta(value: Double): Color = when {
        value > 0 -> gain
        value < 0 -> loss
        else -> neutral
    }
}

private val DarkSemantic = PorticoSemantic(
    gain = GainDark,
    loss = LossDark,
    neutral = NeutralDark,
    hairline = InkHairline,
    rule = InkRule,
    panel = InkPanel,
    panelSunk = InkPanelRaised,
    tertiaryText = InkTertiaryText,
    isDark = true
)

private val LightSemantic = PorticoSemantic(
    gain = GainLight,
    loss = LossLight,
    neutral = NeutralLight,
    hairline = BoneHairline,
    rule = BoneRule,
    panel = BonePanel,
    panelSunk = BonePanelSunk,
    tertiaryText = BoneTertiaryText,
    isDark = false
)

val LocalPorticoSemantic: ProvidableCompositionLocal<PorticoSemantic> =
    staticCompositionLocalOf { DarkSemantic }

private val DarkColors = darkColorScheme(
    primary = AmberSignal,
    onPrimary = Color(0xFF1A1206),
    primaryContainer = AmberDeepContainer,
    onPrimaryContainer = AmberOnContainer,
    secondary = Color(0xFFC9C5BE),
    onSecondary = Color(0xFF1F1E1C),
    secondaryContainer = Color(0xFF2E2D2A),
    onSecondaryContainer = Color(0xFFE8E4DD),
    tertiary = Color(0xFFB9A88C),
    onTertiary = Color(0xFF221C12),
    background = InkVoid,
    onBackground = InkPrimaryText,
    surface = InkPanel,
    onSurface = InkPrimaryText,
    surfaceVariant = InkPanelRaised,
    onSurfaceVariant = InkSecondaryText,
    surfaceContainerHighest = Color(0xFF232326),
    outline = InkRule,
    outlineVariant = InkHairline,
    error = LossDark,
    onError = Color(0xFF3A0A06),
    errorContainer = Color(0xFF4A1712),
    onErrorContainer = Color(0xFFFFD6D1),
    scrim = Color(0xCC000000)
)

private val LightColors = lightColorScheme(
    primary = BronzeSignal,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BronzeContainer,
    onPrimaryContainer = BronzeOnContainer,
    secondary = Color(0xFF44413C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E1DC),
    onSecondaryContainer = Color(0xFF23211E),
    tertiary = Color(0xFF6B5836),
    onTertiary = Color(0xFFFFFFFF),
    background = BoneGround,
    onBackground = BonePrimaryText,
    surface = BonePanel,
    onSurface = BonePrimaryText,
    surfaceVariant = BonePanelSunk,
    onSurfaceVariant = BoneSecondaryText,
    surfaceContainerHighest = Color(0xFFE7E4E0),
    outline = BoneRule,
    outlineVariant = BoneHairline,
    error = LossLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDA),
    onErrorContainer = Color(0xFF44100B),
    scrim = Color(0x99000000)
)

/*
 * Tabular figures are the reason a column of money in Portico can be compared
 * by eye. Roboto ships `tnum`; without it "1,240,500" and "184,200" set to
 * different widths and the decimal alignment that makes a ledger legible falls
 * apart. Applied to every style that can carry a number, which is all of them
 * except bodyLarge, the one style reserved for running prose.
 */
private const val TABULAR = "tnum"

private val Sans = FontFamily.SansSerif

private fun figure(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Double
) = TextStyle(
    fontFamily = Sans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = TABULAR
)

private val PorticoTypography = Typography(
    // Dominant readouts: portfolio value, property value, net position.
    displayLarge = figure(46, 50, FontWeight.SemiBold, -1.6),
    displayMedium = figure(36, 40, FontWeight.SemiBold, -1.1),
    displaySmall = figure(28, 34, FontWeight.SemiBold, -0.7),

    headlineLarge = figure(26, 32, FontWeight.SemiBold, -0.5),
    headlineMedium = figure(22, 28, FontWeight.SemiBold, -0.4),
    headlineSmall = figure(19, 25, FontWeight.SemiBold, -0.2),

    titleLarge = figure(18, 24, FontWeight.SemiBold, -0.2),
    titleMedium = figure(15, 21, FontWeight.Medium, 0.0),
    titleSmall = figure(13, 18, FontWeight.Medium, 0.0),

    // The one proportional style: explanation, help text, assistant replies.
    bodyLarge = TextStyle(fontFamily = Sans, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = figure(14, 20, FontWeight.Normal, 0.0),
    bodySmall = figure(12, 17, FontWeight.Normal, 0.0),

    // Instrument labels: tracked, compact, frequently set in caps by callers.
    labelLarge = figure(14, 19, FontWeight.Medium, 0.1),
    labelMedium = figure(12, 16, FontWeight.Medium, 0.4),
    labelSmall = figure(11, 14, FontWeight.Medium, 0.6)
)

/** Portico's appearance preference, persisted from Profile. */
object Appearance {
    const val LIGHT = "Light"
    const val DARK = "Dark"
    const val SYSTEM = "System"
}

@Composable
fun PorticoTheme(
    appearance: String = Appearance.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (appearance) {
        Appearance.DARK -> true
        Appearance.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(
        LocalPorticoSemantic provides if (dark) DarkSemantic else LightSemantic
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = PorticoTypography,
            content = content
        )
    }
}

/** Convenience accessor mirroring `MaterialTheme.colorScheme`. */
object PorticoTheme {
    val semantic: PorticoSemantic
        @Composable get() = LocalPorticoSemantic.current
}
