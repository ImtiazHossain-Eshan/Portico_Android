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
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.portico.android.model.ChatMessage
import com.portico.android.ui.PorticoState
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoOrange
import com.portico.android.ui.theme.PorticoTeal

@Composable
fun LegacyAssistantScreen(state: PorticoState, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) {
        if (state.assistantMessages.isEmpty()) {
            state.assistantMessages = listOf(ChatMessage("I can help you read return, cashflow, tax impact, or a new acquisition.", false, "Portico intelligence"))
        }
    }
    val prompts = listOf("What is driving my net return?", "Compare Harbor House and Lakeside", "Test an \$800 rent scenario", "Where is my portfolio concentrated?")
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RulePanel(modifier = Modifier.fillMaxWidth(), emphasized = true) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { SurfaceIcon(Icons.Outlined.AutoAwesome, MaterialTheme.colorScheme.primary); Column { Text("Portico intelligence", style = MaterialTheme.typography.titleLarge); Text("Context-aware investment assistance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                Text("Use a property or your whole portfolio as context. Calculations are illustrative until connected to verified records.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Portfolio", "Harbor House", "Lakeside Duplex", "Scenario").forEach { FilterChip(selected = state.assistantContext == it, onClick = { state.assistantContext = it }, label = { Text(it) }) } }
        if (state.assistantMessages.size <= 1) {
            SectionHeading("Start with a question", "Suggested prompts grounded in your register")
            prompts.forEach { prompt ->
                RulePanel(modifier = Modifier.fillMaxWidth().clickable { state.assistantInput = prompt }) { Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = PorticoTeal, modifier = Modifier.size(18.dp)); Text(prompt, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = PorticoMuted) } }
            }
        }
        state.assistantMessages.forEach { message ->
            ChatBubble(message)
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = state.assistantInput, onValueChange = { state.assistantInput = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask about your portfolio…") }, minLines = 1, maxLines = 4)
            IconButton(onClick = {
                val input = state.assistantInput.trim()
                if (input.isNotBlank()) {
                    state.assistantMessages = state.assistantMessages + ChatMessage(input, true, state.assistantContext)
                    state.assistantMessages = state.assistantMessages + ChatMessage("Based on the ${state.assistantContext} view, the clearest next read is net cashflow: your portfolio currently keeps about \$4,820 each month after operating costs. Treat this as a demo interpretation until live records are connected.", false, state.assistantContext)
                    state.assistantInput = ""
                }
            }) { Icon(Icons.Outlined.UploadFile, contentDescription = "Send question") }
        }
        Text("Portico intelligence is not financial, tax, or legal advice.", style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start) {
        Surface(shape = RoundedCornerShape(16.dp, 16.dp, if (message.fromUser) 4.dp else 16.dp, if (message.fromUser) 16.dp else 4.dp), color = if (message.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
            Column(modifier = Modifier.width(310.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (message.context != null) Text(message.context, style = MaterialTheme.typography.labelSmall, color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = .7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(message.text, style = MaterialTheme.typography.bodyMedium, color = if (message.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun ProfileScreen(
    state: PorticoState,
    accountName: String,
    accountEmail: String,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var notifications by remember { mutableStateOf(true) }
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SurfaceIcon(Icons.Outlined.Person, MaterialTheme.colorScheme.primary, Modifier.size(64.dp))
            Column(modifier = Modifier.weight(1f)) { Text(accountName, style = MaterialTheme.typography.headlineMedium); Text(accountEmail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); StatusPill("Free plan", PorticoOrange) }
        }
        SectionHeading("Preferences", "Set the way Portico reads your data")
        RulePanel { Column { SettingRow(Icons.Outlined.AccountBalance, "Currency", "USD · United States") { state.toastMessage = "Currency selector opened" }; SettingRow(Icons.Outlined.Public, "Tax jurisdiction", "Uruguay · Montevideo") { state.navigate("tax") }; SettingRow(Icons.Outlined.Language, "Language", "English") { state.toastMessage = "Language selector opened" }; SettingRow(Icons.Outlined.NotificationsNone, "Notifications", "Rent, documents, and alerts", trailing = { Switch(checked = notifications, onCheckedChange = { notifications = it }) }) } }
        SectionHeading("Appearance", "Material You stays readable in any light")
        RulePanel { Column { listOf("Light mode", "Dark mode", "System default").forEach { option -> SettingRow(if (option == "Dark mode") Icons.Outlined.DarkMode else Icons.Outlined.Settings, option, if (state.appearance == option) "Selected" else "Use this appearance", trailing = { androidx.compose.material3.RadioButton(selected = state.appearance == option, onClick = { state.appearance = option }) }, onClick = { state.appearance = option }) } } }
        SectionHeading("Workspace", "The product can grow with the portfolio")
        RulePanel { Column { SettingRow(Icons.Outlined.Description, "Document library", "${state.documents.size} private records") { state.navigate("documents") }; SettingRow(Icons.Outlined.Subscriptions, "Subscription", "Free · upgrade when needed") { state.navigate("subscription") }; SettingRow(Icons.Outlined.Business, "Enterprise workspace", "Teams, roles, and permissions") { state.navigate("enterprise") } } }
        SectionHeading("Security & privacy", "Controls for sensitive financial data")
        RulePanel { Column { SettingRow(Icons.Outlined.Lock, "Change password", "Last changed 42 days ago") { state.toastMessage = "Password flow opened" }; SettingRow(Icons.Outlined.Shield, "Privacy controls", "Private records and data export") { state.toastMessage = "Privacy controls opened" }; SettingRow(Icons.Outlined.Delete, "Delete account", "Permanent and irreversible", color = MaterialTheme.colorScheme.error) { state.toastMessage = "Account deletion requires confirmation" } } }
        OutlinedButton(onClick = { state.showLogoutDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Logout, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Log out") }
        if (state.showLogoutDialog) androidx.compose.material3.AlertDialog(onDismissRequest = { state.showLogoutDialog = false }, title = { Text("Log out of Portico?") }, text = { Text("Your Clerk session will be revoked on this device. Local portfolio records remain available after you sign in again.") }, confirmButton = { Button(onClick = { state.showLogoutDialog = false; onSignOut() }) { Text("Log out") } }, dismissButton = { OutlinedButton(onClick = { state.showLogoutDialog = false }) { Text("Cancel") } })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, supporting: String, color: Color = MaterialTheme.colorScheme.onSurface, trailing: (@Composable (() -> Unit))? = null, onClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = if (color == MaterialTheme.colorScheme.error) color else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyMedium, color = color); Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (trailing != null) trailing() else Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = PorticoMuted, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun SubscriptionScreen(state: PorticoState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("More room for the portfolio", style = MaterialTheme.typography.headlineMedium)
        Text("The product stays calm about monetization: compare what changes, then choose when it matters.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PlanPanel("Free", "For an early perimeter", "Up to 2 properties", listOf("Basic dashboard", "Core portfolio view", "Private document library"), false) { state.toastMessage = "You are on the Free plan" }
        PlanPanel("Pro", "For a portfolio that keeps growing", "Unlimited properties", listOf("Advanced reports", "Advanced taxation", "AI investment assistance", "Increased document storage"), true) { state.showUpgradeDialog = true }
        RulePanel { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Subscription status", style = MaterialTheme.typography.titleLarge); Text("Free plan · active", style = MaterialTheme.typography.bodyMedium); Text("No payment method or renewal is connected in this demo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (state.showUpgradeDialog) androidx.compose.material3.AlertDialog(onDismissRequest = { state.showUpgradeDialog = false }, title = { Text("Upgrade to Pro") }, text = { Text("A connected build would take you to secure checkout. This local demo keeps the plan decision reversible.") }, confirmButton = { Button(onClick = { state.showUpgradeDialog = false; state.toastMessage = "Upgrade flow started" }) { Text("Continue") } }, dismissButton = { OutlinedButton(onClick = { state.showUpgradeDialog = false }) { Text("Not now") } })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlanPanel(name: String, subtitle: String, price: String, features: List<String>, recommended: Boolean, onClick: () -> Unit) {
    RulePanel(modifier = Modifier.fillMaxWidth(), emphasized = recommended) { Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(name, style = MaterialTheme.typography.headlineMedium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (recommended) StatusPill("Recommended", PorticoTeal) }; Text(price, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary); features.forEach { feature -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = PorticoTeal, modifier = Modifier.size(17.dp)); Text(feature, style = MaterialTheme.typography.bodyMedium) } }; if (recommended) Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Explore Pro") } else OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Current plan") } } }
}

@Composable
fun EnterpriseScreen(state: PorticoState, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("A workspace for the next perimeter", style = MaterialTheme.typography.headlineMedium)
        Text("Bring partners, funds, and permissions into the same financial record without changing the property model.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RulePanel(modifier = Modifier.fillMaxWidth(), emphasized = true) { Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { SurfaceIcon(Icons.Outlined.Business, MaterialTheme.colorScheme.primary); Column { Text("Portico workspace", style = MaterialTheme.typography.titleLarge); Text("Solo investor · owner", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }; Text("One organization, role-based access, bulk uploads, advanced reports, and export-ready records.", style = MaterialTheme.typography.bodyMedium) } }
        SectionHeading("Enterprise building blocks", "Ready for a connected organization layer")
        listOf(Icons.Outlined.Group to ("Members & roles" to "Invite partners and define permissions"), Icons.Outlined.AccountBalance to ("Funds & portfolios" to "Separate ownership from performance"), Icons.Outlined.UploadFile to ("Bulk property import" to "Bring a normalized register into Portico"), Icons.Outlined.Security to ("Audit & controls" to "Keep every sensitive action visible")).forEach { (icon, pair) -> RulePanel(modifier = Modifier.fillMaxWidth().clickable { state.toastMessage = "${pair.first} is scoped for the enterprise workspace" }) { Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { SurfaceIcon(icon, PorticoTeal); Column(modifier = Modifier.weight(1f)) { Text(pair.first, style = MaterialTheme.typography.titleMedium); Text(pair.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = PorticoMuted) } } }
        Spacer(Modifier.height(24.dp))
    }
}
