package com.portico.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import kotlinx.coroutines.launch
import com.portico.android.data.PorticoExport
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.Appearance
import com.portico.android.ui.theme.PorticoTheme

@Composable
fun ProfileScreen(state: PorticoState, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val store = state.store
    val semantic = PorticoTheme.semantic
    val profile = store.profile
    val portfolio = store.portfolio()

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        // ---- identity --------------------------------------------------------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    profile.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text(profile.name.ifBlank { "Portico member" }, style = MaterialTheme.typography.titleLarge)
                Text(
                    profile.email.ifBlank { "Local workspace" },
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.tertiaryText
                )
            }
            if (state.demoMode) StatusChip("Demo", tone = semantic.neutral)
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            MetricGrid(
                metrics = listOf(
                    Metric("Properties", store.properties.size.toString()),
                    Metric("Portfolio", Money.compact(portfolio.portfolioValue, profile.currency)),
                    Metric("Plan", store.subscription.tier.label)
                ),
                columns = 3
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Account")
            NavRow("Preferences", glyph = Glyph.SETTINGS, value = profile.currency,
                supporting = "Currency, exchange rates, jurisdiction, appearance") {
                state.navigate(Route.SETTINGS_PREFERENCES)
            }
            Hairline()
            NavRow("Notifications", glyph = Glyph.NOTIFICATION,
                value = if (store.unreadNotifications > 0) "${store.unreadNotifications} new" else null,
                supporting = "What Portico tells you about") {
                state.navigate(Route.NOTIFICATIONS)
            }
            Hairline()
            NavRow("Security", glyph = Glyph.LOCK, supporting = "Password, sessions and sign-in") {
                state.navigate(Route.SETTINGS_SECURITY)
            }
            Hairline()
            NavRow("Privacy and data", glyph = Glyph.SHIELD, supporting = "What is stored and how to remove it") {
                state.navigate(Route.SETTINGS_PRIVACY)
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Portfolio tools")
            NavRow("Documents", glyph = Glyph.DOCUMENT, value = store.documents.size.toString()) {
                state.navigate(Route.DOCUMENTS)
            }
            Hairline()
            NavRow("Tax position", glyph = Glyph.TAX, value = store.taxProfile.jurisdiction.name) {
                state.navigate(Route.TAX)
            }
            Hairline()
            NavRow("Valuation", glyph = Glyph.VALUATION, supporting = "Compare against the market") {
                state.navigate(Route.VALUATION)
            }
            Hairline()
            NavRow("Acquisition", glyph = Glyph.ACQUISITION, supporting = "Analyse a property before buying") {
                state.navigate(Route.ACQUISITION)
            }
            Hairline()
            NavRow("Plans", glyph = Glyph.SUBSCRIPTION, value = store.subscription.tier.label) {
                state.navigate(Route.SUBSCRIPTION)
            }
            Hairline()
            NavRow("Workspace", glyph = Glyph.ENTERPRISE, supporting = "Members, roles and export") {
                state.navigate(Route.ENTERPRISE)
            }
            Hairline()
            NavRow("Admin platform", glyph = Glyph.ADMIN, supporting = "Users, subscriptions, analytics and audit") {
                state.navigate(Route.ADMIN)
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Session")
            NavRow("Sign out", glyph = Glyph.LOGOUT, tint = MaterialTheme.colorScheme.error) {
                state.showLogoutDialog = true
            }
        }

        Box(Modifier.padding(horizontal = Space.lg)) {
            Text(
                "Portico · Imtiaz Hossain · 23101137",
                style = MaterialTheme.typography.labelSmall,
                color = semantic.tertiaryText
            )
        }
    }

    if (state.showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { state.showLogoutDialog = false },
            title = { Text("Sign out of Portico?") },
            text = { Text("Your records stay on this device. You'll need to sign in again to reach them.") },
            confirmButton = {
                TextButton(onClick = {
                    state.showLogoutDialog = false
                    onSignOut()
                }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { state.showLogoutDialog = false }) { Text("Stay signed in") }
            },
            containerColor = PorticoTheme.semantic.panel
        )
    }
}

// ------------------------------------------------------------- preferences

