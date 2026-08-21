package com.portico.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.clerk.api.user.User
import com.clerk.api.user.updatePassword
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
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import kotlinx.coroutines.launch
import com.portico.android.data.PorticoExport
import com.portico.android.domain.*
import androidx.compose.ui.res.stringResource
import com.portico.android.R
import com.portico.android.ui.AppLanguage
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
    val avatarContext = LocalContext.current
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                avatarContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            store.setProfile { it.copy(profileImageUri = uri.toString()) }
        }
    }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        // ---- identity --------------------------------------------------------
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /*
             * Tapping the avatar changes it. Initials remain the fallback and
             * the resting state, because most members never set a picture and a
             * grey silhouette says less about who they are than their own
             * initials do.
             */
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                    .clickable {
                        avatarPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val avatar = profile.profileImageUri
                if (avatar.isNullOrBlank()) {
                    Text(
                        profile.name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString(""),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    AsyncImage(
                        model = avatar,
                        contentDescription = "Your profile photograph",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
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
            text = { Text("Your device cache remains private. Sign in again to resume secure cloud sync.") },
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

        /*
         * The language selector is back because it now does something. The
         * previous one set a preference and changed nothing on screen, which
         * is the kind of control this build exists to remove; this one hands
         * the choice to the platform, which recreates the activity and re-reads
         * every label from the resource set.
         */
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.settings_language))
            val language = AppLanguage.current()
            SegmentedRow(
                AppLanguage.entries.map { it.label },
                language.label
            ) { label ->
                AppLanguage.entries.firstOrNull { it.label == label }?.let(AppLanguage::apply)
            }
            Box(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                Text(
                    stringResource(R.string.settings_language_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.tertiaryText
                )
            }
        }

        /*
         * Cloud analysis is the one setting that changes where a member's
         * figures go, so it says so in the row rather than in a policy page
         * nobody opens. Off by default; the on-device analyst answers either
         * way, so turning it off never removes a capability.
         */
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Portico Intelligence")
            SwitchRow(
                "Cloud analysis",
                preferences.cloudAssistant,
                supporting = if (preferences.cloudAssistant) {
                    "Computed figures are sent to Gemma for wording. No address, document or note is included."
                } else {
                    "Answers are worked out on this device. Nothing is sent anywhere."
                },
                glyph = Glyph.ASSISTANT
            ) { value -> store.setPreferences { it.copy(cloudAssistant = value) } }
            if (preferences.cloudAssistant) {
                Box(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                    Text(
                        "Sent: property names, types, regions and the figures Portico computed. Never sent: street addresses, documents, notes or your identity. The arithmetic shown under each answer is always the device's own.",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.tertiaryText
                    )
                }
            }
        }

        SyntheticNote(
            "Preferences sync with your private workspace. Push delivery still needs a messaging service, which is not connected."
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
                supporting = "Set a new password without leaving Portico"
            ) {
                state.showPasswordDialog = true
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
            DataRow("Financial records", "Private cloud sync", supporting = "Owner-scoped Firestore with an offline device cache")
            Hairline()
            DataRow("Documents", "Private cloud files", supporting = "Fetched only through your authenticated session")
            Hairline()
            DataRow("Password", "Never stored by Portico", supporting = "Handled by the identity provider")
            Hairline()
            DataRow("Failed sign-ins", "Locks after 10 attempts", supporting = "For 60 minutes")
        }

        SyntheticNote(
            "Firestore and private file storage enforce per-user access. Passwords and server credentials never ship in the APK."
        )
    }

    if (state.showPasswordDialog) ChangePasswordDialog(state)
}

/**
 * Changing a password in the app rather than in a browser.
 *
 * Clerk enforces the same policy the sign-up screen advertises, so the rule is
 * stated before the member types and checked here too; a server rejection after
 * a filled form is the failure this build set out to remove. Signing other
 * sessions out is offered because a password change is usually prompted by
 * suspecting one of them.
 */
@Composable
private fun ChangePasswordDialog(state: PorticoState) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var signOutOthers by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val tooShort = next.isNotEmpty() && next.length < PasswordPolicy.MIN_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != next
    val ready = current.isNotBlank() &&
        PasswordPolicy.isValid(next) &&
        confirm == next &&
        !working

    AlertDialog(
        onDismissRequest = { if (!working) state.showPasswordDialog = false },
        title = { Text("Change password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                PorticoField(
                    current, { current = it }, "Current password",
                    isPassword = true, enabled = !working
                )
                PorticoField(
                    next, { next = it }, "New password",
                    isPassword = true, enabled = !working,
                    supporting = if (tooShort) null else PasswordPolicy.summary,
                    error = if (tooShort) {
                        "At least ${PasswordPolicy.MIN_LENGTH} characters (${next.length}/${PasswordPolicy.MIN_LENGTH})"
                    } else null
                )
                PorticoField(
                    confirm, { confirm = it }, "Confirm new password",
                    isPassword = true, enabled = !working,
                    error = if (mismatch) "The two entries do not match." else null
                )
                SwitchRow(
                    "Sign out other devices",
                    signOutOthers,
                    supporting = "Recommended if you think someone else has the old password"
                ) { signOutOthers = it }
                error?.let { InlineError(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = {
                scope.launch {
                    working = true
                    error = null
                    val user = Clerk.user
                    if (user == null) {
                        error = "You are not signed in."
                    } else {
                        when (val result = user.updatePassword(
                            User.UpdatePasswordParams(
                                currentPassword = current,
                                newPassword = next,
                                signOutOfOtherSessions = signOutOthers
                            )
                        )) {
                            is ClerkResult.Success -> {
                                state.showPasswordDialog = false
                                state.notify("Password changed")
                            }
                            is ClerkResult.Failure -> error = explainPasswordFailure(result)
                        }
                    }
                    working = false
                }
            }) { Text(if (working) "Saving..." else "Change password") }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = { state.showPasswordDialog = false }) {
                Text("Cancel")
            }
        },
        containerColor = PorticoTheme.semantic.panel
    )
}

