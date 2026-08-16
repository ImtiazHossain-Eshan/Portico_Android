package com.portico.android.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * One drawn icon language for the whole app.
 *
 * Every glyph is built on a 24-unit grid at a single stroke weight with butt
 * caps and mitre joins, which is what makes the set read as instrument
 * markings rather than assorted clip art. Nothing here is an emoji or a
 * character standing in for a picture, and no glyph is borrowed from a
 * different icon family. The mixed custom-plus-Material set this replaces was
 * the most visible inconsistency in the old build.
 */

private const val GRID = 24f

enum class Glyph {
    // primary navigation
    OVERVIEW, PORTFOLIO, REPORTS, ASSISTANT, PROFILE,
    // commands
    ADD, EDIT, DELETE, SEARCH, FILTER, SORT, REFRESH, MORE, CLOSE, CHECK,
    // movement
    BACK, FORWARD, UP, DOWN, EXTERNAL,
    // product surfaces
    DOCUMENT, UPLOAD, TAX, VALUATION, ACQUISITION, SUBSCRIPTION, ENTERPRISE,
    ADMIN, SETTINGS, NOTIFICATION,
    // financial
    INCOME, EXPENSE, TREND, CASHFLOW, ALLOCATION, COMPARE,
    // system and state
    LOCK, SHIELD, OFFLINE, WARNING, INFO, CALENDAR, LOCATION, CURRENCY,
    LANGUAGE, APPEARANCE, LOGOUT, EMPTY_BOX, KEY, USERS
}

@Composable
fun PorticoIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String? = null
) {
    val described = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else modifier
    Canvas(described.size(size)) {
        drawGlyph(glyph, tint, this.size.minDimension)
    }
}

