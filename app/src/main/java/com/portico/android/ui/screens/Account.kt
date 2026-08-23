package com.portico.android.ui.screens

import android.content.Context
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
import com.portico.android.ui.humanError
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
                        contentDescription = stringResource(R.string.your_profile_photograph),
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
            if (state.demoMode) StatusChip(stringResource(R.string.demo), tone = semantic.neutral)
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            MetricGrid(
                metrics = listOf(
                    Metric(stringResource(R.string.properties), store.properties.size.toString()),
                    Metric(stringResource(R.string.portfolio), Money.compact(portfolio.portfolioValue, profile.currency)),
                    Metric(stringResource(R.string.plan), store.subscription.tier.label)
                ),
                columns = 3
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.account))
            NavRow(stringResource(R.string.preferences), glyph = Glyph.SETTINGS, value = profile.currency,
                supporting = stringResource(R.string.currency_exchange_rates_jurisdiction_appearanc)) {
                state.navigate(Route.SETTINGS_PREFERENCES)
            }
            Hairline()
            NavRow(stringResource(R.string.notifications), glyph = Glyph.NOTIFICATION,
                value = if (store.unreadNotifications > 0) stringResource(R.string.n_new, store.unreadNotifications) else null,
                supporting = stringResource(R.string.what_portico_tells_you_about)) {
                state.navigate(Route.NOTIFICATIONS)
            }
            Hairline()
            NavRow(stringResource(R.string.security), glyph = Glyph.LOCK, supporting = stringResource(R.string.password_sessions_and_sign_in)) {
                state.navigate(Route.SETTINGS_SECURITY)
            }
            Hairline()
            NavRow(stringResource(R.string.privacy_and_data), glyph = Glyph.SHIELD, supporting = stringResource(R.string.what_is_stored_and_how_to_remove_it)) {
                state.navigate(Route.SETTINGS_PRIVACY)
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.portfolio_tools))
            NavRow(stringResource(R.string.documents), glyph = Glyph.DOCUMENT, value = store.documents.size.toString()) {
                state.navigate(Route.DOCUMENTS)
            }
            Hairline()
            NavRow(stringResource(R.string.tax_position), glyph = Glyph.TAX, value = store.taxProfile.jurisdiction.name) {
                state.navigate(Route.TAX)
            }
            Hairline()
            NavRow(stringResource(R.string.valuation), glyph = Glyph.VALUATION, supporting = stringResource(R.string.compare_against_the_market)) {
                state.navigate(Route.VALUATION)
            }
            Hairline()
            NavRow(stringResource(R.string.acquisition), glyph = Glyph.ACQUISITION, supporting = stringResource(R.string.analyse_a_property_before_buying)) {
                state.navigate(Route.ACQUISITION)
            }
            Hairline()
            NavRow(stringResource(R.string.plans), glyph = Glyph.SUBSCRIPTION, value = store.subscription.tier.label) {
                state.navigate(Route.SUBSCRIPTION)
            }
            Hairline()
            NavRow(stringResource(R.string.workspace), glyph = Glyph.ENTERPRISE, supporting = stringResource(R.string.members_roles_and_export)) {
                state.navigate(Route.ENTERPRISE)
            }
            Hairline()
            NavRow(stringResource(R.string.admin_platform), glyph = Glyph.ADMIN, supporting = stringResource(R.string.users_subscriptions_analytics_and_audit)) {
                state.navigate(Route.ADMIN)
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.session))
            NavRow(stringResource(R.string.sign_out), glyph = Glyph.LOGOUT, tint = MaterialTheme.colorScheme.error) {
                state.showLogoutDialog = true
            }
        }

        Box(Modifier.padding(horizontal = Space.lg)) {
            Text(
                stringResource(R.string.portico_imtiaz_hossain_23101137),
                style = MaterialTheme.typography.labelSmall,
                color = semantic.tertiaryText
            )
        }
    }

    if (state.showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { state.showLogoutDialog = false },
            title = { Text(stringResource(R.string.sign_out_of_portico)) },
            text = { Text(stringResource(R.string.your_device_cache_remains_private_sign_in_agai)) },
            confirmButton = {
                TextButton(onClick = {
                    state.showLogoutDialog = false
                    onSignOut()
                }) { Text(stringResource(R.string.sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { state.showLogoutDialog = false }) { Text(stringResource(R.string.stay_signed_in)) }
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
            PanelHeader(stringResource(R.string.profile))
            Box(Modifier.padding(Space.lg)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                    PorticoField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        label = stringResource(R.string.display_name)
                    )
                    PrimaryButton(stringResource(R.string.save_name), Modifier.fillMaxWidth(), enabled = editingName != profile.name) {
                        store.setProfile { it.copy(name = editingName.trim()) }
                        state.notify(R.string.name_updated)
                    }
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.currency), supporting = stringResource(R.string.every_figure_in_the_app_is_shown_in_this_curre))
            SegmentedRow(Money.currencies, profile.currency) { currency ->
                store.setProfile { it.copy(currency = currency) }
                state.notify("Now showing $currency")
            }
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                stringResource(R.string.exchange_rates),
                supporting = stringResource(R.string.units_per_1_usd_used_whenever_a_property_is_he),
                action = if (store.exchangeRates.isEdited) "Reset" else null,
                onAction = if (store.exchangeRates.isEdited) {
                    {
                        store.setExchangeRates { it.reset() }
                        state.notify(R.string.exchange_rates_reset_to_defaults)
                    }
                } else null
            )
            store.exchangeRates.rows().forEachIndexed { index, row ->
                if (index > 0) Hairline()
                if (row.isBase) {
                    DataRow(
                        "${row.currency} ${row.symbol}",
                        "base",
                        supporting = stringResource(R.string.every_other_rate_is_quoted_against_this)
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
                            label = stringResource(R.string.per_usd),
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                }
            }
            if (store.exchangeRates.updated.isNotBlank()) {
                Hairline()
                DataRow(stringResource(R.string.you_last_set_these), store.exchangeRates.updated)
            }
            SyntheticNote(FX_NOTICE)
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.tax_jurisdiction))
            NavRow(
                store.taxProfile.jurisdiction.name,
                glyph = Glyph.TAX,
                supporting = "${store.taxProfile.jurisdiction.countryName} · ${store.taxProfile.effectiveRules().size} rules"
            ) { state.navigate(Route.TAX_ASSUMPTIONS) }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.appearance))
            SegmentedRow(
                listOf(Appearance.SYSTEM, Appearance.LIGHT, Appearance.DARK),
                preferences.theme
            ) { theme -> store.setPreferences { it.copy(theme = theme) } }
            Spacer(Modifier.height(Space.md))
            SwitchRow(
                label = stringResource(R.string.reduce_motion),
                checked = preferences.reducedMotion,
                supporting = stringResource(R.string.turn_off_chart_reveals_and_value_transitions),
                glyph = Glyph.APPEARANCE,
                onCheckedChange = { value -> store.setPreferences { it.copy(reducedMotion = value) } }
            )
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.notify_me_about))
            SwitchRow(
                stringResource(R.string.rent_received),
                preferences.notificationsRent,
                supporting = stringResource(R.string.when_income_is_recorded_against_a_property),
                glyph = Glyph.INCOME
            ) { value -> store.setPreferences { it.copy(notificationsRent = value) } }
            Hairline()
            SwitchRow(
                stringResource(R.string.documents_and_leases),
                preferences.notificationsDocuments,
                supporting = stringResource(R.string.expiring_leases_and_missing_paperwork),
                glyph = Glyph.DOCUMENT
            ) { value -> store.setPreferences { it.copy(notificationsDocuments = value) } }
            Hairline()
            SwitchRow(
                stringResource(R.string.market_movement),
                preferences.notificationsMarket,
                supporting = stringResource(R.string.needs_a_connected_data_provider),
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
            val languageContext = LocalContext.current
            val language = AppLanguage.current(languageContext)
            SegmentedRow(
                AppLanguage.entries.map { it.label },
                language.label
            ) { label ->
                AppLanguage.entries.firstOrNull { it.label == label }?.let {
                    AppLanguage.apply(languageContext, it)
                }
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
            PanelHeader(stringResource(R.string.portico_intelligence))
            SwitchRow(
                stringResource(R.string.cloud_analysis),
                preferences.cloudAssistant,
                supporting = if (preferences.cloudAssistant) {
                    stringResource(R.string.computed_figures_are_sent_to_gemma_for_wording)
                } else {
                    stringResource(R.string.answers_are_worked_out_on_this_device_nothing)
                },
                glyph = Glyph.ASSISTANT
            ) { value -> store.setPreferences { it.copy(cloudAssistant = value) } }
            if (preferences.cloudAssistant) {
                Box(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                    Text(
                        stringResource(R.string.sent_property_names_types_regions_and_the_figu),
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.tertiaryText
                    )
                }
            }
        }

        SyntheticNote(
            stringResource(R.string.preferences_sync_with_your_private_workspace_p)
        )
    }
}