@Composable
fun PreferencesScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val semantic = PorticoTheme.semantic
    val profile = store.profile
    val preferences = store.preferences
    var editingName by remember { mutableStateOf(profile.name) }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Profile")
            Box(Modifier.padding(Space.lg)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    PorticoField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        label = "Display name"
                    )
                    PrimaryButton("Save name", Modifier.fillMaxWidth(), enabled = editingName != profile.name) {
                        store.setProfile { it.copy(name = editingName.trim()) }
                        state.notify("Name updated")
                    }
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Currency", supporting = "Every figure in the app is shown in this currency")
            SegmentedRow(Money.currencies, profile.currency) { currency ->
                store.setProfile { it.copy(currency = currency) }
                state.notify("Now showing $currency")
            }
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                "Exchange rates",
                supporting = "Units per 1 USD. Used whenever a property is held in another currency.",
                action = if (store.exchangeRates.isEdited) "Reset" else null,
                onAction = if (store.exchangeRates.isEdited) {
                    {
                        store.setExchangeRates { it.reset() }
                        state.notify("Exchange rates reset to defaults")
                    }
                } else null
            )
            store.exchangeRates.rows().forEachIndexed { index, row ->
                if (index > 0) Hairline()
                if (row.isBase) {
                    DataRow(
                        "${row.currency} ${row.symbol}",
                        "base",
                        supporting = "Every other rate is quoted against this"
                    )
                } else {
                    var draft by remember(row.currency, row.perUsd) {
                        mutableStateOf(formatRate(row.perUsd))
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${row.currency} ${row.symbol}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${row.symbol}${formatRate(row.perUsd)} per \u00241",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.tertiaryText
                            )
                        }
                        Spacer(Modifier.width(Space.md))
                        PorticoField(
                            value = draft,
                            onValueChange = { raw ->
                                draft = raw.filter { c -> c.isDigit() || c == '.' }
                                draft.toDoubleOrNull()?.let { parsed ->
                                    if (parsed > 0) {
                                        store.setExchangeRates {
                                            it.withRate(row.currency, parsed, SimpleDate.today().format())
                                        }
                                    }
                                }
                            },
                            label = "per USD",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                }
            }
            if (store.exchangeRates.updated.isNotBlank()) {
                Hairline()
                DataRow("You last set these", store.exchangeRates.updated)
            }
            SyntheticNote(FX_NOTICE)
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Tax jurisdiction")
            NavRow(
                store.taxProfile.jurisdiction.name,
                glyph = Glyph.TAX,
                supporting = "${store.taxProfile.jurisdiction.countryName} · ${store.taxProfile.effectiveRules().size} rules"
            ) { state.navigate(Route.TAX_ASSUMPTIONS) }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Appearance")
            SegmentedRow(
                listOf(Appearance.SYSTEM, Appearance.LIGHT, Appearance.DARK),
                preferences.theme
            ) { theme -> store.setPreferences { it.copy(theme = theme) } }
            Spacer(Modifier.height(Space.md))
            SwitchRow(
                label = "Reduce motion",
                checked = preferences.reducedMotion,
                supporting = "Turn off chart reveals and value transitions",
                glyph = Glyph.APPEARANCE,
                onCheckedChange = { value -> store.setPreferences { it.copy(reducedMotion = value) } }
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Notify me about")
            SwitchRow(
                "Rent received",
                preferences.notificationsRent,
                supporting = "When income is recorded against a property",
                glyph = Glyph.INCOME
            ) { value -> store.setPreferences { it.copy(notificationsRent = value) } }
            Hairline()
            SwitchRow(
                "Documents and leases",
                preferences.notificationsDocuments,
                supporting = "Expiring leases and missing paperwork",
                glyph = Glyph.DOCUMENT
            ) { value -> store.setPreferences { it.copy(notificationsDocuments = value) } }
            Hairline()
            SwitchRow(
                "Market movement",
                preferences.notificationsMarket,
                supporting = "Needs a connected data provider",
                glyph = Glyph.TREND
            ) { value -> store.setPreferences { it.copy(notificationsMarket = value) } }
        }

        SyntheticNote(
            "Preferences are stored on this device. Push delivery needs a messaging service, which is not connected."
        )
    }
}

