@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.portico.android.ui.screens
import com.portico.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.portico.android.data.PorticoStore
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme

@Composable
fun PropertyDetailScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val currency = store.profile.currency
    val result = store.financialsFor(state.selectedPropertyId)

    if (result == null) {
        EmptyState(
            title = stringResource(R.string.property_not_found),
            body = stringResource(R.string.this_record_is_no_longer_in_your_register_it_m),
            actionLabel = "Back to portfolio",
            onAction = { state.selectDestination(Route.PORTFOLIO) },
            modifier = modifier
        )
        return
    }

    val property = result.property
    /*
     * Two lists: the keys the state stores and the `when` below compares, and
     * the labels shown. Translating the stored value would break navigation,
     * so the selector matches by position instead of by text.
     */
    val tabKeys = listOf("Overview", "Income", "Expenses", "Documents", "Tax")
    val tabs = listOf(
        stringResource(R.string.title_overview),
        stringResource(R.string.income),
        stringResource(R.string.expenses),
        stringResource(R.string.documents),
        stringResource(R.string.tax)
    )

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        // ---- headline -------------------------------------------------------
        Box(Modifier.padding(horizontal = Space.lg)) {
            PropertyHero(
                photoUri = property.photoUris.firstOrNull(),
                propertyName = property.name,
                store = state.store
            )
        }
        Column(Modifier.padding(horizontal = Space.lg)) {
            SectionLabel("${property.location} · ${property.type}")
            Spacer(Modifier.height(Space.xs))
            FigureText(
                Money.format(property.currentValue, currency),
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(Space.xs))
            DeltaLine(
                delta = result.appreciation,
                text = stringResource(
                    R.string.delta_on_cash_in,
                    Money.signed(result.appreciation, currency),
                    Money.signedPercent(result.capitalRoi)
                )
            )
            Spacer(Modifier.height(Space.md))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                SecondaryButton(stringResource(R.string.edit_property), Modifier.weight(1f), glyph = Glyph.EDIT) {
                    state.loadDraftFrom(property)
                    state.navigate(Route.ADD_PROPERTY)
                }
                SecondaryButton(
                    stringResource(R.string.delete),
                    Modifier.weight(1f),
                    glyph = Glyph.DELETE,
                    destructive = true
                ) {
                    state.pendingDeletePropertyId = property.id
                }
            }
        }

        // ---- actions --------------------------------------------------------
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            SecondaryButton(stringResource(R.string.add_income), glyph = Glyph.INCOME) {
                state.transactionIsIncome = true
                state.showTransactionSheet = true
            }
            SecondaryButton(stringResource(R.string.add_expense), glyph = Glyph.EXPENSE) {
                state.transactionIsIncome = false
                state.showTransactionSheet = true
            }
            SecondaryButton(stringResource(R.string.valuation), glyph = Glyph.VALUATION) { state.showValuationSheet = true }
            SecondaryButton(stringResource(R.string.upload), glyph = Glyph.UPLOAD) {
                state.uploadPropertyId = property.id
                state.uploadStage = com.portico.android.ui.UploadStage.FILE
                state.showUploadSheet = true
            }
        }

        SegmentedRow(
            tabs,
            tabs[tabKeys.indexOf(state.propertyTab).coerceAtLeast(0)]
        ) { label -> state.propertyTab = tabKeys[tabs.indexOf(label)] }

        when (state.propertyTab) {
            "Income" -> PropertyIncomeTab(state, result)
            "Expenses" -> PropertyExpensesTab(state, result)
            "Documents" -> PropertyDocumentsTab(state, property)
            "Tax" -> PropertyTaxTab(state, result)
            else -> PropertyOverviewTab(state, result)
        }
    }

    if (state.showTransactionSheet) {
        TransactionSheet(state, property) { state.showTransactionSheet = false }
    }
    if (state.showValuationSheet) {
        ValuationSheet(state, property, result) { state.showValuationSheet = false }
    }
    if (state.showUploadSheet) {
        UploadSheet(state) { state.showUploadSheet = false }
    }
    PropertyDeleteDialog(state)
}

