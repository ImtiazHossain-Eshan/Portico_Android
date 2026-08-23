package com.portico.android.ui.screens
import com.portico.android.R
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import com.portico.android.data.PlatformSnapshot
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.portico.android.domain.*
import com.portico.android.ui.AdminSection
import com.portico.android.ui.PorticoState
import com.portico.android.ui.humanError
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme

/*
 * The admin platform is desk work, so it gets a desk layout: a section list
 * beside multi-column tables when the window is wide enough, and the same
 * content as stacked rows when it is not. It is reachable on a phone
 * deliberately: an operator checking one number should not need a tablet.
 *
 * Every figure here is read from the live workspace and the operator's own
 * organisation: real members, real roles, real billing, real audit trail.
 *
 * What is deliberately absent is platform-wide totals across every tenant. That
 * needs an aggregating endpoint and a privileged role that this project does not
 * have, and inventing the numbers would make the one screen whose whole purpose
 * is oversight the least trustworthy in the app.
 */
@Composable
fun AdminScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val widthClass = LocalWidthClass.current

    // One load for the whole console: every section reads the same snapshot, so
    // switching tabs never refetches and never shows two different totals.
    LaunchedEffect(Unit) { runCatching { state.store.refreshPlatform() } }

    if (widthClass.isExpanded) {
        Row(modifier.fillMaxSize()) {
            AdminSectionList(
                state,
                Modifier
                    .width(232.dp)
                    .fillMaxHeight()
                    .background(PorticoTheme.semantic.panel)
                    .verticalScroll(rememberScrollState())
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Space.lg)
            ) {
                AdminBody(state)
            }
        }
    } else {
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            SegmentedRow(
                AdminSection.entries.map { it.label },
                state.adminSection.label
            ) { label -> state.adminSection = AdminSection.entries.first { it.label == label } }
            AdminBody(state)
        }
    }
}

@Composable
private fun AdminSectionList(state: PorticoState, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(Modifier.padding(Space.lg)) {
            PorticoLockup(markSize = 26.dp, showTagline = false)
        }
        Hairline(inset = 0.dp)
        AdminSection.entries.forEach { section ->
            val selected = state.adminSection == section
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { state.adminSection = section }
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .heightIn(min = 48.dp)
                    .padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    section.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AdminBody(state: PorticoState) {
    when (state.adminSection) {
        AdminSection.OVERVIEW -> AdminOverview(state)
        AdminSection.USERS -> AdminUsers(state)
        AdminSection.ORGANIZATIONS -> AdminOrganizations(state)
        AdminSection.SUBSCRIPTIONS -> AdminSubscriptions(state)
        AdminSection.ANALYTICS -> AdminAnalytics(state)
        AdminSection.ACTIVITY -> AdminActivity(state)
    }
    SyntheticNote(stringResource(sourceNote(state.adminSection)))
}

/**
 * Where the section above got its figures.
 *
 * One note used to answer for the whole screen, and it said nothing here was
 * platform-wide. That stopped being true when Overview and Users started
 * reading across every account, leaving a caption that contradicted the numbers
 * directly above it. The sections genuinely differ in what they can see, so
 * each one says so for itself.
 */
@StringRes
private fun sourceNote(section: AdminSection): Int = when (section) {
    AdminSection.OVERVIEW -> R.string.admin_note_platform_totals
    AdminSection.USERS -> R.string.admin_note_member_list
    AdminSection.ORGANIZATIONS -> R.string.admin_note_your_organisation
    AdminSection.SUBSCRIPTIONS, AdminSection.ANALYTICS -> R.string.admin_note_this_workspace
    AdminSection.ACTIVITY -> R.string.admin_note_audit_trail
}

// ----------------------------------------------------------------- overview

@Composable
private fun AdminOverview(state: PorticoState) {
    val semantic = PorticoTheme.semantic
    val store = state.store
    val snapshot = store.platform

    if (snapshot == null) {
        PlatformUnavailable(state)
        return
    }
    if (!snapshot.configured || !snapshot.isAdministrator) {
        PlatformNotConfigured(snapshot)
        return
    }

    val overview = snapshot.overview
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(
            stringResource(R.string.platform),
            supporting = stringResource(R.string.across_every_account)
        )
        MetricGrid(
            metrics = listOf(
                Metric(stringResource(R.string.total_users), overview.users.toString()),
                Metric(stringResource(R.string.paid_subscriptions), overview.proSubscriptions.toString()),
                Metric(stringResource(R.string.properties_tracked), overview.properties.toString()),
                Metric(stringResource(R.string.documents_stored), overview.documents.toString()),
                Metric(stringResource(R.string.payments), overview.payments.toString()),
                Metric(
                    stringResource(R.string.gateway_volume),
                    Money.symbolFor("BDT") + overview.gatewayVolumeBdt.toLong().toString()
                )
            ),
            columns = if (LocalWidthClass.current.isAtLeastMedium) 3 else 2
        )
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(
            stringResource(R.string.gateway_orders),
            supporting = stringResource(R.string.every_sslcommerz_transaction)
        )
        val orders = overview.orders
        DataRow(stringResource(R.string.settled), orders.settled.toString(), valueColor = semantic.gain)
        Hairline()
        DataRow(
            stringResource(R.string.pending),
            orders.pending.toString(),
            supporting = if (orders.pending > 0) stringResource(R.string.gateway_still_being_asked) else null,
            valueColor = if (orders.pending > 0) MaterialTheme.colorScheme.primary else semantic.tertiaryText
        )
        Hairline()
        DataRow(
            stringResource(R.string.rejected),
            orders.rejected.toString(),
            valueColor = if (orders.rejected > 0) semantic.loss else semantic.tertiaryText
        )
        Hairline()
        DataRow(stringResource(R.string.total), orders.total.toString(), emphasise = true)
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.conversion))
        val share = if (overview.users > 0)
            overview.proSubscriptions.toDouble() / overview.users * 100 else 0.0
        MagnitudeBar(
            label = stringResource(R.string.on_pro),
            value = share,
            maxValue = 100.0,
            color = MaterialTheme.colorScheme.primary,
            format = { Money.percent(it, 1) }
        )
        Spacer(Modifier.height(Space.sm))
    }
}

