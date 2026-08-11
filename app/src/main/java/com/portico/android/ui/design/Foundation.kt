@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.portico.android.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portico.android.domain.Money
import com.portico.android.ui.theme.PorticoTheme

/*
 * Portico's component vocabulary.
 *
 * The unit of composition here is the *panel* — a hairline-bounded region with
 * a label strip and rows inside it — not the card. Cards float, need shadows to
 * separate, and cost roughly half a screen of vertical space each; panels butt
 * up against one another and let a phone carry a dozen figures instead of four.
 * There is exactly one elevation declaration in the system (a 1dp hairline) and
 * no drop shadows anywhere.
 */

object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Page gutter by width class. */
    fun gutter(widthDp: Dp): Dp = when {
        widthDp >= 840.dp -> 32.dp
        widthDp >= 600.dp -> 24.dp
        else -> 16.dp
    }
}

/**
 * Responsive behaviour in Portico is structural — which navigation, how many
 * columns, how dense a table — never fluid type. Text follows the system font
 * scale instead, so a user who has enlarged their font gets larger text rather
 * than a differently proportioned screen.
 */
enum class WidthClass {
    COMPACT,   // phones: bottom navigation bar
    MEDIUM,    // small tablets, unfolded phones: navigation rail
    EXPANDED;  // tablets and desktop-style layouts: rail plus multi-column

    val isAtLeastMedium: Boolean get() = this != COMPACT
    val isExpanded: Boolean get() = this == EXPANDED

    companion object {
        fun from(width: Dp): WidthClass = when {
            width >= 840.dp -> EXPANDED
            width >= 600.dp -> MEDIUM
            else -> COMPACT
        }
    }
}

val LocalWidthClass = androidx.compose.runtime.compositionLocalOf { WidthClass.COMPACT }

/** Two columns when there is room, stacked when there is not. */
@Composable
fun ResponsiveColumns(
    modifier: Modifier = Modifier,
    spacing: Dp = Space.lg,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit
) {
    val widthClass = LocalWidthClass.current
    if (widthClass.isExpanded) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing), content = left)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing), content = right)
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            left()
            right()
        }
    }
}

val PanelShape = RoundedCornerShape(12.dp)
val ControlShape = RoundedCornerShape(10.dp)

// ------------------------------------------------------------------- panels

/**
 * The base surface. One hairline, one radius, no shadow — the whole depth
 * system in three lines.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val semantic = PorticoTheme.semantic
    Surface(
        modifier = modifier,
        shape = PanelShape,
        color = semantic.panel,
        border = BorderStroke(
            1.dp,
            if (accent) MaterialTheme.colorScheme.primary.copy(alpha = .45f) else semantic.hairline
        )
    ) {
        Column(content = content)
    }
}

/** Label strip that opens a panel. Tracked caps, so it never competes with data. */
@Composable
fun PanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.lg, end = if (onAction != null) Space.sm else Space.lg, top = Space.md, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            SectionLabel(title)
            if (supporting != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = PorticoTheme.semantic.tertiaryText
                )
            }
        }
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.sm)) {
                Text(action, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** Small tracked caps. The system's only label voice. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = PorticoTheme.semantic.tertiaryText
) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/** Screen-level heading, used above a group of panels. */
@Composable
fun ScreenHeading(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (supporting != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = PorticoTheme.semantic.tertiaryText
                )
            }
        }
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, inset: Dp = Space.lg) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = inset),
        thickness = 1.dp,
        color = PorticoTheme.semantic.hairline
    )
}

/** Heavier rule. Closes a total; the ledger's double underline. */
@Composable
fun TotalRule(modifier: Modifier = Modifier, inset: Dp = Space.lg) {
    Column(modifier.padding(horizontal = inset)) {
        HorizontalDivider(thickness = 1.dp, color = PorticoTheme.semantic.rule)
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(thickness = 1.dp, color = PorticoTheme.semantic.rule)
    }
}

// --------------------------------------------------------------------- rows

/**
 * The workhorse: label left, value right, both on one baseline.
 * Values are tabular by theme, so a stack of these aligns without effort.
 */
@Composable
fun DataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    emphasise: Boolean = false,
    indent: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val base = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .heightIn(min = if (onClick != null) 48.dp else 44.dp)
        .padding(start = Space.lg + indent, end = Space.lg, top = Space.sm, bottom = Space.sm)

    Row(base, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = if (emphasise) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (emphasise) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = PorticoTheme.semantic.tertiaryText
                )
            }
        }
        Spacer(Modifier.width(Space.md))
        Text(
            value,
            style = if (emphasise) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasise) FontWeight.SemiBold else FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End
        )
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            trailing()
        }
    }
}

