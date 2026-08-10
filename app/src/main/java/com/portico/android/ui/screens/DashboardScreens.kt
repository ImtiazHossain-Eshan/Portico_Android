package com.portico.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddHome
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.portico.android.model.ActivityItem
import com.portico.android.model.ActivityTone
import com.portico.android.model.Property
import com.portico.android.ui.PorticoState
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoOrange
import com.portico.android.ui.theme.PorticoTeal

@Composable
fun LegacyDashboardScreen(state: PorticoState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Your perimeter is up", style = MaterialTheme.typography.headlineMedium)
                Text("A clean read on the month so far.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill("Demo data", PorticoMuted)
        }

        RulePanel(modifier = Modifier.fillMaxWidth(), emphasized = true) {
            Row(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Portfolio value", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("\$984,300", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null, tint = PorticoTeal, modifier = Modifier.size(18.dp))
                        Text("\$98,600 · 11.1% this year", style = MaterialTheme.typography.bodyMedium, color = PorticoTeal)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("As of 21 Mar 2026", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Text("3 properties", style = MaterialTheme.typography.labelLarge)
                    Text("2 countries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        RulePanel(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SectionHeading("Value trend", "A twelve-month view of your portfolio", "1Y", onAction = { state.toastMessage = "Time range selector is ready for connected data" })
                Spacer(Modifier.height(14.dp))
                TrendChart(modifier = Modifier.fillMaxWidth().height(150.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Apr '25", "Jul '25", "Oct '25", "Jan '26", "Mar '26").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = PorticoMuted) }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RulePanel(modifier = Modifier.weight(1f)) { MetricCell("Total invested", "\$838,000", "Cost basis") }
            RulePanel(modifier = Modifier.weight(1f)) { MetricCell("Total return", "+\$98,600", "+11.1%", true) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RulePanel(modifier = Modifier.weight(1f)) { MetricCell("Net yield", "6.9%", "Across portfolio", true) }
            RulePanel(modifier = Modifier.weight(1f)) { MetricCell("Monthly cashflow", "\$4,820", "After operating costs", true) }
        }

        SectionHeading("Move the register", "Common actions for this review")
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("Add property", Icons.Outlined.AddHome) { state.resetDraft(); state.navigate("add") }
            QuickAction("Reports", Icons.Outlined.Assessment) { state.selectDestination("reports") }
            QuickAction("Documents", Icons.Outlined.Description) { state.navigate("documents") }
            QuickAction("Tax view", Icons.Outlined.Calculate) { state.navigate("tax") }
            QuickAction("Value a property", Icons.Outlined.ManageSearch) { state.navigate("valuation") }
        }

        SectionHeading("Holdings", "The three plots currently in your perimeter", "See all", onAction = { state.selectDestination("portfolio") })
        state.properties.take(3).forEach { property ->
            PropertyRow(property = property, onClick = { state.selectedPropertyId = property.id; state.navigate("property") })
        }

        SectionHeading("Recent activity", "Latest records across your portfolio")
        state.activities.take(4).forEach { ActivityRow(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.widthIn(min = 148.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun PortfolioScreen(state: PorticoState, modifier: Modifier = Modifier) {
    var selectedFilter by remember { mutableStateOf("All plots") }
    var sortMode by remember { mutableStateOf("Value") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val filtered = state.properties.filter { property ->
        state.portfolioQuery.isBlank() || property.name.contains(state.portfolioQuery, ignoreCase = true) || property.location.contains(state.portfolioQuery, ignoreCase = true)
    }.filter { property ->
        when (selectedFilter) {
            "Strong cashflow" -> property.monthlyCashflow >= 1_700.0
            "Highest ROI" -> property.roi >= 14.0
            "Uruguay" -> property.location.contains("Montevideo", true) || property.location.contains("Canelones", true)
            "Argentina" -> property.location.contains("Buenos Aires", true)
            else -> true
        }
    }.let { properties ->
        when (sortMode) {
            "ROI" -> properties.sortedByDescending { it.roi }
            "Cashflow" -> properties.sortedByDescending { it.monthlyCashflow }
            else -> properties.sortedByDescending { it.currentValue }
        }
    }
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("The full register", style = MaterialTheme.typography.headlineMedium)
        Text("Every asset, cost basis, and net return in one perimeter.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RulePanel {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricCell("Value", "\$984,300", modifier = Modifier.weight(1f))
                MetricCell("Invested", "\$838,000", modifier = Modifier.weight(1f))
                MetricCell("Net yield", "6.9%", positive = true, modifier = Modifier.weight(1f))
            }
        }
        androidx.compose.material3.OutlinedTextField(
            value = state.portfolioQuery,
            onValueChange = { state.portfolioQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search properties") },
            leadingIcon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
            trailingIcon = { if (state.portfolioQuery.isNotBlank()) IconButton(onClick = { state.portfolioQuery = "" }) { Text("×") } },
            supportingText = { Text("Search your saved register · market research lives in Acquisition Lab") },
            singleLine = true
        )
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All plots", "Strong cashflow", "Highest ROI", "Uruguay", "Argentina").forEach { label ->
                androidx.compose.material3.FilterChip(selected = selectedFilter == label, onClick = { selectedFilter = label }, label = { Text(label) })
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${filtered.size} properties", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                OutlinedButton(onClick = { sortMenuExpanded = true }, modifier = Modifier.widthIn(min = 126.dp)) { Text("Sort: $sortMode") }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                    listOf("Value", "ROI", "Cashflow").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                sortMode = option
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            EmptyState("No plots found", "Try another property name or clear the search.") { state.portfolioQuery = "" }
        } else {
            filtered.forEach { property ->
                PropertyRow(property = property, detailed = true, onClick = { state.selectedPropertyId = property.id; state.navigate("property") })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PropertyRow(property: Property, detailed: Boolean = false, onClick: () -> Unit) {
    RulePanel(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            PlotPreview(property, modifier = Modifier.size(if (detailed) 84.dp else 72.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(property.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(property.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detailed) {
                    Spacer(Modifier.height(6.dp))
                    Text("${property.type} · ${property.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(formatCurrency(property.currentValue, true), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Text("${property.roi}% ROI", style = MaterialTheme.typography.labelMedium, color = PorticoTeal, maxLines = 1)
                }
            }
            Column(modifier = Modifier.widthIn(min = 68.dp), horizontalAlignment = Alignment.End) {
                Text("${property.netYield}%", style = MaterialTheme.typography.titleMedium, color = PorticoTeal, maxLines = 1)
                Text("net yield", style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
                Text(formatCurrency(property.monthlyCashflow, true), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text("cashflow", style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
            }
        }
    }
}

@Composable
fun ActivityRow(item: ActivityItem) {
    val accent = when (item.tone) {
        ActivityTone.Positive -> PorticoTeal
        ActivityTone.Warning -> PorticoOrange
        ActivityTone.Neutral -> MaterialTheme.colorScheme.primary
    }
    val icon = when (item.tone) {
        ActivityTone.Positive -> Icons.AutoMirrored.Outlined.TrendingUp
        ActivityTone.Warning -> Icons.Outlined.WarningAmber
        ActivityTone.Neutral -> Icons.Outlined.Update
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = accent.copy(alpha = .12f), border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .24f))) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = item.title, tint = accent, modifier = Modifier.size(22.dp)) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(modifier = Modifier.widthIn(min = 62.dp), horizontalAlignment = Alignment.End) {
            Text(item.amount, style = MaterialTheme.typography.labelLarge, color = if (item.tone == ActivityTone.Positive) PorticoTeal else MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(item.time, style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
        }
    }
}

@Composable
private fun LegacyActivityRow(item: ActivityItem) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = when (item.tone) { ActivityTone.Positive -> PorticoTeal.copy(alpha = .12f); ActivityTone.Warning -> PorticoOrange.copy(alpha = .12f); ActivityTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant }, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Box(contentAlignment = Alignment.Center) { Text(if (item.tone == ActivityTone.Positive) "+" else "·", style = MaterialTheme.typography.titleMedium, color = when (item.tone) { ActivityTone.Positive -> PorticoTeal; ActivityTone.Warning -> PorticoOrange; ActivityTone.Neutral -> MaterialTheme.colorScheme.primary }) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(modifier = Modifier.widthIn(min = 62.dp), horizontalAlignment = Alignment.End) {
            Text(item.amount, style = MaterialTheme.typography.labelLarge, color = if (item.tone == ActivityTone.Positive) PorticoTeal else MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(item.time, style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
        }
    }
}

@Composable
fun EmptyState(title: String, supporting: String, action: () -> Unit) {
    RulePanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = action) { Text("Reset view") }
        }
    }
}
