package com.portico.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.portico.android.domain.*
import com.portico.android.ui.ChartRange
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme

/*
 * The first viewport is the product's whole argument: what the portfolio is
 * worth, how it moved, and what survives costs and tax. Everything below that
 * is detail the investor can reach when they want it.
 */
@Composable
fun DashboardScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val currency = store.profile.currency
    val results = store.financials()
    val portfolio = store.portfolio()
    val reduceMotion = store.preferences.reducedMotion

    if (portfolio.isEmpty) {
        FirstRunDashboard(state, modifier)
        return
    }

    Column(
        modifier.padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(Space.lg)
    ) {
        // ---- position -----------------------------------------------------
        MetricReadout(
            label = "Portfolio value",
            value = Money.format(portfolio.portfolioValue, currency),
            delta = portfolio.totalReturn,
            deltaText = "${Money.signed(portfolio.totalReturn, currency)}   ${Money.signedPercent(portfolio.totalRoi)}",
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)
        )

        Panel(Modifier.padding(horizontal = Space.lg)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                MetricCell(
                    "Invested",
                    Money.compact(portfolio.investedCapital, currency),
                    Modifier.weight(1f),
                    supporting = "${portfolio.propertyCount} properties"
                )
                androidx.compose.material3.VerticalDivider(
                    Modifier.fillMaxHeight(), 1.dp, PorticoTheme.semantic.hairline
                )
                MetricCell(
                    "Appreciation",
                    Money.compact(portfolio.appreciation, currency),
                    Modifier.weight(1f),
                    supporting = Money.signedPercent(portfolio.capitalRoi),
                    delta = portfolio.capitalRoi
                )
                androidx.compose.material3.VerticalDivider(
                    Modifier.fillMaxHeight(), 1.dp, PorticoTheme.semantic.hairline
                )
                MetricCell(
                    "Net / month",
                    Money.format(portfolio.monthlyCashflow, currency),
                    Modifier.weight(1f),
                    supporting = if (portfolio.monthlyCashflow >= 0) "after tax" else "after tax · negative",
                    delta = portfolio.monthlyCashflow
                )
            }
        }

        // ---- performance --------------------------------------------------
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Value over time", supporting = "Interpolated between purchase and current value")
            SegmentedRow(
                options = ChartRangeOptions,
                selected = state.chartRange.label,
                onSelect = { label ->
                    state.chartRange = ChartRange.entries.first { it.label == label }
                }
            )
            Spacer(Modifier.height(Space.md))
            ValueChart(
                series = Finance.portfolioValueSeries(
                    store.properties.toList(),
                    points = pointsFor(state.chartRange),
                    rates = store.exchangeRates,
                    displayCurrency = currency
                ),
                currency = currency,
                rangeLabel = state.chartRange.label,
                animate = !reduceMotion
            )
            Spacer(Modifier.height(Space.md))
        }

        // ---- the argument -------------------------------------------------
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                "Gross to net",
                supporting = "Annual, across the portfolio",
                action = "Tax",
                onAction = { state.navigate(Route.TAX) }
            )
            WaterfallLedger(
                steps = portfolio.waterfall(),
                currency = currency,
                animate = !reduceMotion
            )
            Spacer(Modifier.height(Space.md))
        }

        // ---- financial summary --------------------------------------------
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Financial summary", supporting = "Rates recomputed from totals, not averaged")
            MetricGrid(
                metrics = listOf(
                    rateMetric("Total ROI", portfolio.totalRoi),
                    Metric("Cap rate", Money.percent(portfolio.capRate), "on current value"),
                    Metric("Gross yield", Money.percent(portfolio.grossYield), "before costs"),
                    Metric("Net yield", Money.percent(portfolio.netYield), "after costs and tax"),
                    Metric("Annual income", Money.compact(portfolio.annualGrossIncome, currency), "gross rent"),
                    Metric(
                        "Annual costs",
                        Money.compact(portfolio.annualOperatingExpenses + portfolio.annualTaxes, currency),
                        "expenses and tax"
                    )
                ),
                columns = if (LocalWidthClass.current.isAtLeastMedium) 3 else 2
            )
        }

        // ---- holdings ------------------------------------------------------
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                "Properties",
                supporting = "${results.size} holdings",
                action = "All",
                onAction = { state.selectDestination(Route.PORTFOLIO) }
            )
            results.forEachIndexed { index, result ->
                if (index > 0) Hairline()
                PropertyRow(
                    result = result,
                    currency = currency,
                    shareOfPortfolio = if (portfolio.portfolioValue > 0)
                        result.property.currentValue / portfolio.portfolioValue else 0.0,
                    onClick = { state.openProperty(result.property.id) }
                )
            }
        }

        // ---- activity -------------------------------------------------------
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Recent activity")
            if (store.activity.isEmpty()) {
                EmptyState(
                    title = "No activity yet",
                    body = "Rent, expenses, valuations and documents you record will appear here.",
                    actionLabel = "Record income",
                    onAction = {
                        state.transactionIsIncome = true
                        state.showTransactionSheet = true
                    }
                )
            } else {
                store.activity.take(6).forEachIndexed { index, event ->
                    if (index > 0) Hairline()
                    ActivityRow(
                        event = event,
                        currency = currency,
                        onClick = event.propertyId?.let { { state.openProperty(it) } }
                    )
                }
            }
        }

        SyntheticNote(
            "Portfolio figures are computed from the records in this workspace. " +
                "Seeded properties are illustrative sample data."
        )
    }
}

/** Zero properties: teach the first action instead of showing an empty chart. */
@Composable
private fun FirstRunDashboard(state: PorticoState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Spacer(Modifier.height(Space.xl))
        EmptyState(
            title = "Add your first property",
            body = "Portico works out ROI, cap rate, yields and cashflow from what you enter: purchase price, rent, and running costs. It takes about two minutes.",
            glyph = Glyph.PORTFOLIO,
            actionLabel = "Add a property",
            onAction = {
                state.resetDraft()
                state.navigate(Route.ADD_PROPERTY)
            }
        )
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("What you'll get")
            DataRow("Return on the cash you put in", "ROI")
            Hairline()
            DataRow("What the asset yields now", "Cap rate")
            Hairline()
            DataRow("What survives costs and tax", "Net yield")
            Hairline()
            DataRow("What reaches your account monthly", "Cashflow")
        }
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Just exploring?")
            DataRow(
                "Explore a sample portfolio",
                "Demo cockpit",
                supporting = "Sample records stay separate from your private workspace",
                onClick = { state.notify("Sign out and choose Enter the demo cockpit") }
            )
        }
    }
}

internal val ChartRangeOptions = ChartRange.entries.map { it.label }

internal fun pointsFor(range: ChartRange): Int = when (range) {
    ChartRange.M1 -> 8
    ChartRange.M6 -> 14
    ChartRange.Y1 -> 20
    ChartRange.Y5 -> 28
    ChartRange.ALL -> 34
}