/** Navigable row: label, optional value, chevron. */
@Composable
fun NavRow(
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    value: String? = null,
    glyph: Glyph? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glyph != null) {
            PorticoIcon(glyph, size = 20.dp, tint = tint, contentDescription = null)
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = PorticoTheme.semantic.tertiaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(Space.sm))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = PorticoTheme.semantic.tertiaryText)
        }
        Spacer(Modifier.width(Space.xs))
        PorticoIcon(Glyph.FORWARD, size = 16.dp, tint = PorticoTheme.semantic.tertiaryText, contentDescription = null)
    }
}

// ----------------------------------------------------------------- readouts

/**
 * The dominant figure on a screen: value large in tabular figures, a signed
 * delta beneath. Nothing else competes for this slot.
 */
@Composable
fun MetricReadout(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    delta: Double? = null,
    deltaText: String? = null,
    compact: Boolean = false
) {
    val semantic = PorticoTheme.semantic
    Column(modifier) {
        SectionLabel(label)
        Spacer(Modifier.height(Space.xs))
        Text(
            value,
            style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (delta != null && deltaText != null) {
            Spacer(Modifier.height(Space.xs))
            DeltaLine(delta = delta, text = deltaText)
        }
    }
}

/**
 * Signed value with a direction arrow. The arrow is what keeps the meaning
 * off colour alone, which matters for the ~8% of users who cannot separate
 * the green from the red.
 */
@Composable
fun DeltaLine(
    delta: Double,
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    val semantic = PorticoTheme.semantic
    val color = semantic.forDelta(delta)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        if (delta != 0.0) {
            PorticoIcon(
                if (delta > 0) Glyph.UP else Glyph.DOWN,
                size = 14.dp,
                tint = color,
                contentDescription = null
            )
        }
        Text(text, style = style, color = color)
    }
}

/** Compact metric for grids: label above, figure below, optional delta. */
@Composable
fun MetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    delta: Double? = null
) {
    Column(modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        SectionLabel(label)
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            // When the figure *is* the movement, it carries the colour itself
            // rather than repeating itself as a caption underneath.
            color = if (delta != null && supporting == null) {
                PorticoTheme.semantic.forDelta(delta)
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        when {
            delta != null && supporting != null -> DeltaLine(delta, supporting, style = MaterialTheme.typography.bodySmall)
            supporting != null -> Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = PorticoTheme.semantic.tertiaryText
            )
        }
    }
}

/**
 * One cell of a metric grid.
 *
 * [supporting] is the caption shown under the figure and [direction] only
 * decides its colour and arrow. Keeping them separate matters: an early build
 * derived the caption from the direction value and rendered a monthly cashflow
 * of $2,987 as "+2987.8%".
 */
data class Metric(
    val label: String,
    val value: String,
    val supporting: String? = null,
    val direction: Double? = null
)

/** Percentage metric where the figure is itself the movement. */
fun rateMetric(label: String, rate: Double) =
    Metric(label, Money.percent(rate), null, rate)

/**
 * Metric grid that wraps instead of forcing a fragile fixed column count.
 * Two per row on a phone, three or four when there is room.
 */