/** Rates read better without trailing zeros: 122 rather than 122.00. */
private fun formatRate(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.4f".format(value).trimEnd('0').trimEnd('.')

// ---------------------------------------------------------------- security

@Composable
fun SecurityScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val semantic = PorticoTheme.semantic
    val scope = rememberCoroutineScope()
    var revoking by remember { mutableStateOf(false) }

    // Sessions are the identity provider's truth, so they are read live rather
    // than mirrored into local state.
    val sessions by Clerk.sessionsFlow.collectAsState(initial = emptyList())
    val currentSessionId = Clerk.session?.id
    val otherSessions = sessions.filter { it.id != currentSessionId }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Sign-in")
            DataRow(
                "Method",
                if (state.demoMode) "Demo workspace" else "Email and password",
                supporting = if (state.demoMode) "No account attached" else "Managed by Clerk"
            )
            Hairline()
            DataRow("Session", if (state.signedIn) "Active on this device" else "Local only")
            Hairline()
            DataRow("Password policy", "15 characters minimum", supporting = "Set by your workspace")
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                "Active sessions",
                supporting = if (sessions.isEmpty()) "Nothing signed in remotely"
                else "${sessions.size} session${if (sessions.size == 1) "" else "s"} on your account"
            )
            if (sessions.isEmpty()) {
                DataRow(
                    "This device",
                    if (state.demoMode) "Demo" else "Local",
                    supporting = "No remote session to revoke"
                )
            } else {
                sessions.forEachIndexed { index, session ->
                    if (index > 0) Hairline()
                    val activity = session.latestActivity
                    val where = listOfNotNull(activity?.deviceType, activity?.city, activity?.country)
                        .joinToString(" · ").ifBlank { "Unknown device" }
                    val isCurrent = session.id == currentSessionId
                    DataRow(
                        label = if (isCurrent) "This device" else where,
                        value = if (isCurrent) "Current" else "Revoke",
                        supporting = if (isCurrent) where else session.status.name.lowercase(),
                        valueColor = if (isCurrent) semantic.tertiaryText else MaterialTheme.colorScheme.error,
                        onClick = if (isCurrent) null else {
                            {
                                scope.launch {
                                    revoking = true
                                    when (Clerk.auth.revokeSession(session)) {
                                        is ClerkResult.Success -> state.notify("Session on $where revoked")
                                        is ClerkResult.Failure<*> -> state.notify("Couldn't revoke that session.")
                                    }
                                    revoking = false
                                }
                            }
                        }
                    )
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Manage")
            NavRow(
                "Change password",
                glyph = Glyph.KEY,
                supporting = "Opens your account portal"
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://sharing-iguana-58.accounts.dev/user"))
                    )
                }.onFailure { state.notify("No browser available to open the account portal.") }
            }
            Hairline()
            NavRow(
                "Sign out everywhere else",
                glyph = Glyph.LOGOUT,
                supporting = if (otherSessions.isEmpty()) "No other sessions to end"
                else "Ends ${otherSessions.size} other session${if (otherSessions.size == 1) "" else "s"}",
                tint = if (otherSessions.isEmpty()) semantic.tertiaryText else MaterialTheme.colorScheme.error
            ) {
                if (otherSessions.isEmpty()) {
                    state.notify("You're only signed in on this device.")
                    return@NavRow
                }
                scope.launch {
                    revoking = true
                    var revoked = 0
                    var failed = 0
                    otherSessions.forEach { session ->
                        when (Clerk.auth.revokeSession(session)) {
                            is ClerkResult.Success -> revoked++
                            is ClerkResult.Failure<*> -> failed++
                        }
                    }
                    revoking = false
                    state.notify(
                        when {
                            failed == 0 -> "Signed out of $revoked other session${if (revoked == 1) "" else "s"}"
                            revoked == 0 -> "Couldn't reach the session service."
                            else -> "$revoked ended, $failed could not be reached."
                        }
                    )
                }
            }
            if (revoking) {
                Hairline()
                Row(
                    Modifier.fillMaxWidth().padding(Space.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text("Contacting the session service", style = MaterialTheme.typography.bodySmall, color = semantic.tertiaryText)
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("How Portico protects your records")
            DataRow("Financial records", "On this device", supporting = "Never sent to a Portico server")
            Hairline()
            DataRow("Documents", "On this device", supporting = "Stored as references to your own files")
            Hairline()
            DataRow("Password", "Never stored by Portico", supporting = "Handled by the identity provider")
            Hairline()
            DataRow("Failed sign-ins", "Locks after 10 attempts", supporting = "For 60 minutes")
        }

        SyntheticNote(
            "Encryption at rest, per-user access rules and audit logging belong to the backend, " +
                "which is an integration seam in this build rather than a shipped service."
        )
    }
}

