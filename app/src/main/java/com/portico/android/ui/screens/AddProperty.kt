@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.portico.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme

private val stepTitles = listOf("Property", "Purchase", "Income", "Expenses", "Analysis", "Review")

/*
 * Six steps, one vertical column, keyboard-safe. Step 5 is the point of the
 * whole flow: the moment the numbers the user just typed become a return they
 * can judge — computed live, so changing rent on step 3 moves the yield here.
 */
@Composable
fun AddPropertyScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val currency = store.profile.currency
    val step = state.addStep.coerceIn(0, stepTitles.lastIndex)
    val draft = state.draft
    val isEditing = state.editingPropertyId != null

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        StepIndicator(step, Modifier.padding(horizontal = Space.lg, vertical = Space.sm))

        Column(Modifier.padding(horizontal = Space.lg)) {
            Text(stepTitles[step], style = MaterialTheme.typography.titleLarge)
            Text(
                when (step) {
                    0 -> "Where it is and what kind of property it is."
                    1 -> "What you paid, and how much of it was your own cash."
                    2 -> "What it collects each month."
                    3 -> "What it costs to run each month."
                    4 -> "What those numbers mean as a return."
                    else -> "Check it over, then save."
                },
                style = MaterialTheme.typography.bodySmall,
                color = PorticoTheme.semantic.tertiaryText
            )
        }

        when (step) {
            0 -> StepProperty(state)
            1 -> StepPurchase(state, currency)
            2 -> StepIncome(state, currency)
            3 -> StepExpenses(state, currency)
            4 -> StepAnalysis(state, currency)
            else -> StepReview(state, currency)
        }

        draft.validationHint(step)?.let { hint ->
            Box(Modifier.padding(horizontal = Space.lg)) { InlineError(hint) }
        }

        // ---- navigation ------------------------------------------------------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            SecondaryButton(
                label = if (step == 0) "Cancel" else "Back",
                modifier = Modifier.weight(1f)
            ) {
                if (step == 0) {
                    if (draft.name.isNotBlank() || draft.purchasePrice.isNotBlank()) {
                        state.showDiscardDraftDialog = true
                    } else {
                        state.resetDraft()
                        if (!state.goBack()) state.selectDestination(Route.PORTFOLIO)
                    }
                } else state.addStep--
            }
            PrimaryButton(
                label = if (step == stepTitles.lastIndex) (if (isEditing) "Save changes" else "Save property") else "Next",
                modifier = Modifier.weight(1f),
                enabled = draft.isStepValid(step)
            ) {
                if (step == stepTitles.lastIndex) {
                    state.commitDraft()
                    state.notify(if (isEditing) "Property updated" else "Property added")
                    state.selectDestination(Route.PORTFOLIO)
                } else state.addStep++
            }
        }
    }

    if (state.showDiscardDraftDialog) {
        AlertDialog(
            onDismissRequest = { state.showDiscardDraftDialog = false },
            title = { Text("Discard this property?") },
            text = { Text("Nothing you've entered will be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    state.showDiscardDraftDialog = false
                    state.resetDraft()
                    state.selectDestination(Route.PORTFOLIO)
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { state.showDiscardDraftDialog = false }) { Text("Keep editing") }
            },
            containerColor = PorticoTheme.semantic.panel
        )
    }
}

@Composable
private fun StepIndicator(current: Int, modifier: Modifier = Modifier) {
    val semantic = PorticoTheme.semantic
    Column(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            stepTitles.indices.forEach { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                index < current -> MaterialTheme.colorScheme.primary
                                index == current -> MaterialTheme.colorScheme.primary
                                else -> semantic.panelSunk
                            }
                        )
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "Step ${current + 1} of ${stepTitles.size}",
            style = MaterialTheme.typography.labelSmall,
            color = semantic.tertiaryText
        )
    }
}

// -------------------------------------------------------------------- steps