// ------------------------------------------------------------------ overview

@Composable
private fun PropertyOverviewTab(state: PorticoState, result: PropertyFinancials) {
    val store = state.store
    val currency = store.profile.currency
    val property = result.property
    val reduceMotion = store.preferences.reducedMotion

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.performance), supporting = stringResource(R.string.interpolated_from_purchase_to_current_value))
            ValueChart(
                series = Finance.valueSeries(
                    property, pointsFor(state.chartRange),
                    store.exchangeRates, currency
                ),
                currency = currency,
                rangeLabel = state.chartRange.label,
                animate = !reduceMotion
            )
            Spacer(Modifier.height(Space.md))
            run {
                val ranges = chartRangeOptions()
                SegmentedRow(ranges, ranges[com.portico.android.ui.ChartRange.entries.indexOf(state.chartRange)]) { label ->
                    state.chartRange = com.portico.android.ui.ChartRange.entries[ranges.indexOf(label)]
                }
            }
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.return_and_yield))
            MetricGrid(
                metrics = listOf(
                    rateMetric(stringResource(R.string.total_roi), result.totalRoi),
                    Metric(stringResource(R.string.cap_rate), Money.percent(result.capRate), stringResource(R.string.supp_on_current)),
                    Metric(stringResource(R.string.gross_yield), Money.percent(result.grossYield), stringResource(R.string.supp_before_costs)),
                    Metric(stringResource(R.string.net_yield), Money.percent(result.netYield), stringResource(R.string.after_costs_and_tax)),
                    rateMetric(stringResource(R.string.cash_on_cash), result.cashOnCash),
                    rateMetric(stringResource(R.string.capital_roi), result.capitalRoi)
                ),
                columns = if (LocalWidthClass.current.isAtLeastMedium) 3 else 2
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.monthly), supporting = stringResource(R.string.what_moves_through_the_account_each_month))
            DataRow(stringResource(R.string.gross_income), Money.format(result.monthlyGrossIncome, currency))
            Hairline()
            DataRow(stringResource(R.string.operating_expenses), Money.format(-result.monthlyOperatingExpenses, currency), valueColor = PorticoTheme.semantic.loss)
            Hairline()
            DataRow(stringResource(R.string.taxes_and_fees), Money.format(-result.monthlyTaxes, currency), valueColor = PorticoTheme.semantic.loss)
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
            PanelHeader(stringResource(R.string.acquisition))
            DataRow(stringResource(R.string.purchase_price), Money.format(property.purchasePrice, currency))
            Hairline()
            DataRow(stringResource(R.string.cash_invested), Money.format(property.initialInvestment, currency))
            Hairline()
            if (property.financingAmount > 0) {
                DataRow(stringResource(R.string.financed), Money.format(property.financingAmount, currency))
                Hairline()
            }
            DataRow(stringResource(R.string.purchase_date), property.purchaseDate)
            Hairline()
            DataRow(stringResource(R.string.held_for), "${"%.1f".format(java.util.Locale.ROOT, result.holdingYears)} years")
            Hairline()
            DataRow(stringResource(R.string.size), "${property.sizeSqm.toInt()} m²")
            Hairline()
            DataRow(stringResource(R.string.price_per_m2), Money.format(
                if (property.sizeSqm > 0) property.currentValue / property.sizeSqm else 0.0, currency
            ))
            Hairline()
            DataRow(stringResource(R.string.address), property.address, supporting = property.location)
            if (property.note.isNotBlank()) {
                Hairline()
                DataRow(stringResource(R.string.note), "", supporting = property.note)
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.manage))
            NavRow(stringResource(R.string.valuation_and_comparables), glyph = Glyph.VALUATION) {
                state.valuationPropertyId = property.id
                state.navigate(Route.VALUATION)
            }
            Hairline()
            NavRow(stringResource(R.string.ask_the_assistant_about_this_property), glyph = Glyph.ASSISTANT) {
                state.assistantContextPropertyId = property.id
                state.selectDestination(Route.ASSISTANT)
            }
        }
    }
}