/** Rates read better without trailing zeros: 122 rather than 122.00. */
private fun formatRate(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.4f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')

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
            PanelHeader(stringResource(R.string.sign_in))
            DataRow(
                stringResource(R.string.method),
                if (state.demoMode) stringResource(R.string.demo_workspace) else stringResource(R.string.email_and_password),
                supporting = if (state.demoMode) "No account attached" else "Managed by Clerk"
            )
            Hairline()
            DataRow(stringResource(R.string.session), if (state.signedIn) stringResource(R.string.active_on_this_device) else "Local only")
            Hairline()
            DataRow(stringResource(R.string.password_policy), "15 characters minimum", supporting = stringResource(R.string.set_by_your_workspace))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                stringResource(R.string.active_sessions),
                supporting = if (sessions.isEmpty()) stringResource(R.string.nothing_signed_in_remotely)
                else stringResource(R.string.n_sessions_on_account, sessions.size)
            )
            if (sessions.isEmpty()) {
                DataRow(
                    stringResource(R.string.this_device),
                    if (state.demoMode) "Demo" else "Local",
                    supporting = stringResource(R.string.no_remote_session_to_revoke)
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
                                        is ClerkResult.Failure<*> -> state.notify(R.string.couldn_t_revoke_that_session)
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
            PanelHeader(state.store.string(R.string.manage))
            NavRow(
                state.store.string(R.string.change_password),
                glyph = Glyph.KEY,
                supporting = state.store.string(R.string.set_a_new_password_without_leaving_portico)
            ) {
                state.showPasswordDialog = true
            }
            Hairline()
            NavRow(
                state.store.string(R.string.sign_out_everywhere_else),
                glyph = Glyph.LOGOUT,
                supporting = if (otherSessions.isEmpty()) state.store.string(R.string.no_other_sessions_to_end)
                else "Ends ${otherSessions.size} other session${if (otherSessions.size == 1) "" else "s"}",
                tint = if (otherSessions.isEmpty()) semantic.tertiaryText else MaterialTheme.colorScheme.error
            ) {
                if (otherSessions.isEmpty()) {
                    state.notify(R.string.you_re_only_signed_in_on_this_device)
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
                            revoked == 0 -> state.store.string(R.string.couldn_t_reach_the_session_service)
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
                    Text(stringResource(R.string.contacting_the_session_service), style = MaterialTheme.typography.bodySmall, color = semantic.tertiaryText)
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(state.store.string(R.string.how_portico_protects_your_records))
            DataRow(state.store.string(R.string.financial_records), "Private cloud sync", supporting = state.store.string(R.string.owner_scoped_firestore_with_an_offline_device))
            Hairline()
            DataRow(state.store.string(R.string.documents), "Private cloud files", supporting = state.store.string(R.string.fetched_only_through_your_authenticated_sessio))
            Hairline()
            DataRow(state.store.string(R.string.password), state.store.string(R.string.never_stored_by_portico), supporting = state.store.string(R.string.handled_by_the_identity_provider))
            Hairline()
            DataRow(state.store.string(R.string.failed_sign_ins), state.store.string(R.string.locks_after_10_attempts), supporting = state.store.string(R.string.for_60_minutes))
        }

        SyntheticNote(
            stringResource(R.string.firestore_and_private_file_storage_enforce_per)
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
    val passwordContext = LocalContext.current
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
        title = { Text(stringResource(R.string.change_password)) },
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
                    error = if (mismatch) stringResource(R.string.the_two_entries_do_not_match) else null
                )
                SwitchRow(
                    stringResource(R.string.sign_out_other_devices),
                    signOutOthers,
                    supporting = stringResource(R.string.recommended_if_you_think_someone_else_has_the)
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
                        error = state.store.string(R.string.you_are_not_signed_in)
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
                                state.notify(R.string.password_changed)
                            }
                            is ClerkResult.Failure -> error = explainPasswordFailure(passwordContext, result)
                        }
                    }
                    working = false
                }
            }) { Text(if (working) "Saving..." else "Change password") }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = { state.showPasswordDialog = false }) {
                Text(stringResource(R.string.cancel))
            }
        },
        containerColor = PorticoTheme.semantic.panel
    )
}