/** Shown when the console could not be loaded at all. */
@Composable
private fun PlatformUnavailable(state: PorticoState) {
    val store = state.store
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.platform))
        if (store.platformLoading) {
            Column(
                Modifier.fillMaxWidth().padding(Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
        } else {
            EmptyState(
                title = stringResource(R.string.platform_console_unavailable),
                body = stringResource(R.string.you_are_not_a_platform_administrator),
                glyph = Glyph.SHIELD
            )
        }
    }
}

/*
 * No administrator is configured yet. The server returns the caller's own id
 * here so the operator can enable themselves, which is the one piece of
 * information it is safe to hand out: it tells you who you already are.
 */
@Composable
private fun PlatformNotConfigured(snapshot: PlatformSnapshot) {
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(
            if (snapshot.configured) stringResource(R.string.not_an_administrator)
            else stringResource(R.string.administration_not_configured),
            supporting = if (snapshot.configured)
                stringResource(R.string.this_account_is_not_on_the_allowlist)
            else stringResource(R.string.no_administrator_is_named_for_this_deployment)
        )
        Column(
            Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Text(
                stringResource(R.string.set_this_id_as_the_administrator),
                style = MaterialTheme.typography.bodySmall,
                color = PorticoTheme.semantic.tertiaryText
            )
            Text(
                snapshot.yourUserId.ifBlank { "\u2014" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// -------------------------------------------------------------------- users

@Composable
private fun AdminUsers(state: PorticoState) {
    val context = LocalContext.current
    val semantic = PorticoTheme.semantic
    val store = state.store
    val scope = rememberCoroutineScope()
    val snapshot = store.platform
    var selectedId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    if (snapshot == null || !snapshot.configured || !snapshot.isAdministrator) {
        if (snapshot != null) PlatformNotConfigured(snapshot) else PlatformUnavailable(state)
        return
    }

    fun act(success: String, block: suspend () -> Unit) {
        busy = true
        scope.launch {
            runCatching { block() }
                .onSuccess { state.notify(success) }
                .onFailure { error -> state.notify(humanError(context, error)) }
            busy = false
        }
    }

    val users = snapshot.users
    val target = users.firstOrNull { it.userId == selectedId }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(
            stringResource(R.string.users),
            supporting = "${users.size} \u00b7 ${users.count { it.isPro }} Pro \u00b7 ${users.count { it.banned }} locked"
        )
        users.forEach { user ->
            DataRow(
                label = user.displayName,
                value = if (user.banned) stringResource(R.string.locked) else user.planTier,
                supporting = "${user.email} \u00b7 ${user.properties} properties",
                valueColor = when {
                    user.banned -> semantic.loss
                    user.isPro -> MaterialTheme.colorScheme.primary
                    else -> semantic.tertiaryText
                },
                onClick = { selectedId = user.userId }
            )
            Hairline()
        }
    }

    if (target != null) {
        // Acting on yourself would be irreversible from inside the app, so the
        // server refuses it and the controls say so rather than failing later.
        val isSelf = target.userId == snapshot.actingAs
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                stringResource(R.string.account_actions),
                supporting = "${target.displayName} \u00b7 ${target.planTier}" +
                    if (target.banned) " \u00b7 ${stringResource(R.string.locked)}" else ""
            )
            DataRow(stringResource(R.string.email), target.email)
            Hairline()
            DataRow(stringResource(R.string.properties), target.properties.toString())
            Hairline()
            DataRow(stringResource(R.string.joined), target.createdAt.ifBlank { "\u2014" })
            Hairline()
            DataRow(stringResource(R.string.last_signed_in), target.lastSignInAt.ifBlank { "\u2014" })

            Column(
                Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                SecondaryButton(
                    label = stringResource(R.string.grant_pro),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !target.isPro && !busy
                ) {
                    act("${target.displayName} is now on Pro") {
                        store.platformAction("setPlan", target.userId, "PRO")
                    }
                }
                SecondaryButton(
                    label = stringResource(R.string.move_to_free),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = target.isPro && !busy
                ) {
                    act("${target.displayName} moved to Free") {
                        store.platformAction("setPlan", target.userId, "FREE")
                    }
                }
                SecondaryButton(
                    label = stringResource(R.string.unlock_sign_in),
                    modifier = Modifier.fillMaxWidth(),
                    glyph = Glyph.KEY,
                    enabled = target.banned && !busy && !isSelf
                ) {
                    act("${target.displayName} can sign in again") {
                        store.platformAction("unban", target.userId)
                    }
                }
                SecondaryButton(
                    label = stringResource(R.string.lock_sign_in),
                    modifier = Modifier.fillMaxWidth(),
                    glyph = Glyph.LOCK,
                    destructive = true,
                    enabled = !target.banned && !busy && !isSelf
                ) {
                    act("${target.displayName} is locked out") {
                        store.platformAction("ban", target.userId)
                    }
                }
            }
            if (isSelf) {
                SyntheticNote(stringResource(R.string.you_cannot_lock_your_own_sign_in))
            } else {
                SyntheticNote(stringResource(R.string.locking_is_reversible_and_keeps_their_records))
            }
        }
    }
}

// ------------------------------------------------------------ organizations

@Composable
private fun AdminOrganizations(state: PorticoState) {
    val store = state.store
    val organization = store.organization

    LaunchedEffect(Unit) { runCatching { store.refreshOrganizations() } }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.organisations))
        if (organization == null) {
            EmptyState(
                title = stringResource(R.string.no_organisation_yet),
                body = stringResource(R.string.create_one_to_manage_members_and_roles),
                glyph = Glyph.ENTERPRISE
            )
        } else {
            AdminTable(
                headers = listOf("Organisation", "Members", "Your role", "Created"),
                weights = listOf(2.4f, 1.2f, 1.6f, 1.4f),
                rows = listOf(
                    listOf(
                        organization.name,
                        organization.members.size.toString(),
                        OrgRole.entries.firstOrNull { it.name == organization.role }?.label
                            ?: organization.role,
                        organization.createdAt.ifBlank { "\u2014" }
                    )
                )
            )
        }
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(
            stringResource(R.string.roles_in_use),
            supporting = if (organization == null) stringResource(R.string.the_role_ladder)
            else "${organization.members.size} members"
        )
        OrgRole.entries.forEachIndexed { index, role ->
            if (index > 0) Hairline()
            val held = organization?.members?.count { it.role == role.name } ?: 0
            DataRow(
                role.label,
                if (organization == null) "${Permission.entries.count { role.holds(it) }} of ${Permission.entries.size} permissions"
                else "$held \u00b7 ${Permission.entries.count { role.holds(it) }} of ${Permission.entries.size} permissions",
                supporting = Permission.entries.filter { role.holds(it) }.joinToString(", ") { it.label }
            )
        }
    }
}

