package com.portico.android.ui.screens
import com.portico.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.portico.android.data.PorticoExport
import com.portico.android.data.PorticoImport
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.launch

/*
 * Plans are presented as a comparison, not a sales page: what each tier does,
 * what the current one is, and what changes on upgrade. Price is the one thing
 * this product cannot invent, so it ships marked as unset rather than as a
 * plausible-looking number the user might believe.
 */
@Composable
fun SubscriptionScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val scope = rememberCoroutineScope()
    val current = store.subscription.tier
    val semantic = PorticoTheme.semantic
    var showPlanHistory by remember { mutableStateOf(false) }
    val planEvents = store.activity.filter { it.kind == ActivityKind.PLAN_CHANGED }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.your_plan))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(current.label, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (current == PlanTier.FREE)
                            "${store.properties.size} of ${current.propertyLimit} properties used"
                        else "Unlimited properties",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.tertiaryText
                    )
                }
                StatusChip(
                    store.subscription.status,
                    tone = if (current == PlanTier.PRO) MaterialTheme.colorScheme.primary else semantic.neutral,
                    glyph = Glyph.CHECK
                )
            }
            if (current == PlanTier.FREE) {
                Spacer(Modifier.height(Space.sm))
                Box(Modifier.padding(horizontal = Space.lg)) {
                    // Register usage against the cap, so the limit is visible
                    // before it is hit rather than as a wall at the wrong moment.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(semantic.panelSunk)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(
                                    (store.properties.size.toFloat() / current.propertyLimit).coerceIn(0f, 1f)
                                )
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (store.canAddProperty) MaterialTheme.colorScheme.primary else semantic.loss
                                )
                        )
                    }
                }
                Spacer(Modifier.height(Space.md))
            } else {
                Hairline()
                DataRow(stringResource(R.string.started), store.subscription.startDate.ifBlank { "-" })
                Hairline()
                DataRow(
                    if (store.subscription.cancelAtPeriodEnd) "Access until" else "Renews",
                    store.subscription.renewsOn ?: "-"
                )
                Hairline()
                Box(Modifier.padding(Space.lg)) {
                    if (store.subscription.cancelAtPeriodEnd) {
                        PrimaryButton(stringResource(R.string.resume_subscription), Modifier.fillMaxWidth()) {
                            scope.launch {
                                runCatching { store.resumeSubscription() }
                                    .onSuccess { state.notify(R.string.subscription_resumed) }
                                    .onFailure { state.notify(it.message ?: store.string(R.string.subscription_could_not_be_resumed)) }
                            }
                        }
                    } else {
                        SecondaryButton(
                            stringResource(R.string.cancel_subscription),
                            Modifier.fillMaxWidth(),
                            destructive = true
                        ) {
                            scope.launch {
                                runCatching { store.cancelSubscription() }
                                    .onSuccess { state.notify(R.string.cancelled_access_continues_to_the_end_of_the_p) }
                                    .onFailure { state.notify(it.message ?: store.string(R.string.subscription_could_not_be_cancelled)) }
                            }
                        }
                    }
                }
            }
        }

        PlanPanel(
            tier = PlanTier.FREE,
            current = current,
            features = listOf(
                stringResource(R.string.up_to_2_properties) to true,
                stringResource(R.string.roi_cap_rate_yields_and_cashflow) to true,
                stringResource(R.string.performance_and_cashflow_reports) to true,
                stringResource(R.string.document_library_on_device) to true,
                stringResource(R.string.allocation_and_comparison_reports) to false,
                "Editable tax assumptions" to false,
                "On-device assistant" to false
            ),
            onSelect = {
                scope.launch {
                    runCatching { store.setPlan(PlanTier.FREE) }
                        .onSuccess { state.notify(R.string.switched_to_free) }
                        .onFailure { state.notify(it.message ?: store.string(R.string.plan_could_not_be_changed)) }
                }
            }
        )

        PlanPanel(
            tier = PlanTier.PRO,
            current = current,
            features = listOf(
                "Unlimited properties" to true,
                stringResource(R.string.roi_cap_rate_yields_and_cashflow) to true,
                stringResource(R.string.performance_and_cashflow_reports) to true,
                stringResource(R.string.document_library_on_device) to true,
                stringResource(R.string.allocation_and_comparison_reports) to true,
                "Editable tax assumptions" to true,
                "On-device assistant" to true
            ),
            onSelect = {
                state.checkoutPlanId = SubscriptionPlan.PRO_MONTHLY.id
                state.checkoutStage = com.portico.android.ui.CheckoutStage.DETAILS
                state.navigate(Route.CHECKOUT)
            }
        )

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.manage))
            NavRow(
                stringResource(R.string.billing_history),
                glyph = Glyph.CURRENCY,
                value = "${store.payments.size}",
                supporting = stringResource(R.string.every_sandbox_charge_successful_or_not)
            ) { showPlanHistory = !showPlanHistory }
            if (showPlanHistory) {
                if (store.payments.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.no_charges_yet),
                        body = stringResource(R.string.subscribing_records_a_receipt_here_including_d),
                        glyph = Glyph.CURRENCY
                    )
                } else {
                    store.payments.forEach { payment ->
                        Hairline()
                        DataRow(
                            label = SubscriptionPlan.byId(payment.planId)?.name ?: "Subscription",
                            value = payment.displayAmount,
                            supporting = "${payment.date} · ${payment.cardBrand} ending ${payment.cardLast4}" +
                                (payment.failureReason?.let { " · $it" } ?: ""),
                            valueColor = if (payment.succeeded) semantic.gain else semantic.loss
                        )
                    }
                }
            }
            Hairline()
            NavRow(stringResource(R.string.workspace_and_team), glyph = Glyph.ENTERPRISE, supporting = stringResource(R.string.organisations_roles_and_export)) {
                state.navigate(Route.ENTERPRISE)
            }
        }

        SyntheticNote(SANDBOX_NOTICE)
    }

}

