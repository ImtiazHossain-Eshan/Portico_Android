package com.portico.android.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.portico.android.model.Property
import com.portico.android.ui.theme.PorticoLine
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoOrange
import com.portico.android.ui.theme.PorticoTeal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min
import kotlin.math.floor

fun formatCurrency(value: Double, compact: Boolean = false): String {
    if (compact && value >= 1_000_000) return "\$${(value / 1_000_000).formatOne()}M"
    if (compact && value >= 1_000) return "\$${(value / 1_000).formatOne()}k"
    return NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }.format(value)
}

private fun Double.formatOne(): String = "%.1f".format(Locale.US, this)

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    PorticoLogoMark(modifier = modifier.size(42.dp))
}

@Composable
fun SectionHeading(
    title: String,
    supporting: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (supporting != null) {
                Spacer(Modifier.height(4.dp))
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null && onAction != null) {
            Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onAction).padding(10.dp))
        }
    }
}

@Composable
fun RulePanel(modifier: Modifier = Modifier, emphasized: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.graphicsLayer { shadowElevation = if (emphasized) 14.dp.toPx() else 7.dp.toPx() },
        shape = RoundedCornerShape(if (emphasized) 24.dp else 20.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (emphasized) 3.dp else 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (emphasized) MaterialTheme.colorScheme.primary.copy(alpha = .34f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .9f)),
        content = content
    )
}

