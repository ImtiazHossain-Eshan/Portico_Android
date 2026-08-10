package com.portico.android.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.portico.android.model.DraftProperty
import com.portico.android.model.Property
import com.portico.android.ui.PorticoState
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoOrange
import com.portico.android.ui.theme.PorticoTeal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PropertyDetailScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val property = state.properties.firstOrNull { it.id == state.selectedPropertyId } ?: return
    var tab by remember { mutableStateOf("Overview") }
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            property.photoUris.firstOrNull()?.let { PhotoThumbnail(it, Modifier.size(108.dp)) }
                ?: PlotPreview(property, modifier = Modifier.size(108.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(property.name, style = MaterialTheme.typography.headlineMedium)
                Text(property.location, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(7.dp))
                StatusPill(property.note, Color(property.accent))
            }
            EditButton { state.toastMessage = "Edit mode is ready for connected data" }
        }
        RulePanel(modifier = Modifier.fillMaxWidth(), emphasized = true) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Current value", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatCurrency(property.currentValue), style = MaterialTheme.typography.headlineLarge)
                Text("${formatCurrency(property.returnValue)} unrealized return · ${property.roi}% ROI", style = MaterialTheme.typography.bodyMedium, color = PorticoTeal)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RulePanel(modifier = Modifier.weight(1f)) { MetricCell("Net yield", "${property.netYield}%", "After costs", true) }
            RulePanel(modifier = Modifier.weight(1f)) { MetricCell("Cashflow", formatCurrency(property.monthlyCashflow), "Monthly", true) }
            RulePanel(modifier = Modifier.weight(1f)) { MetricCell("Cap rate", "${property.capRate}%", "Unlevered", true) }
        }
        ScrollableTabRow(selectedTabIndex = listOf("Overview", "Income", "Expenses", "Documents", "Tax").indexOf(tab), edgePadding = 0.dp, containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.primary, divider = {}) {
            listOf("Overview", "Income", "Expenses", "Documents", "Tax").forEach { label ->
                Tab(selected = tab == label, onClick = { tab = label }, text = { Text(label, maxLines = 1, softWrap = false) })
            }
        }
        when (tab) {
            "Overview" -> PropertyOverview(state, property)
            "Income" -> PropertyIncome(property)
            "Expenses" -> PropertyExpenses(property)
            "Documents" -> PropertyDocuments(state, property)
            "Tax" -> PropertyTax(property)
        }
        SectionHeading("Actions", "Keep the record current")
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Add income", Icons.Outlined.Payments) { state.transactionType = "Income"; state.showTransactionDialog = true }
            ActionButton("Add expense", Icons.Outlined.AttachMoney) { state.transactionType = "Expense"; state.showTransactionDialog = true }
            ActionButton("Upload document", Icons.Outlined.UploadFile) { state.navigate("documents") }
            ActionButton("Update value", Icons.Outlined.Update) {
                val index = state.properties.indexOfFirst { it.id == property.id }
                if (index >= 0) state.properties[index] = property.copy(currentValue = property.currentValue + 8_600.0)
                state.toastMessage = "Valuation updated by +\$8,600"
            }
        }
        if (state.showTransactionDialog) TransactionDialog(state, property)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PropertyOverview(state: PorticoState, property: Property) {
    var selectedRange by remember { mutableStateOf("1Y") }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeading("Performance", "The line is your historical value, the numbers are net")
        RulePanel {
            Column(modifier = Modifier.padding(16.dp)) {
                TrendChart(modifier = Modifier.fillMaxWidth().height(150.dp), period = selectedRange)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("1M", "6M", "1Y", "5Y", "All").forEachIndexed { index, label ->
                        FilterChip(
                            selected = selectedRange == label,
                            onClick = {
                                selectedRange = label
                                state.toastMessage = "Showing $label performance"
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
        SectionHeading("Overview", "Acquisition and physical facts")
        RulePanel {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailLine("Purchase price", formatCurrency(property.purchasePrice), Icons.Outlined.AttachMoney)
                DetailLine("Purchase date", property.purchaseDate, Icons.Outlined.CalendarMonth)
                DetailLine("Property type", property.type, Icons.Outlined.HomeWork)
                DetailLine("Size", property.size, Icons.Outlined.HomeWork)
                DetailLine("Address", property.location, Icons.Outlined.HomeWork)
            }
        }
    }
}

@Composable
private fun PropertyIncome(property: Property) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeading("Income register", "March 2026 · ${property.name}")
        RulePanel {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailLine("Rental income", formatCurrency(property.monthlyIncome), Icons.Outlined.Payments, PorticoTeal)
                DetailLine("Other income", "\$0", Icons.Outlined.AddCircleOutline, PorticoMuted)
                HorizontalDivider()
                DetailLine("Total gross income", formatCurrency(property.monthlyIncome), Icons.Outlined.AttachMoney, PorticoTeal)
            }
        }
        ActivityRow(com.portico.android.model.ActivityItem("Rent received", "Monthly rental income", formatCurrency(property.monthlyIncome), "21 Mar", com.portico.android.model.ActivityTone.Positive))
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Export income history") }
    }
}

