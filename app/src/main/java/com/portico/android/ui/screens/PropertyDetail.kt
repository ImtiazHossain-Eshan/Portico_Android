@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.portico.android.ui.screens

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
            title = "Property not found",
            body = "This record is no longer in your register. It may have been deleted from another device.",
            actionLabel = "Back to portfolio",
            onAction = { state.selectDestination(Route.PORTFOLIO) },
            modifier = modifier
        )
        return
    }

    val property = result.property
    val tabs = listOf("Overview", "Income", "Expenses", "Documents", "Tax")

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
            Text(
                Money.format(property.currentValue, currency),
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(Space.xs))
            DeltaLine(
                delta = result.appreciation,
                text = "${Money.signed(result.appreciation, currency)}   ${Money.signedPercent(result.capitalRoi)} on cash in"
            )
            Spacer(Modifier.height(Space.md))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                SecondaryButton("Edit property", Modifier.weight(1f), glyph = Glyph.EDIT) {
                    state.loadDraftFrom(property)
                    state.navigate(Route.ADD_PROPERTY)
                }
                SecondaryButton(
                    "Delete",
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
            SecondaryButton("Add income", glyph = Glyph.INCOME) {
                state.transactionIsIncome = true
                state.showTransactionSheet = true
            }
            SecondaryButton("Add expense", glyph = Glyph.EXPENSE) {
                state.transactionIsIncome = false
                state.showTransactionSheet = true
            }
            SecondaryButton("Valuation", glyph = Glyph.VALUATION) { state.showValuationSheet = true }
            SecondaryButton("Upload", glyph = Glyph.UPLOAD) {
                state.uploadPropertyId = property.id
                state.uploadStage = com.portico.android.ui.UploadStage.FILE
                state.showUploadSheet = true
            }
        }

        SegmentedRow(tabs, state.propertyTab) { state.propertyTab = it }

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
            PanelHeader("Performance", supporting = "Interpolated from purchase to current value")
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
            SegmentedRow(ChartRangeOptions, state.chartRange.label) { label ->
                state.chartRange = com.portico.android.ui.ChartRange.entries.first { it.label == label }
            }
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Return and yield")
            MetricGrid(
                metrics = listOf(
                    rateMetric("Total ROI", result.totalRoi),
                    Metric("Cap rate", Money.percent(result.capRate), "on current value"),
                    Metric("Gross yield", Money.percent(result.grossYield), "before costs"),
                    Metric("Net yield", Money.percent(result.netYield), "after costs and tax"),
                    rateMetric("Cash on cash", result.cashOnCash),
                    rateMetric("Capital ROI", result.capitalRoi)
                ),
                columns = if (LocalWidthClass.current.isAtLeastMedium) 3 else 2
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Monthly", supporting = "What moves through the account each month")
            DataRow("Gross income", Money.format(result.monthlyGrossIncome, currency))
            Hairline()
            DataRow("Operating expenses", Money.format(-result.monthlyOperatingExpenses, currency), valueColor = PorticoTheme.semantic.loss)
            Hairline()
            DataRow("Taxes and fees", Money.format(-result.monthlyTaxes, currency), valueColor = PorticoTheme.semantic.loss)
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
            PanelHeader("Acquisition")
            DataRow("Purchase price", Money.format(property.purchasePrice, currency))
            Hairline()
            DataRow("Cash invested", Money.format(property.initialInvestment, currency))
            Hairline()
            if (property.financingAmount > 0) {
                DataRow("Financed", Money.format(property.financingAmount, currency))
                Hairline()
            }
            DataRow("Purchase date", property.purchaseDate)
            Hairline()
            DataRow("Held for", "${"%.1f".format(result.holdingYears)} years")
            Hairline()
            DataRow("Size", "${property.sizeSqm.toInt()} m²")
            Hairline()
            DataRow("Price per m²", Money.format(
                if (property.sizeSqm > 0) property.currentValue / property.sizeSqm else 0.0, currency
            ))
            Hairline()
            DataRow("Address", property.address, supporting = property.location)
            if (property.note.isNotBlank()) {
                Hairline()
                DataRow("Note", "", supporting = property.note)
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Manage")
            NavRow("Valuation and comparables", glyph = Glyph.VALUATION) {
                state.valuationPropertyId = property.id
                state.navigate(Route.VALUATION)
            }
            Hairline()
            NavRow("Ask the assistant about this property", glyph = Glyph.ASSISTANT) {
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
            PanelHeader("Income", supporting = "Monthly, recurring unless noted")
            if (entries.isEmpty()) {
                EmptyState(
                    title = "No income recorded",
                    body = "Add the rent this property collects and Portico can work out its yield and cashflow.",
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
                                state.notify("Income entry removed")
                            }
                        }
                    )
                }
                TotalRule()
                DataRow("Monthly total", Money.format(result.monthlyGrossIncome, currency), emphasise = true)
                DataRow("Annual total", Money.format(result.annualGrossIncome, currency))
                Spacer(Modifier.height(Space.sm))
            }
        }
        Box(Modifier.padding(horizontal = Space.lg)) {
            PrimaryButton("Add income", Modifier.fillMaxWidth(), glyph = Glyph.ADD) {
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
            PanelHeader("Expenses", supporting = "Monthly, recurring unless noted")
            if (entries.isEmpty()) {
                EmptyState(
                    title = "No expenses recorded",
                    body = "Maintenance, insurance, fees and taxes all reduce net yield. Add them to see the real return.",
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
                                state.notify("Expense removed")
                            }
                        }
                    )
                }
                TotalRule()
                DataRow("Operating monthly", Money.format(-result.monthlyOperatingExpenses, currency), emphasise = true, valueColor = semantic.loss)
                DataRow("Operating annually", Money.format(-result.annualOperatingExpenses, currency), valueColor = semantic.loss)
                Spacer(Modifier.height(Space.sm))
            }
        }
        Box(Modifier.padding(horizontal = Space.lg)) {
            PrimaryButton("Add expense", Modifier.fillMaxWidth(), glyph = Glyph.ADD) {
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
            PanelHeader("Documents", supporting = "Private to this workspace")
            if (docs.isEmpty()) {
                EmptyState(
                    title = "No documents yet",
                    body = "Keep the deed, lease, insurance and tax receipts for this property together and on-device.",
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
            PrimaryButton("Upload document", Modifier.fillMaxWidth(), glyph = Glyph.UPLOAD) {
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
                "Gross to net",
                supporting = "Annual, ${profile.jurisdiction.name}",
                action = "Assumptions",
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
            PanelHeader("Tax lines", supporting = profile.jurisdiction.countryName)
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
            DataRow("Annual tax", Money.format(result.annualTaxes, currency), emphasise = true, valueColor = PorticoTheme.semantic.loss)
            Spacer(Modifier.height(Space.sm))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("After tax")
            DataRow("Net yield", Money.percent(result.netYield))
            Hairline()
            DataRow("Gross yield", Money.percent(result.grossYield))
            Hairline()
            DataRow(
                "Tax as share of gross",
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
                    supporting = if (recurring) "Repeats every month" else "One-off, spread across the year",
                    error = error
                )
            }

            ChoiceRow("Category", categories, category) { category = it }

            Box(Modifier.padding(horizontal = Space.lg)) {
                PorticoField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Note (optional)",
                    placeholder = "Unit A, quarterly billing…"
                )
            }

            SwitchRow(
                label = "Recurring monthly",
                checked = recurring,
                supporting = "Turn off for a one-time amount",
                onCheckedChange = { recurring = it }
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                SecondaryButton("Cancel", Modifier.weight(1f)) { onDismiss() }
                PrimaryButton(if (isIncome) "Add income" else "Add expense", Modifier.weight(1f)) {
                    val value = amount.toDoubleOrNull()
                    if (value == null || value <= 0) {
                        error = "Enter an amount greater than zero."
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
                Text("Update valuation", style = MaterialTheme.typography.titleLarge)
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
                    label = "Current value",
                    currency = currency,
                    supporting = "Was ${Money.format(property.currentValue, currency)}",
                    error = error
                )
            }
            ChoiceRow(
                "Source",
                listOf("Owner estimate", "Comparable model", "Broker", "Formal appraisal"),
                source
            ) { source = it }

            if (delta != 0.0) {
                Panel(Modifier.padding(horizontal = Space.lg)) {
                    DataRow(
                        "Change",
                        Money.signed(delta, currency),
                        valueColor = PorticoTheme.semantic.forDelta(delta)
                    )
                    Hairline()
                    DataRow(
                        "Net yield after change",
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
                SecondaryButton("Cancel", Modifier.weight(1f)) { onDismiss() }
                PrimaryButton("Save valuation", Modifier.weight(1f)) {
                    val parsed = value.toDoubleOrNull()
                    if (parsed == null || parsed <= 0) {
                        error = "Enter a value greater than zero."
                        return@PrimaryButton
                    }
                    store.updateValuation(property.id, parsed, source)
                    state.notify("Valuation updated")
                    onDismiss()
                }
            }
        }
    }
}