@Composable
private fun PlanPanel(
    tier: PlanTier,
    current: PlanTier,
    features: List<Pair<String, Boolean>>,
    onSelect: () -> Unit
) {
    val semantic = PorticoTheme.semantic
    val isCurrent = tier == current

    Panel(Modifier.padding(horizontal = Space.lg), accent = tier == PlanTier.PRO) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, top = Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tier.label, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (tier == PlanTier.FREE) stringResource(R.string.everything_needed_to_track_a_small_portfolio)
                    else stringResource(R.string.for_portfolios_past_a_couple_of_properties),
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.tertiaryText
                )
            }
            if (tier == PlanTier.PRO) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        SubscriptionPlan.PRO_MONTHLY.displayPrice,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(stringResource(R.string.per_month_sandbox), style = MaterialTheme.typography.labelSmall, color = semantic.tertiaryText)
                }
            }
        }
        Spacer(Modifier.height(Space.md))
        features.forEach { (label, included) ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = Space.lg, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PorticoIcon(
                    if (included) Glyph.CHECK else Glyph.CLOSE,
                    size = 15.dp,
                    tint = if (included) semantic.gain else semantic.tertiaryText,
                    contentDescription = if (included) "Included" else "Not included"
                )
                Spacer(Modifier.width(Space.md))
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (included) MaterialTheme.colorScheme.onSurface else semantic.tertiaryText
                )
            }
        }
        Spacer(Modifier.height(Space.md))
        Box(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
            if (isCurrent) {
                SecondaryButton(stringResource(R.string.current_plan), Modifier.fillMaxWidth(), enabled = false) {}
            } else {
                PrimaryButton(
                    if (tier == PlanTier.PRO) "Subscribe" else "Switch to Free",
                    Modifier.fillMaxWidth(),
                    glyph = if (tier == PlanTier.PRO) Glyph.LOCK else null,
                    onClick = onSelect
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
    }
}

// ------------------------------------------------------------- enterprise

/*
 * The workspace view: who is in the organisation, what their role permits, and
 * the operations a team needs that an individual does not: bulk import and
 * export. Permissions are derived from the role model rather than listed by
 * hand, so the table cannot drift from the rules.
 */
@Composable
fun EnterpriseScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val semantic = PorticoTheme.semantic
    val organization = store.organization
    val members = organization?.members.orEmpty()
    var selectedRole by remember { mutableStateOf(OrgRole.ANALYST) }
    var importResult by remember { mutableStateOf<PorticoImport.Result?>(null) }
    var newOrgName by remember { mutableStateOf("") }
    var inviteEmail by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf(OrgRole.ANALYST) }
    var busy by remember { mutableStateOf(false) }

    // Membership decides what the rest of this screen may do, so it is read
    // from the server on entry rather than assumed from anything cached.
    LaunchedEffect(Unit) { store.refreshOrganizations() }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = PorticoImport.read(context, uri, store.properties.toList())
        if (result == null) {
            state.notify(R.string.that_file_couldn_t_be_read_as_csv)
        } else {
            scope.launch {
                runCatching { store.importProperties(result.imported, result.income, result.expenses) }
                    .onSuccess {
                        importResult = result
                        state.notify(
                            if (result.hasAnything) "Imported ${result.summary}"
                            else "Nothing imported. ${result.summary}"
                        )
                    }
                    .onFailure { error ->
                        if (error is com.portico.android.data.PorticoBackendException &&
                            error.code == "plan_limit_reached"
                        ) state.showPaywall = true
                        else state.notify(error.message ?: store.string(R.string.import_could_not_be_completed))
                    }
            }
        }
    }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        if (organization == null) {
            /*
             * No organization is the normal state for an individual investor,
             * so this is an invitation rather than an error. The old build
             * showed everybody the same fictional firm, which made the whole
             * section unreadable as a feature.
             */
            Panel(Modifier.padding(horizontal = Space.lg)) {
                PanelHeader(stringResource(R.string.organisation))
                Column(
                    Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    Text(
                        if (store.organizationsLoading) "Checking your memberships." else
                            stringResource(R.string.you_are_investing_on_your_own_account_create_a),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PorticoField(newOrgName, { newOrgName = it }, "Organisation name", placeholder = stringResource(R.string.rambla_capital))
                    PrimaryButton(
                        if (busy) "Creating..." else "Create organisation",
                        Modifier.fillMaxWidth(),
                        enabled = newOrgName.isNotBlank() && !busy && store.usesSecureBackend
                    ) {
                        scope.launch {
                            busy = true
                            runCatching { store.createOrganization(newOrgName.trim()) }
                                .onSuccess {
                                    newOrgName = ""
                                    state.notify(R.string.organisation_created_you_are_the_owner)
                                }
                                .onFailure { state.notify(it.message ?: store.string(R.string.the_organisation_could_not_be_created)) }
                            busy = false
                        }
                    }
                    if (!store.usesSecureBackend) {
                        Text(
                            stringResource(R.string.sign_in_to_create_an_organisation_demo_workspa),
                            style = MaterialTheme.typography.bodySmall,
                            color = semantic.tertiaryText
                        )
                    }
                }
            }
        } else {
            Panel(Modifier.padding(horizontal = Space.lg)) {
                PanelHeader(stringResource(R.string.organisation), supporting = "You are ${store.myRole.label.lowercase()}")
                DataRow(stringResource(R.string.name), organization.name)
                Hairline()
                DataRow(stringResource(R.string.members), members.size.toString())
                Hairline()
                DataRow(stringResource(R.string.properties), store.properties.size.toString())
                Hairline()
                DataRow(stringResource(R.string.created), organization.createdAt.take(10))
            }

            Panel(Modifier.padding(horizontal = Space.lg)) {
                PanelHeader(
                    stringResource(R.string.members_and_roles),
                    supporting = "${members.size} ${if (members.size == 1) "person" else "people"}"
                )
                members.forEachIndexed { index, member ->
                    if (index > 0) Hairline()
                    val role = runCatching { OrgRole.valueOf(member.role) }.getOrDefault(OrgRole.VIEWER)
                    DataRow(
                        label = member.name.ifBlank { member.email },
                        value = role.label,
                        supporting = member.email,
                        valueColor = if (role == OrgRole.OWNER) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { selectedRole = role }
                    )
                }
            }

            /*
             * Only shown to somebody who may actually use it. A control that
             * appears and then refuses is worse than one that is absent, and
             * the server enforces the same rule regardless of what renders.
             */
            if (store.may(Permission.MANAGE_MEMBERS)) {
                Panel(Modifier.padding(horizontal = Space.lg)) {
                    PanelHeader(stringResource(R.string.invite_a_member), supporting = stringResource(R.string.they_need_a_portico_account_already))
                    Column(
                        Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                        verticalArrangement = Arrangement.spacedBy(Space.md)
                    ) {
                        PorticoField(
                            inviteEmail, { inviteEmail = it }, "Email address",
                            keyboardType = KeyboardType.Email,
                            placeholder = stringResource(R.string.colleague_example_com)
                        )
                        SegmentedRow(
                            OrgRole.entries.filter { it.rank < store.myRole.rank }.map { it.label },
                            inviteRole.label
                        ) { label -> inviteRole = OrgRole.entries.first { it.label == label } }
                        PrimaryButton(
                            if (busy) "Inviting..." else "Add to organisation",
                            Modifier.fillMaxWidth(),
                            enabled = inviteEmail.contains("@") && !busy
                        ) {
                            scope.launch {
                                busy = true
                                runCatching { store.inviteMember(inviteEmail.trim(), inviteRole) }
                                    .onSuccess {
                                        inviteEmail = ""
                                        state.notify("Added to ${organization.name}")
                                    }
                                    .onFailure { state.notify(it.message ?: store.string(R.string.that_invitation_could_not_be_completed)) }
                                busy = false
                            }
                        }
                    }
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                "What ${selectedRole.label} can do",
                supporting = if (organization == null) stringResource(R.string.roles_apply_once_you_create_an_organisation)
                else stringResource(R.string.tap_a_member_above_to_see_their_permissions)
            )
            SegmentedRow(
                OrgRole.entries.map { it.label },
                selectedRole.label
            ) { label -> selectedRole = OrgRole.entries.first { it.label == label } }
            Spacer(Modifier.height(Space.md))
            Permission.entries.forEachIndexed { index, permission ->
                if (index > 0) Hairline()
                val allowed = selectedRole.holds(permission)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = Space.lg, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PorticoIcon(
                        if (allowed) Glyph.CHECK else Glyph.LOCK,
                        size = 15.dp,
                        tint = if (allowed) semantic.gain else semantic.tertiaryText,
                        contentDescription = if (allowed) "Allowed" else "Not allowed"
                    )
                    Spacer(Modifier.width(Space.md))
                    Text(
                        permission.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = if (allowed) MaterialTheme.colorScheme.onSurface else semantic.tertiaryText
                    )
                }
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.operations))
            NavRow(
                stringResource(R.string.bulk_property_import),
                glyph = Glyph.UPLOAD,
                supporting = stringResource(R.string.bring_a_register_in_as_csv)
            ) {
                importResult = null
                runCatching { importPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) }
                    .onFailure { state.notify(R.string.no_file_picker_available_on_this_device) }
            }
            importResult?.let { result ->
                Hairline()
                Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                    Text(result.summary, style = MaterialTheme.typography.titleMedium)
                    if (result.errors.isNotEmpty()) {
                        Spacer(Modifier.height(Space.sm))
                        result.errors.take(5).forEach { rowError ->
                            Text(
                                "Line ${rowError.line}: ${rowError.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.loss
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        "Expected header: ${PorticoImport.expectedHeader}",
                        style = MaterialTheme.typography.labelSmall,
                        color = semantic.tertiaryText
                    )
                }
            }
            Hairline()
            NavRow(
                stringResource(R.string.export_portfolio_data),
                glyph = Glyph.DOCUMENT,
                supporting = stringResource(R.string.every_property_income_expense_and_tax_line_as)
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
            NavRow(stringResource(R.string.admin_platform), glyph = Glyph.ADMIN, supporting = stringResource(R.string.users_organisations_subscriptions_and_audit)) {
                state.navigate(Route.ADMIN)
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(stringResource(R.string.audit_trail), supporting = stringResource(R.string.every_sensitive_action_is_recorded))
            store.auditTrail(limit = 5).forEachIndexed { index, entry ->
                if (index > 0) Hairline()
                DataRow(
                    label = entry.action,
                    value = "",
                    supporting = "${entry.actor} · ${entry.entity} · ${entry.timestamp}"
                )
            }
        }

        SyntheticNote(
            stringResource(R.string.organisation_members_and_audit_entries_are_ill) +
                stringResource(R.string.enforcing_it_needs_server_side_rules)
        )
    }
}