/** Clerk's codes, turned into something a person can act on. */
private fun explainPasswordFailure(failure: ClerkResult.Failure<*>): String {
    val response = failure.error as? ClerkErrorResponse
    val code = response?.errors?.firstOrNull()?.code.orEmpty()
    if (code.isEmpty() && failure.throwable != null) {
        return "Can't reach the identity service. Check your connection and try again."
    }
    return when {
        code.contains("incorrect") || code.contains("verification") ->
            "That current password is not right."
        code == "form_password_length_too_short" || code.contains("length") ->
            PasswordPolicy.summary
        code == "form_password_pwned" ->
            "That password appears in a known breach list. Choose another."
        code.contains("rate") || code.contains("too_many") ->
            "Too many attempts. Wait a few minutes and try again."
        else -> "The password could not be changed. Check your connection and try again."
    }
}

// ----------------------------------------------------------------- privacy

@Composable
fun PrivacyScreen(state: PorticoState, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val store = state.store
    val context = LocalContext.current
    val semantic = PorticoTheme.semantic
    val scope = rememberCoroutineScope()

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
            NavRow(
                "Sample portfolio",
                glyph = Glyph.REFRESH,
                supporting = if (state.demoMode) "Restore the demonstration records" else "Available separately from the demo cockpit"
            ) {
                if (state.demoMode) {
                    store.resetToSeed()
                    state.notify("Sample portfolio restored")
                } else {
                    state.notify("Sign out and choose Enter the demo cockpit to explore sample records")
                }
            }
            Hairline()
            NavRow(
                "Erase all records",
                glyph = Glyph.DELETE,
                supporting = "Removes every synced property, record and private document",
                tint = MaterialTheme.colorScheme.error
            ) { state.showDeleteAccountDialog = true }
            Hairline()
            NavRow(
                "Delete account",
                glyph = Glyph.DELETE,
                supporting = "Erases the workspace and closes the account permanently",
                tint = MaterialTheme.colorScheme.error
            ) {
                state.deleteIdentityConfirmation = ""
                state.showDeleteIdentityDialog = true
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Principles")
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                listOf(
                    "Financial records are isolated to your authenticated Firestore workspace.",
                    if (store.preferences.cloudAssistant) {
                        "Cloud analysis is on: computed figures go to Gemma, addresses and documents never do."
                    } else {
                        "Assistant analysis runs locally; no question is sent anywhere."
                    },
                    "Document bytes use private Blob storage; metadata stays in Firestore.",
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
                    "This removes all properties, income, expenses, private documents and conversations from this workspace. It cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { store.clearEverything() }
                            .onSuccess {
                                state.showDeleteAccountDialog = false
                                state.selectDestination(Route.DASHBOARD)
                                state.notify("All records erased")
                            }
                            .onFailure { state.notify(it.message ?: "Records could not be erased") }
                    }
                }) { Text("Erase everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { state.showDeleteAccountDialog = false }) { Text("Cancel") }
            },
            containerColor = PorticoTheme.semantic.panel
        )
    }

    /*
     * Deleting an account is the one action in the product with no undo and no
     * support path, so it asks for the word rather than a second tap. The same
     * word is re-checked on the server, which is what actually protects the
     * account; this is here so nobody arrives at the outcome by reflex.
     */
    if (state.showDeleteIdentityDialog) {
        val confirmed = state.deleteIdentityConfirmation.trim() == "DELETE"
        AlertDialog(
            onDismissRequest = {
                if (!state.deletingIdentity) state.showDeleteIdentityDialog = false
            },
            title = { Text("Delete your account?") },
            text = {
                Column {
                    Text(
                        "Every property, income and expense record, private document, receipt and conversation is erased, and your sign-in is closed. This cannot be undone and support cannot restore it."
                    )
                    Spacer(Modifier.height(Space.md))
                    PorticoField(
                        value = state.deleteIdentityConfirmation,
                        onValueChange = { state.deleteIdentityConfirmation = it },
                        label = "Type DELETE to confirm",
                        enabled = !state.deletingIdentity,
                        supporting = if (confirmed) null else "The word must match exactly."
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = confirmed && !state.deletingIdentity,
                    onClick = {
                        scope.launch {
                            state.deletingIdentity = true
                            val outcome = runCatching { store.deleteAccount() }
                            state.deletingIdentity = false
                            state.showDeleteIdentityDialog = false
                            state.deleteIdentityConfirmation = ""
                            outcome
                                .onSuccess { complete ->
                                    state.notify(
                                        if (complete) "Account deleted"
                                        else "Records deleted. Closing the sign-in did not complete; contact support if you can still sign in."
                                    )
                                    onSignOut()
                                }
                                .onFailure {
                                    // The device cache is wiped either way, so
                                    // signing out keeps the app honest about
                                    // what is left.
                                    state.notify(it.message ?: "The account could not be fully deleted")
                                    onSignOut()
                                }
                        }
                    }
                ) {
                    Text(
                        if (state.deletingIdentity) "Deleting..." else "Delete account",
                        color = if (confirmed && !state.deletingIdentity) {
                            MaterialTheme.colorScheme.error
                        } else {
                            PorticoTheme.semantic.tertiaryText
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.deletingIdentity,
                    onClick = { state.showDeleteIdentityDialog = false }
                ) { Text("Keep my account") }
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
