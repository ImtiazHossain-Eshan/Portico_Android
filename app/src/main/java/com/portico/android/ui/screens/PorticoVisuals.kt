package com.portico.android.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.portico.android.ui.theme.PorticoLine
import com.portico.android.ui.theme.PorticoLilac
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoOrange
import com.portico.android.ui.theme.PorticoTeal
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.sp
import com.portico.android.R

val LocalPorticoMotion = compositionLocalOf { true }

enum class PorticoGlyphType {
    Overview, Portfolio, Reports, Assistant, Profile, Add, Documents, Tax, Valuation,
    Back, More, Notify, Search, Forward, Property, Income, Expense, Update, Location,
    Check, Lock, Cloud, People, Settings, Logout, Upload, Warning, Calendar, Business,
    Security
}

@Composable
fun PorticoMotionProvider(enabled: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPorticoMotion provides enabled, content = content)
}

@Composable
fun PorticoLogoMark(modifier: Modifier = Modifier, contentDescription: String = "Portico") {
    val motion = LocalPorticoMotion.current
    val transition = rememberInfiniteTransition(label = "portico-logo")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (motion) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "logo-orbit"
    )
    val scale by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = if (motion) 1.015f else 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "logo-breathe"
    )
    Image(
        painter = painterResource(R.drawable.portico_logo_mark_clean),
        contentDescription = contentDescription,
        modifier = modifier
            .graphicsLayer {
                rotationZ = if (motion) rotation else 0f
                scaleX = scale
                scaleY = scale
            }
    )
}

@Composable
fun PorticoLogoLockup(modifier: Modifier = Modifier, showTagline: Boolean = true) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        PorticoLogoMark(Modifier.size(50.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("PORTICO", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            if (showTagline) Text("INVESTMENT FLIGHT DECK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .68f), letterSpacing = 1.2.sp)
        }
    }
}

@Composable
fun CockpitBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .background(Brush.linearGradient(listOf(scheme.background, scheme.surfaceVariant.copy(alpha = .55f), scheme.background)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(listOf(scheme.primary.copy(alpha = .12f), Color.Transparent)),
                radius = size.maxDimension * .70f,
                center = androidx.compose.ui.geometry.Offset(size.width * .84f, size.height * .08f)
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(scheme.secondary.copy(alpha = .10f), Color.Transparent)),
                radius = size.maxDimension * .58f,
                center = androidx.compose.ui.geometry.Offset(size.width * .04f, size.height * .76f)
            )
            val lineColor = scheme.outlineVariant.copy(alpha = .24f)
            for (index in 1..8) {
                val y = size.height * index / 9f
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
            }
            for (index in 1..5) {
                val x = size.width * index / 6f
                drawLine(lineColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f)
            }
        }
        content()
    }
}

