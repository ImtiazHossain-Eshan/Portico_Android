package com.portico.android.ui.screens
import com.portico.android.R
import androidx.compose.ui.res.stringResource
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
            label = stringResource(R.string.portfolio_value),
            value = Money.format(portfolio.portfolioValue, currency),
            delta = portfolio.totalReturn,
            deltaText = "${Money.signed(portfolio.totalReturn, currency)}   ${Money.signedPercent(portfolio.totalRoi)}",
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)
        )

        Panel(Modifier.padding(horizontal = Space.lg)) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                MetricCell(
                    stringResource(R.string.invested),
                    Money.compact(portfolio.investedCapital, currency),
                    Modifier.weight(1f),
                    supporting = stringResource(R.string.n_properties, portfolio.propertyCount)
                )
                androidx.compose.material3.VerticalDivider(
                    Modifier.fillMaxHeight(), 1.dp, PorticoTheme.semantic.hairline
                )
                MetricCell(
                    stringResource(R.string.appreciation),
                    Money.compact(portfolio.appreciation, currency),
                    Modifier.weight(1f),
                    supporting = Money.signedPercent(portfolio.capitalRoi),
                    delta = portfolio.capitalRoi
                )
                androidx.compose.material3.VerticalDivider(
                    Modifier.fillMaxHeight(), 1.dp, PorticoTheme.semantic.hairline
                )
                MetricCell(
                    stringResource(R.string.net_month),
                    Money.format(portfolio.monthlyCashflow, currency),
                    Modifier.weight(1f),
                    supporting = if (portfolio.monthlyCashflow >= 0) stringResource(R.string.supp_after_tax) else stringResource(R.string.supp_after_tax_neg),
                    delta = portfolio.monthlyCashflow
                )
            }
        }

        // ---- performance --------------------------------------------------
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.value_over_time), supporting = stringResource(R.string.interpolated_between_purchase_and_current_valu))
            // Matched by position, not by text: the visible label is
            // translated and no longer equals the enum's own label.
            val ranges = chartRangeOptions()
            SegmentedRow(
                options = ranges,
                selected = ranges[ChartRange.entries.indexOf(state.chartRange)],
                onSelect = { label ->
                    state.chartRange = ChartRange.entries[ranges.indexOf(label)]
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
                stringResource(R.string.gross_to_net),
                supporting = stringResource(R.string.annual_across_the_portfolio),
                action = stringResource(R.string.tax),
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
            PanelHeader(stringResource(R.string.financial_summary), supporting = stringResource(R.string.rates_recomputed_from_totals_not_averaged))
            MetricGrid(
                metrics = listOf(
                    rateMetric(stringResource(R.string.total_roi), portfolio.totalRoi),
                    Metric(stringResource(R.string.cap_rate), Money.percent(portfolio.capRate), stringResource(R.string.supp_on_current)),
                    Metric(stringResource(R.string.gross_yield), Money.percent(portfolio.grossYield), stringResource(R.string.supp_before_costs)),
                    Metric(stringResource(R.string.net_yield), Money.percent(portfolio.netYield), stringResource(R.string.supp_after_costs_tax)),
                    Metric(stringResource(R.string.annual_income), Money.compact(portfolio.annualGrossIncome, currency), stringResource(R.string.supp_gross_rent)),
                    Metric(
                        stringResource(R.string.annual_costs),
                        Money.compact(portfolio.annualOperatingExpenses + portfolio.annualTaxes, currency),
                        stringResource(R.string.supp_expenses_tax)
                    )
                ),
                columns = if (LocalWidthClass.current.isAtLeastMedium) 3 else 2
            )
        }

        // ---- holdings ------------------------------------------------------
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                stringResource(R.string.properties),
                supporting = stringResource(R.string.n_holdings, results.size),
                action = stringResource(R.string.all),
                onAction = { state.selectDestination(Route.PORTFOLIO) }
            )
            results.forEachIndexed { index, result ->
                if (index > 0) Hairline()
                PropertyRow(
                    result = result,
                    currency = currency,
                    store = state.store,
                    shareOfPortfolio = if (portfolio.portfolioValue > 0)
                        result.property.currentValue / portfolio.portfolioValue else 0.0,
                    onClick = { state.openProperty(result.property.id) }
                )
            }
        }

        // ---- activity -------------------------------------------------------
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.recent_activity))
            if (store.activity.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.no_activity_yet),
                    body = stringResource(R.string.rent_expenses_valuations_and_documents_you_rec),
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
            stringResource(R.string.portfolio_figures_are_computed_from_the_record) +
                stringResource(R.string.seeded_properties_are_illustrative_sample_data)
        )
    }
}

/** Zero properties: teach the first action instead of showing an empty chart. */
@Composable
private fun FirstRunDashboard(state: PorticoState, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Spacer(Modifier.height(Space.xl))
        EmptyState(
            title = stringResource(R.string.add_your_first_property),
            body = stringResource(R.string.portico_works_out_roi_cap_rate_yields_and_cash),
            glyph = Glyph.PORTFOLIO,
            actionLabel = "Add a property",
            onAction = {
                state.resetDraft()
                state.navigate(Route.ADD_PROPERTY)
            }
        )
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.what_you_ll_get))
            DataRow(stringResource(R.string.return_on_the_cash_you_put_in), "ROI")
            Hairline()
            DataRow(stringResource(R.string.what_the_asset_yields_now), "Cap rate")
            Hairline()
            DataRow(stringResource(R.string.what_survives_costs_and_tax), "Net yield")
            Hairline()
            DataRow(stringResource(R.string.what_reaches_your_account_monthly), "Cashflow")
        }
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.just_exploring))
            DataRow(
                stringResource(R.string.explore_a_sample_portfolio),
                "Demo cockpit",
                supporting = stringResource(R.string.sample_records_stay_separate_from_your_private),
                onClick = { state.notify(R.string.sign_out_and_choose_enter_the_demo_cockpit) }
            )
        }
    }
}

/** Range labels, with the one word among them translated. */
@Composable
internal fun chartRangeOptions(): List<String> = ChartRange.entries.map {
    if (it.isAll) stringResource(R.string.all) else it.label
}

internal fun pointsFor(range: ChartRange): Int = when (range) {
    ChartRange.M1 -> 8
    ChartRange.M6 -> 14
    ChartRange.Y1 -> 20
    ChartRange.Y5 -> 28
    ChartRange.ALL -> 34
}
