@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.portico.android.ui.screens
import com.portico.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.SortMode
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme

/*
 * The register. Search, filter and sort operate on computed financials rather
 * than stored fields, so "sort by net yield" ranks by the same number the
 * property page shows.
 */
@Composable
fun PortfolioScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val currency = store.profile.currency
    val portfolio = store.portfolio()
    val visible = state.visibleProperties()

    // Clears the floating action button so the last row is never behind it.
    Column(modifier.padding(bottom = 128.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.position), supporting = "${portfolio.propertyCount} properties")
            MetricGrid(
                metrics = listOf(
                    Metric(stringResource(R.string.portfolio_value), Money.compact(portfolio.portfolioValue, currency), "current"),
                    Metric(stringResource(R.string.invested), Money.compact(portfolio.investedCapital, currency), "cash in"),
                    rateMetric(stringResource(R.string.total_roi), portfolio.totalRoi),
                    Metric(stringResource(R.string.net_yield), Money.percent(portfolio.netYield), stringResource(R.string.after_costs_and_tax)),
                    Metric(
                        stringResource(R.string.monthly_net),
                        Money.format(portfolio.monthlyCashflow, currency),
                        direction = portfolio.monthlyCashflow
                    ),
                    Metric(
                        stringResource(R.string.annual_net),
                        Money.compact(portfolio.annualNetIncome, currency),
                        direction = portfolio.annualNetIncome
                    )
                ),
                columns = if (LocalWidthClass.current.isAtLeastMedium) 3 else 2
            )
        }

        // ---- find ----------------------------------------------------------
        Column(Modifier.padding(horizontal = Space.lg)) {
            PorticoField(
                value = state.portfolioQuery,
                onValueChange = { state.portfolioQuery = it },
                label = stringResource(R.string.search_properties),
                placeholder = stringResource(R.string.name_region_or_address),
                trailing = {
                    if (state.portfolioQuery.isNotBlank()) {
                        GlyphButton(Glyph.CLOSE, "Clear search") { state.portfolioQuery = "" }
                    } else {
                        PorticoIcon(
                            Glyph.SEARCH,
                            size = 18.dp,
                            tint = PorticoTheme.semantic.tertiaryText,
                            contentDescription = null
                        )
                    }
                }
            )
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                SecondaryButton(
                    label = if (state.hasActiveFilters) "Filters on" else "Filter",
                    glyph = Glyph.FILTER,
                    modifier = Modifier.weight(1f)
                ) { state.showFilterSheet = true }
                SecondaryButton(
                    label = state.sortMode.label,
                    glyph = Glyph.SORT,
                    modifier = Modifier.weight(1f)
                ) {
                    val order = SortMode.entries
                    state.sortMode = order[(order.indexOf(state.sortMode) + 1) % order.size]
                    state.notify("Sorted by ${state.sortMode.label.lowercase()}")
                }
            }
        }

        // ---- register -------------------------------------------------------
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                stringResource(R.string.register),
                supporting = if (visible.size == store.properties.size) "${visible.size} properties"
                else "${visible.size} of ${store.properties.size} shown"
            )
            when {
                store.properties.isEmpty() -> EmptyState(
                    title = stringResource(R.string.no_properties_yet),
                    body = stringResource(R.string.add_your_first_holding_and_portico_will_work_o),
                    glyph = Glyph.PORTFOLIO,
                    actionLabel = "Add a property",
                    onAction = { state.resetDraft(); state.navigate(Route.ADD_PROPERTY) }
                )
                visible.isEmpty() -> EmptyState(
                    title = stringResource(R.string.nothing_matches),
                    body = stringResource(R.string.no_property_matches_this_search_and_filter_com),
                    glyph = Glyph.SEARCH,
                    actionLabel = stringResource(R.string.clear_search_and_filters),
                    onAction = { state.portfolioQuery = ""; state.clearFilters() }
                )
                else -> visible.forEachIndexed { index, result ->
                    if (index > 0) Hairline()
                    PropertyRow(
                        result = result,
                        currency = currency,
                        store = state.store,
                        detailed = true,
                        shareOfPortfolio = if (portfolio.portfolioValue > 0)
                            result.property.currentValue / portfolio.portfolioValue else 0.0,
                        onEdit = {
                            state.loadDraftFrom(result.property)
                            state.navigate(Route.ADD_PROPERTY)
                        },
                        onDelete = {
                            state.pendingDeletePropertyId = result.property.id
                        },
                        onClick = { state.openProperty(result.property.id) }
                    )
                }
            }
        }

        if (!store.canAddProperty) {
            Panel(Modifier.padding(horizontal = Space.lg), accent = true) {
                PlanLimitState(
                    limit = store.subscription.tier.propertyLimit,
                    onUpgrade = { state.navigate(Route.SUBSCRIPTION) }
                )
            }
        }
    }

    if (state.showFilterSheet) {
        FilterSheet(state) { state.showFilterSheet = false }
    }
    PropertyDeleteDialog(state)
}

@Composable
private fun FilterSheet(state: PorticoState, onDismiss: () -> Unit) {
    val store = state.store
    val countries = listOf("All") + store.properties.map { it.country }.distinct()
    val regions = listOf("All") + store.properties.map { it.region }.distinct()
    val types = listOf("All") + PropertyType.entries.map { it.label }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PorticoTheme.semantic.panel) {
        Column(
            Modifier.padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            Text(
                stringResource(R.string.filter_register),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Space.lg)
            )
            ChoiceRow(stringResource(R.string.country), countries, state.filterCountry) { state.filterCountry = it }
            ChoiceRow(stringResource(R.string.region), regions, state.filterRegion) { state.filterRegion = it }
            ChoiceRow(stringResource(R.string.property_type), types, state.filterType) { state.filterType = it }
            ChoiceRow(
                stringResource(R.string.performance),
                listOf("All", "Positive cashflow", "Negative cashflow"),
                state.filterPerformance
            ) { state.filterPerformance = it }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                SecondaryButton(stringResource(R.string.clear_all), Modifier.weight(1f)) { state.clearFilters() }
                PrimaryButton("Show ${state.visibleProperties().size}", Modifier.weight(1f)) { onDismiss() }
            }
        }
    }
}
