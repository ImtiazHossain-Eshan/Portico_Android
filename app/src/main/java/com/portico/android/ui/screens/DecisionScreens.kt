package com.portico.android.ui.screens

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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.portico.android.ui.PorticoState
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoOrange
import com.portico.android.ui.theme.PorticoTeal

@Composable
fun ValuationScreen(state: PorticoState, modifier: Modifier = Modifier) {
    var address by remember { mutableStateOf("") }
    var propertyType by remember { mutableStateOf("Residential") }
    var size by remember { mutableStateOf("") }
    var hasResult by remember { mutableStateOf(false) }
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Survey the next value", style = MaterialTheme.typography.headlineMedium)
        Text("Compare a property's estimate with its purchase price and nearby market signals.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RulePanel { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Property inputs", style = MaterialTheme.typography.titleLarge); OutlinedTextField(address, { address = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Address") }, leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) }, singleLine = true); Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Residential", "Multi-family", "Short stay").forEach { FilterChip(selected = propertyType == it, onClick = { propertyType = it }, label = { Text(it) }) } }; OutlinedTextField(size, { size = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Size") }, suffix = { Text("m²") }, singleLine = true); Button(onClick = { hasResult = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.ManageSearch, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Estimate value") } } }
        if (hasResult) {
            RulePanel(modifier = Modifier.fillMaxWidth(), emphasized = true) { Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { SectionHeading("Estimated value", address.ifBlank { "Sample address" }); Text("\$512,000", style = MaterialTheme.typography.displayLarge); Text("Illustrative estimate · not a verified market quote", style = MaterialTheme.typography.bodySmall, color = PorticoMuted); Spacer(Modifier.height(5.dp)); ValuationBar("Estimated value", 0.84f, "\$512k", PorticoTeal); ValuationBar("Purchase price", 0.63f, "\$380k", PorticoOrange); ValuationBar("Market comparables", 0.75f, "\$458k", MaterialTheme.colorScheme.primary) } }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { RulePanel(modifier = Modifier.weight(1f)) { MetricCell("Rent estimate", "\$3,280", "Monthly", true) }; RulePanel(modifier = Modifier.weight(1f)) { MetricCell("Gross yield", "7.7%", "At estimate", true) }; RulePanel(modifier = Modifier.weight(1f)) { MetricCell("ROI", "34.7%", "On cost basis", true) } }
            SectionHeading("Comparable properties", "Synthetic examples for the demo")
            listOf("Marina View 02" to "\$498k", "Pocitos Garden" to "\$526k", "Rambla Corner" to "\$471k").forEach { (name, value) -> RulePanel { Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { PlotPreview(com.portico.android.data.DemoRepository.properties.first(), Modifier.size(52.dp)); Column(modifier = Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.titleMedium); Text("${propertyType} · 0.8 km away", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Text(value, style = MaterialTheme.typography.labelLarge) } } }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ValuationBar(label: String, fraction: Float, value: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.bodySmall); Text(value, style = MaterialTheme.typography.labelLarge, color = color) }; androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))) { androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth(fraction).height(10.dp).background(color, RoundedCornerShape(5.dp))) } }
}

@Composable
fun AcquisitionScreen(state: PorticoState, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("Search") }
    val stages = listOf("Search", "Details", "Comparables", "Valuation", "Analysis", "Decision")
    val listings = listOf("Canal View Apartment" to "\$410,000", "Parkside Duplex" to "\$365,000", "Rambla Workhome" to "\$525,000")
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Acquisition lab", style = MaterialTheme.typography.headlineMedium)
        Text("Move from search to decision with the same return language as your portfolio.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { stages.forEach { FilterChip(selected = stage == it, onClick = { stage = it }, label = { Text(it) }) } }
        RulePanel { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search properties or locations") }, leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }, trailingIcon = { Icon(Icons.Outlined.Tune, contentDescription = null) }, singleLine = true); Text("External data is an integration seam in this demo.", style = MaterialTheme.typography.bodySmall, color = PorticoMuted) } }
        if (stage == "Search") {
            SectionHeading("Synthetic market signals", "Replace with a verified provider before launch")
            listings.filter { query.isBlank() || it.first.contains(query, true) }.forEach { listing -> ListingRow(listing.first, listing.second) { stage = "Details" } }
        } else {
            AcquisitionStage(stage, onBack = { stage = stages[(stages.indexOf(stage) - 1).coerceAtLeast(0)] }, onNext = { stage = stages[(stages.indexOf(stage) + 1).coerceAtMost(stages.lastIndex)] })
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ListingRow(name: String, price: String, onClick: () -> Unit) {
    RulePanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) { Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { PlotPreview(com.portico.android.data.DemoRepository.properties[1], Modifier.size(70.dp)); Column(modifier = Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.titleMedium); Text("Montevideo · synthetic listing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("4.8% cap rate · 7.2% net yield", style = MaterialTheme.typography.labelSmall, color = PorticoTeal) }; Column(horizontalAlignment = Alignment.End) { Text(price, style = MaterialTheme.typography.labelLarge); Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = PorticoMuted, modifier = Modifier.size(18.dp)) } } }
}

@Composable
private fun AcquisitionStage(stage: String, onBack: () -> Unit, onNext: () -> Unit) {
    val copy = when (stage) { "Details" -> "Review the address, type, size, and asking price."; "Comparables" -> "Place comparable properties beside the candidate."; "Valuation" -> "Compare estimate, price, and market range."; "Analysis" -> "Model cashflow, yield, ROI, and tax impact."; else -> "Make the investment decision with the full trail visible." }
    RulePanel { Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(stage, style = MaterialTheme.typography.headlineMedium); Text(copy, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCell("Asking price", "\$410k", "Synthetic", modifier = Modifier.weight(1f)); MetricCell("Net yield", "7.2%", "Modeled", true, Modifier.weight(1f)) }; if (stage == "Analysis" || stage == "Decision") { ValuationBar("Gross income", .85f, "\$41k", PorticoTeal); ValuationBar("Expenses + tax", .34f, "−\$14k", PorticoOrange); ValuationBar("Net income", .51f, "\$27k", MaterialTheme.colorScheme.primary) }; Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }; Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text(if (stage == "Decision") "Save scenario" else "Next") } } } }
}