@Composable
fun MetricCell(label: String, value: String, supporting: String? = null, positive: Boolean? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = when (positive) {
                true -> PorticoTeal
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            })
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color = PorticoTeal, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(9.dp), color = color.copy(alpha = .12f), border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = .25f))) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun PlotPreview(property: Property, modifier: Modifier = Modifier) {
    val accent = Color(property.accent)
    val depth = Color(
        red = (accent.red * .60f).coerceIn(0f, 1f),
        green = (accent.green * .60f).coerceIn(0f, 1f),
        blue = (accent.blue * .60f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val surfaceColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .graphicsLayer { rotationX = 4f; rotationY = -4f; shadowElevation = 12.dp.toPx(); cameraDistance = 18f * density }
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription = "Isometric property scene for ${property.name}" }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val unit = min(width, height)
            val stroke = 1.8.dp.toPx()
            drawCircle(accent.copy(alpha = .10f), radius = unit * .36f, center = androidx.compose.ui.geometry.Offset(width * .5f, height * .42f))
            drawOval(
                color = Color.Black.copy(alpha = .08f),
                topLeft = androidx.compose.ui.geometry.Offset(width * .16f, height * .76f),
                size = androidx.compose.ui.geometry.Size(width * .68f, unit * .11f)
            )
            drawArc(
                color = accent.copy(alpha = .62f),
                startAngle = 202f + property.id * 11f,
                sweepAngle = 112f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(width * .12f, height * .10f),
                size = androidx.compose.ui.geometry.Size(width * .76f, height * .70f),
                style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
            )
            when ((property.id - 1).mod(4)) {
                0 -> {
                    val front = Path().apply {
                        moveTo(width * .18f, height * .46f)
                        lineTo(width * .60f, height * .40f)
                        lineTo(width * .60f, height * .76f)
                        lineTo(width * .18f, height * .82f)
                        close()
                    }
                    val side = Path().apply {
                        moveTo(width * .60f, height * .40f)
                        lineTo(width * .84f, height * .28f)
                        lineTo(width * .84f, height * .65f)
                        lineTo(width * .60f, height * .76f)
                        close()
                    }
                    val roof = Path().apply {
                        moveTo(width * .14f, height * .46f)
                        lineTo(width * .47f, height * .16f)
                        lineTo(width * .84f, height * .28f)
                        lineTo(width * .60f, height * .40f)
                        close()
                    }
                    drawPath(side, color = depth.copy(alpha = .86f))
                    drawPath(front, color = accent.copy(alpha = .78f))
                    drawPath(roof, color = accent)
                    drawPath(front, color = accent, style = Stroke(width = stroke, join = StrokeJoin.Round))
                    drawLine(accent.copy(alpha = .70f), androidx.compose.ui.geometry.Offset(width * .47f, height * .16f), androidx.compose.ui.geometry.Offset(width * .47f, height * .72f), 1.dp.toPx())
                    drawRoundRect(surfaceColor.copy(alpha = .86f), topLeft = androidx.compose.ui.geometry.Offset(width * .29f, height * .53f), size = androidx.compose.ui.geometry.Size(width * .12f, height * .14f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                    drawRoundRect(surfaceColor.copy(alpha = .64f), topLeft = androidx.compose.ui.geometry.Offset(width * .49f, height * .50f), size = androidx.compose.ui.geometry.Size(width * .08f, height * .12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                    drawCircle(accent, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(width * .74f, height * .44f))
                }
                1 -> {
                    val left = Path().apply {
                        moveTo(width * .12f, height * .50f)
                        lineTo(width * .36f, height * .36f)
                        lineTo(width * .36f, height * .76f)
                        lineTo(width * .12f, height * .82f)
                        close()
                    }
                    val right = Path().apply {
                        moveTo(width * .36f, height * .44f)
                        lineTo(width * .63f, height * .30f)
                        lineTo(width * .63f, height * .70f)
                        lineTo(width * .36f, height * .76f)
                        close()
                    }
                    val leftRoof = Path().apply {
                        moveTo(width * .08f, height * .50f)
                        lineTo(width * .25f, height * .25f)
                        lineTo(width * .47f, height * .38f)
                        lineTo(width * .36f, height * .44f)
                        close()
                    }
                    val rightRoof = Path().apply {
                        moveTo(width * .36f, height * .44f)
                        lineTo(width * .52f, height * .18f)
                        lineTo(width * .80f, height * .32f)
                        lineTo(width * .63f, height * .42f)
                        close()
                    }
                    drawPath(left, color = accent.copy(alpha = .58f))
                    drawPath(right, color = depth.copy(alpha = .86f))
                    drawPath(leftRoof, color = accent)
                    drawPath(rightRoof, color = accent.copy(alpha = .86f))
                    drawLine(accent.copy(alpha = .65f), androidx.compose.ui.geometry.Offset(width * .36f, height * .38f), androidx.compose.ui.geometry.Offset(width * .36f, height * .76f), 1.dp.toPx())
                    repeat(2) { index ->
                        drawRoundRect(surfaceColor.copy(alpha = .85f), topLeft = androidx.compose.ui.geometry.Offset(width * (.19f + index * .22f), height * .53f), size = androidx.compose.ui.geometry.Size(width * .08f, height * .12f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                    }
                    drawCircle(accent, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(width * .74f, height * .52f))
                }
                2 -> {
                    val tower = Path().apply {
                        moveTo(width * .28f, height * .26f)
                        lineTo(width * .66f, height * .18f)
                        lineTo(width * .66f, height * .78f)
                        lineTo(width * .28f, height * .84f)
                        close()
                    }
                    val towerSide = Path().apply {
                        moveTo(width * .66f, height * .18f)
                        lineTo(width * .84f, height * .28f)
                        lineTo(width * .84f, height * .70f)
                        lineTo(width * .66f, height * .78f)
                        close()
                    }
                    drawPath(towerSide, color = depth.copy(alpha = .86f))
                    drawPath(tower, color = accent.copy(alpha = .72f))
                    drawLine(accent, androidx.compose.ui.geometry.Offset(width * .28f, height * .26f), androidx.compose.ui.geometry.Offset(width * .66f, height * .18f), stroke, cap = StrokeCap.Round)
                    repeat(3) { row ->
                        repeat(2) { column ->
                            drawRoundRect(surfaceColor.copy(alpha = if (row == 1) .92f else .72f), topLeft = androidx.compose.ui.geometry.Offset(width * (.36f + column * .15f), height * (.34f + row * .14f)), size = androidx.compose.ui.geometry.Size(width * .08f, height * .07f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()))
                        }
                    }
                    drawCircle(accent, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(width * .76f, height * .48f))
                }
                else -> {
                    val rear = Path().apply {
                        moveTo(width * .16f, height * .42f)
                        lineTo(width * .52f, height * .24f)
                        lineTo(width * .84f, height * .38f)
                        lineTo(width * .52f, height * .54f)
                        close()
                    }
                    val leftWing = Path().apply {
                        moveTo(width * .16f, height * .42f)
                        lineTo(width * .34f, height * .52f)
                        lineTo(width * .34f, height * .78f)
                        lineTo(width * .16f, height * .68f)
                        close()
                    }
                    val rightWing = Path().apply {
                        moveTo(width * .52f, height * .54f)
                        lineTo(width * .84f, height * .38f)
                        lineTo(width * .84f, height * .68f)
                        lineTo(width * .52f, height * .78f)
                        close()
                    }
                    drawPath(rear, color = accent.copy(alpha = .72f))
                    drawPath(leftWing, color = accent)
                    drawPath(rightWing, color = depth.copy(alpha = .86f))
                    drawCircle(surfaceColor.copy(alpha = .84f), radius = unit * .12f, center = androidx.compose.ui.geometry.Offset(width * .50f, height * .56f))
                    drawCircle(accent, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(width * .50f, height * .56f))
                    drawLine(accent.copy(alpha = .70f), androidx.compose.ui.geometry.Offset(width * .52f, height * .24f), androidx.compose.ui.geometry.Offset(width * .52f, height * .54f), 1.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun LegacyPlotPreview(property: Property, modifier: Modifier = Modifier) {
    val accent = Color(property.accent)
    val surfaceColor = MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .graphicsLayer { rotationX = 8f; rotationY = -6f; shadowElevation = 10.dp.toPx(); cameraDistance = 14f * density }
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface)))
            .semantics { contentDescription = "Three dimensional property preview for ${property.name}" }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val grid = 20.dp.toPx()
            var x = 0f
            while (x < size.width + grid) {
                drawLine(PorticoLine.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x - size.height * .22f, size.height), 1f)
                x += grid
            }
            when ((property.id - 1).mod(4)) {
                0 -> {
                    val base = Path().apply {
                        moveTo(size.width * .16f, size.height * .68f)
                        lineTo(size.width * .28f, size.height * .34f)
                        lineTo(size.width * .72f, size.height * .30f)
                        lineTo(size.width * .86f, size.height * .68f)
                        lineTo(size.width * .78f, size.height * .84f)
                        lineTo(size.width * .22f, size.height * .84f)
                        close()
                    }
                    drawPath(base, brush = Brush.linearGradient(listOf(accent.copy(alpha = .32f), accent.copy(alpha = .08f))))
                    drawPath(base, color = accent, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
                    drawLine(accent.copy(alpha = .78f), androidx.compose.ui.geometry.Offset(size.width * .28f, size.height * .34f), androidx.compose.ui.geometry.Offset(size.width * .72f, size.height * .30f), 1.5.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(accent.copy(alpha = .62f), androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .30f), androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .84f), 1.2.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(surfaceColor, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .58f, size.height * .50f))
                    drawCircle(accent, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .58f, size.height * .50f))
                    drawLine(accent.copy(alpha = .80f), androidx.compose.ui.geometry.Offset(size.width * .70f, size.height * .30f), androidx.compose.ui.geometry.Offset(size.width * .92f, size.height * .12f), 1.5.dp.toPx(), cap = StrokeCap.Round)
                }
                1 -> {
                    val duplex = Path().apply {
                        moveTo(size.width * .12f, size.height * .78f)
                        lineTo(size.width * .12f, size.height * .44f)
                        lineTo(size.width * .34f, size.height * .24f)
                        lineTo(size.width * .56f, size.height * .44f)
                        lineTo(size.width * .56f, size.height * .78f)
                        close()
                    }
                    drawPath(duplex, brush = Brush.linearGradient(listOf(accent.copy(alpha = .28f), accent.copy(alpha = .07f))))
                    drawPath(duplex, color = accent, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
                    drawLine(accent, androidx.compose.ui.geometry.Offset(size.width * .56f, size.height * .44f), androidx.compose.ui.geometry.Offset(size.width * .78f, size.height * .26f), 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(accent, androidx.compose.ui.geometry.Offset(size.width * .78f, size.height * .26f), androidx.compose.ui.geometry.Offset(size.width * .92f, size.height * .40f), 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(accent.copy(alpha = .65f), androidx.compose.ui.geometry.Offset(size.width * .34f, size.height * .24f), androidx.compose.ui.geometry.Offset(size.width * .34f, size.height * .78f), 1.dp.toPx())
                    drawCircle(surfaceColor, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .70f, size.height * .56f))
                    drawCircle(accent, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .70f, size.height * .56f))
                }
                2 -> {
                    val building = androidx.compose.ui.geometry.Rect(size.width * .25f, size.height * .18f, size.width * .75f, size.height * .82f)
                    drawRoundRect(accent.copy(alpha = .14f), topLeft = building.topLeft, size = building.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()))
                    drawRoundRect(accent, topLeft = building.topLeft, size = building.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
                    repeat(2) { row ->
                        val y = size.height * (.38f + row * .20f)
                        drawLine(accent.copy(alpha = .72f), androidx.compose.ui.geometry.Offset(size.width * .25f, y), androidx.compose.ui.geometry.Offset(size.width * .75f, y), 1.dp.toPx())
                    }
                    repeat(2) { column ->
                        val x = size.width * (.42f + column * .16f)
                        drawLine(accent.copy(alpha = .72f), androidx.compose.ui.geometry.Offset(x, size.height * .18f), androidx.compose.ui.geometry.Offset(x, size.height * .82f), 1.dp.toPx())
                    }
                    drawLine(accent, androidx.compose.ui.geometry.Offset(size.width * .72f, size.height * .18f), androidx.compose.ui.geometry.Offset(size.width * .92f, size.height * .06f), 1.5.dp.toPx(), cap = StrokeCap.Round)
                }
                else -> {
                    val lot = Path().apply {
                        moveTo(size.width * .14f, size.height * .56f)
                        lineTo(size.width * .50f, size.height * .18f)
                        lineTo(size.width * .86f, size.height * .56f)
                        lineTo(size.width * .66f, size.height * .84f)
                        lineTo(size.width * .34f, size.height * .84f)
                        close()
                    }
                    drawPath(lot, brush = Brush.linearGradient(listOf(accent.copy(alpha = .30f), accent.copy(alpha = .08f))))
                    drawPath(lot, color = accent, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
                    drawLine(accent.copy(alpha = .70f), androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .18f), androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .72f), 1.2.dp.toPx())
                    drawCircle(surfaceColor, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .52f))
                    drawCircle(accent, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .52f))
                    drawLine(accent, androidx.compose.ui.geometry.Offset(size.width * .66f, size.height * .34f), androidx.compose.ui.geometry.Offset(size.width * .92f, size.height * .12f), 1.5.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Text("PLOT ${property.id.toString().padStart(2, '0')}", modifier = Modifier.align(Alignment.BottomStart).padding(9.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TrendChart(modifier: Modifier = Modifier, positive: Boolean = true, period: String = "1Y") {
    val points = when (period) {
        "1M" -> listOf(.38f, .42f, .40f, .48f, .55f, .63f)
        "6M" -> listOf(.24f, .31f, .29f, .43f, .46f, .58f, .64f, .72f)
        "5Y" -> listOf(.10f, .16f, .14f, .25f, .30f, .38f, .43f, .52f, .58f, .66f, .71f, .82f)
        "All" -> listOf(.08f, .12f, .11f, .20f, .18f, .30f, .36f, .34f, .49f, .57f, .54f, .78f)
        else -> listOf(.12f, .18f, .15f, .28f, .25f, .36f, .43f, .41f, .55f, .62f, .58f, .78f)
    }
    val lineColor = if (positive) PorticoTeal else MaterialTheme.colorScheme.error
    val motion = LocalPorticoMotion.current
    val reveal = remember(positive, period) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(positive, period, motion) {
        if (motion) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, animationSpec = tween(1100, easing = FastOutSlowInEasing))
        } else {
            reveal.snapTo(1f)
        }
    }
    Canvas(modifier.semantics { contentDescription = if (positive) "Portfolio value trend rising over the selected period" else "Portfolio value trend falling over the selected period" }) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val bottom = size.height - 9.dp.toPx()
        val top = 10.dp.toPx()
        repeat(4) { index ->
            val y = top + (bottom - top) * index / 3f
            drawLine(PorticoLine.copy(alpha = .4f), androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y), 1f)
        }
        val path = Path()
        val visibleIndex = points.lastIndex * reveal.value.coerceIn(0f, 1f)
        val lastVisible = floor(visibleIndex).toInt().coerceIn(1, points.lastIndex)
        points.take(lastVisible + 1).forEachIndexed { index, point ->
            val x = left + (right - left) * index / (points.lastIndex).coerceAtLeast(1)
            val y = bottom - point * (bottom - top)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val markerX: Float
        val markerY: Float
        if (lastVisible < points.lastIndex) {
            val fraction = visibleIndex - lastVisible
            val nextIndex = lastVisible + 1
            val nextX = left + (right - left) * nextIndex / (points.lastIndex).coerceAtLeast(1)
            val currentX = left + (right - left) * lastVisible / (points.lastIndex).coerceAtLeast(1)
            val currentY = bottom - points[lastVisible] * (bottom - top)
            val nextY = bottom - points[nextIndex] * (bottom - top)
            markerX = currentX + (nextX - currentX) * fraction
            markerY = currentY + (nextY - currentY) * fraction
            path.lineTo(markerX, markerY)
        } else {
            markerX = right
            markerY = bottom - points.last() * (bottom - top)
        }
        drawPath(path, color = lineColor.copy(alpha = .12f), style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color = lineColor, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(lineColor, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(markerX, markerY))
    }
}

@Composable
fun AllocationDonut(slices: List<com.portico.android.model.AllocationSlice>, modifier: Modifier = Modifier) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val motion = LocalPorticoMotion.current
    val reveal = remember(slices, motion) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(slices, motion) {
        if (motion) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
        } else {
            reveal.snapTo(1f)
        }
    }
    Canvas(modifier = modifier.semantics { contentDescription = "Portfolio allocation by location" }) {
        val stroke = min(size.width, size.height) * .17f
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = slice.value * 360f * reveal.value.coerceIn(0f, 1f)
            drawArc(Color(slice.color), startAngle, (sweep - 2f).coerceAtLeast(0f), false, style = Stroke(width = stroke))
            startAngle += sweep
        }
        drawCircle(surfaceColor, radius = min(size.width, size.height) * .26f)
        drawCircle(PorticoTeal.copy(alpha = .18f), radius = min(size.width, size.height) * .08f)
    }
}

@Composable
fun IconActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) { Icon(icon, contentDescription = null) }
}

@Composable
fun SmallArrow(modifier: Modifier = Modifier) {
    Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = modifier.size(18.dp), tint = PorticoMuted)
}

@Composable
fun EditButton(onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant))
}
