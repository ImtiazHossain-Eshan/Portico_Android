package com.portico.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme

/*
 * The tax module answers one question: how much of what this portfolio earns
 * does the investor actually keep? It runs the same waterfall as everywhere
 * else, then shows which rule took which slice — and lets the user change any
 * of those rules, because they are assumptions the user owns.
 */
@Composable
fun TaxScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val currency = store.profile.currency
    val results = store.financials()
    val portfolio = Finance.portfolio(results)
    val profile = store.taxProfile
    val semantic = PorticoTheme.semantic

    if (results.isEmpty()) {
        EmptyState(
            title = "No properties to tax",
            body = "Add a property and Portico will model its tax under your chosen jurisdiction.",
            glyph = Glyph.TAX,
            actionLabel = "Add a property",
            onAction = { state.resetDraft(); state.navigate(Route.ADD_PROPERTY) },
            modifier = modifier
        )
        return
    }

    val lines = profile.breakdown(
        portfolio.annualGrossIncome,
        portfolio.annualOperatingExpenses,
        portfolio.portfolioValue
    )

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Column(Modifier.padding(horizontal = Space.lg)) {
            MetricReadout(
                label = "Net income after tax",
                value = Money.format(portfolio.annualNetIncome, currency),
                delta = portfolio.annualNetIncome,
                deltaText = "${Money.percent(portfolio.netYield)} net yield",
                compact = true
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                "Jurisdiction",
                supporting = profile.jurisdiction.countryName,
                action = "Change",
                onAction = { state.navigate(Route.TAX_ASSUMPTIONS) }
            )
            DataRow("Region", profile.jurisdiction.name)
            Hairline()
            DataRow("Rules applied", "${lines.size}")
            if (profile.hasOverrides) {
                Hairline()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip("Edited", tone = MaterialTheme.colorScheme.primary, glyph = Glyph.EDIT)
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        "You've changed ${profile.overrides.size} rate from the default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.tertiaryText
                    )
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Gross to net", supporting = "Annual, across the portfolio")
            WaterfallLedger(
                steps = portfolio.waterfall(),
                currency = currency,
                animate = !store.preferences.reducedMotion
            )
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("What each rule takes")
            lines.forEachIndexed { index, line ->
                if (index > 0) Hairline()
                DataRow(
                    label = line.rule.label,
                    value = Money.format(line.amount, currency),
                    supporting = "${Money.percent(line.rule.ratePct, 2)} of ${line.rule.basis.label}",
                    valueColor = semantic.loss
                )
            }
            TotalRule()
            DataRow("Total tax", Money.format(portfolio.annualTaxes, currency), emphasise = true, valueColor = semantic.loss)
            Spacer(Modifier.height(Space.sm))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("The cost of tax", supporting = "Gross against net, side by side")
            DataRow("Gross yield", Money.percent(portfolio.grossYield))
            Hairline()
            DataRow("Net yield", Money.percent(portfolio.netYield), emphasise = true)
            Hairline()
            DataRow("Difference", Money.percent(portfolio.grossYield - portfolio.netYield), valueColor = semantic.loss)
            Hairline()
            DataRow("Net ROI on cash in", Money.percent(portfolio.totalRoi), emphasise = true)
            Hairline()
            DataRow(
                "Tax as share of gross income",
                Money.percent(
                    if (portfolio.annualGrossIncome > 0)
                        portfolio.annualTaxes / portfolio.annualGrossIncome * 100 else 0.0
                ),
                valueColor = semantic.loss
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("By property")
            results.forEachIndexed { index, result ->
                if (index > 0) Hairline()
                DataRow(
                    label = result.property.name,
                    value = Money.format(result.annualTaxes, currency),
                    supporting = "Net yield ${Money.percent(result.netYield)}",
                    valueColor = semantic.loss,
                    onClick = {
                        state.selectedPropertyId = result.property.id
                        state.propertyTab = "Tax"
                        state.navigate(Route.PROPERTY)
                    }
                )
            }
        }

        Box(Modifier.padding(horizontal = Space.lg)) {
            SecondaryButton("Edit tax assumptions", Modifier.fillMaxWidth(), glyph = Glyph.SETTINGS) {
                state.navigate(Route.TAX_ASSUMPTIONS)
            }
        }

        SyntheticNote(TAX_DISCLAIMER)
    }
}

// ------------------------------------------------------------- assumptions

@Composable
fun TaxAssumptionsScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val profile = store.taxProfile
    val semantic = PorticoTheme.semantic
    val grouped = TaxCatalog.byCountry

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Column(Modifier.padding(horizontal = Space.lg)) {
            Text("Where is this portfolio taxed?", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick a jurisdiction, then adjust any rate that doesn't match your situation.",
                style = MaterialTheme.typography.bodySmall,
                color = semantic.tertiaryText
            )
        }

        grouped.forEach { (country, jurisdictions) ->
            Panel(Modifier.padding(horizontal = Space.lg)) {
                PanelHeader(country)
                jurisdictions.forEachIndexed { index, jurisdiction ->
                    if (index > 0) Hairline()
                    DataRow(
                        label = jurisdiction.name,
                        value = if (jurisdiction.id == profile.jurisdictionId) "Selected" else "",
                        supporting = "${jurisdiction.rules.count { it.isRecurring }} recurring rules · ${jurisdiction.currency}",
                        valueColor = MaterialTheme.colorScheme.primary,
                        onClick = {
                            store.setTaxProfile { TaxProfile(jurisdiction.id, it.overrides) }
                            store.setProfile { it.copy(taxJurisdiction = jurisdiction.id, country = country) }
                            state.notify("Now modelling ${jurisdiction.name}")
                        },
                        trailing = if (jurisdiction.id == profile.jurisdictionId) {
                            {
                                PorticoIcon(
                                    Glyph.CHECK,
                                    size = 18.dp,
                                    tint = MaterialTheme.colorScheme.primary,
                                    contentDescription = "Selected"
                                )
                            }
                        } else null
                    )
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                "Rates for ${profile.jurisdiction.name}",
                supporting = "Drag to change. These are your assumptions, not filed figures.",
                action = if (profile.hasOverrides) "Reset" else null,
                onAction = if (profile.hasOverrides) {
                    {
                        store.setTaxProfile { it.clearOverrides() }
                        state.notify("Rates reset to defaults")
                    }
                } else null
            )
            profile.effectiveRules().forEachIndexed { index, rule ->
                if (index > 0) Hairline()
                Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "on ${rule.basis.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.tertiaryText
                            )
                        }
                        Text(
                            Money.percent(rule.ratePct, 2),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = rule.ratePct.toFloat(),
                        onValueChange = { value ->
                            store.setTaxProfile { it.withOverride(rule.id, value.toDouble()) }
                        },
                        valueRange = 0f..if (rule.basis == TaxBasis.PROPERTY_VALUE) 5f else 50f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        rule.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.tertiaryText
                    )
                    if (profile.overrides.containsKey(rule.id)) {
                        Spacer(Modifier.height(Space.sm))
                        StatusChip("Changed from default", tone = MaterialTheme.colorScheme.primary, glyph = Glyph.EDIT)
                    }
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Effect on this portfolio")
            val portfolio = store.portfolio()
            DataRow("Annual tax", Money.format(portfolio.annualTaxes, store.profile.currency), valueColor = semantic.loss)
            Hairline()
            DataRow("Net yield", Money.percent(portfolio.netYield), emphasise = true)
            Hairline()
            DataRow("Net income", Money.format(portfolio.annualNetIncome, store.profile.currency))
        }

        SyntheticNote(TAX_DISCLAIMER)
    }
}