@Composable
private fun PropertyExpenses(property: Property) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeading("Expense register", "Keep operating costs separate from tax")
        RulePanel {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailLine("Maintenance", formatCurrency(property.monthlyExpenses * .41), Icons.Outlined.HomeWork, PorticoOrange)
                DetailLine("Insurance", formatCurrency(property.monthlyExpenses * .18), Icons.Outlined.Description, PorticoOrange)
                DetailLine("Fees & taxes", formatCurrency(property.monthlyExpenses * .41), Icons.Outlined.ReceiptLong, PorticoOrange)
                HorizontalDivider()
                DetailLine("Total monthly expenses", formatCurrency(property.monthlyExpenses), Icons.Outlined.AttachMoney, PorticoOrange)
            }
        }
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Export expense history") }
    }
}

@Composable
private fun PropertyDocuments(state: PorticoState, property: Property) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("Private documents", "Stored for ${property.name}", "Open library", onAction = { state.navigate("documents") })
        state.documents.filter { it.property == property.name }.forEach { document ->
            DocumentRow(document = document, onClick = { state.showViewerDialog = true })
        }
        if (state.documents.none { it.property == property.name }) EmptyState("No documents yet", "Upload a deed, lease, or tax record to keep this plot complete.") { state.showDocumentDialog = true }
    }
}

@Composable
private fun PropertyTax(property: Property) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading("Net return bridge", "A readable path from gross rent to investor return")
        RulePanel {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TaxBridgeLine("Gross income", formatCurrency(property.monthlyIncome), PorticoTeal)
                TaxBridgeLine("Operating expenses", "−${formatCurrency(property.monthlyExpenses)}", PorticoOrange)
                TaxBridgeLine("Taxes + fees", "−${formatCurrency(property.monthlyExpenses * .22)}", PorticoOrange)
                HorizontalDivider()
                TaxBridgeLine("Net income", formatCurrency(property.monthlyCashflow * .78), PorticoTeal)
                TaxBridgeLine("Net yield", "${property.netYield}%", PorticoTeal)
                TaxBridgeLine("Net ROI", "${property.roi}%", PorticoTeal)
            }
        }
    }
}

@Composable
fun DetailLine(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = PorticoMuted) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun TaxBridgeLine(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.height(48.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label)
    }
}

@Composable
private fun TransactionDialog(state: PorticoState, property: Property) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(if (state.transactionType == "Income") "Rent" else "Maintenance") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { state.showTransactionDialog = false },
        title = { Text("Add ${state.transactionType.lowercase()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(property.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, prefix = { Text("\$ ") }, singleLine = true)
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { state.showTransactionDialog = false; state.toastMessage = "${state.transactionType} added to ${property.name}" }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = { state.showTransactionDialog = false }) { Text("Cancel") } }
    )
}

