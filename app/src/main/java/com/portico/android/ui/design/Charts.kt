package com.portico.android.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.portico.android.domain.*
import com.portico.android.ui.theme.PorticoTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Charts are drawn geometry, not pictures. Every mark carries data: gridlines
 * are hairlines at the same weight as the panel rules, the series is a single
 * stroke, and the only fill is a flat low-alpha wash under the line — no
 * gradients, no glow, no shadow.
 *
 * Each chart also carries a spoken summary, because a screen reader user needs
 * the trend, not the pixels.
 */

// -------------------------------------------------------------- value chart

/**
 * Portfolio and property value over time, with a scrub.
 *
 * Touch anywhere on the plot to read the value at that point; releasing
 * returns to the latest figure. The reveal animation runs once per data
 * change and ends on the newest point so the motion explains chronology.
 */
@Composable
fun ValueChart(
    series: List<Double>,
    modifier: Modifier = Modifier,
    currency: String = "USD",
    rangeLabel: String = "1Y",
    animate: Boolean = true,
    onScrub: ((Int?) -> Unit)? = null
) {
    val semantic = PorticoTheme.semantic
    val lineColor = if (series.isNotEmpty() && series.last() >= series.first()) semantic.gain else semantic.loss
    val density = LocalDensity.current

    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    val reveal = remember(series, animate) { Animatable(if (animate) 0f else 1f) }

    LaunchedEffect(series, animate) {
        if (animate) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        } else {
            reveal.snapTo(1f)
        }
    }

    if (series.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Not enough history yet", style = MaterialTheme.typography.bodySmall, color = semantic.tertiaryText)
        }
        return
    }

    val min = series.min()
    val max = series.max()
    val span = (max - min).takeIf { it > 0.0 } ?: 1.0
    val change = series.last() - series.first()
    val changePct = if (series.first() != 0.0) change / series.first() * 100 else 0.0

    val summary = "Value chart over $rangeLabel. " +
        "From ${Money.compact(series.first(), currency)} to ${Money.compact(series.last(), currency)}, " +
        "${if (change >= 0) "up" else "down"} ${Money.percent(abs(changePct))}."

    Column(modifier) {
        // Scrubbed readout sits above the plot so the finger never covers it.
        val activeIndex = scrubIndex ?: series.lastIndex
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                Money.format(series[activeIndex], currency),
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.width(Space.md))
            if (scrubIndex == null) {
                DeltaLine(
                    delta = change,
                    text = "${Money.signed(change, currency)}  ${Money.signedPercent(changePct)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "point ${activeIndex + 1} of ${series.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.tertiaryText
                )
            }
        }

        Spacer(Modifier.height(Space.md))

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = Space.lg)
                .semantics { contentDescription = summary }
                .pointerInput(series) {
                    detectDragGestures(
                        onDragEnd = { scrubIndex = null; onScrub?.invoke(null) },
                        onDragCancel = { scrubIndex = null; onScrub?.invoke(null) }
                    ) { change2, _ ->
                        val fraction = (change2.position.x / size.width).coerceIn(0f, 1f)
                        val index = (fraction * series.lastIndex).roundToInt()
                        scrubIndex = index
                        onScrub?.invoke(index)
                    }
                }
                .pointerInput(series) {
                    detectTapGestures(
                        onPress = { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            scrubIndex = (fraction * series.lastIndex).roundToInt()
                            tryAwaitRelease()
                            scrubIndex = null
                            onScrub?.invoke(null)
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val bottom = h - 4.dp.toPx()
            val top = 8.dp.toPx()

            fun xAt(index: Int) = w * index / series.lastIndex
            fun yAt(value: Double) = bottom - ((value - min) / span).toFloat() * (bottom - top)

            // Gridlines at the same hairline weight as every rule in the app.
            repeat(4) { step ->
                val y = top + (bottom - top) * step / 3f
                drawLine(semantic.hairline, Offset(0f, y), Offset(w, y), 1f)
            }

            val visible = (series.lastIndex * reveal.value).coerceIn(0f, series.lastIndex.toFloat())
            val lastWhole = visible.toInt().coerceIn(1, series.lastIndex)

            val linePath = Path()
            val areaPath = Path()
            series.take(lastWhole + 1).forEachIndexed { index, value ->
                val x = xAt(index)
                val y = yAt(value)
                if (index == 0) {
                    linePath.moveTo(x, y)
                    areaPath.moveTo(x, bottom)
                    areaPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    areaPath.lineTo(x, y)
                }
            }
            // Partial segment so the reveal is smooth rather than stepped.
            val headX: Float
            val headY: Float
            if (lastWhole < series.lastIndex) {
                val t = visible - lastWhole
                val x0 = xAt(lastWhole); val y0 = yAt(series[lastWhole])
                val x1 = xAt(lastWhole + 1); val y1 = yAt(series[lastWhole + 1])
                headX = x0 + (x1 - x0) * t
                headY = y0 + (y1 - y0) * t
                linePath.lineTo(headX, headY)
                areaPath.lineTo(headX, headY)
            } else {
                headX = xAt(series.lastIndex)
                headY = yAt(series.last())
            }
            areaPath.lineTo(headX, bottom)
            areaPath.close()

            drawPath(areaPath, lineColor.copy(alpha = 0.10f))
            drawPath(
                linePath,
                lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Scrub marker: a vertical hairline and a dot on the series.
            scrubIndex?.let { index ->
                val x = xAt(index)
                val y = yAt(series[index])
                drawLine(
                    semantic.rule,
                    Offset(x, top),
                    Offset(x, bottom),
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                )
                drawCircle(semantic.panel, 5.dp.toPx(), Offset(x, y))
                drawCircle(lineColor, 4.dp.toPx(), Offset(x, y))
            }

            if (scrubIndex == null) {
                drawCircle(lineColor, 3.5.dp.toPx(), Offset(headX, headY))
            }
        }

        Spacer(Modifier.height(Space.sm))
        // Labelled, because bare figures at the two bottom corners read as a
        // time axis rather than the value range they actually are.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Low ${Money.compact(min, currency)}",
                style = MaterialTheme.typography.labelSmall,
                color = semantic.tertiaryText
            )
            Text(
                "High ${Money.compact(max, currency)}",
                style = MaterialTheme.typography.labelSmall,
                color = semantic.tertiaryText
            )
        }
    }
}