// -------------------------------------------------------------------- income

@Composable
private fun PropertyIncomeTab(state: PorticoState, result: PropertyFinancials) {
    val store = state.store
    val currency = store.profile.currency
    val entries = store.incomeFor(result.property.id)

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.income), supporting = stringResource(R.string.monthly_recurring_unless_noted))
            if (entries.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.no_income_recorded),
                    body = stringResource(R.string.add_the_rent_this_property_collects_and_portic),
                    glyph = Glyph.INCOME,
                    actionLabel = "Add income",
                    onAction = { state.transactionIsIncome = true; state.showTransactionSheet = true }
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) Hairline()
                    DataRow(
                        label = entry.category,
                        value = Money.format(entry.amount, currency),
                        supporting = listOfNotNull(
                            entry.date,
                            entry.note.ifBlank { null },
                            if (!entry.recurring) "one-off" else null
                        ).joinToString(" · "),
                        valueColor = PorticoTheme.semantic.gain,
                        trailing = {
                            GlyphButton(Glyph.DELETE, "Remove ${entry.category}") {
                                store.removeIncome(entry.id)
                                state.notify(R.string.income_entry_removed)
                            }
                        }
                    )
                }
                TotalRule()
                DataRow(stringResource(R.string.monthly_total), Money.format(result.monthlyGrossIncome, currency), emphasise = true)
                DataRow(stringResource(R.string.annual_total), Money.format(result.annualGrossIncome, currency))
                Spacer(Modifier.height(Space.sm))
            }
        }
        Box(Modifier.padding(horizontal = Space.lg)) {
            PrimaryButton(stringResource(R.string.add_income), Modifier.fillMaxWidth(), glyph = Glyph.ADD) {
                state.transactionIsIncome = true
                state.showTransactionSheet = true
            }
        }
    }
}

// ------------------------------------------------------------------ expenses

@Composable
private fun PropertyExpensesTab(state: PorticoState, result: PropertyFinancials) {
    val store = state.store
    val currency = store.profile.currency
    val entries = store.expensesFor(result.property.id)
    val semantic = PorticoTheme.semantic

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.expenses), supporting = stringResource(R.string.monthly_recurring_unless_noted))
            if (entries.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.no_expenses_recorded),
                    body = stringResource(R.string.maintenance_insurance_fees_and_taxes_all_reduc),
                    glyph = Glyph.EXPENSE,
                    actionLabel = "Add expense",
                    onAction = { state.transactionIsIncome = false; state.showTransactionSheet = true }
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) Hairline()
                    DataRow(
                        label = entry.category,
                        value = Money.format(-entry.amount, currency),
                        supporting = listOfNotNull(
                            entry.date,
                            entry.note.ifBlank { null },
                            if (!ExpenseCategory.from(entry.category).isOperating) "tax or fee" else null
                        ).joinToString(" · "),
                        valueColor = semantic.loss,
                        trailing = {
                            GlyphButton(Glyph.DELETE, "Remove ${entry.category}") {
                                store.removeExpense(entry.id)
                                state.notify(R.string.expense_removed)
                            }
                        }
                    )
                }
                TotalRule()
                DataRow(stringResource(R.string.operating_monthly), Money.format(-result.monthlyOperatingExpenses, currency), emphasise = true, valueColor = semantic.loss)
                DataRow(stringResource(R.string.operating_annually), Money.format(-result.annualOperatingExpenses, currency), valueColor = semantic.loss)
                Spacer(Modifier.height(Space.sm))
            }
        }
        Box(Modifier.padding(horizontal = Space.lg)) {
            PrimaryButton(stringResource(R.string.add_expense), Modifier.fillMaxWidth(), glyph = Glyph.ADD) {
                state.transactionIsIncome = false
                state.showTransactionSheet = true
            }
        }
    }
}

// ----------------------------------------------------------------- documents