@Composable
private fun StepProperty(state: PorticoState) {
    val draft = state.draft
    val countries = TaxCatalog.countries + listOf("Other")

    Column(
        Modifier.padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        PorticoField(draft.name, { v -> state.updateDraft { it.copy(name = v) } }, "Property name", placeholder = "Harbor House")
        PorticoField(draft.address, { v -> state.updateDraft { it.copy(address = v) } }, "Street address", placeholder = "Bulevar España 2340")
        PorticoField(draft.region, { v -> state.updateDraft { it.copy(region = v) } }, "City and neighbourhood", placeholder = "Montevideo · Pocitos")
        PorticoField(
            draft.sizeSqm,
            { v -> state.updateDraft { it.copy(sizeSqm = v.filter { c -> c.isDigit() || c == '.' }) } },
            "Size",
            placeholder = "184",
            keyboardType = KeyboardType.Decimal,
            supporting = "Square metres"
        )
    }
    Spacer(Modifier.height(Space.md))
    ChoiceRow("Country", countries, draft.country) { v -> state.updateDraft { it.copy(country = v) } }
    Spacer(Modifier.height(Space.md))
    ChoiceRow("Property type", PropertyType.entries.map { it.label }, draft.type) { v ->
        state.updateDraft { it.copy(type = v) }
    }
    Spacer(Modifier.height(Space.md))
    ChoiceRow("Figures entered in", Money.currencies, draft.currency) { v ->
        state.updateDraft { it.copy(currency = v) }
    }
    if (draft.currency != state.store.profile.currency) {
        SyntheticNote(
            "Entered in ${draft.currency}; the portfolio displays ${state.store.profile.currency}. " +
                "Conversion uses the rate you set in Preferences."
        )
    }
}

@Composable
private fun StepPurchase(state: PorticoState, currency: String) {
    val draft = state.draft
    Column(
        Modifier.padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        PorticoField(
            draft.purchaseDate,
            { v -> state.updateDraft { it.copy(purchaseDate = v) } },
            "Purchase date",
            placeholder = SimpleDate.today().format(),
            supporting = "Format: 14 Mar 2022"
        )
        CurrencyField(
            draft.purchasePrice,
            { v -> state.updateDraft { it.copy(purchasePrice = v) } },
            "Purchase price",
            currency
        )
        CurrencyField(
            draft.initialInvestment,
            { v -> state.updateDraft { it.copy(initialInvestment = v) } },
            "Cash invested",
            currency,
            supporting = "Your own money in. Leave blank if you paid the full price in cash — return is measured against this."
        )
        CurrencyField(
            draft.financing,
            { v -> state.updateDraft { it.copy(financing = v) } },
            "Financed amount",
            currency,
            supporting = "Mortgage or loan. Record the repayment as an expense on the next steps."
        )
    }
}

@Composable
private fun StepIncome(state: PorticoState, currency: String) {
    val draft = state.draft
    Column(
        Modifier.padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        CurrencyField(
            draft.monthlyRent,
            { v -> state.updateDraft { it.copy(monthlyRent = v) } },
            "Expected monthly rent",
            currency
        )
        CurrencyField(
            draft.otherIncome,
            { v -> state.updateDraft { it.copy(otherIncome = v) } },
            "Other monthly income",
            currency,
            supporting = "Parking, storage, services"
        )
        Panel {
            DataRow("Monthly gross", Money.format(draft.monthlyIncomeTotal, currency), emphasise = true)
            Hairline()
            DataRow("Annual gross", Money.format(draft.monthlyIncomeTotal * 12, currency))
        }
    }
}

@Composable
private fun StepExpenses(state: PorticoState, currency: String) {
    val draft = state.draft
    Column(
        Modifier.padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        CurrencyField(draft.maintenance, { v -> state.updateDraft { it.copy(maintenance = v) } }, "Maintenance", currency)
        CurrencyField(draft.insurance, { v -> state.updateDraft { it.copy(insurance = v) } }, "Insurance", currency)
        CurrencyField(draft.managementFees, { v -> state.updateDraft { it.copy(managementFees = v) } }, "Management and building fees", currency)
        CurrencyField(
            draft.propertyTax,
            { v -> state.updateDraft { it.copy(propertyTax = v) } },
            "Property tax",
            currency,
            supporting = "Leave at zero to use your jurisdiction's assumed rate instead"
        )
        CurrencyField(
            draft.otherExpenses,
            { v -> state.updateDraft { it.copy(otherExpenses = v) } },
            "Other recurring costs",
            currency,
            supporting = "Mortgage servicing, utilities, anything else monthly"
        )
        Panel {
            DataRow(
                "Monthly operating cost",
                Money.format(-draft.monthlyOperatingTotal, currency),
                emphasise = true,
                valueColor = PorticoTheme.semantic.loss
            )
        }
    }
}