// ----------------------------------------------------------- the waterfall

/**
 * Portico's signature composition: gross income falling through each deduction
 * to net, as a ledger rather than a floating-bar chart.
 *
 * Bars are proportional to gross, so "tax takes a fifth of this" is legible
 * without reading a single figure. Deductions indent, the total closes under a
 * double rule. This same component renders on the dashboard, on a property, in
 * the tax module and in reports — one idea at four scales.
 */
@Composable
fun WaterfallLedger(
    steps: List<WaterfallStep>,
    modifier: Modifier = Modifier,
    currency: String = "USD",
    animate: Boolean = true
) {
    val semantic = PorticoTheme.semantic
    val opening = steps.firstOrNull { it.kind == WaterfallKind.OPENING }?.amount ?: 0.0
    val scale = abs(opening).takeIf { it > 0.0 } ?: 1.0

    val progress = remember(steps, animate) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(steps, animate) {
        if (animate) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        } else progress.snapTo(1f)
    }

    val net = steps.lastOrNull { it.kind == WaterfallKind.TOTAL }?.amount ?: 0.0
    val summary = buildString {
        append("Income breakdown. ")
        steps.forEach { append("${it.label}: ${Money.format(it.amount, currency)}. ") }
        append(
            if (net >= 0) "Net income is positive."
            else "Net income is negative — this loses money after costs."
        )
    }

    Column(modifier.semantics { contentDescription = summary }) {
        steps.forEachIndexed { index, step ->
            val isTotal = step.kind == WaterfallKind.TOTAL
            val isDeduction = step.kind == WaterfallKind.DEDUCTION

            if (isTotal) {
                Spacer(Modifier.height(Space.xs))
                TotalRule()
                Spacer(Modifier.height(Space.sm))
            }

            val barColor = when {
                isTotal -> if (step.amount >= 0) MaterialTheme.colorScheme.primary else semantic.loss
                isDeduction -> semantic.loss
                else -> semantic.neutral
            }
            val amountColor = when {
                isTotal -> if (step.amount >= 0) MaterialTheme.colorScheme.onSurface else semantic.loss
                isDeduction -> semantic.loss
                else -> MaterialTheme.colorScheme.onSurface
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Space.lg + if (isDeduction) Space.lg else 0.dp,
                        end = Space.lg,
                        top = Space.sm,
                        bottom = Space.sm
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        step.label,
                        style = if (isTotal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        color = if (isTotal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (isDeduction) Money.format(step.amount, currency) else Money.format(step.amount, currency),
                        style = if (isTotal) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isTotal) FontWeight.SemiBold else FontWeight.Medium,
                        color = amountColor
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Proportional bar: the part of gross this line represents.
                val fraction = (abs(step.amount) / scale).coerceIn(0.0, 1.0).toFloat() * progress.value
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (isTotal) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(semantic.panelSunk)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }
                if (!isTotal && scale > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${Money.percent(abs(step.amount) / scale * 100, 0)} of gross",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantic.tertiaryText
                    )
                }
            }
            if (!isTotal && index < steps.lastIndex - 1) Hairline()
        }
    }
}