/** Clerk's codes, turned into something a person can act on. */
private fun explainPasswordFailure(context: Context, failure: ClerkResult.Failure<*>): String {
    val response = failure.error as? ClerkErrorResponse
    val code = response?.errors?.firstOrNull()?.code.orEmpty()
    if (code.isEmpty() && failure.throwable != null) {
        return context.getString(R.string.can_t_reach_the_identity_service_check_your_co)
    }
    return when {
        code.contains("incorrect") || code.contains("verification") ->
            context.getString(R.string.that_current_password_is_not_right)
        code == "form_password_length_too_short" || code.contains("length") ->
            PasswordPolicy.summary
        code == "form_password_pwned" ->
            context.getString(R.string.that_password_appears_in_a_known_breach_list_c)
        code.contains("rate") || code.contains("too_many") ->
            context.getString(R.string.too_many_attempts_wait_a_few_minutes_and_try_a)
        else -> context.getString(R.string.the_password_could_not_be_changed_check_your_c)
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
            PanelHeader(stringResource(R.string.what_this_app_stores))
            DataRow(stringResource(R.string.properties), store.properties.size.toString())
            Hairline()
            DataRow(stringResource(R.string.income_and_expense_records), (store.income.size + store.expenses.size).toString())
            Hairline()
            DataRow(stringResource(R.string.documents), store.documents.size.toString())
            Hairline()
            DataRow(stringResource(R.string.conversations), store.conversations.size.toString())
            Hairline()
            DataRow(stringResource(R.string.location), stringResource(R.string.this_device_only))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.your_data))
            NavRow(
                stringResource(R.string.export_everything),
                glyph = Glyph.DOCUMENT,
                supporting = stringResource(R.string.n_records_as_csv, PorticoExport.recordCount(store))
            ) {
                val intent = PorticoExport.shareIntent(context, store)
                if (intent == null) {
                    state.notify(R.string.couldn_t_write_the_export_files_to_this_device)
                } else {
                    runCatching { context.startActivity(Intent.createChooser(intent, "Export portfolio")) }
                        .onFailure { state.notify(R.string.no_app_on_this_device_can_receive_the_export) }
                }
            }
            Hairline()
            NavRow(
                stringResource(R.string.sample_portfolio),
                glyph = Glyph.REFRESH,
                supporting = if (state.demoMode) stringResource(R.string.restore_the_demonstration_records) else stringResource(R.string.available_separately_from_the_demo_cockpit)
            ) {
                if (state.demoMode) {
                    store.resetToSeed()
                    state.notify(R.string.sample_portfolio_restored)
                } else {
                    state.notify(R.string.sign_out_and_choose_enter_the_demo_cockpit_to)
                }
            }
            Hairline()
            NavRow(
                stringResource(R.string.erase_all_records),
                glyph = Glyph.DELETE,
                supporting = stringResource(R.string.removes_every_synced_property_record_and_priva),
                tint = MaterialTheme.colorScheme.error
            ) { state.showDeleteAccountDialog = true }
            Hairline()
            NavRow(
                stringResource(R.string.delete_account),
                glyph = Glyph.DELETE,
                supporting = stringResource(R.string.erases_the_workspace_and_closes_the_account_pe),
                tint = MaterialTheme.colorScheme.error
            ) {
                state.deleteIdentityConfirmation = ""
                state.showDeleteIdentityDialog = true
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.principles))
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                listOf(
                    stringResource(R.string.financial_records_are_isolated_to_your_authent),
                    if (store.preferences.cloudAssistant) {
                        stringResource(R.string.cloud_analysis_is_on_computed_figures_go_to_ge)
                    } else {
                        stringResource(R.string.assistant_analysis_runs_locally_no_question_is)
                    },
                    stringResource(R.string.document_bytes_use_private_blob_storage_metada),
                    stringResource(R.string.deleting_a_property_deletes_everything_attache)
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
            stringResource(R.string.a_production_deployment_would_add_a_data_proce)
        )
    }

    if (state.showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { state.showDeleteAccountDialog = false },
            title = { Text(stringResource(R.string.erase_every_record)) },
            text = {
                Text(
                    stringResource(R.string.this_removes_all_properties_income_expenses_pr)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { store.clearEverything() }
                            .onSuccess {
                                state.showDeleteAccountDialog = false
                                state.selectDestination(Route.DASHBOARD)
                                state.notify(R.string.all_records_erased)
                            }
                            .onFailure { state.notify(it.message ?: store.string(R.string.records_could_not_be_erased)) }
                    }
                }) { Text(stringResource(R.string.erase_everything), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { state.showDeleteAccountDialog = false }) { Text(stringResource(R.string.cancel)) }
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
            title = { Text(stringResource(R.string.delete_your_account)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.every_property_income_and_expense_record_priva)
                    )
                    Spacer(Modifier.height(Space.md))
                    PorticoField(
                        value = state.deleteIdentityConfirmation,
                        onValueChange = { state.deleteIdentityConfirmation = it },
                        label = stringResource(R.string.type_delete_to_confirm),
                        enabled = !state.deletingIdentity,
                        supporting = if (confirmed) null else stringResource(R.string.the_word_must_match_exactly)
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
                                        else store.string(R.string.records_deleted_closing_the_sign_in_did_not_co)
                                    )
                                    onSignOut()
                                }
                                .onFailure {
                                    // The device cache is wiped either way, so
                                    // signing out keeps the app honest about
                                    // what is left.
                                    state.notify(it.message ?: store.string(R.string.the_account_could_not_be_fully_deleted))
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
                ) { Text(stringResource(R.string.keep_my_account)) }
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
                stringResource(R.string.notifications),
                supporting = if (store.unreadNotifications > 0) "${store.unreadNotifications} unread" else "All read",
                action = if (store.unreadNotifications > 0) "Mark all read" else null,
                onAction = if (store.unreadNotifications > 0) {
                    { store.markAllNotificationsRead() }
                } else null
            )
            if (store.notifications.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.nothing_to_catch_up_on),
                    body = stringResource(R.string.rent_lease_expiries_and_tax_changes_will_show),
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
                            { StatusChip(stringResource(R.string.new_label), tone = MaterialTheme.colorScheme.primary) }
                        } else null
                    )
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.settings))
            NavRow(stringResource(R.string.choose_what_to_be_notified_about), glyph = Glyph.SETTINGS) {
                state.navigate(Route.SETTINGS_PREFERENCES)
            }
        }
    }
}
