package com.portico.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.portico.android.domain.*
import com.portico.android.ui.ChartRange
import com.portico.android.ui.PorticoState
import com.portico.android.ui.ReportSection
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme
import kotlin.math.abs

@Composable
fun ReportsScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val results = store.financials()

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        SegmentedRow(
            options = ReportSection.entries.map { it.label },
            selected = state.reportSection.label
        ) { label ->
            state.reportSection = ReportSection.entries.first { it.label == label }
        }

        if (results.isEmpty()) {
            EmptyState(
                title = "Nothing to report yet",
                body = "Reports are built from your properties. Add one and every chart here fills in.",
                glyph = Glyph.REPORTS,
                actionLabel = "Add a property",
                onAction = { state.resetDraft(); state.navigate(Route.ADD_PROPERTY) }
            )
            return@Column
        }

        when (state.reportSection) {
            ReportSection.PERFORMANCE -> PerformanceReport(state, results)
            ReportSection.CASHFLOW -> CashflowReport(state, results)
            ReportSection.ALLOCATION -> AllocationReport(state, results)
            ReportSection.COMPARISON -> ComparisonReport(state, results)
            ReportSection.GROSS_NET -> GrossNetReport(state, results)
        }
    }
}

// ------------------------------------------------------------- performance

@Composable
private fun PerformanceReport(state: PorticoState, results: List<PropertyFinancials>) {
    val store = state.store
    val currency = store.profile.currency
    val portfolio = Finance.portfolio(results)

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Portfolio value", supporting = "Across ${results.size} properties")
            SegmentedRow(ChartRangeOptions, state.chartRange.label) { label ->
                state.chartRange = ChartRange.entries.first { it.label == label }
            }
            Spacer(Modifier.height(Space.md))
            ValueChart(
                series = Finance.portfolioValueSeries(store.properties.toList(), pointsFor(state.chartRange)),
                currency = currency,
                rangeLabel = state.chartRange.label,
                animate = !store.preferences.reducedMotion
            )
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Return by property", supporting = "Total ROI on cash invested")
            val peak = results.maxOfOrNull { abs(it.totalRoi) } ?: 1.0
            results.sortedByDescending { it.totalRoi }.forEach { result ->
                MagnitudeBar(
                    label = result.property.name,
                    value = result.totalRoi,
                    maxValue = peak,
                    color = PorticoTheme.semantic.forDelta(result.totalRoi),
                    format = { Money.percent(it) }
                )
            }
            Spacer(Modifier.height(Space.sm))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Portfolio rates")
            MetricGrid(
                metrics = listOf(
                    rateMetric("Total ROI", portfolio.totalRoi),
                    rateMetric("Capital ROI", portfolio.capitalRoi),
                    rateMetric("Cash on cash", portfolio.cashOnCash),
                    Metric("Cap rate", Money.percent(portfolio.capRate), "on current value")
                ),
                columns = 2
            )
        }
    }
}

// ---------------------------------------------------------------- cashflow

@Composable
private fun CashflowReport(state: PorticoState, results: List<PropertyFinancials>) {
    val store = state.store
    val currency = store.profile.currency
    val portfolio = Finance.portfolio(results)
    val semantic = PorticoTheme.semantic

    // Twelve equal months from the recurring position, so the shape is honest
    // about being a projection rather than a recorded history.
    val monthly = List(12) { portfolio.monthlyCashflow }
    val labels = SimpleDate.MONTH_NAMES.map { it.take(1) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Annual cashflow", supporting = "Income less operating costs and tax")
            DataRow("Gross income", Money.format(portfolio.annualGrossIncome, currency), valueColor = semantic.gain)
            Hairline()
            DataRow("Operating expenses", Money.format(-portfolio.annualOperatingExpenses, currency), valueColor = semantic.loss)
            Hairline()
            DataRow("Taxes and fees", Money.format(-portfolio.annualTaxes, currency), valueColor = semantic.loss)
            TotalRule()
            DataRow(
                "Net cashflow",
                Money.format(portfolio.annualNetIncome, currency),
                emphasise = true,
                valueColor = semantic.forDelta(portfolio.annualNetIncome)
            )
            Spacer(Modifier.height(Space.sm))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Monthly projection", supporting = "Recurring position repeated across the year")
            Spacer(Modifier.height(Space.sm))
            CashflowColumns(values = monthly, labels = labels, currency = currency)
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Cashflow by property")
            results.sortedByDescending { it.monthlyCashflow }.forEachIndexed { index, result ->
                if (index > 0) Hairline()
                DataRow(
                    label = result.property.name,
                    value = Money.format(result.monthlyCashflow, currency),
                    supporting = result.property.location,
                    valueColor = semantic.forDelta(result.monthlyCashflow),
                    onClick = { state.openProperty(result.property.id) }
                )
            }
        }
    }
}

// -------------------------------------------------------------- allocation

@Composable
private fun AllocationReport(state: PorticoState, results: List<PropertyFinancials>) {
    val store = state.store
    val properties = store.properties.toList()

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        AllocationPanel("By country", Finance.allocation(properties) { it.country })
        AllocationPanel("By region", Finance.allocation(properties) { it.region })
        AllocationPanel("By property type", Finance.allocation(properties) { it.type })
    }
}