@Composable
fun AddPropertyScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val stepTitles = listOf("Property", "Purchase", "Income", "Expenses", "Analysis", "Review")
    val step = state.addStep.coerceIn(0, stepTitles.lastIndex)
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Build a new plot", style = MaterialTheme.typography.headlineMedium)
        Text("A six-step record keeps acquisition facts and return assumptions together.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            stepTitles.forEachIndexed { index, title ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
                    Spacer(Modifier.height(6.dp))
                    Text(title, style = MaterialTheme.typography.labelSmall, color = if (index == step) MaterialTheme.colorScheme.primary else PorticoMuted)
                }
            }
        }
        when (step) {
            0 -> PropertyStep(state)
            1 -> PurchaseStep(state)
            2 -> IncomeStep(state)
            3 -> ExpensesStep(state)
            4 -> AnalysisStep(state)
            else -> ReviewStep(state)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) OutlinedButton(onClick = { state.addStep-- }, modifier = Modifier.weight(1f)) { Text("Back") }
            if (step < stepTitles.lastIndex) Button(onClick = { state.addStep++ }, modifier = Modifier.weight(1f)) { Text("Next") }
            else Button(onClick = { saveDraft(state); state.selectDestination("portfolio") }, modifier = Modifier.weight(1f)) { Text("Save property") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PropertyStep(state: PorticoState) {
    val countryOptions = listOf("Bangladesh", "Uruguay", "Argentina")
    val regionOptions = regionsFor(state.draft.country)
    val typeOptions = listOf("Residential", "Multi-family", "Short stay", "Commercial")
    FormPanel("Property facts", "Start with the place, not the math.") {
        DraftField("Property name", state.draft.name, { value -> state.updateDraft { draft -> draft.copy(name = value) } })
        DraftField("Address", state.draft.address, { value -> state.updateDraft { draft -> draft.copy(address = value) } })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownField("Country", state.draft.country, countryOptions, modifier = Modifier.weight(1f)) { country ->
                state.updateDraft { draft -> draft.copy(country = country, region = regionsFor(country).first()) }
            }
            DropdownField("Region", state.draft.region, regionOptions, modifier = Modifier.weight(1f)) { region ->
                state.updateDraft { draft -> draft.copy(region = region) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownField("Property type", state.draft.type, typeOptions, modifier = Modifier.weight(1f)) { type ->
                state.updateDraft { draft -> draft.copy(type = type) }
            }
            DraftField("Size", state.draft.size, { value -> state.updateDraft { draft -> draft.copy(size = value) } }, Modifier.weight(1f))
        }
        PropertyPhotoPicker(state)
    }
}

@Composable
private fun PropertyPhotoPicker(state: PorticoState) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            state.updateDraft { draft -> draft.copy(photoUris = uris.take(6).map { it.toString() }) }
            state.toastMessage = "${uris.size.coerceAtMost(6)} property photo(s) attached"
        }
    }
    val photoCount = state.draft.photoUris.size
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            state.draft.photoUris.firstOrNull()?.let { PhotoThumbnail(it, Modifier.size(42.dp)) }
                ?: SurfaceIcon(Icons.Outlined.UploadFile, MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text("Property photos", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (photoCount == 0) "Add up to 6 reference images" else "$photoCount photo(s) ready to attach",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { picker.launch("image/*") }) {
                Text(if (photoCount == 0) "Add photos" else "Replace")
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(uriString: String, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uriString) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Property photo",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(12.dp))
        )
    } else {
        SurfaceIcon(Icons.Outlined.UploadFile, MaterialTheme.colorScheme.primary, modifier)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, value: String, options: List<String>, modifier: Modifier = Modifier, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Select") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

private fun regionsFor(country: String): List<String> = when (country) {
    "Bangladesh" -> listOf("Dhaka", "Chattogram", "Sylhet", "Rajshahi")
    "Argentina" -> listOf("Buenos Aires", "Córdoba", "Mendoza", "Santa Fe")
    else -> listOf("Montevideo", "Canelones", "Maldonado", "Colonia")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseStep(state: PorticoState) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    FormPanel("Purchase position", "Record cost basis and financing assumptions.") {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.draft.purchaseDate,
                onValueChange = {},
                readOnly = true,
                label = { Text("Purchase date") },
                placeholder = { Text("Choose a date") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.matchParentSize().padding(end = 64.dp).clickable { showDatePicker = true }
            )
            IconButton(onClick = { showDatePicker = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Open calendar")
            }
        }
        DraftField("Purchase price", state.draft.purchasePrice, { value -> state.updateDraft { draft -> draft.copy(purchasePrice = value) } }, prefix = "\$ ")
        DraftField("Initial investment", state.draft.initialInvestment, { value -> state.updateDraft { draft -> draft.copy(initialInvestment = value) } }, prefix = "\$ ")
        DraftField("Financing", state.draft.financing, { value -> state.updateDraft { draft -> draft.copy(financing = value) } }, prefix = "\$ ")
    }
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val formatted = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(millis))
                        state.updateDraft { draft -> draft.copy(purchaseDate = formatted) }
                    }
                    showDatePicker = false
                }) { Text("Use date") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState, showModeToggle = false)
        }
    }
}

@Composable
private fun IncomeStep(state: PorticoState) {
    FormPanel("Income assumptions", "Use a conservative monthly baseline.") {
        DraftField("Expected monthly rent", state.draft.monthlyRent, { value -> state.updateDraft { draft -> draft.copy(monthlyRent = value) } }, prefix = "\$ ")
        DraftField("Other monthly income", state.draft.otherIncome, { value -> state.updateDraft { draft -> draft.copy(otherIncome = value) } }, prefix = "\$ ")
        StatusPill("Illustrative inputs · editable later", PorticoMuted)
    }
}

@Composable
private fun ExpensesStep(state: PorticoState) {
    FormPanel("Operating costs", "Keep recurring costs visible before looking at yield.") {
        DraftField("Property taxes", state.draft.propertyTaxes, { value -> state.updateDraft { draft -> draft.copy(propertyTaxes = value) } }, prefix = "\$ ")
        DraftField("Maintenance", state.draft.maintenance, { value -> state.updateDraft { draft -> draft.copy(maintenance = value) } }, prefix = "\$ ")
        DraftField("Insurance", state.draft.insurance, { value -> state.updateDraft { draft -> draft.copy(insurance = value) } }, prefix = "\$ ")
        DraftField("Fees", state.draft.fees, { value -> state.updateDraft { draft -> draft.copy(fees = value) } }, prefix = "\$ ")
        DraftField("Recurring expenses", state.draft.recurringExpenses, { value -> state.updateDraft { draft -> draft.copy(recurringExpenses = value) } }, prefix = "\$ ")
    }
}

