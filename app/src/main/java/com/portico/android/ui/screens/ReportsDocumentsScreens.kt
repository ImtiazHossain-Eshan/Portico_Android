package com.portico.android.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.portico.android.model.AllocationSlice
import com.portico.android.model.DocumentStatus
import com.portico.android.model.PortfolioDocument
import com.portico.android.ui.PorticoState
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoOrange
import com.portico.android.ui.theme.PorticoTeal
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReportsScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val sections = listOf("Performance", "Cashflow", "Allocation", "Compare", "Gross vs net")
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Decisions need a legible trail", style = MaterialTheme.typography.headlineMedium)
        Text("Turn the register into a view you can act on.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ScrollableTabRow(
            selectedTabIndex = sections.indexOf(state.reportSection).coerceAtLeast(0),
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            sections.forEach { section ->
                Tab(
                    selected = state.reportSection == section,
                    onClick = { state.reportSection = section },
                    modifier = Modifier.height(52.dp),
                    text = { Text(section, maxLines = 1, softWrap = false) }
                )
            }
        }
        when (state.reportSection) {
            "Performance" -> PerformanceReport()
            "Cashflow" -> CashflowReport()
            "Allocation" -> AllocationReport()
            "Compare" -> CompareReport(state)
            else -> GrossNetReport()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PerformanceReport() {
    RulePanel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionHeading("Portfolio performance", "Value and return since acquisition")
            Text("\$984,300", style = MaterialTheme.typography.headlineLarge)
            Text("+\$98,600 · 11.1% total return", style = MaterialTheme.typography.bodyMedium, color = PorticoTeal)
            TrendChart(modifier = Modifier.fillMaxWidth().height(190.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("Apr 25", "Jul 25", "Oct 25", "Jan 26", "Mar 26").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = PorticoMuted) } }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RulePanel(modifier = Modifier.weight(1f)) { MetricCell("ROI", "11.1%", "Portfolio", true) }
        RulePanel(modifier = Modifier.weight(1f)) { MetricCell("Cap rate", "6.0%", "Weighted", true) }
        RulePanel(modifier = Modifier.weight(1f)) { MetricCell("Net yield", "6.9%", "Weighted", true) }
    }
}

