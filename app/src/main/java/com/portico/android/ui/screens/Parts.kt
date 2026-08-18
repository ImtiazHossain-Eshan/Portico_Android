package com.portico.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.launch

/*
 * Rows shared across surfaces. A property reads the same on the dashboard, in
 * the register and in a report (same figures in the same order) because
 * recognising a holding at a glance matters more than variety.
 */

/**
 * Property row. Instead of a photo placeholder it carries a *value bar*: the
 * property's share of portfolio value, which is information a grey rectangle
 * would not have been.
 */
@Composable
fun PropertyRow(
    result: PropertyFinancials,
    modifier: Modifier = Modifier,
    currency: String = "USD",
    shareOfPortfolio: Double = 0.0,
    detailed: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val semantic = PorticoTheme.semantic
    val property = result.property
    val cashflow = result.monthlyCashflow

    val summary = "${property.name}, ${property.location}. " +
        "Value ${Money.format(property.currentValue, currency)}. " +
        "Total return ${Money.signedPercent(result.totalRoi)}. " +
        "Net yield ${Money.percent(result.netYield)}. " +
        "Monthly cashflow ${Money.format(cashflow, currency)}."

    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = summary }
            .padding(horizontal = Space.lg, vertical = Space.md)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    property.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${property.location} · ${property.type}",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.tertiaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(Space.md))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Money.format(property.currentValue, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                DeltaLine(
                    delta = result.totalRoi,
                    text = Money.signedPercent(result.totalRoi),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(Space.md))

        // Share-of-portfolio bar: proportion, not decoration.
        if (shareOfPortfolio > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(semantic.panelSunk)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(shareOfPortfolio.toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                )
            }
            Spacer(Modifier.height(Space.md))
        }

        // Three figures every property is judged on.
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
            InlineFigure("Net yield", Money.percent(result.netYield))
            InlineFigure("Cap rate", Money.percent(result.capRate))
            InlineFigure(
                "Cashflow",
                Money.format(cashflow, currency),
                valueColor = semantic.forDelta(cashflow)
            )
        }

        if (detailed) {
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                InlineFigure("Purchase", Money.compact(property.purchasePrice, currency))
                InlineFigure("Size", "${property.sizeSqm.toInt()} m²")
                InlineFigure("Held", "${"%.1f".format(result.holdingYears)} yr")
            }
        }

        if (onEdit != null || onDelete != null) {
            Spacer(Modifier.height(Space.md))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                if (onEdit != null) {
                    SecondaryButton(
                        label = "Edit",
                        modifier = Modifier.weight(1f),
                        glyph = Glyph.EDIT,
                        onClick = onEdit
                    )
                }
                if (onDelete != null) {
                    SecondaryButton(
                        label = "Delete",
                        modifier = Modifier.weight(1f),
                        glyph = Glyph.DELETE,
                        destructive = true,
                        onClick = onDelete
                    )
                }
            }
        }

        if (cashflow < 0) {
            Spacer(Modifier.height(Space.md))
            StatusChip("Negative cashflow", tone = semantic.loss, glyph = Glyph.WARNING)
        }
    }
}

/**
 * One confirmation path for property deletion, whether the action starts in
 * the register or the property detail. The property is resolved from the
 * pending id so the dialog can never name or delete the wrong row.
 */
@Composable
fun PropertyDeleteDialog(state: PorticoState) {
    val propertyId = state.pendingDeletePropertyId ?: return
    val property = state.store.propertyById(propertyId) ?: return
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { state.pendingDeletePropertyId = null },
        title = { Text("Delete ${property.name}?") },
        text = {
            Text(
                "This permanently removes the property and all income, expenses, valuations, documents, activity and assistant conversations attached to it."
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        runCatching { state.store.deleteProperty(propertyId) }
                            .onSuccess {
                                state.pendingDeletePropertyId = null
                                state.selectDestination(Route.PORTFOLIO)
                                state.notify("${property.name} deleted")
                            }
                            .onFailure { state.notify(it.message ?: "Property could not be deleted") }
                    }
                }
            ) {
                Text("Delete property", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { state.pendingDeletePropertyId = null }) {
                Text("Keep property")
            }
        },
        containerColor = PorticoTheme.semantic.panel
    )
}

@Composable
fun InlineFigure(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier) {
        SectionLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}

@Composable
fun ActivityRow(
    event: ActivityEvent,
    modifier: Modifier = Modifier,
    currency: String = "USD",
    onClick: (() -> Unit)? = null
) {
    val semantic = PorticoTheme.semantic
    val glyph = when (event.kind) {
        ActivityKind.RENT_RECEIVED -> Glyph.INCOME
        ActivityKind.EXPENSE_ADDED -> Glyph.EXPENSE
        ActivityKind.VALUATION_UPDATED -> Glyph.VALUATION
        ActivityKind.TAX_RECORDED -> Glyph.TAX
        ActivityKind.DOCUMENT_UPLOADED -> Glyph.DOCUMENT
        ActivityKind.PROPERTY_ADDED, ActivityKind.PROPERTY_UPDATED -> Glyph.PORTFOLIO
        ActivityKind.PLAN_CHANGED -> Glyph.SUBSCRIPTION
    }
    val amountColor = event.amount?.let { semantic.forDelta(it) } ?: semantic.tertiaryText

    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(semantic.panelSunk),
            contentAlignment = Alignment.Center
        ) {
            PorticoIcon(glyph, size = 16.dp, tint = semantic.tertiaryText, contentDescription = null)
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                event.detail,
                style = MaterialTheme.typography.bodySmall,
                color = semantic.tertiaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(Space.sm))
        Column(horizontalAlignment = Alignment.End) {
            if (event.amount != null) {
                Text(
                    Money.signed(event.amount, currency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = amountColor
                )
            }
            Text(event.timestamp, style = MaterialTheme.typography.labelSmall, color = semantic.tertiaryText)
        }
    }
}

@Composable
fun DocumentRow(
    document: PortfolioDocument,
    propertyName: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val semantic = PorticoTheme.semantic
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(semantic.panelSunk),
            contentAlignment = Alignment.Center
        ) {
            PorticoIcon(Glyph.DOCUMENT, size = 18.dp, tint = semantic.tertiaryText, contentDescription = null)
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                document.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(propertyName, document.category, document.readableSize).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = semantic.tertiaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(Space.sm))
        when (document.status) {
            DocumentStatus.UPLOADING -> StatusChip("Uploading", tone = MaterialTheme.colorScheme.primary)
            DocumentStatus.FAILED -> StatusChip("Failed", tone = semantic.loss, glyph = Glyph.WARNING)
            DocumentStatus.UNAVAILABLE -> StatusChip("Unavailable", tone = semantic.tertiaryText)
            DocumentStatus.RESTRICTED -> StatusChip("Restricted", tone = semantic.tertiaryText, glyph = Glyph.LOCK)
            DocumentStatus.READY -> Text(
                document.uploadedAt,
                style = MaterialTheme.typography.labelSmall,
                color = semantic.tertiaryText
            )
        }
    }
}

/** Property picker used by upload, transaction entry and assistant context. */
@Composable
fun PropertyPickerRow(
    property: Property,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    val semantic = PorticoTheme.semantic
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .heightIn(min = 56.dp)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                PorticoIcon(Glyph.CHECK, size = 14.dp, tint = MaterialTheme.colorScheme.onPrimary, contentDescription = null)
            } else {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(semantic.panelSunk)
                )
            }
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(property.name, style = MaterialTheme.typography.bodyMedium)
            Text(property.location, style = MaterialTheme.typography.bodySmall, color = semantic.tertiaryText)
        }
    }
}