@Composable
private fun AnalysisStep(state: PorticoState) {
    val draft = state.draft
    val price = draft.purchasePrice.toDoubleOrNull() ?: 0.0
    val rent = draft.monthlyRent.toDoubleOrNull() ?: 0.0
    val expenses = listOf(draft.propertyTaxes, draft.maintenance, draft.insurance, draft.fees, draft.recurringExpenses).sumOf { it.toDoubleOrNull() ?: 0.0 }
    val annualNet = (rent - expenses) * 12
    val capRate = if (price > 0) annualNet / price * 100 else 0.0
    FormPanel("Investment analysis", "The assumptions flow directly into net return.") {
        AnalysisMetric("Gross income", formatCurrency(rent * 12), "12-month rent")
        AnalysisMetric("Operating expenses", "−${formatCurrency(expenses * 12)}", "Annualized costs")
        AnalysisMetric("Net cashflow", formatCurrency(annualNet), "Before financing")
        AnalysisMetric("Cap rate", "${"%.1f".format(capRate)}%", "Unlevered return")
        AnalysisMetric("Net yield", "${"%.1f".format(if (price > 0) annualNet / price * 100 else 0.0)}%", "Net / purchase price")
    }
}

@Composable
private fun ReviewStep(state: PorticoState) {
    val draft = state.draft
    FormPanel("Review the plot", "Check the record before it enters your perimeter.") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)))
            Column {
                Text(draft.name.ifBlank { "Untitled property" }, style = MaterialTheme.typography.titleLarge)
                Text(draft.address.ifBlank { "Address to be added" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        listOf("Country" to draft.country, "Property type" to draft.type, "Purchase price" to "${if (draft.purchasePrice.isBlank()) "Not set" else "\$${draft.purchasePrice}"}", "Monthly rent" to "${if (draft.monthlyRent.isBlank()) "Not set" else "\$${draft.monthlyRent}"}").forEach { (label, value) -> DetailLine(label, value, Icons.Outlined.HomeWork) }
        StatusPill("All values remain editable after save", PorticoTeal)
    }
}

@Composable
private fun FormPanel(title: String, supporting: String, content: @Composable ColumnScope.() -> Unit) {
    RulePanel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        })
    }
}

@Composable
private fun DraftField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), prefix: String? = null) {
    OutlinedTextField(value = value, onValueChange = onValueChange, label = { Text(label) }, prefix = if (prefix != null) ({ Text(prefix) }) else null, modifier = modifier, singleLine = true)
}

@Composable
private fun AnalysisMetric(label: String, value: String, supporting: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Text(label, style = MaterialTheme.typography.bodyMedium); Text(supporting, style = MaterialTheme.typography.bodySmall, color = PorticoMuted) }
        Text(value, style = MaterialTheme.typography.titleMedium, color = if (label.contains("expense", true)) PorticoOrange else PorticoTeal)
    }
}

private fun saveDraft(state: PorticoState) {
    val draft = state.draft
    val price = draft.purchasePrice.toDoubleOrNull() ?: 250_000.0
    val monthlyRent = draft.monthlyRent.toDoubleOrNull() ?: 1_800.0
    val expenses = listOf(draft.propertyTaxes, draft.maintenance, draft.insurance, draft.fees, draft.recurringExpenses).sumOf { it.toDoubleOrNull() ?: 420.0 }
    val id = (state.properties.maxOfOrNull { it.id } ?: 0) + 1
    val accent = when ((id - 1) % 4) {
        0 -> 0xFFE3744BL
        1 -> 0xFF3C8D87L
        2 -> 0xFF8E6EAEL
        else -> 0xFFA86F32L
    }
    state.properties.add(Property(id, draft.name.ifBlank { "New property" }, draft.address.ifBlank { "Location pending" }, draft.type, price, price * 1.06, draft.purchaseDate.ifBlank { "21 Mar 2026" }, draft.size.ifBlank { "—" }, monthlyRent, expenses, 6.2, 5.8, monthlyRent * 12 / price * 100, (monthlyRent - expenses) * 12 / price * 100, accent, "Newly added plot", draft.photoUris))
    state.activities.add(0, com.portico.android.model.ActivityItem("Property added", draft.name.ifBlank { "New property" }, "NEW", "Just now", com.portico.android.model.ActivityTone.Positive))
    state.resetDraft()
    state.toastMessage = "Property added to your perimeter"
}