@Composable
private fun PropertyDocumentsTab(state: PorticoState, property: Property) {
    val store = state.store
    val docs = store.documentsFor(property.id)

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.documents), supporting = stringResource(R.string.private_to_this_workspace))
            if (docs.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.no_documents_yet),
                    body = stringResource(R.string.keep_the_deed_lease_insurance_and_tax_receipts),
                    glyph = Glyph.DOCUMENT,
                    actionLabel = "Upload a document",
                    onAction = {
                        state.uploadPropertyId = property.id
                        state.uploadStage = com.portico.android.ui.UploadStage.FILE
                        state.showUploadSheet = true
                    }
                )
            } else {
                docs.forEachIndexed { index, document ->
                    if (index > 0) Hairline()
                    DocumentRow(document, null) {
                        state.viewerDocumentId = document.id
                        state.navigate(Route.DOCUMENTS)
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = Space.lg)) {
            PrimaryButton(stringResource(R.string.upload_document), Modifier.fillMaxWidth(), glyph = Glyph.UPLOAD) {
                state.uploadPropertyId = property.id
                state.uploadStage = com.portico.android.ui.UploadStage.FILE
                state.showUploadSheet = true
            }
        }
    }
}

// ----------------------------------------------------------------------- tax

@Composable
private fun PropertyTaxTab(state: PorticoState, result: PropertyFinancials) {
    val store = state.store
    val currency = store.profile.currency
    val profile = store.taxProfile
    val lines = profile.breakdown(
        result.annualGrossIncome,
        result.annualOperatingExpenses,
        result.property.currentValue
    )

    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                stringResource(R.string.gross_to_net),
                supporting = "Annual, ${profile.jurisdiction.name}",
                action = stringResource(R.string.assumptions),
                onAction = { state.navigate(Route.TAX_ASSUMPTIONS) }
            )
            WaterfallLedger(
                steps = result.waterfall(),
                currency = currency,
                animate = !store.preferences.reducedMotion
            )
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.tax_lines), supporting = profile.jurisdiction.countryName)
            lines.forEachIndexed { index, line ->
                if (index > 0) Hairline()
                DataRow(
                    label = line.rule.label,
                    value = Money.format(line.amount, currency),
                    supporting = "${Money.percent(line.rule.ratePct, 2)} of ${line.rule.basis.label}",
                    valueColor = PorticoTheme.semantic.loss
                )
            }
            TotalRule()
            DataRow(stringResource(R.string.annual_tax), Money.format(result.annualTaxes, currency), emphasise = true, valueColor = PorticoTheme.semantic.loss)
            Spacer(Modifier.height(Space.sm))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.after_tax))
            DataRow(stringResource(R.string.net_yield), Money.percent(result.netYield))
            Hairline()
            DataRow(stringResource(R.string.gross_yield), Money.percent(result.grossYield))
            Hairline()
            DataRow(
                stringResource(R.string.tax_as_share_of_gross),
                Money.percent(
                    if (result.annualGrossIncome > 0) result.annualTaxes / result.annualGrossIncome * 100 else 0.0
                )
            )
        }

        SyntheticNote(TAX_DISCLAIMER)
    }
}

// ------------------------------------------------------------------- sheets