@Composable
private fun CashflowReport() {
    val motion = LocalPorticoMotion.current
    val reveal = remember(motion) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
        } else {
            reveal.snapTo(1f)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        RulePanel {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionHeading("Cashflow register", "Monthly average · trailing 12 months")
                CashflowBar("Income", 0.84f, 7_490, reveal.value, PorticoTeal)
                CashflowBar("Expenses", 0.30f, -2_670, reveal.value, PorticoOrange)
                HorizontalDivider()
                CashflowBar("Net cashflow", 0.54f, 4_820, reveal.value, MaterialTheme.colorScheme.primary)
            }
        }
        RulePanel {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Monthly movement", style = MaterialTheme.typography.titleLarge)
                listOf("Jan" to 4_310, "Feb" to 4_680, "Mar" to 4_820).forEach { (month, amount) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(month)
                        AnimatedCashflowValue(amount, reveal.value, PorticoTeal, showPositiveSign = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun CashflowBar(label: String, fraction: Float, amount: Int, reveal: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            AnimatedCashflowValue(amount, reveal, color)
        }
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth((fraction * reveal).coerceIn(0f, 1f)).height(10.dp).background(color, RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
private fun AnimatedCashflowValue(value: Int, reveal: Float, color: Color, showPositiveSign: Boolean = false) {
    val current = (value * reveal.coerceIn(0f, 1f)).roundToInt()
    val sign = when {
        current < 0 -> "−"
        showPositiveSign && current > 0 -> "+"
        else -> ""
    }
    Text(
        "$sign\$${String.format(java.util.Locale.US, "%,d", abs(current))}",
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun AllocationReport() {
    val slices = listOf(AllocationSlice("Montevideo", .53f, 0xFFE3744BL), AllocationSlice("Canelones", .28f, 0xFF3C8D87L), AllocationSlice("Buenos Aires", .19f, 0xFF8E6EAEL))
    RulePanel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SectionHeading("Allocation", "Current value by location")
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AllocationDonut(slices, modifier = Modifier.size(160.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    slices.forEach { slice ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.foundation.layout.Box(modifier = Modifier.size(10.dp).background(Color(slice.color), RoundedCornerShape(3.dp)))
                            Text(slice.label, style = MaterialTheme.typography.bodySmall)
                            Text("${(slice.value * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            Text("The portfolio is weighted toward Montevideo residential property. Use the acquisition lab to test diversification.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompareReport(state: PorticoState) {
    RulePanel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("Property comparison", "Same measures, side by side")
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Column(modifier = Modifier.width(120.dp)) { Text("Measure", style = MaterialTheme.typography.labelMedium, color = PorticoMuted); Spacer(Modifier.height(20.dp)); listOf("Current value", "ROI", "Cap rate", "Net yield", "Cashflow").forEach { Text(it, modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall) } }
                state.properties.forEach { property ->
                    Column(modifier = Modifier.width(138.dp)) {
                        Text(property.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Spacer(Modifier.height(20.dp))
                        listOf(formatCurrency(property.currentValue, true), "${property.roi}%", "${property.capRate}%", "${property.netYield}%", formatCurrency(property.monthlyCashflow, true)).forEach { Text(it, modifier = Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = if (it.contains("%")) PorticoTeal else MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrossNetReport() {
    RulePanel {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeading("Gross → net", "The calculation trail behind return")
            val rows = listOf("Gross income" to "\$7,490", "Operating expenses" to "−\$2,670", "Taxes + fees" to "−\$620", "Net income" to "\$4,200", "Net yield" to "6.9%", "Net ROI" to "11.1%")
            rows.forEachIndexed { index, row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(row.first, style = if (index == 3) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
                    Text(row.second, style = if (index >= 4) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge, color = if (index == 1 || index == 2) PorticoOrange else if (index >= 3) PorticoTeal else MaterialTheme.colorScheme.onSurface)
                }
                if (index == 0 || index == 2 || index == 3) HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

@Composable
fun DocumentsScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val categories = listOf("All", "Contracts", "Deeds", "Leases", "Taxes", "Other")
    val docs = state.documents.filter { state.documentCategory == "All" || it.category == state.documentCategory }
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("A private shelf for the paper trail", style = MaterialTheme.typography.headlineMedium)
                Text("Contracts, deeds, leases, and tax records stay tied to their property.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { state.showDocumentDialog = true }) { Icon(Icons.Outlined.Add, contentDescription = "Upload document") }
        }
        RulePanel {
            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = PorticoTeal)
                Column(modifier = Modifier.weight(1f)) { Text("Private by default", style = MaterialTheme.typography.labelLarge); Text("Only you can access these demo records.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                StatusPill("Encrypted", PorticoTeal)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { categories.forEach { category -> FilterChip(selected = state.documentCategory == category, onClick = { state.documentCategory = category }, label = { Text(category) }) } }
        Text("${docs.size} documents", style = MaterialTheme.typography.labelLarge, color = PorticoMuted)
        if (docs.isEmpty()) EmptyState("Your shelf is empty", "Upload a private record to keep the portfolio complete.") { state.showDocumentDialog = true }
        docs.forEach { document -> DocumentRow(document, onClick = { state.showViewerDialog = true }) }
        if (state.showDocumentDialog) DocumentUploadDialog(state)
        if (state.showViewerDialog) DocumentViewerDialog(state)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun DocumentRow(document: PortfolioDocument, onClick: () -> Unit) {
    RulePanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SurfaceIcon(Icons.Outlined.PictureAsPdf, if (document.status == DocumentStatus.Failed) PorticoOrange else PorticoTeal)
            Column(modifier = Modifier.weight(1f)) { Text(document.name, style = MaterialTheme.typography.titleMedium); Text("${document.property} · ${document.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Column(horizontalAlignment = Alignment.End) { Text(document.size, style = MaterialTheme.typography.labelLarge); Text(document.date, style = MaterialTheme.typography.labelSmall, color = PorticoMuted) }
        }
    }
}

@Composable
private fun DocumentUploadDialog(state: PorticoState) {
    var name by remember { mutableStateOf("") }
    var property by remember { mutableStateOf(state.properties.firstOrNull()?.name ?: "Harbor House") }
    var category by remember { mutableStateOf("Leases") }
    androidx.compose.material3.AlertDialog(onDismissRequest = { state.showDocumentDialog = false }, title = { Text("Upload private document") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Document name") }, singleLine = true); OutlinedTextField(value = property, onValueChange = { property = it }, label = { Text("Property") }, singleLine = true); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Leases", "Deeds", "Taxes", "Other").forEach { FilterChip(selected = category == it, onClick = { category = it }, label = { Text(it) }) } }; OutlinedButton(onClick = { state.toastMessage = "File picker would open here" }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.AttachFile, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("Choose file") } } }, confirmButton = { Button(onClick = { val id = (state.documents.maxOfOrNull { it.id } ?: 0) + 1; state.documents.add(0, PortfolioDocument(id, name.ifBlank { "Untitled document" }, property, category, "21 Mar 2026", "1.2 MB")); state.showDocumentDialog = false; state.toastMessage = "Document uploaded" }) { Text("Upload") } }, dismissButton = { OutlinedButton(onClick = { state.showDocumentDialog = false }) { Text("Cancel") } })
}

@Composable
private fun DocumentViewerDialog(state: PorticoState) {
    androidx.compose.material3.AlertDialog(onDismissRequest = { state.showViewerDialog = false }, title = { Text("PDF viewer") }, text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { SurfaceIcon(Icons.Outlined.PictureAsPdf, PorticoOrange, Modifier.size(58.dp)); Text("Document preview", style = MaterialTheme.typography.titleLarge); Text("The local demo keeps document content private. A connected build would render the PDF here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); StatusPill("Ready to view", PorticoTeal) } }, confirmButton = { Button(onClick = { state.showViewerDialog = false }) { Text("Close") } })
}

@Composable
fun SurfaceIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier.size(42.dp)) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = .13f)) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(icon, contentDescription = null, tint = tint) } }
}

@Composable
fun TaxScreen(state: PorticoState, modifier: Modifier = Modifier) {
    var country by remember { mutableStateOf("Uruguay") }
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Tax, without the fog", style = MaterialTheme.typography.headlineMedium)
        Text("Make the difference between gross and net visible before it affects a decision.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Uruguay", "Argentina").forEach { FilterChip(selected = country == it, onClick = { country = it }, label = { Text(it) }) } }
        RulePanel(modifier = Modifier.fillMaxWidth(), emphasized = true) { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { SectionHeading("Tax position", "Initial assumptions · $country"); TaxBridgeLine("Gross income", "\$89,880", PorticoTeal); TaxBridgeLine("Operating expenses", "−\$32,040", PorticoOrange); TaxBridgeLine("Taxes + fees", "−\$7,440", PorticoOrange); HorizontalDivider(); TaxBridgeLine("Net income", "\$50,400", PorticoTeal); TaxBridgeLine("Net yield", "6.9%", PorticoTeal); TaxBridgeLine("Net ROI", "11.1%", PorticoTeal) } }
        SectionHeading("Tax assumptions", "These are illustrative until a jurisdiction is connected")
        RulePanel { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { DetailLine("Tax jurisdiction", if (country == "Uruguay") "Montevideo" else "Buenos Aires", Icons.Outlined.LocationOn); DetailLine("Property tax", "2.4% assumed", Icons.Outlined.ReceiptLong, PorticoOrange); DetailLine("Income tax", "Progressive · review", Icons.Outlined.Assessment, PorticoOrange); DetailLine("Capital gains", "Not included", Icons.Outlined.WarningAmber, PorticoMuted) } }
        OutlinedButton(onClick = { state.toastMessage = "Tax assumption editor opened" }, modifier = Modifier.fillMaxWidth()) { Text("Review assumptions") }
        Spacer(Modifier.height(24.dp))
    }
}
