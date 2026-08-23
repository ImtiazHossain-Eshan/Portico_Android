@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.portico.android.ui.screens
import com.portico.android.R
import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.humanError
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.launch

private val stepTitles = listOf("Property", "Purchase", "Income", "Expenses", "Analysis", "Review")

/*
 * Six steps, one vertical column, keyboard-safe. Step 5 is the point of the
 * whole flow: the moment the numbers the user just typed become a return they
 * can judge, computed live, so changing rent on step 3 moves the yield here.
 */
@Composable
fun AddPropertyScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = state.store
    val currency = store.profile.currency
    val step = state.addStep.coerceIn(0, stepTitles.lastIndex)
    val draft = state.draft
    val isEditing = state.editingPropertyId != null
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        StepIndicator(step, Modifier.padding(horizontal = Space.lg, vertical = Space.sm))

        Column(Modifier.padding(horizontal = Space.lg)) {
            Text(stepTitles[step], style = MaterialTheme.typography.titleLarge)
            Text(
                when (step) {
                    0 -> stringResource(R.string.where_it_is_and_what_kind_of_property_it_is)
                    1 -> stringResource(R.string.what_you_paid_and_how_much_of_it_was_your_own)
                    2 -> stringResource(R.string.what_it_collects_each_month)
                    3 -> stringResource(R.string.what_it_costs_to_run_each_month)
                    4 -> stringResource(R.string.what_those_numbers_mean_as_a_return)
                    else -> stringResource(R.string.check_it_over_then_save)
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

        draft.validationHint(step)?.let { hintRes ->
            val hint = stringResource(hintRes)
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
                enabled = draft.isStepValid(step) && !saving
            ) {
                if (step == stepTitles.lastIndex) {
                    scope.launch {
                        saving = true
                        runCatching { state.commitDraft() }
                            .onSuccess {
                                state.notify(if (isEditing) "Property updated" else "Property added")
                                state.selectDestination(Route.PORTFOLIO)
                            }
                            .onFailure { error ->
                                if (error is com.portico.android.data.PorticoBackendException &&
                                    error.code == "plan_limit_reached"
                                ) {
                                    state.showPaywall = true
                                } else {
                                    state.notify(humanError(context, error))
                                }
                            }
                        saving = false
                    }
                } else state.addStep++
            }
        }
    }

    if (state.showDiscardDraftDialog) {
        AlertDialog(
            onDismissRequest = { state.showDiscardDraftDialog = false },
            title = { Text(stringResource(R.string.discard_this_property)) },
            text = { Text(stringResource(R.string.nothing_you_ve_entered_will_be_saved)) },
            confirmButton = {
                TextButton(onClick = {
                    state.showDiscardDraftDialog = false
                    state.resetDraft()
                    state.selectDestination(Route.PORTFOLIO)
                }) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { state.showDiscardDraftDialog = false }) { Text(stringResource(R.string.keep_editing)) }
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
    val context = LocalContext.current

    /*
     * The system photo picker needs no storage permission and shows only what
     * the user chooses, so nothing else in their gallery is ever readable. The
     * URI is held with a persistable grant, otherwise it stops resolving after
     * a reboot and the property silently loses its picture.
     */
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            state.updateDraft { it.copy(photoUris = listOf(uri.toString())) }
        }
    }

    Column(
        Modifier.padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PropertyThumbnail(
                photoUri = draft.photoUris.firstOrNull(),
                propertyName = draft.name.ifBlank { "this property" },
                size = 64.dp
            )
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    if (draft.photoUris.isEmpty()) "Add a photograph" else "Photograph added",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.optional_it_appears_on_the_register_and_the_pr),
                    style = MaterialTheme.typography.bodySmall,
                    color = PorticoTheme.semantic.tertiaryText
                )
            }
            Spacer(Modifier.width(Space.sm))
            TextButton(onClick = {
                if (draft.photoUris.isEmpty()) {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } else {
                    state.updateDraft { it.copy(photoUris = emptyList()) }
                }
            }) {
                Text(if (draft.photoUris.isEmpty()) "Choose" else "Remove")
            }
        }
        Hairline()
        PorticoField(draft.name, { v -> state.updateDraft { it.copy(name = v) } }, "Property name", placeholder = stringResource(R.string.harbor_house))
        PorticoField(draft.address, { v -> state.updateDraft { it.copy(address = v) } }, "Street address", placeholder = stringResource(R.string.bulevar_espan_a_2340))
        PorticoField(draft.region, { v -> state.updateDraft { it.copy(region = v) } }, "City and neighbourhood", placeholder = stringResource(R.string.montevideo_pocitos))
        PorticoField(
            draft.sizeSqm,
            { v -> state.updateDraft { it.copy(sizeSqm = v.filter { c -> c.isDigit() || c == '.' }) } },
            "Size",
            placeholder = "184",
            keyboardType = KeyboardType.Decimal,
            supporting = stringResource(R.string.square_metres)
        )
    }
    Spacer(Modifier.height(Space.md))
    ChoiceRow(stringResource(R.string.country), countries, draft.country) { v -> state.updateDraft { it.copy(country = v) } }
    Spacer(Modifier.height(Space.md))
    ChoiceRow(stringResource(R.string.property_type), PropertyType.entries.map { it.label }, draft.type) { v ->
        state.updateDraft { it.copy(type = v) }
    }
    Spacer(Modifier.height(Space.md))
    ChoiceRow(stringResource(R.string.figures_entered_in), Money.currencies, draft.currency) { v ->
        state.updateDraft { it.copy(currency = v) }
    }
    if (draft.currency != state.store.profile.currency) {
        SyntheticNote(
            "Entered in ${draft.currency}; the portfolio displays ${state.store.profile.currency}. " +
                stringResource(R.string.conversion_uses_the_rate_you_set_in_preference)
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
            supporting = stringResource(R.string.format_14_mar_2022)
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
            supporting = stringResource(R.string.your_own_money_in_leave_blank_if_you_paid_the)
        )
        CurrencyField(
            draft.financing,
            { v -> state.updateDraft { it.copy(financing = v) } },
            "Financed amount",
            currency,
            supporting = stringResource(R.string.mortgage_or_loan_record_the_repayment_as_an_ex)
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
            supporting = stringResource(R.string.parking_storage_services)
        )
        Panel {
            DataRow(stringResource(R.string.monthly_gross), Money.format(draft.monthlyIncomeTotal, currency), emphasise = true)
            Hairline()
            DataRow(stringResource(R.string.annual_gross), Money.format(draft.monthlyIncomeTotal * 12, currency))
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
        CurrencyField(draft.managementFees, { v -> state.updateDraft { it.copy(managementFees = v) } }, stringResource(R.string.management_and_building_fees), currency)
        CurrencyField(
            draft.propertyTax,
            { v -> state.updateDraft { it.copy(propertyTax = v) } },
            "Property tax",
            currency,
            supporting = stringResource(R.string.leave_at_zero_to_use_your_jurisdiction_s_assum)
        )
        CurrencyField(
            draft.otherExpenses,
            { v -> state.updateDraft { it.copy(otherExpenses = v) } },
            "Other recurring costs",
            currency,
            supporting = stringResource(R.string.mortgage_servicing_utilities_anything_else_mon)
        )
        Panel {
            DataRow(
                stringResource(R.string.monthly_operating_cost),
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
                SectionLabel(stringResource(R.string.monthly_cashflow))
                Spacer(Modifier.height(Space.xs))
                Text(
                    Money.format(result.monthlyCashflow, currency),
                    style = MaterialTheme.typography.displaySmall,
                    color = PorticoTheme.semantic.forDelta(result.monthlyCashflow)
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    if (result.monthlyCashflow >= 0)
                        stringResource(R.string.this_property_covers_its_costs_and_returns_cas)
                    else
                        stringResource(R.string.this_property_costs_more_to_run_than_it_collec),
                    style = MaterialTheme.typography.bodySmall,
                    color = PorticoTheme.semantic.tertiaryText
                )
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.return_label))
            MetricGrid(
                metrics = listOf(
                    Metric(stringResource(R.string.cap_rate), Money.percent(result.capRate), "on purchase price"),
                    Metric(stringResource(R.string.gross_yield), Money.percent(result.grossYield), "before costs"),
                    Metric(stringResource(R.string.net_yield), Money.percent(result.netYield), stringResource(R.string.after_costs_and_tax)),
                    rateMetric(stringResource(R.string.cash_on_cash), result.cashOnCash)
                ),
                columns = 2
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.gross_to_net), supporting = "Annual, ${store.taxProfile.jurisdiction.name}")
            WaterfallLedger(
                steps = result.waterfall(),
                currency = currency,
                animate = !store.preferences.reducedMotion
            )
            Spacer(Modifier.height(Space.md))
        }

        SyntheticNote(
            stringResource(R.string.tax_is_estimated_from_your_jurisdiction_s_assu)
        )
    }
}