private fun DrawScope.drawGlyph(glyph: Glyph, tint: Color, extent: Float) {
    val unit = extent / GRID
    val stroke = Stroke(
        width = 1.75f * unit,
        cap = StrokeCap.Butt,
        join = StrokeJoin.Miter
    )
    fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 1.75f) =
        drawLine(tint, p(x1, y1), p(x2, y2), width * unit, StrokeCap.Butt)

    fun path(build: Path.() -> Unit) {
        val shape = Path().apply {
            build()
        }
        drawPath(shape, tint, style = stroke)
    }

    fun Path.move(x: Float, y: Float) = moveTo(x * unit, y * unit)
    fun Path.to(x: Float, y: Float) = lineTo(x * unit, y * unit)

    fun rect(x: Float, y: Float, w: Float, h: Float) = drawRect(
        tint, p(x, y), Size(w * unit, h * unit), style = stroke
    )

    fun circle(cx: Float, cy: Float, r: Float, filled: Boolean = false) {
        if (filled) drawCircle(tint, r * unit, p(cx, cy))
        else drawCircle(tint, r * unit, p(cx, cy), style = stroke)
    }

    when (glyph) {
        // ---------------------------------------------------- navigation
        /* A facade read as three bays under a lintel, Portico's own mark. */
        Glyph.OVERVIEW -> {
            line(3f, 7f, 21f, 7f)
            line(5f, 7f, 5f, 20f); line(12f, 7f, 12f, 20f); line(19f, 7f, 19f, 20f)
            line(3f, 20f, 21f, 20f)
            line(4f, 4.5f, 20f, 4.5f)
        }
        /* A register: one marked entry per holding. */
        Glyph.PORTFOLIO -> {
            rect(3.5f, 5f, 3.2f, 3.2f); line(9.5f, 6.6f, 20.5f, 6.6f)
            rect(3.5f, 10.4f, 3.2f, 3.2f); line(9.5f, 12f, 20.5f, 12f)
            rect(3.5f, 15.8f, 3.2f, 3.2f); line(9.5f, 17.4f, 20.5f, 17.4f)
        }
        /* A plotted series with its baseline. */
        Glyph.REPORTS -> {
            line(3.5f, 20f, 20.5f, 20f)
            path { move(4f, 15f); to(9f, 10f); to(13f, 13f); to(20f, 5f) }
            circle(20f, 5f, 1.6f, filled = true)
        }
        /* A reading aperture: the assistant looks at the portfolio. */
        Glyph.ASSISTANT -> {
            path {
                move(3.5f, 6f); to(20.5f, 6f); to(20.5f, 16f); to(9f, 16f)
                to(5.5f, 19.5f); to(5.5f, 16f); to(3.5f, 16f); close()
            }
            circle(9f, 11f, 1.1f, filled = true)
            circle(12.5f, 11f, 1.1f, filled = true)
            circle(16f, 11f, 1.1f, filled = true)
        }
        Glyph.PROFILE -> {
            circle(12f, 8.5f, 3.6f)
            path { move(4.5f, 20f); to(4.5f, 17.5f); to(19.5f, 17.5f); to(19.5f, 20f) }
            line(4.5f, 17.5f, 8f, 14.6f); line(19.5f, 17.5f, 16f, 14.6f)
        }

        // ------------------------------------------------------- commands
        Glyph.ADD -> { line(12f, 4f, 12f, 20f); line(4f, 12f, 20f, 12f) }
        Glyph.EDIT -> {
            path { move(4f, 20f); to(4f, 16f); to(16f, 4f); to(20f, 8f); to(8f, 20f); close() }
            line(14f, 6f, 18f, 10f)
        }
        Glyph.DELETE -> {
            line(4f, 6.5f, 20f, 6.5f)
            path { move(6f, 6.5f); to(7f, 20f); to(17f, 20f); to(18f, 6.5f) }
            line(9.5f, 6.5f, 9.5f, 3.5f); line(9.5f, 3.5f, 14.5f, 3.5f); line(14.5f, 3.5f, 14.5f, 6.5f)
        }
        Glyph.SEARCH -> { circle(10.5f, 10.5f, 6f); line(15f, 15f, 20.5f, 20.5f) }
        Glyph.FILTER -> {
            path { move(3.5f, 5f); to(20.5f, 5f); to(14f, 12.5f); to(14f, 20f); to(10f, 17.5f); to(10f, 12.5f); close() }
        }
        Glyph.SORT -> {
            line(4f, 6f, 20f, 6f); line(4f, 12f, 15f, 12f); line(4f, 18f, 10f, 18f)
        }
        Glyph.REFRESH -> {
            drawArc(tint, 40f, 280f, false, p(4f, 4f), Size(16f * unit, 16f * unit), style = stroke)
            path { move(18.5f, 3.5f); to(19.5f, 8.5f); to(14.5f, 7.5f) }
        }
        Glyph.MORE -> { circle(5.5f, 12f, 1.5f, true); circle(12f, 12f, 1.5f, true); circle(18.5f, 12f, 1.5f, true) }
        Glyph.CLOSE -> { line(5.5f, 5.5f, 18.5f, 18.5f); line(18.5f, 5.5f, 5.5f, 18.5f) }
        Glyph.CHECK -> path { move(4.5f, 12.5f); to(9.5f, 17.5f); to(19.5f, 6.5f) }

        // ------------------------------------------------------- movement
        Glyph.BACK -> { line(20f, 12f, 4f, 12f); path { move(10f, 6f); to(4f, 12f); to(10f, 18f) } }
        Glyph.FORWARD -> path { move(9f, 5f); to(16f, 12f); to(9f, 19f) }
        Glyph.UP -> path { move(5f, 15f); to(12f, 8f); to(19f, 15f) }
        Glyph.DOWN -> path { move(5f, 9f); to(12f, 16f); to(19f, 9f) }
        Glyph.EXTERNAL -> {
            path { move(13f, 4.5f); to(19.5f, 4.5f); to(19.5f, 11f) }
            line(19.5f, 4.5f, 11f, 13f)
            path { move(16f, 15f); to(16f, 19.5f); to(4.5f, 19.5f); to(4.5f, 8f); to(9f, 8f) }
        }

        // ------------------------------------------------------- surfaces
        Glyph.DOCUMENT -> {
            path { move(5.5f, 3f); to(14f, 3f); to(18.5f, 7.5f); to(18.5f, 21f); to(5.5f, 21f); close() }
            path { move(14f, 3f); to(14f, 7.5f); to(18.5f, 7.5f) }
            line(8.5f, 12f, 15.5f, 12f); line(8.5f, 15.5f, 15.5f, 15.5f)
        }
        Glyph.UPLOAD -> {
            path { move(4.5f, 15f); to(4.5f, 20.5f); to(19.5f, 20.5f); to(19.5f, 15f) }
            line(12f, 3.5f, 12f, 15f)
            path { move(7f, 8.5f); to(12f, 3.5f); to(17f, 8.5f) }
        }
        /* A levy taken off a stack. */
        Glyph.TAX -> {
            rect(4f, 4f, 16f, 16f)
            line(7.5f, 16.5f, 16.5f, 7.5f)
            circle(8.5f, 8.5f, 1.8f)
            circle(15.5f, 15.5f, 1.8f)
        }
        /* A value under measurement. */
        Glyph.VALUATION -> {
            path { move(3.5f, 9f); to(12f, 4f); to(20.5f, 9f) }
            line(3.5f, 9f, 3.5f, 13f); line(20.5f, 9f, 20.5f, 13f)
            line(12f, 4f, 12f, 20.5f)
            line(8f, 20.5f, 16f, 20.5f)
        }
        Glyph.ACQUISITION -> {
            circle(10f, 10f, 6f)
            line(14.4f, 14.4f, 20.5f, 20.5f)
            line(10f, 7f, 10f, 13f); line(7f, 10f, 13f, 10f)
        }
        Glyph.SUBSCRIPTION -> {
            rect(3.5f, 6f, 17f, 12f)
            line(3.5f, 10f, 20.5f, 10f)
            line(7f, 14.5f, 12f, 14.5f)
        }
        Glyph.ENTERPRISE -> {
            rect(3.5f, 8f, 7f, 12f)
            rect(13.5f, 4f, 7f, 16f)
            line(6f, 11.5f, 8f, 11.5f); line(6f, 15f, 8f, 15f)
            line(16f, 7.5f, 18f, 7.5f); line(16f, 11.5f, 18f, 11.5f); line(16f, 15f, 18f, 15f)
        }
        Glyph.ADMIN -> {
            rect(3.5f, 4.5f, 17f, 12f)
            line(3.5f, 8.5f, 20.5f, 8.5f)
            line(8f, 20f, 16f, 20f); line(12f, 16.5f, 12f, 20f)
            circle(6.2f, 6.5f, 0.8f, true)
        }
        Glyph.SETTINGS -> {
            circle(12f, 12f, 3.2f)
            line(12f, 3f, 12f, 6.5f); line(12f, 17.5f, 12f, 21f)
            line(3f, 12f, 6.5f, 12f); line(17.5f, 12f, 21f, 12f)
            line(5.6f, 5.6f, 8f, 8f); line(16f, 16f, 18.4f, 18.4f)
            line(18.4f, 5.6f, 16f, 8f); line(8f, 16f, 5.6f, 18.4f)
        }
        Glyph.NOTIFICATION -> {
            // Bell: angular dome over straight sides, on a rail.
            path {
                move(6f, 17f); to(6f, 11.2f); to(9f, 6.4f)
                to(15f, 6.4f); to(18f, 11.2f); to(18f, 17f)
            }
            line(3.5f, 17f, 20.5f, 17f)
            line(10.2f, 20f, 13.8f, 20f)
            line(12f, 3.4f, 12f, 6.4f)
        }

        // ------------------------------------------------------ financial
        /* Money arriving into the account. */
        Glyph.INCOME -> {
            path { move(4.5f, 13f); to(4.5f, 20f); to(19.5f, 20f); to(19.5f, 13f) }
            line(12f, 3.5f, 12f, 15.5f)
            path { move(7.5f, 10.5f); to(12f, 15.5f); to(16.5f, 10.5f) }
        }
        /* Money leaving it. */
        Glyph.EXPENSE -> {
            path { move(4.5f, 13f); to(4.5f, 20f); to(19.5f, 20f); to(19.5f, 13f) }
            line(12f, 3.5f, 12f, 15.5f)
            path { move(7.5f, 8.5f); to(12f, 3.5f); to(16.5f, 8.5f) }
        }
        Glyph.TREND -> {
            path { move(3.5f, 16f); to(9f, 10.5f); to(13f, 14f); to(20.5f, 6f) }
            path { move(15.5f, 6f); to(20.5f, 6f); to(20.5f, 11f) }
        }
        Glyph.CASHFLOW -> {
            line(3.5f, 8f, 16f, 8f)
            path { move(12.5f, 4.5f); to(16f, 8f); to(12.5f, 11.5f) }
            line(20.5f, 16f, 8f, 16f)
            path { move(11.5f, 12.5f); to(8f, 16f); to(11.5f, 19.5f) }
        }
        Glyph.ALLOCATION -> {
            circle(12f, 12f, 8f)
            line(12f, 4f, 12f, 12f)
            line(12f, 12f, 19f, 16f)
        }
        Glyph.COMPARE -> {
            line(6f, 20f, 6f, 9f); line(12f, 20f, 12f, 4.5f); line(18f, 20f, 18f, 13f)
            line(3.5f, 20f, 20.5f, 20f)
        }

        // -------------------------------------------------- system, state
        Glyph.LOCK -> {
            rect(4.5f, 10.5f, 15f, 10f)
            path { move(8f, 10.5f); to(8f, 7.5f); to(16f, 7.5f); to(16f, 10.5f) }
            circle(12f, 15.5f, 1.6f, true)
        }
        Glyph.SHIELD -> {
            path { move(12f, 3f); to(20f, 6f); to(20f, 12f); to(12f, 21f); to(4f, 12f); to(4f, 6f); close() }
            path { move(8.5f, 12f); to(11f, 14.5f); to(15.5f, 9.5f) }
        }
        Glyph.OFFLINE -> {
            path { move(4f, 10f); to(8f, 6.5f) }
            path { move(7.5f, 13f); to(10f, 10.8f) }
            circle(12f, 16.5f, 1.4f, true)
            path { move(16f, 10.8f); to(20f, 10f) }
            line(4.5f, 19.5f, 19.5f, 4.5f)
        }
        Glyph.WARNING -> {
            path { move(12f, 3.5f); to(21f, 20f); to(3f, 20f); close() }
            line(12f, 9.5f, 12f, 14.5f)
            circle(12f, 17.2f, 1.1f, true)
        }
        Glyph.INFO -> {
            circle(12f, 12f, 8.5f)
            line(12f, 11f, 12f, 16.5f)
            circle(12f, 7.6f, 1.1f, true)
        }
        Glyph.CALENDAR -> {
            rect(3.5f, 5.5f, 17f, 15f)
            line(3.5f, 10f, 20.5f, 10f)
            line(8f, 3f, 8f, 7f); line(16f, 3f, 16f, 7f)
            circle(8.5f, 14f, 1.1f, true); circle(12f, 14f, 1.1f, true)
        }
        Glyph.LOCATION -> {
            path { move(12f, 21f); to(5.5f, 11f); to(5.5f, 8f); to(18.5f, 8f); to(18.5f, 11f); close() }
            circle(12f, 9.6f, 2.2f)
        }
        Glyph.CURRENCY -> {
            circle(12f, 12f, 8.5f)
            line(12f, 6.5f, 12f, 17.5f)
            path { move(15f, 9f); to(10.5f, 9f); to(10.5f, 11.8f); to(14f, 11.8f); to(14f, 15f); to(9f, 15f) }
        }
        Glyph.LANGUAGE -> {
            circle(12f, 12f, 8.5f)
            line(3.5f, 12f, 20.5f, 12f)
            path { move(12f, 3.5f); to(8.5f, 8f); to(8.5f, 16f); to(12f, 20.5f) }
            path { move(12f, 3.5f); to(15.5f, 8f); to(15.5f, 16f); to(12f, 20.5f) }
        }
        Glyph.APPEARANCE -> {
            circle(12f, 12f, 8f)
            path {
                move(12f, 4f)
                to(12f, 20f)
            }
            drawArc(tint, 270f, 180f, true, p(4f, 4f), Size(16f * unit, 16f * unit))
        }
        Glyph.LOGOUT -> {
            path { move(14f, 4.5f); to(4.5f, 4.5f); to(4.5f, 19.5f); to(14f, 19.5f) }
            line(9.5f, 12f, 20.5f, 12f)
            path { move(16.5f, 8f); to(20.5f, 12f); to(16.5f, 16f) }
        }
        Glyph.EMPTY_BOX -> {
            path { move(3.5f, 8f); to(12f, 3.5f); to(20.5f, 8f); to(20.5f, 17f); to(12f, 21.5f); to(3.5f, 17f); close() }
            path { move(3.5f, 8f); to(12f, 12.5f); to(20.5f, 8f) }
            line(12f, 12.5f, 12f, 21.5f)
        }
        Glyph.KEY -> {
            circle(7.5f, 12f, 4f)
            line(11.5f, 12f, 20.5f, 12f)
            line(17f, 12f, 17f, 16f); line(20f, 12f, 20f, 15f)
        }
        Glyph.USERS -> {
            circle(9f, 8.5f, 3.4f)
            path { move(2.5f, 20f); to(2.5f, 17f); to(15.5f, 17f); to(15.5f, 20f) }
            circle(17f, 8f, 2.6f)
            path { move(17f, 13f); to(21.5f, 13f); to(21.5f, 16f) }
        }
    }
}