@Composable
fun MetricGrid(
    metrics: List<Metric>,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    val semantic = PorticoTheme.semantic
    Column(modifier) {
        metrics.chunked(columns).forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Hairline(inset = 0.dp)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                repeat(columns) { index ->
                    if (index > 0) {
                        VerticalDivider(
                            modifier = Modifier.fillMaxHeight(),
                            thickness = 1.dp,
                            color = semantic.hairline
                        )
                    }
                    val metric = row.getOrNull(index)
                    if (metric == null) {
                        // Hold the column so a short last row still aligns above.
                        Spacer(Modifier.weight(1f))
                    } else {
                        MetricCell(
                            label = metric.label,
                            value = metric.value,
                            modifier = Modifier.weight(1f),
                            supporting = metric.supporting,
                            delta = metric.direction
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ controls

@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    glyph: Glyph? = null,
    /** Forward actions read better with the arrow after the word. */
    glyphTrailing: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled && !loading,
        shape = ControlShape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = Space.xl)
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(Space.md))
            Text("Working…", style = MaterialTheme.typography.labelLarge)
        } else {
            if (glyph != null && !glyphTrailing) {
                PorticoIcon(glyph, size = 18.dp, tint = MaterialTheme.colorScheme.onPrimary, contentDescription = null)
                Spacer(Modifier.width(Space.sm))
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (glyph != null && glyphTrailing) {
                Spacer(Modifier.width(Space.sm))
                PorticoIcon(glyph, size = 18.dp, tint = MaterialTheme.colorScheme.onPrimary, contentDescription = null)
            }
        }
    }
}

@Composable
fun SecondaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glyph: Glyph? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val contentColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = ControlShape,
        border = BorderStroke(1.dp, if (destructive) MaterialTheme.colorScheme.error.copy(alpha = .5f) else PorticoTheme.semantic.rule),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(horizontal = Space.xl)
    ) {
        if (glyph != null) {
            PorticoIcon(glyph, size = 18.dp, tint = contentColor, contentDescription = null)
            Spacer(Modifier.width(Space.sm))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Icon-only control. Always 48dp of touch area regardless of glyph size. */
@Composable
fun GlyphButton(
    glyph: Glyph,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    badge: Int = 0,
    onClick: () -> Unit
) {
    Box(modifier) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            PorticoIcon(glyph, size = 22.dp, tint = tint, contentDescription = contentDescription)
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 8.dp)
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (badge > 9) "9+" else badge.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/** Segmented selector: periods, tabs, filters. Scrolls rather than squashing. */
@Composable
fun SegmentedRow(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    val semantic = PorticoTheme.semantic
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                tween(160), label = "segment-bg"
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(160), label = "segment-fg"
            )
            Box(
                Modifier
                    .clip(ControlShape)
                    .background(background)
                    .border(1.dp, if (isSelected) Color.Transparent else semantic.hairline, ControlShape)
                    .clickable { onSelect(option) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                contentAlignment = Alignment.Center
            ) {
                Text(option, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
            }
        }
    }
}

/** Status marker. Never the only carrier of its meaning — always has a word. */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = PorticoTheme.semantic.neutral,
    glyph: Glyph? = null
) {
    Row(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tone.copy(alpha = .14f))
            .padding(horizontal = Space.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (glyph != null) PorticoIcon(glyph, size = 12.dp, tint = tone, contentDescription = null)
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = tone, maxLines = 1)
    }
}

@Composable
fun PorticoField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    prefix: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = PorticoTheme.semantic.tertiaryText) } },
            prefix = prefix?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            trailingIcon = trailing,
            isError = error != null,
            singleLine = singleLine,
            shape = ControlShape,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = PorticoTheme.semantic.rule,
                unfocusedContainerColor = PorticoTheme.semantic.panelSunk.copy(alpha = .5f),
                focusedContainerColor = PorticoTheme.semantic.panelSunk.copy(alpha = .5f)
            )
        )
        val helper = error ?: supporting
        if (helper != null) {
            Spacer(Modifier.height(Space.xs))
            Text(
                helper,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else PorticoTheme.semantic.tertiaryText,
                modifier = Modifier.padding(start = Space.md)
            )
        }
    }
}

/** Currency input. Prefixes the symbol and strips anything that is not a figure. */
@Composable
fun CurrencyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    currency: String = "USD",
    modifier: Modifier = Modifier,
    supporting: String? = null,
    error: String? = null
) {
    PorticoField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || it == '.' }) },
        label = label,
        modifier = modifier,
        placeholder = "0",
        supporting = supporting,
        error = error,
        keyboardType = KeyboardType.Decimal,
        prefix = Money.symbolFor(currency)
    )
}

/** Inline single-choice picker; avoids a modal for a two-to-six option decision. */
@Composable
fun ChoiceRow(
    label: String,
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel(label, Modifier.padding(start = Space.lg, bottom = Space.sm))
        SegmentedRow(options = options, selected = selected, onSelect = onSelect)
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    glyph: Glyph? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glyph != null) {
            PorticoIcon(glyph, size = 20.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant, contentDescription = null)
            Spacer(Modifier.width(Space.md))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = PorticoTheme.semantic.tertiaryText)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// -------------------------------------------------------------------- motion

/**
 * The one authored moment: a value that changed tints its own row for a beat,
 * then settles. Nothing else in the app animates on its own.
 */
@Composable
fun rememberTickFlash(key: Any?, direction: Double, enabled: Boolean = true): Color {
    if (!enabled) return Color.Transparent
    val target = PorticoTheme.semantic.forDelta(direction)
    // Starts lit on a new key, then decays to transparent.
    var settled by remember(key) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (settled) 0f else 0.16f,
        animationSpec = tween(durationMillis = 640),
        label = "tick-flash"
    )
    LaunchedEffect(key) { settled = true }
    return target.copy(alpha = alpha)
}

/** Illustrative-data marker. Used wherever a figure is not the user's own. */
@Composable
fun SyntheticNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        PorticoIcon(Glyph.INFO, size = 14.dp, tint = PorticoTheme.semantic.tertiaryText, contentDescription = null)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = PorticoTheme.semantic.tertiaryText
        )
    }
}