// ----------------------------------------------------------------- privacy

@Composable
fun PrivacyScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val context = LocalContext.current
    val semantic = PorticoTheme.semantic

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("What this app stores")
            DataRow("Properties", store.properties.size.toString())
            Hairline()
            DataRow("Income and expense records", (store.income.size + store.expenses.size).toString())
            Hairline()
            DataRow("Documents", store.documents.size.toString())
            Hairline()
            DataRow("Conversations", store.conversations.size.toString())
            Hairline()
            DataRow("Location", "This device only")
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Your data")
            NavRow(
                "Export everything",
                glyph = Glyph.DOCUMENT,
                supporting = "${PorticoExport.recordCount(store)} records as CSV"
            ) {
                val intent = PorticoExport.shareIntent(context, store)
                if (intent == null) {
                    state.notify("Couldn't write the export files to this device.")
                } else {
                    runCatching { context.startActivity(Intent.createChooser(intent, "Export portfolio")) }
                        .onFailure { state.notify("No app on this device can receive the export.") }
                }
            }
            Hairline()
            NavRow("Restore sample portfolio", glyph = Glyph.REFRESH, supporting = "Replaces current records with the demo set") {
                store.resetToSeed()
                state.notify("Sample portfolio restored")
            }
            Hairline()
            NavRow(
                "Erase all records",
                glyph = Glyph.DELETE,
                supporting = "Removes every property, record and document from this device",
                tint = MaterialTheme.colorScheme.error
            ) { state.showDeleteAccountDialog = true }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Principles")
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                listOf(
                    "Your financial records never leave this device in this build.",
                    "Assistant analysis runs locally; no question is sent anywhere.",
                    "Documents are stored as references to files you already have.",
                    "Deleting a property deletes everything attached to it."
                ).forEach { line ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        PorticoIcon(Glyph.CHECK, size = 14.dp, tint = semantic.gain, contentDescription = null)
                        Spacer(Modifier.width(Space.sm))
                        Text(line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        SyntheticNote(
            "A production deployment would add a data-processing agreement, retention policy and GDPR request handling."
        )
    }

    if (state.showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { state.showDeleteAccountDialog = false },
            title = { Text("Erase every record?") },
            text = {
                Text(
                    "This removes all properties, income, expenses, documents and conversations from this device. It cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    store.clearEverything()
                    state.showDeleteAccountDialog = false
                    state.selectDestination(Route.DASHBOARD)
                    state.notify("All records erased")
                }) { Text("Erase everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { state.showDeleteAccountDialog = false }) { Text("Cancel") }
            },
            containerColor = PorticoTheme.semantic.panel
        )
    }
}

// ----------------------------------------------------------- notifications

@Composable
fun NotificationsScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val semantic = PorticoTheme.semantic

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                "Notifications",
                supporting = if (store.unreadNotifications > 0) "${store.unreadNotifications} unread" else "All read",
                action = if (store.unreadNotifications > 0) "Mark all read" else null,
                onAction = if (store.unreadNotifications > 0) {
                    { store.markAllNotificationsRead() }
                } else null
            )
            if (store.notifications.isEmpty()) {
                EmptyState(
                    title = "Nothing to catch up on",
                    body = "Rent, lease expiries and tax changes will show up here.",
                    glyph = Glyph.NOTIFICATION
                )
            } else {
                store.notifications.forEachIndexed { index, notification ->
                    if (index > 0) Hairline()
                    DataRow(
                        label = notification.title,
                        value = "",
                        supporting = "${notification.message}  ·  ${notification.timestamp}",
                        onClick = { store.markNotificationRead(notification.id) },
                        trailing = if (!notification.read) {
                            { StatusChip("New", tone = MaterialTheme.colorScheme.primary) }
                        } else null
                    )
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Settings")
            NavRow("Choose what to be notified about", glyph = Glyph.SETTINGS) {
                state.navigate(Route.SETTINGS_PREFERENCES)
            }
        }
    }
}