/** The payoff: live analysis of what the user just entered. */
@Composable
private fun StepAnalysis(state: PorticoState, currency: String) {
    val result = state.draftFinancials()
    val store = state.store

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg), accent = true) {
            Column(Modifier.padding(Space.lg)) {
                SectionLabel("Monthly cashflow")
                Spacer(Modifier.height(Space.xs))
                Text(
                    Money.format(result.monthlyCashflow, currency),
                    style = MaterialTheme.typography.displaySmall,
                    color = PorticoTheme.semantic.forDelta(result.monthlyCashflow)
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (result.monthlyCashflow >= 0)
                        "This property covers its costs and returns cash each month."
                    else
                        "This property costs more to run than it collects. It can still be worth holding if the value grows, but it will draw on your cash.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PorticoTheme.semantic.tertiaryText
                )
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Return")
            MetricGrid(
                metrics = listOf(
                    Metric("Cap rate", Money.percent(result.capRate), "on purchase price"),
                    Metric("Gross yield", Money.percent(result.grossYield), "before costs"),
                    Metric("Net yield", Money.percent(result.netYield), "after costs and tax"),
                    rateMetric("Cash on cash", result.cashOnCash)
                ),
                columns = 2
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Gross to net", supporting = "Annual, ${store.taxProfile.jurisdiction.name}")
            WaterfallLedger(
                steps = result.waterfall(),
                currency = currency,
                animate = !store.preferences.reducedMotion
            )
            Spacer(Modifier.height(Space.md))
        }

        SyntheticNote(
            "Tax is estimated from your jurisdiction's assumptions. Change them any time in Tax assumptions."
        )
    }
}

@Composable
private fun StepReview(state: PorticoState, currency: String) {
    val draft = state.draft
    val result = state.draftFinancials()

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Property", action = "Edit", onAction = { state.addStep = 0 })
            DataRow("Name", draft.name.ifBlank { "—" })
            Hairline()
            DataRow("Address", draft.address.ifBlank { "—" }, supporting = draft.region)
            Hairline()
            DataRow("Country", draft.country)
            Hairline()
            DataRow("Type", draft.type)
            Hairline()
            DataRow("Size", "${draft.number(draft.sizeSqm).toInt()} m²")
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Purchase", action = "Edit", onAction = { state.addStep = 1 })
            DataRow("Purchase price", Money.format(draft.purchasePriceValue, currency))
            Hairline()
            DataRow("Cash invested", Money.format(draft.investmentValue, currency))
            Hairline()
            DataRow("Financed", Money.format(draft.number(draft.financing), currency))
            Hairline()
            DataRow("Purchase date", draft.purchaseDate.ifBlank { SimpleDate.today().format() })
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Monthly", action = "Edit", onAction = { state.addStep = 2 })
            DataRow("Income", Money.format(draft.monthlyIncomeTotal, currency), valueColor = PorticoTheme.semantic.gain)
            Hairline()
            DataRow("Operating cost", Money.format(-draft.monthlyOperatingTotal, currency), valueColor = PorticoTheme.semantic.loss)
            Hairline()
            DataRow("Tax", Money.format(-result.monthlyTaxes, currency), valueColor = PorticoTheme.semantic.loss)
            TotalRule()
            DataRow(
                "Net cashflow",
                Money.format(result.monthlyCashflow, currency),
                emphasise = true,
                valueColor = PorticoTheme.semantic.forDelta(result.monthlyCashflow)
            )
            Spacer(Modifier.height(Space.sm))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Return")
            MetricGrid(
                metrics = listOf(
                    Metric("Cap rate", Money.percent(result.capRate), "on purchase price"),
                    Metric("Net yield", Money.percent(result.netYield), "after costs and tax"),
                    Metric("Gross yield", Money.percent(result.grossYield), "before costs"),
                    rateMetric("Cash on cash", result.cashOnCash)
                ),
                columns = 2
            )
        }
    }
}