@Composable
fun PortfolioOrbit(modifier: Modifier = Modifier, progress: Float = .76f, label: String = "LIVE PORTFOLIO SCAN") {
    val scheme = MaterialTheme.colorScheme
    val motion = LocalPorticoMotion.current
    val transition = rememberInfiniteTransition(label = "portfolio-orbit")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (motion) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit-phase"
    )
    Box(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .graphicsLayer {
                rotationX = if (motion) 9f else 0f
                rotationY = if (motion) -12f else 0f
                cameraDistance = 18f * density
            }
            .semantics { contentDescription = "Animated portfolio scan at ${(progress * 100).toInt()} percent" }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val orbitUnit = minOf(size.width, size.height)
            val radius = orbitUnit * .25f
            val horizontalOrbit = androidx.compose.ui.geometry.Size(orbitUnit * .72f, orbitUnit * .20f)
            val verticalOrbit = androidx.compose.ui.geometry.Size(orbitUnit * .30f, orbitUnit * .66f)
            val horizontalTopLeft = androidx.compose.ui.geometry.Offset(center.x - horizontalOrbit.width / 2f, center.y - horizontalOrbit.height / 2f)
            val verticalTopLeft = androidx.compose.ui.geometry.Offset(center.x - verticalOrbit.width / 2f, center.y - verticalOrbit.height / 2f)
            drawCircle(brush = Brush.radialGradient(listOf(scheme.primary.copy(alpha = .25f), Color.Transparent)), radius = radius * 1.65f, center = center)
            rotate(phase, center) {
                drawOval(
                    color = scheme.primary.copy(alpha = .70f),
                    topLeft = horizontalTopLeft,
                    size = horizontalOrbit,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawArc(scheme.tertiary, -18f, 72f, false, topLeft = horizontalTopLeft, size = horizontalOrbit, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            rotate(-phase * .55f, center) {
                drawOval(
                    color = scheme.secondary.copy(alpha = .60f),
                    topLeft = verticalTopLeft,
                    size = verticalOrbit,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawArc(scheme.primary, 126f, 44f, false, topLeft = verticalTopLeft, size = verticalOrbit, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            val core = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius * .74f, center.y - radius * .18f)
                lineTo(center.x + radius * .54f, center.y + radius * .72f)
                lineTo(center.x, center.y + radius)
                lineTo(center.x - radius * .54f, center.y + radius * .72f)
                lineTo(center.x - radius * .74f, center.y - radius * .18f)
                close()
            }
            drawPath(core, brush = Brush.linearGradient(listOf(scheme.surfaceVariant, scheme.surface)))
            drawPath(core, color = scheme.primary.copy(alpha = .82f), style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))
            drawLine(scheme.primary, androidx.compose.ui.geometry.Offset(center.x, center.y - radius), androidx.compose.ui.geometry.Offset(center.x, center.y + radius), 1.dp.toPx())
            drawLine(scheme.secondary.copy(alpha = .72f), androidx.compose.ui.geometry.Offset(center.x - radius * .74f, center.y - radius * .18f), androidx.compose.ui.geometry.Offset(center.x + radius * .74f, center.y - radius * .18f), 1.dp.toPx())
            repeat(6) { index ->
                val angle = (phase + index * 60f) * (Math.PI / 180f)
                val dot = androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat() * orbitUnit * .36f, center.y + sin(angle).toFloat() * orbitUnit * .36f)
                drawCircle(if (index % 3 == 0) scheme.tertiary else scheme.primary, radius = 3.dp.toPx(), center = dot)
            }
        }
        Text(label, modifier = Modifier.align(Alignment.BottomCenter), style = MaterialTheme.typography.labelSmall, color = PorticoMuted, letterSpacing = 1.1.sp)
    }
}

@Composable
fun GaugeMetric(label: String, value: String, detail: String, progress: Float, accent: Color = PorticoTeal, modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(
        modifier = modifier.graphicsLayer { shadowElevation = 10.dp.toPx(); rotationX = 3f },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .28f))
    ) {
        Column(modifier = Modifier.padding(if (compact) 9.dp else 14.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
            Canvas(Modifier.fillMaxWidth().height(if (compact) 46.dp else 58.dp)) {
                val stroke = 5.dp.toPx()
                val left = stroke
                val top = stroke
                val diameter = minOf(size.width, size.height) - stroke * 2
                drawArc(PorticoLine.copy(alpha = .75f), 145f, 250f, false, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(diameter, diameter), style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawArc(accent, 145f, 250f * progress.coerceIn(0f, 1f), false, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(diameter, diameter), style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawCircle(accent, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .54f))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = PorticoMuted, maxLines = 1)
            Text(value, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = accent, maxLines = 1)
        }
    }
}

private enum class PorticoButtonTone { Primary, Tonal, Outline }

@Composable
private fun PorticoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: PorticoButtonTone,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = when (tone) {
                PorticoButtonTone.Primary -> scheme.primary
                PorticoButtonTone.Tonal -> scheme.secondaryContainer
                PorticoButtonTone.Outline -> scheme.surface
            },
            contentColor = when (tone) {
                PorticoButtonTone.Primary -> scheme.onPrimary
                PorticoButtonTone.Tonal -> scheme.onSecondaryContainer
                PorticoButtonTone.Outline -> scheme.onSurface
            },
            disabledContainerColor = scheme.surfaceVariant,
            disabledContentColor = scheme.onSurface.copy(alpha = .45f)
        ),
        border = if (tone == PorticoButtonTone.Outline) BorderStroke(1.dp, scheme.outline) else null,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, contentPadding: PaddingValues = ButtonDefaults.ContentPadding, content: @Composable RowScope.() -> Unit) =
    PorticoButton(onClick, modifier, enabled, PorticoButtonTone.Primary, contentPadding, content)

@Composable
fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, contentPadding: PaddingValues = ButtonDefaults.ContentPadding, content: @Composable RowScope.() -> Unit) =
    PorticoButton(onClick, modifier, enabled, PorticoButtonTone.Tonal, contentPadding, content)

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, contentPadding: PaddingValues = ButtonDefaults.ContentPadding, content: @Composable RowScope.() -> Unit) =
    PorticoButton(onClick, modifier, enabled, PorticoButtonTone.Outline, contentPadding, content)