@Composable
private fun AllocationPanel(title: String, shares: List<Pair<String, Double>>) {
    if (shares.isEmpty()) return
    val palette = allocationPalette(shares.size)
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(title, supporting = "Share of portfolio value")
        Box(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
            AllocationDonut(
                slices = shares.mapIndexed { index, (label, share) ->
                    Triple(label, share, palette[index])
                },
                centerLabel = "${shares.size}"
            )
        }
        Spacer(Modifier.height(Space.md))
    }
}

// -------------------------------------------------------------- comparison

@Composable
private fun ComparisonReport(state: PorticoState, results: List<PropertyFinancials>) {
    val store = state.store
    val currency = store.profile.currency
    val semantic = PorticoTheme.semantic

    val selected = if (state.comparisonSelection.isEmpty()) {
        results.map { it.property.id }
    } else {
        state.comparisonSelection.toList()
    }
    val chosen = results.filter { it.property.id in selected }

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Choose properties", supporting = "${chosen.size} of ${results.size} in the comparison")
            results.forEach { result ->
                PropertyPickerRow(
                    property = result.property,
                    selected = result.property.id in selected
                ) {
                    if (state.comparisonSelection.isEmpty()) {
                        // First interaction starts from "all", so removing one
                        // behaves the way the checkboxes look.
                        state.comparisonSelection.addAll(results.map { it.property.id })
                    }
                    if (result.property.id in state.comparisonSelection) {
                        state.comparisonSelection.remove(result.property.id)
                    } else {
                        state.comparisonSelection.add(result.property.id)
                    }
                }
            }
        }

        if (chosen.isEmpty()) {
            EmptyState(
                title = "Select at least one property",
                body = "Pick properties above to compare their value, return and cashflow side by side.",
                glyph = Glyph.COMPARE
            )
            return@Column
        }

        ComparisonMetric("Current value", chosen, currency) { it.property.currentValue }
        ComparisonMetric("Total ROI", chosen, currency, isPercent = true) { it.totalRoi }
        ComparisonMetric("Cap rate", chosen, currency, isPercent = true) { it.capRate }
        ComparisonMetric("Net yield", chosen, currency, isPercent = true) { it.netYield }
        ComparisonMetric("Monthly cashflow", chosen, currency) { it.monthlyCashflow }
    }
}

@Composable
private fun ComparisonMetric(
    title: String,
    results: List<PropertyFinancials>,
    currency: String,
    isPercent: Boolean = false,
    selector: (PropertyFinancials) -> Double
) {
    val peak = results.maxOfOrNull { abs(selector(it)) }?.takeIf { it > 0 } ?: 1.0
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(title)
        results.sortedByDescending(selector).forEach { result ->
            val value = selector(result)
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        result.property.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (isPercent) Money.percent(value) else Money.format(value, currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (value < 0) PorticoTheme.semantic.loss else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(PorticoTheme.semantic.panelSunk)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((abs(value) / peak).toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (value < 0) PorticoTheme.semantic.loss else MaterialTheme.colorScheme.primary
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
    }
}

// -------------------------------------------------------------- gross v net

@Composable
private fun GrossNetReport(state: PorticoState, results: List<PropertyFinancials>) {
    val store = state.store
    val currency = store.profile.currency
    val portfolio = Finance.portfolio(results)
    val semantic = PorticoTheme.semantic

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                "Portfolio",
                supporting = "Annual, ${store.taxProfile.jurisdiction.name}",
                action = "Assumptions",
                onAction = { state.navigate(Route.TAX_ASSUMPTIONS) }
            )
            WaterfallLedger(
                steps = portfolio.waterfall(),
                currency = currency,
                animate = !store.preferences.reducedMotion
            )
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Gross against net", supporting = "The gap is what costs and tax take")
            DataRow("Gross yield", Money.percent(portfolio.grossYield))
            Hairline()
            DataRow("Net yield", Money.percent(portfolio.netYield), emphasise = true)
            Hairline()
            DataRow(
                "Lost to costs and tax",
                Money.percent(portfolio.grossYield - portfolio.netYield),
                valueColor = semantic.loss
            )
            Hairline()
            DataRow(
                "Share of gross retained",
                Money.percent(
                    if (portfolio.annualGrossIncome > 0)
                        portfolio.annualNetIncome / portfolio.annualGrossIncome * 100 else 0.0
                )
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Per property")
            results.forEachIndexed { index, result ->
                if (index > 0) Hairline()
                Column(
                    Modifier
                        .clickable { state.openProperty(result.property.id) }
                        .padding(vertical = Space.sm)
                ) {
                    DataRow(
                        result.property.name,
                        Money.percent(result.netYield),
                        supporting = "Gross ${Money.percent(result.grossYield)} → net"
                    )
                    WaterfallLedger(
                        steps = result.waterfall(),
                        currency = currency,
                        animate = false
                    )
                }
            }
        }

        SyntheticNote(TAX_DISCLAIMER)
    }
}
