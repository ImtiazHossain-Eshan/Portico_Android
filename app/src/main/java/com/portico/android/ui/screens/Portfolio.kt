@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.portico.android.ui.screens

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
            PanelHeader("Position", supporting = "${portfolio.propertyCount} properties")
            MetricGrid(
                metrics = listOf(
                    Metric("Portfolio value", Money.compact(portfolio.portfolioValue, currency), "current"),
                    Metric("Invested", Money.compact(portfolio.investedCapital, currency), "cash in"),
                    rateMetric("Total ROI", portfolio.totalRoi),
                    Metric("Net yield", Money.percent(portfolio.netYield), "after costs and tax"),
                    Metric(
                        "Monthly net",
                        Money.format(portfolio.monthlyCashflow, currency),
                        direction = portfolio.monthlyCashflow
                    ),
                    Metric(
                        "Annual net",
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
                label = "Search properties",
                placeholder = "Name, region or address",
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
                "Register",
                supporting = if (visible.size == store.properties.size) "${visible.size} properties"
                else "${visible.size} of ${store.properties.size} shown"
            )
            when {
                store.properties.isEmpty() -> EmptyState(
                    title = "No properties yet",
                    body = "Add your first holding and Portico will work out its return, yield and cashflow.",
                    glyph = Glyph.PORTFOLIO,
                    actionLabel = "Add a property",
                    onAction = { state.resetDraft(); state.navigate(Route.ADD_PROPERTY) }
                )
                visible.isEmpty() -> EmptyState(
                    title = "Nothing matches",
                    body = "No property matches this search and filter combination. Clear them to see the full register.",
                    glyph = Glyph.SEARCH,
                    actionLabel = "Clear search and filters",
                    onAction = { state.portfolioQuery = ""; state.clearFilters() }
                )
                else -> visible.forEachIndexed { index, result ->
                    if (index > 0) Hairline()
                    PropertyRow(
                        result = result,
                        currency = currency,
                        detailed = true,
                        shareOfPortfolio = if (portfolio.portfolioValue > 0)
                            result.property.currentValue / portfolio.portfolioValue else 0.0,
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
                "Filter register",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Space.lg)
            )
            ChoiceRow("Country", countries, state.filterCountry) { state.filterCountry = it }
            ChoiceRow("Region", regions, state.filterRegion) { state.filterRegion = it }
            ChoiceRow("Property type", types, state.filterType) { state.filterType = it }
            ChoiceRow(
                "Performance",
                listOf("All", "Positive cashflow", "Negative cashflow"),
                state.filterPerformance
            ) { state.filterPerformance = it }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                SecondaryButton("Clear all", Modifier.weight(1f)) { state.clearFilters() }
                PrimaryButton("Show ${state.visibleProperties().size}", Modifier.weight(1f)) { onDismiss() }
            }
        }
    }
}
