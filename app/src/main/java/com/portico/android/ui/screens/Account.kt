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
import androidx.compose.ui.unit.dp
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
                supporting = "Currency, jurisdiction, language, appearance") {
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
            PanelHeader("Language")
            SegmentedRow(
                listOf("English", "Español", "Português"),
                preferences.language
            ) { language ->
                store.setPreferences { it.copy(language = language) }
                if (language != "English") {
                    state.notify("$language is selected. Translations are not bundled in this build.")
                }
            }
            Spacer(Modifier.height(Space.md))
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

// ---------------------------------------------------------------- security

@Composable
fun SecurityScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val semantic = PorticoTheme.semantic

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
                "Sign out everywhere",
                glyph = Glyph.LOGOUT,
                supporting = "Ends every session except this one",
                tint = MaterialTheme.colorScheme.error
            ) { state.notify("Session revocation needs the account portal.") }
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
            NavRow("Export everything", glyph = Glyph.DOCUMENT, supporting = "A copy of every record in this workspace") {
                state.notify("Prepared ${store.properties.size} properties and ${store.income.size + store.expenses.size} financial records for export.")
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