@Composable
fun TransactionSheet(state: PorticoState, property: Property, onDismiss: () -> Unit) {
    val store = state.store
    val isIncome = state.transactionIsIncome
    var amount by remember(isIncome) { mutableStateOf("") }
    var category by remember(isIncome) {
        mutableStateOf(if (isIncome) IncomeCategory.RENT.label else ExpenseCategory.MAINTENANCE.label)
    }
    var note by remember(isIncome) { mutableStateOf("") }
    var recurring by remember(isIncome) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val categories = if (isIncome) IncomeCategory.entries.map { it.label }
    else ExpenseCategory.entries.map { it.label }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PorticoTheme.semantic.panel) {
        Column(
            Modifier.padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            Column(Modifier.padding(horizontal = Space.lg)) {
                Text(
                    if (isIncome) "Add income" else "Add expense",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    property.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = PorticoTheme.semantic.tertiaryText
                )
            }

            Box(Modifier.padding(horizontal = Space.lg)) {
                CurrencyField(
                    value = amount,
                    onValueChange = { amount = it; error = null },
                    label = if (isIncome) "Monthly amount" else "Monthly amount",
                    currency = store.profile.currency,
                    supporting = if (recurring) "Repeats every month" else stringResource(R.string.one_off_spread_across_the_year),
                    error = error
                )
            }

            ChoiceRow(stringResource(R.string.category), categories, category) { category = it }

            Box(Modifier.padding(horizontal = Space.lg)) {
                PorticoField(
                    value = note,
                    onValueChange = { note = it },
                    label = stringResource(R.string.note_optional),
                    placeholder = stringResource(R.string.unit_a_quarterly_billing)
                )
            }

            SwitchRow(
                label = stringResource(R.string.recurring_monthly),
                checked = recurring,
                supporting = stringResource(R.string.turn_off_for_a_one_time_amount),
                onCheckedChange = { recurring = it }
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                SecondaryButton(stringResource(R.string.cancel), Modifier.weight(1f)) { onDismiss() }
                PrimaryButton(if (isIncome) "Add income" else "Add expense", Modifier.weight(1f)) {
                    val value = amount.toDoubleOrNull()
                    if (value == null || value <= 0) {
                        error = store.string(R.string.enter_an_amount_greater_than_zero)
                        return@PrimaryButton
                    }
                    val today = SimpleDate.today().format()
                    if (isIncome) {
                        store.addIncome(
                            IncomeEntry(PorticoStore.newId("i"), property.id, value, category, today, note, recurring)
                        )
                    } else {
                        store.addExpense(
                            ExpenseEntry(PorticoStore.newId("e"), property.id, value, category, today, note, recurring)
                        )
                    }
                    state.notify(if (isIncome) "Income recorded" else "Expense recorded")
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun ValuationSheet(
    state: PorticoState,
    property: Property,
    result: PropertyFinancials,
    onDismiss: () -> Unit
) {
    val store = state.store
    val currency = store.profile.currency
    var value by remember { mutableStateOf(property.currentValue.toInt().toString()) }
    var source by remember { mutableStateOf("Owner estimate") }
    var error by remember { mutableStateOf<String?>(null) }

    val newValue = value.toDoubleOrNull() ?: property.currentValue
    val delta = newValue - property.currentValue

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PorticoTheme.semantic.panel) {
        Column(
            Modifier.padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            Column(Modifier.padding(horizontal = Space.lg)) {
                Text(stringResource(R.string.update_valuation), style = MaterialTheme.typography.titleLarge)
                Text(
                    property.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = PorticoTheme.semantic.tertiaryText
                )
            }
            Box(Modifier.padding(horizontal = Space.lg)) {
                CurrencyField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = stringResource(R.string.current_value),
                    currency = currency,
                    supporting = "Was ${Money.format(property.currentValue, currency)}",
                    error = error
                )
            }
            ChoiceRow(
                stringResource(R.string.source),
                listOf("Owner estimate", "Comparable model", "Broker", "Formal appraisal"),
                source
            ) { source = it }

            if (delta != 0.0) {
                Panel(Modifier.padding(horizontal = Space.lg)) {
                    DataRow(
                        stringResource(R.string.change),
                        Money.signed(delta, currency),
                        valueColor = PorticoTheme.semantic.forDelta(delta)
                    )
                    Hairline()
                    DataRow(
                        stringResource(R.string.net_yield_after_change),
                        Money.percent(
                            if (newValue > 0) result.annualNetIncome / newValue * 100 else 0.0
                        )
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                SecondaryButton(stringResource(R.string.cancel), Modifier.weight(1f)) { onDismiss() }
                PrimaryButton(stringResource(R.string.save_valuation), Modifier.weight(1f)) {
                    val parsed = value.toDoubleOrNull()
                    if (parsed == null || parsed <= 0) {
                        error = store.string(R.string.enter_a_value_greater_than_zero)
                        return@PrimaryButton
                    }
                    store.updateValuation(property.id, parsed, source)
                    state.notify(R.string.valuation_updated)
                    onDismiss()
                }
            }
        }
    }
}