@Composable
private fun StepReview(state: PorticoState, currency: String) {
    val draft = state.draft
    val result = state.draftFinancials()

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.property), action = stringResource(R.string.edit), onAction = { state.addStep = 0 })
            DataRow(stringResource(R.string.name), draft.name.ifBlank { "-" })
            Hairline()
            DataRow(stringResource(R.string.address), draft.address.ifBlank { "-" }, supporting = draft.region)
            Hairline()
            DataRow(stringResource(R.string.country), draft.country)
            Hairline()
            DataRow(stringResource(R.string.type), draft.type)
            Hairline()
            DataRow(stringResource(R.string.size), "${draft.number(draft.sizeSqm).toInt()} m²")
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.purchase), action = stringResource(R.string.edit), onAction = { state.addStep = 1 })
            DataRow(stringResource(R.string.purchase_price), Money.format(draft.purchasePriceValue, currency))
            Hairline()
            DataRow(stringResource(R.string.cash_invested), Money.format(draft.investmentValue, currency))
            Hairline()
            DataRow(stringResource(R.string.financed), Money.format(draft.number(draft.financing), currency))
            Hairline()
            DataRow(stringResource(R.string.purchase_date), draft.purchaseDate.ifBlank { SimpleDate.today().format() })
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.monthly), action = stringResource(R.string.edit), onAction = { state.addStep = 2 })
            DataRow(stringResource(R.string.income), Money.format(draft.monthlyIncomeTotal, currency), valueColor = PorticoTheme.semantic.gain)
            Hairline()
            DataRow(stringResource(R.string.operating_cost), Money.format(-draft.monthlyOperatingTotal, currency), valueColor = PorticoTheme.semantic.loss)
            Hairline()
            DataRow(stringResource(R.string.tax), Money.format(-result.monthlyTaxes, currency), valueColor = PorticoTheme.semantic.loss)
            TotalRule()
            DataRow(
                stringResource(R.string.net_cashflow),
                Money.format(result.monthlyCashflow, currency),
                emphasise = true,
                valueColor = PorticoTheme.semantic.forDelta(result.monthlyCashflow)
            )
            Spacer(Modifier.height(Space.sm))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.return_label))
            MetricGrid(
                metrics = listOf(
                    Metric(stringResource(R.string.cap_rate), Money.percent(result.capRate), "on purchase price"),
                    Metric(stringResource(R.string.net_yield), Money.percent(result.netYield), stringResource(R.string.after_costs_and_tax)),
                    Metric(stringResource(R.string.gross_yield), Money.percent(result.grossYield), "before costs"),
                    rateMetric(stringResource(R.string.cash_on_cash), result.cashOnCash)
                ),
                columns = 2
            )
        }
    }
}
