package com.portico.android.ui.screens
import com.portico.android.R
import androidx.compose.ui.res.stringResource
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
        val sections = ReportSection.entries.map {
            when (it) {
                ReportSection.PERFORMANCE -> stringResource(R.string.report_performance)
                ReportSection.CASHFLOW -> stringResource(R.string.report_cashflow)
                ReportSection.ALLOCATION -> stringResource(R.string.report_allocation)
                ReportSection.COMPARISON -> stringResource(R.string.report_comparison)
                ReportSection.GROSS_NET -> stringResource(R.string.report_gross_net)
            }
        }
        SegmentedRow(
            options = sections,
            selected = state.reportSection.label
        ) { label ->
            state.reportSection = ReportSection.entries[sections.indexOf(label)]
        }

        if (results.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.nothing_to_report_yet),
                body = stringResource(R.string.reports_are_built_from_your_properties_add_one),
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
            PanelHeader(stringResource(R.string.portfolio_value), supporting = stringResource(R.string.across_n_properties, results.size))
            run {
                val ranges = chartRangeOptions()
                SegmentedRow(ranges, ranges[ChartRange.entries.indexOf(state.chartRange)]) { label ->
                    state.chartRange = ChartRange.entries[ranges.indexOf(label)]
                }
            }
            Spacer(Modifier.height(Space.md))
            ValueChart(
                series = Finance.portfolioValueSeries(
                    store.properties.toList(), pointsFor(state.chartRange),
                    store.exchangeRates, store.profile.currency,
                    windowMonths = state.chartRange.months
                ),
                currency = currency,
                rangeLabel = state.chartRange.label,
                animate = !store.preferences.reducedMotion
            )
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.return_by_property), supporting = stringResource(R.string.total_roi_on_cash_invested))
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
            PanelHeader(stringResource(R.string.portfolio_rates))
            MetricGrid(
                metrics = listOf(
                    rateMetric(stringResource(R.string.total_roi), portfolio.totalRoi),
                    rateMetric(stringResource(R.string.capital_roi), portfolio.capitalRoi),
                    rateMetric(stringResource(R.string.cash_on_cash), portfolio.cashOnCash),
                    Metric(stringResource(R.string.cap_rate), Money.percent(portfolio.capRate), "on current value")
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
            PanelHeader(stringResource(R.string.annual_cashflow), supporting = stringResource(R.string.income_less_operating_costs_and_tax))
            DataRow(stringResource(R.string.gross_income), Money.format(portfolio.annualGrossIncome, currency), valueColor = semantic.gain)
            Hairline()
            DataRow(stringResource(R.string.operating_expenses), Money.format(-portfolio.annualOperatingExpenses, currency), valueColor = semantic.loss)
            Hairline()
            DataRow(stringResource(R.string.taxes_and_fees), Money.format(-portfolio.annualTaxes, currency), valueColor = semantic.loss)
            TotalRule()
            DataRow(
                stringResource(R.string.net_cashflow),
                Money.format(portfolio.annualNetIncome, currency),
                emphasise = true,
                valueColor = semantic.forDelta(portfolio.annualNetIncome)
            )
            Spacer(Modifier.height(Space.sm))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.monthly_projection), supporting = stringResource(R.string.recurring_position_repeated_across_the_year))
            Spacer(Modifier.height(Space.sm))
            CashflowColumns(values = monthly, labels = labels, currency = currency)
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.cashflow_by_property))
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
        val rates = store.exchangeRates
        val display = store.profile.currency
        AllocationPanel("By country", Finance.allocation(properties, rates, display) { it.country })
        AllocationPanel("By region", Finance.allocation(properties, rates, display) { it.region })
        AllocationPanel("By property type", Finance.allocation(properties, rates, display) { it.type })
    }
}

@Composable
private fun AllocationPanel(title: String, shares: List<Pair<String, Double>>) {
    if (shares.isEmpty()) return
    val palette = allocationPalette(shares.size)
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(title, supporting = stringResource(R.string.share_of_portfolio_value))
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
            PanelHeader(stringResource(R.string.choose_properties), supporting = "${chosen.size} of ${results.size} in the comparison")
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
                title = stringResource(R.string.select_at_least_one_property),
                body = stringResource(R.string.pick_properties_above_to_compare_their_value_r),
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
                stringResource(R.string.portfolio),
                supporting = "Annual, ${store.taxProfile.jurisdiction.name}",
                action = stringResource(R.string.assumptions),
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
            PanelHeader(stringResource(R.string.gross_against_net), supporting = stringResource(R.string.the_gap_is_what_costs_and_tax_take))
            DataRow(stringResource(R.string.gross_yield), Money.percent(portfolio.grossYield))
            Hairline()
            DataRow(stringResource(R.string.net_yield), Money.percent(portfolio.netYield), emphasise = true)
            Hairline()
            DataRow(
                stringResource(R.string.lost_to_costs_and_tax),
                Money.percent(portfolio.grossYield - portfolio.netYield),
                valueColor = semantic.loss
            )
            Hairline()
            DataRow(
                stringResource(R.string.share_of_gross_retained),
                Money.percent(
                    if (portfolio.annualGrossIncome > 0)
                        portfolio.annualNetIncome / portfolio.annualGrossIncome * 100 else 0.0
                )
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.per_property))
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