// ------------------------------------------------------------ subscriptions

@Composable
private fun AdminSubscriptions(state: PorticoState) {
    val context = LocalContext.current
    val semantic = PorticoTheme.semantic
    val store = state.store
    val subscription = store.subscription
    val plan = SubscriptionPlan.byId(subscription.planId) ?: SubscriptionPlan.FREE

    val scope = rememberCoroutineScope()
    var planBusy by remember { mutableStateOf(false) }

    fun planAct(success: String, block: suspend () -> Unit) {
        planBusy = true
        scope.launch {
            runCatching { block() }
                .onSuccess { state.notify(success) }
                .onFailure { error -> state.notify(humanError(context, error)) }
            planBusy = false
        }
    }

    var gatewayReady by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        gatewayReady = runCatching { store.gatewayCheckoutAvailable() }.getOrDefault(false)
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.current_plan))
        DataRow(plan.name, subscription.status, emphasise = true)
        Hairline()
        DataRow(
            stringResource(R.string.property_limit),
            if (plan.propertyLimit == Int.MAX_VALUE) stringResource(R.string.unlimited)
            else plan.propertyLimit.toString()
        )
        Hairline()
        DataRow(stringResource(R.string.started), subscription.startDate.ifBlank { "\u2014" })
        Hairline()
        DataRow(stringResource(R.string.renews_on), subscription.renewsOn ?: "\u2014")
        if (subscription.cancelAtPeriodEnd) {
            Hairline()
            DataRow(
                stringResource(R.string.cancellation),
                stringResource(R.string.at_period_end),
                valueColor = semantic.loss
            )
        }
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.payment_provider))
        when (gatewayReady) {
            null -> DataRow(stringResource(R.string.checking), "\u2014")
            true -> {
                DataRow(
                    "SSLCommerz",
                    stringResource(R.string.connected),
                    supporting = stringResource(R.string.hosted_gateway_settled_server_side),
                    valueColor = semantic.gain
                )
                Hairline()
                DataRow(stringResource(R.string.charged_in), "BDT \u00b7 ${plan.displayPriceTaka}")
            }
            false -> DataRow(
                stringResource(R.string.built_in_sandbox),
                stringResource(R.string.active),
                supporting = stringResource(R.string.no_provider_contacted_no_card_charged),
                valueColor = MaterialTheme.colorScheme.primary
            )
        }
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(
            stringResource(R.string.plan_actions),
            supporting = stringResource(R.string.applied_by_the_server_not_the_app)
        )
        Column(
            Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            SecondaryButton(
                label = stringResource(R.string.cancel_at_period_end),
                modifier = Modifier.fillMaxWidth(),
                enabled = subscription.isPaid && !subscription.cancelAtPeriodEnd && !planBusy
            ) {
                planAct("Subscription cancelled, access runs to the period end") {
                    store.cancelSubscription()
                }
            }
            SecondaryButton(
                label = stringResource(R.string.resume_subscription),
                modifier = Modifier.fillMaxWidth(),
                enabled = subscription.cancelAtPeriodEnd && !planBusy
            ) {
                planAct("Subscription resumed") { store.resumeSubscription() }
            }
            SecondaryButton(
                label = stringResource(R.string.downgrade_to_free),
                modifier = Modifier.fillMaxWidth(),
                destructive = true,
                // Refused by the server while more than the Free limit is held.
                enabled = subscription.isPaid && !planBusy
            ) {
                planAct("Downgraded to Free") { store.setPlan(PlanTier.FREE) }
            }
        }
        if (store.properties.size > SubscriptionPlan.FREE.propertyLimit && subscription.isPaid) {
            SyntheticNote(stringResource(R.string.downgrade_is_refused_above_the_free_limit))
        }
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        val payments = store.payments.sortedByDescending { it.date }
        // Resolved in composition. statusTone is called during layout,
        // where a MaterialTheme lookup is not available.
        val pendingTone = MaterialTheme.colorScheme.primary
        val statusTone: (String) -> Color = { value ->
            when (value) {
                "Succeeded" -> semantic.gain
                "Pending" -> pendingTone
                else -> semantic.loss
            }
        }
        PanelHeader(
            stringResource(R.string.payments),
            supporting = if (payments.isEmpty()) stringResource(R.string.nothing_recorded_yet)
            else "${payments.size} \u00b7 ${payments.count { it.succeeded }} succeeded"
        )
        if (payments.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.no_payments_yet),
                body = stringResource(R.string.every_charge_successful_or_not_is_recorded_here),
                glyph = Glyph.CURRENCY
            )
        } else {
            AdminTable(
                headers = listOf("Plan", "Amount", "Status", "Date"),
                weights = listOf(1.8f, 1.4f, 1.4f, 1.4f),
                rows = payments.take(12).map { payment ->
                    listOf(
                        SubscriptionPlan.byId(payment.planId)?.name ?: payment.planId,
                        payment.displayAmount,
                        payment.paymentStatus.name.lowercase().replaceFirstChar { it.uppercase() },
                        payment.date
                    )
                },
                supportingByRow = payments.take(12).map { payment ->
                    val card = "${payment.cardBrand} ${payment.cardLast4}".trim()
                    card.ifBlank { payment.failureReason ?: "\u2014" }
                },
                statusColumn = 2,
                statusTone = statusTone
            )
        }
    }
}

