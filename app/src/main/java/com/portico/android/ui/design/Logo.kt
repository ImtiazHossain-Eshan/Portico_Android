package com.portico.android.ui.design
import com.portico.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portico.android.ui.theme.PorticoTheme

/*
 * The Portico mark: a lintel over three columns of unequal height.
 *
 * Read one way it is an architectural facade, the portico the product is
 * named for. Read the other way the columns are a bar chart of holdings, and
 * the tallest one carries the accent. That double reading is the whole
 * identity, and it is why the mark works at 24dp in a top bar and at 96dp on
 * the splash without needing a different drawing for each.
 */
@Composable
fun PorticoMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    accent: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = "Portico"
) {
    val described = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else modifier

    Canvas(described.size(size)) {
        val extent = this.size.minDimension
        val u = extent / 24f
        fun p(x: Float, y: Float) = Offset(x * u, y * u)

        val rule = 1.8f * u
        val column = 2.8f * u

        // Lintel: the entablature, and the chart's ceiling.
        drawLine(tint, p(2.5f, 5.6f), p(21.5f, 5.6f), rule, StrokeCap.Butt)

        // Three bays rising off the base to different heights. The gap under
        // the lintel is what lets them read as measured quantities rather than
        // structural columns, the whole point of the mark.
        drawLine(tint, p(6.2f, 19.5f), p(6.2f, 8.2f), column, StrokeCap.Butt)
        drawLine(accent, p(12f, 19.5f), p(12f, 11.6f), column, StrokeCap.Butt)
        drawLine(tint, p(17.8f, 19.5f), p(17.8f, 9.8f), column, StrokeCap.Butt)

        // Stylobate: the step it stands on, and the chart baseline.
        drawLine(tint, p(2.5f, 20.4f), p(21.5f, 20.4f), rule, StrokeCap.Butt)
    }
}

/** Mark plus wordmark. Tagline is optional so the lockup fits a top bar. */
@Composable
fun PorticoLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 36.dp,
    showTagline: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onBackground
) {
    // Hoisted: a semantics block is not a composable scope.
    val brand = stringResource(R.string.portico_2)
    Row(
        modifier = modifier.semantics { contentDescription = brand },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PorticoMark(size = markSize, tint = tint, contentDescription = null)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.portico),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.2.sp
                ),
                color = tint
            )
            if (showTagline) {
                Text(
                    stringResource(R.string.property_capital_measured),
                    style = MaterialTheme.typography.labelSmall,
                    color = PorticoTheme.semantic.tertiaryText
                )
            }
        }
    }
}