@Composable
fun PorticoGlyph(type: PorticoGlyphType, modifier: Modifier = Modifier.size(22.dp), tint: Color = MaterialTheme.colorScheme.onSurface, contentDescription: String? = null) {
    Canvas(modifier.semantics { if (contentDescription != null) this.contentDescription = contentDescription }) {
        val stroke = maxOf(1.6f, size.minDimension * .095f)
        val pad = size.minDimension * .18f
        val left = pad
        val right = size.width - pad
        val top = pad
        val bottom = size.height - pad
        val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, androidx.compose.ui.geometry.Offset(x1, y1), androidx.compose.ui.geometry.Offset(x2, y2), stroke, StrokeCap.Round)
        when (type) {
            PorticoGlyphType.Overview -> { line(left, bottom, left, top + 8f); line(left, bottom, right, bottom); line(left + 4f, bottom - 4f, size.width * .46f, size.height * .50f); line(size.width * .46f, size.height * .50f, right, top + 3f) }
            PorticoGlyphType.Portfolio, PorticoGlyphType.Property -> { val p = Path().apply { moveTo(center.x, top); lineTo(right, center.y - 3f); lineTo(size.width * .82f, bottom); lineTo(size.width * .18f, bottom); lineTo(left, center.y - 3f); close() }; drawPath(p, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round)); line(center.x, top, center.x, bottom); line(left + 4f, center.y, right - 4f, center.y) }
            PorticoGlyphType.Reports -> { line(left, bottom, left, top); line(left, bottom, right, bottom); drawRoundRect(tint.copy(alpha = .78f), androidx.compose.ui.geometry.Offset(size.width * .30f, size.height * .58f), androidx.compose.ui.geometry.Size(size.width * .12f, size.height * .28f), CornerRadius(2f, 2f)); drawRoundRect(tint, androidx.compose.ui.geometry.Offset(size.width * .52f, size.height * .38f), androidx.compose.ui.geometry.Size(size.width * .12f, size.height * .48f), CornerRadius(2f, 2f)); drawRoundRect(tint.copy(alpha = .66f), androidx.compose.ui.geometry.Offset(size.width * .74f, size.height * .24f), androidx.compose.ui.geometry.Size(size.width * .12f, size.height * .62f), CornerRadius(2f, 2f)) }
            PorticoGlyphType.Assistant -> { drawCircle(tint, radius = size.minDimension * .22f, center = center); for (index in 0..3) { val angle = index * Math.PI / 2.0; line(center.x + cos(angle).toFloat() * size.minDimension * .34f, center.y + sin(angle).toFloat() * size.minDimension * .34f, center.x + cos(angle).toFloat() * size.minDimension * .47f, center.y + sin(angle).toFloat() * size.minDimension * .47f) } }
            PorticoGlyphType.Profile -> { drawCircle(tint, radius = size.minDimension * .14f, center = androidx.compose.ui.geometry.Offset(center.x, size.height * .31f)); drawArc(tint, 205f, 130f, false, topLeft = androidx.compose.ui.geometry.Offset(size.width * .20f, size.height * .37f), size = androidx.compose.ui.geometry.Size(size.width * .60f, size.height * .48f), style = Stroke(width = stroke, cap = StrokeCap.Round)) }
            PorticoGlyphType.Add -> { line(center.x, top, center.x, bottom); line(left, center.y, right, center.y) }
            PorticoGlyphType.Documents -> { drawRoundRect(tint, androidx.compose.ui.geometry.Offset(left + 2f, top), androidx.compose.ui.geometry.Size(size.width * .60f, size.height * .76f), CornerRadius(4f, 4f), style = Stroke(width = stroke)); line(size.width * .54f, top, size.width * .76f, size.height * .20f); line(size.width * .54f, top, size.width * .54f, size.height * .20f); line(size.width * .30f, size.height * .50f, size.width * .64f, size.height * .50f); line(size.width * .30f, size.height * .68f, size.width * .58f, size.height * .68f) }
            PorticoGlyphType.Tax -> { line(left, bottom, right, bottom); line(left + 3f, top + 3f, right - 2f, top + 3f); line(left + 4f, size.height * .34f, right - 4f, size.height * .34f); line(center.x, top, center.x, bottom); drawCircle(tint, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .31f, size.height * .53f)); drawCircle(tint, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * .69f, size.height * .76f)) }
            PorticoGlyphType.Valuation -> { drawCircle(tint, radius = size.minDimension * .28f, center = center, style = Stroke(width = stroke)); drawCircle(tint, radius = 3.dp.toPx(), center = center); line(center.x, top, center.x, top + 7f); line(center.x, bottom - 7f, center.x, bottom); line(left, center.y, left + 7f, center.y); line(right - 7f, center.y, right, center.y) }
            PorticoGlyphType.Back -> { line(right, center.y, left + 3f, center.y); line(left + 3f, center.y, size.width * .34f, size.height * .30f); line(left + 3f, center.y, size.width * .34f, size.height * .70f) }
            PorticoGlyphType.More -> { drawCircle(tint, 2.3.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width * .25f, center.y)); drawCircle(tint, 2.3.dp.toPx(), center); drawCircle(tint, 2.3.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width * .75f, center.y)) }
            PorticoGlyphType.Notify -> { drawArc(tint, 200f, 140f, false, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(size.width - pad * 2f, size.height * .72f), style = Stroke(width = stroke, cap = StrokeCap.Round)); line(size.width * .25f, size.height * .74f, size.width * .75f, size.height * .74f); drawCircle(tint, 2.dp.toPx(), androidx.compose.ui.geometry.Offset(center.x, bottom - 1f)) }
            PorticoGlyphType.Search -> { drawCircle(tint, radius = size.minDimension * .27f, center = androidx.compose.ui.geometry.Offset(size.width * .40f, size.height * .40f), style = Stroke(width = stroke)); line(size.width * .60f, size.height * .60f, right, bottom) }
            PorticoGlyphType.Forward -> { line(left, center.y, right - 2f, center.y); line(right - 2f, center.y, size.width * .65f, size.height * .30f); line(right - 2f, center.y, size.width * .65f, size.height * .70f) }
            PorticoGlyphType.Income -> { line(center.x, bottom, center.x, top + 4f); line(center.x, top + 4f, size.width * .32f, size.height * .30f); line(center.x, top + 4f, size.width * .68f, size.height * .30f) }
            PorticoGlyphType.Expense -> { line(center.x, top, center.x, bottom - 4f); line(center.x, bottom - 4f, size.width * .32f, size.height * .70f); line(center.x, bottom - 4f, size.width * .68f, size.height * .70f) }
            PorticoGlyphType.Update -> { drawArc(tint, -50f, 260f, false, topLeft = androidx.compose.ui.geometry.Offset(left, top), size = androidx.compose.ui.geometry.Size(size.width - pad * 2f, size.height - pad * 2f), style = Stroke(width = stroke, cap = StrokeCap.Round)); line(right - 1f, top + 5f, right - 2f, size.height * .30f); line(right - 1f, top + 5f, size.width * .70f, top + 6f) }
            PorticoGlyphType.Location -> { val p = Path().apply { moveTo(center.x, bottom); cubicTo(left, size.height * .58f, left + 2f, top, center.x, top); cubicTo(right - 2f, top, right, size.height * .58f, center.x, bottom); close() }; drawPath(p, color = tint, style = Stroke(width = stroke)); drawCircle(tint, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(center.x, size.height * .34f)) }
            PorticoGlyphType.Check -> { line(left, center.y, size.width * .40f, bottom - 3f); line(size.width * .40f, bottom - 3f, right, top + 3f) }
            PorticoGlyphType.Lock -> { drawRoundRect(tint, androidx.compose.ui.geometry.Offset(left, size.height * .40f), androidx.compose.ui.geometry.Size(size.width - pad * 2f, size.height * .43f), CornerRadius(4f, 4f), style = Stroke(width = stroke)); drawArc(tint, 180f, 180f, false, topLeft = androidx.compose.ui.geometry.Offset(size.width * .28f, top), size = androidx.compose.ui.geometry.Size(size.width * .44f, size.height * .55f), style = Stroke(width = stroke)); drawCircle(tint, 2.dp.toPx(), androidx.compose.ui.geometry.Offset(center.x, size.height * .60f)) }
            PorticoGlyphType.Cloud -> { drawArc(tint, 180f, 180f, false, topLeft = androidx.compose.ui.geometry.Offset(left, size.height * .30f), size = androidx.compose.ui.geometry.Size(size.width * .65f, size.height * .50f), style = Stroke(width = stroke)); drawArc(tint, 180f, 180f, false, topLeft = androidx.compose.ui.geometry.Offset(size.width * .30f, size.height * .18f), size = androidx.compose.ui.geometry.Size(size.width * .55f, size.height * .58f), style = Stroke(width = stroke)); line(size.width * .18f, size.height * .70f, size.width * .84f, size.height * .70f) }
            PorticoGlyphType.People -> { drawCircle(tint, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width * .36f, size.height * .30f)); drawCircle(tint, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width * .66f, size.height * .34f)); drawArc(tint, 200f, 140f, false, topLeft = androidx.compose.ui.geometry.Offset(size.width * .12f, size.height * .37f), size = androidx.compose.ui.geometry.Size(size.width * .52f, size.height * .46f), style = Stroke(width = stroke)); drawArc(tint, 200f, 140f, false, topLeft = androidx.compose.ui.geometry.Offset(size.width * .42f, size.height * .40f), size = androidx.compose.ui.geometry.Size(size.width * .46f, size.height * .40f), style = Stroke(width = stroke)) }
            PorticoGlyphType.Settings -> { drawCircle(tint, radius = size.minDimension * .22f, center = center, style = Stroke(width = stroke)); drawCircle(tint, radius = 2.5.dp.toPx(), center = center); for (index in 0..3) { val angle = index * Math.PI / 2.0; line(center.x + cos(angle).toFloat() * size.minDimension * .28f, center.y + sin(angle).toFloat() * size.minDimension * .28f, center.x + cos(angle).toFloat() * size.minDimension * .45f, center.y + sin(angle).toFloat() * size.minDimension * .45f) } }
            PorticoGlyphType.Logout -> { line(left, center.y, right - 4f, center.y); line(right - 4f, center.y, size.width * .66f, size.height * .30f); line(right - 4f, center.y, size.width * .66f, size.height * .70f); line(left + 3f, top, left + 3f, bottom) }
            PorticoGlyphType.Upload -> { line(center.x, bottom, center.x, top + 4f); line(center.x, top + 4f, size.width * .30f, size.height * .30f); line(center.x, top + 4f, size.width * .70f, size.height * .30f); line(left, bottom, right, bottom) }
            PorticoGlyphType.Warning -> { val p = Path().apply { moveTo(center.x, top); lineTo(right, bottom); lineTo(left, bottom); close() }; drawPath(p, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round)); line(center.x, size.height * .38f, center.x, size.height * .66f); drawCircle(tint, 1.5.dp.toPx(), androidx.compose.ui.geometry.Offset(center.x, size.height * .78f)) }
            PorticoGlyphType.Calendar -> { drawRoundRect(tint, androidx.compose.ui.geometry.Offset(left, top + 4f), androidx.compose.ui.geometry.Size(size.width - pad * 2f, size.height - pad * 2f - 4f), CornerRadius(4f, 4f), style = Stroke(width = stroke)); line(left, size.height * .34f, right, size.height * .34f); line(size.width * .32f, top, size.width * .32f, top + 8f); line(size.width * .68f, top, size.width * .68f, top + 8f) }
            PorticoGlyphType.Business -> { drawRoundRect(tint, androidx.compose.ui.geometry.Offset(left, size.height * .30f), androidx.compose.ui.geometry.Size(size.width - pad * 2f, size.height * .60f), CornerRadius(3f, 3f), style = Stroke(width = stroke)); line(size.width * .40f, size.height * .30f, size.width * .40f, top); line(size.width * .40f, top, size.width * .60f, top); line(size.width * .60f, top, size.width * .60f, size.height * .30f); line(size.width * .35f, size.height * .50f, size.width * .35f, size.height * .64f); line(size.width * .50f, size.height * .50f, size.width * .50f, size.height * .64f); line(size.width * .65f, size.height * .50f, size.width * .65f, size.height * .64f) }
            PorticoGlyphType.Security -> { val p = Path().apply { moveTo(center.x, top); lineTo(right, size.height * .28f); lineTo(size.width * .78f, size.height * .74f); lineTo(center.x, bottom); lineTo(size.width * .22f, size.height * .74f); lineTo(left, size.height * .28f); close() }; drawPath(p, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round)); line(center.x, size.height * .30f, center.x, size.height * .70f) }
        }
    }
}

@Composable
fun InstrumentBadge(label: String, value: String, accent: Color = PorticoTeal, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = accent.copy(alpha = .10f), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .24f))) {
        Row(modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
                Text(value, style = MaterialTheme.typography.labelLarge, color = accent)
            }
        }
    }
}