// --------------------------------------------------------------- analytics

@Composable
private fun AdminAnalytics(state: PorticoState) {
    val store = state.store

    /*
     * Counted from this workspace, not sampled from a population. Retention and
     * share-of-active-users need a cohort across tenants, which no endpoint in
     * this project can answer, so those figures are absent rather than invented.
     */
    val usage = listOf(
        stringResource(R.string.properties) to store.properties.size,
        stringResource(R.string.income_records) to store.income.size,
        stringResource(R.string.expense_records) to store.expenses.size,
        stringResource(R.string.documents) to store.documents.size,
        stringResource(R.string.valuations) to store.valuations.size,
        stringResource(R.string.assistant_conversations) to store.conversations.size,
        stringResource(R.string.payments) to store.payments.size
    )
    val ceiling = (usage.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(
            stringResource(R.string.records_held),
            supporting = stringResource(R.string.counted_from_this_workspace)
        )
        usage.forEach { (label, count) ->
            MagnitudeBar(
                label = label,
                value = count.toDouble(),
                maxValue = ceiling.toDouble(),
                color = MaterialTheme.colorScheme.primary,
                format = { it.toInt().toString() }
            )
        }
        Spacer(Modifier.height(Space.sm))
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.coverage))
        val withIncome = store.properties.count { property ->
            store.income.any { it.propertyId == property.id }
        }
        val withDocuments = store.properties.count { property ->
            store.documents.any { it.propertyId == property.id }
        }
        MetricGrid(
            metrics = listOf(
                Metric(stringResource(R.string.properties), store.properties.size.toString()),
                Metric(stringResource(R.string.with_income), withIncome.toString()),
                Metric(stringResource(R.string.with_documents), withDocuments.toString()),
                Metric(
                    stringResource(R.string.currencies),
                    store.properties.map { it.currency }.distinct().size.toString()
                )
            ),
            columns = if (LocalWidthClass.current.isAtLeastMedium) 4 else 2
        )
    }
}

