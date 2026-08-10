package com.portico.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portico.android.ui.PorticoState
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoOrange
import com.portico.android.ui.theme.PorticoTeal

@Composable
fun DashboardScreen(state: PorticoState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Your orbit is clear", style = MaterialTheme.typography.headlineMedium)
                Text("A live read on value, return, and the next move.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InstrumentBadge("DATA", "LOCAL DEMO", PorticoOrange)
        }

        RulePanel(modifier = Modifier.fillMaxWidth(), emphasized = true) {
            PortfolioInstrument(state, Modifier.fillMaxWidth().padding(18.dp))
        }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 520.dp) {
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GaugeMetric("TOTAL RETURN", "+$98.6k", "+11.1% / YEAR", .82f, PorticoTeal, Modifier.width(160.dp))
                    GaugeMetric("NET YIELD", "6.9%", "AFTER OPERATING COSTS", .69f, MaterialTheme.colorScheme.secondary, Modifier.width(160.dp))
                    GaugeMetric("CASHFLOW", "$4.82k", "MONTHLY / NET", .74f, PorticoOrange, Modifier.width(160.dp))
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    GaugeMetric("RETURN", "+$98.6k", "+11.1%", .82f, PorticoTeal, Modifier.weight(1f), compact = true)
                    GaugeMetric("YIELD", "6.9%", "NET", .69f, MaterialTheme.colorScheme.secondary, Modifier.weight(1f), compact = true)
                    GaugeMetric("CASHFLOW", "$4.82k", "MONTHLY", .74f, PorticoOrange, Modifier.weight(1f), compact = true)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionHeading("Decision controls", "Jump from the cockpit into a working surface", modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { state.selectDestination("assistant") }) { PorticoGlyph(PorticoGlyphType.Assistant, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Ask") }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModernQuickAction("Add property", PorticoGlyphType.Add) { state.resetDraft(); state.navigate("add") }
            ModernQuickAction("Reports", PorticoGlyphType.Reports) { state.selectDestination("reports") }
            ModernQuickAction("Documents", PorticoGlyphType.Documents) { state.navigate("documents") }
            ModernQuickAction("Tax view", PorticoGlyphType.Tax) { state.navigate("tax") }
            ModernQuickAction("Value a property", PorticoGlyphType.Valuation) { state.navigate("valuation") }
        }

        SectionHeading("Holdings", "The properties currently in your perimeter", "See all", onAction = { state.selectDestination("portfolio") })
        state.properties.take(3).forEach { property ->
            PropertyRow(property = property, onClick = { state.selectedPropertyId = property.id; state.navigate("property") })
        }

        SectionHeading("Recent activity", "Signals across the portfolio")
        state.activities.take(4).forEach { ActivityRow(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PortfolioInstrument(state: PorticoState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("PORTFOLIO VALUE", style = MaterialTheme.typography.labelMedium, color = PorticoMuted, letterSpacing = 1.1.sp)
        Text("$984,300", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PorticoGlyph(PorticoGlyphType.Income, Modifier.size(17.dp), PorticoTeal)
            Text("+$98,600 · 11.1% this year", style = MaterialTheme.typography.bodyMedium, color = PorticoTeal)
        }
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InstrumentBadge("ASSETS", "${state.properties.size} PROPERTIES", PorticoTeal)
            InstrumentBadge("RANGE", "2 COUNTRIES", MaterialTheme.colorScheme.secondary)
        }
        Text("Last sync · 21 Mar 2026 · illustrative records", style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
    }
}

@Composable
private fun ModernQuickAction(label: String, glyph: PorticoGlyphType, onClick: () -> Unit) {
    Surface(modifier = Modifier.widthIn(min = 148.dp).clickable(onClick = onClick), shape = androidx.compose.foundation.shape.RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(modifier = Modifier.padding(horizontal = 13.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PorticoGlyph(glyph, Modifier.size(22.dp), MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
        }
    }
}