// ------------------------------------------------------------------- donut

@Composable
fun AllocationDonut(
    slices: List<Triple<String, Double, Color>>,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    centerLabel: String? = null
) {
    val semantic = PorticoTheme.semantic
    val progress = remember(slices, animate) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(slices, animate) {
        if (animate) { progress.snapTo(0f); progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        else progress.snapTo(1f)
    }

    val summary = "Allocation: " + slices.joinToString(", ") {
        "${it.first} ${Money.percent(it.second * 100, 0)}"
    }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().semantics { contentDescription = summary }) {
                val stroke = size.minDimension * 0.16f
                val inset = stroke / 2
                var start = -90f
                slices.forEach { (_, share, color) ->
                    val sweep = (share * 360f * progress.value).toFloat()
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = (sweep - 1.5f).coerceAtLeast(0f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                    start += sweep
                }
            }
            if (centerLabel != null) {
                Text(
                    centerLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            slices.forEach { (label, share, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        Money.percent(share * 100, 0),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/** Chart series colours: amber first, then neutral steps. Never a rainbow. */
@Composable
fun allocationPalette(count: Int): List<Color> {
    val primary = MaterialTheme.colorScheme.primary
    val semantic = PorticoTheme.semantic
    val base = listOf(
        primary,
        semantic.neutral,
        primary.copy(alpha = 0.55f),
        semantic.neutral.copy(alpha = 0.55f),
        primary.copy(alpha = 0.32f),
        semantic.neutral.copy(alpha = 0.32f)
    )
    return List(count) { base[it % base.size] }
}

// -------------------------------------------------------------------- bars

/**
 * Horizontal magnitude bar used in cashflow, comparison and usage views.
 *
 * [format] exists because the same bar carries money in one panel and a
 * percentage in the next; hard-coding the currency formatter here rendered a
 * 28.5% return as "$28".
 */
@Composable
fun MagnitudeBar(
    label: String,
    value: Double,
    maxValue: Double,
    modifier: Modifier = Modifier,
    currency: String = "USD",
    color: Color = MaterialTheme.colorScheme.primary,
    animate: Boolean = true,
    format: ((Double) -> String)? = null
) {
    val semantic = PorticoTheme.semantic
    val target = if (maxValue > 0) (abs(value) / maxValue).coerceIn(0.0, 1.0).toFloat() else 0f
    val progress = remember(value, maxValue, animate) { Animatable(if (animate) 0f else target) }
    LaunchedEffect(value, maxValue, animate) {
        if (animate) { progress.snapTo(0f); progress.animateTo(target, tween(650, easing = FastOutSlowInEasing)) }
        else progress.snapTo(target)
    }

    Column(modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                format?.invoke(value) ?: Money.format(value, currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (value < 0) semantic.loss else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(semantic.panelSunk)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.value)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

/** Vertical column chart for month-by-month cashflow, with a zero baseline. */
@Composable
fun CashflowColumns(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    currency: String = "USD"
) {
    val semantic = PorticoTheme.semantic
    if (values.isEmpty()) return
    val peak = values.maxOfOrNull { abs(it) }?.takeIf { it > 0 } ?: 1.0
    val hasNegative = values.any { it < 0 }

    val summary = "Monthly net cashflow. " + values.mapIndexed { i, v ->
        "${labels.getOrElse(i) { "" }} ${Money.format(v, currency)}"
    }.joinToString(", ")

    Column(modifier.semantics { contentDescription = summary }) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEachIndexed { index, value ->
                val fraction = (abs(value) / peak).coerceIn(0.0, 1.0).toFloat()
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Bottom,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Space above the zero line for positive bars.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(if (hasNegative) 0.62f else 1f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (value >= 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction)
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(semantic.gain)
                            )
                        }
                    }
                    if (hasNegative) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(0.38f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            if (value < 0) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(fraction)
                                        .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                        .background(semantic.loss)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            labels.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.tertiaryText,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