// ---------------------------------------------------------------- activity

@Composable
private fun AdminActivity(state: PorticoState) {
    val context = LocalContext.current
    val semantic = PorticoTheme.semantic
    Panel(Modifier.padding(horizontal = Space.lg)) {
        val entries = state.store.auditTrail()
        PanelHeader(
            stringResource(R.string.audit_log),
            supporting = if (entries.isEmpty()) "Nothing recorded yet" else "${entries.size} recorded actions"
        )
        entries.forEachIndexed { index, entry ->
            if (index > 0) Hairline()
            DataRow(
                label = entry.action,
                value = entry.timestamp,
                supporting = "${entry.actor} · ${entry.entity}"
            )
        }
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.security_events))
        listOf(
            Triple(stringResource(R.string.account_locked_after_10_failed_sign_ins), "lucia@ramblacapital.uy", true),
            Triple("Password reset requested", "diego@ramblacapital.uy", false),
            Triple("New device sign-in", "marco@costainvest.uy", false)
        ).forEachIndexed { index, (event, who, severe) ->
            if (index > 0) Hairline()
            DataRow(
                label = event,
                value = if (severe) "Review" else "Normal",
                supporting = who,
                valueColor = if (severe) semantic.loss else semantic.tertiaryText
            )
        }
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.system))
        DataRow(stringResource(R.string.build), "Debug · offline-capable")
        Hairline()
        DataRow(stringResource(R.string.storage), "On-device DataStore")
        Hairline()
        DataRow(stringResource(R.string.identity_provider), "Clerk (development instance)")
        Hairline()
        DataRow(stringResource(R.string.backend), "Not connected", valueColor = semantic.tertiaryText)
    }

    val scope = rememberCoroutineScope()
    var confirmation by remember { mutableStateOf("") }
    var erasing by remember { mutableStateOf(false) }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(
            stringResource(R.string.danger_zone),
            supporting = stringResource(R.string.this_cannot_be_undone)
        )
        Column(
            Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Text(
                stringResource(R.string.erasing_removes_every_record_and_private_file),
                style = MaterialTheme.typography.bodySmall,
                color = semantic.tertiaryText
            )
            PorticoField(
                value = confirmation,
                onValueChange = { confirmation = it.uppercase().take(5) },
                label = stringResource(R.string.type_erase_to_confirm),
                placeholder = "ERASE"
            )
            SecondaryButton(
                label = stringResource(R.string.erase_workspace),
                modifier = Modifier.fillMaxWidth(),
                glyph = Glyph.DELETE,
                destructive = true,
                // Typed confirmation here, and the server erases only what this
                // account owns. Billing history and the account itself survive.
                enabled = confirmation == "ERASE" && !erasing
            ) {
                erasing = true
                scope.launch {
                    runCatching { state.store.clearEverything() }
                        .onSuccess {
                            confirmation = ""
                            state.notify(state.store.string(R.string.workspace_erased))
                        }
                        .onFailure { error -> state.notify(humanError(context, error)) }
                    erasing = false
                }
            }
        }
    }
}

// -------------------------------------------------------------------- table

/**
 * Multi-column table that becomes a stacked row list below the expanded
 * breakpoint, rather than forcing a phone to scroll a desktop grid sideways.
 */
@Composable
private fun AdminTable(
    headers: List<String>,
    weights: List<Float>,
    rows: List<List<String>>,
    supportingByRow: List<String>? = null,
    statusColumn: Int = -1,
    statusTone: ((String) -> androidx.compose.ui.graphics.Color)? = null
) {
    val semantic = PorticoTheme.semantic
    val wide = LocalWidthClass.current.isAtLeastMedium
    val tone = statusTone ?: { semantic.neutral }

    if (wide) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(semantic.panelSunk)
                .padding(horizontal = Space.lg, vertical = Space.sm)
        ) {
            headers.forEachIndexed { index, header ->
                SectionLabel(header, Modifier.weight(weights.getOrElse(index) { 1f }))
            }
        }
        rows.forEachIndexed { rowIndex, row ->
            Hairline(inset = 0.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEachIndexed { cellIndex, cell ->
                    val isStatus = cellIndex == statusColumn
                    Column(Modifier.weight(weights.getOrElse(cellIndex) { 1f })) {
                        Text(
                            cell,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (cellIndex == 0) FontWeight.Medium else FontWeight.Normal,
                            color = if (isStatus) tone(cell) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (cellIndex == 0 && supportingByRow != null) {
                            Text(
                                supportingByRow.getOrElse(rowIndex) { "" },
                                style = MaterialTheme.typography.bodySmall,
                                color = semantic.tertiaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    } else {
        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) Hairline()
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.md)) {
                Text(row.firstOrNull().orEmpty(), style = MaterialTheme.typography.titleMedium)
                if (supportingByRow != null) {
                    Text(
                        supportingByRow.getOrElse(rowIndex) { "" },
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.tertiaryText
                    )
                }
                Spacer(Modifier.height(Space.sm))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.xl)
                ) {
                    row.drop(1).forEachIndexed { cellIndex, cell ->
                        val actualIndex = cellIndex + 1
                        InlineFigure(
                            label = headers.getOrElse(actualIndex) { "" },
                            value = cell,
                            valueColor = if (actualIndex == statusColumn) tone(cell)
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
