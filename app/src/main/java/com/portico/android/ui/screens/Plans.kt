package com.portico.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

/*
 * Plans are presented as a comparison, not a sales page: what each tier does,
 * what the current one is, and what changes on upgrade. Price is the one thing
 * this product cannot invent, so it ships marked as unset rather than as a
 * plausible-looking number the user might believe.
 */
@Composable
fun SubscriptionScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val current = store.subscription.tier
    val semantic = PorticoTheme.semantic
    var showPlanHistory by remember { mutableStateOf(false) }
    val planEvents = store.activity.filter { it.kind == ActivityKind.PLAN_CHANGED }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Your plan")
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
                DataRow("Started", store.subscription.startDate.ifBlank { "-" })
                Hairline()
                DataRow(
                    if (store.subscription.cancelAtPeriodEnd) "Access until" else "Renews",
                    store.subscription.renewsOn ?: "-"
                )
                Hairline()
                Box(Modifier.padding(Space.lg)) {
                    if (store.subscription.cancelAtPeriodEnd) {
                        PrimaryButton("Resume subscription", Modifier.fillMaxWidth()) {
                            store.resumeSubscription()
                            state.notify("Subscription resumed")
                        }
                    } else {
                        SecondaryButton(
                            "Cancel subscription",
                            Modifier.fillMaxWidth(),
                            destructive = true
                        ) {
                            store.cancelSubscription()
                            state.notify("Cancelled. Access continues to the end of the period")
                        }
                    }
                }
            }
        }

        PlanPanel(
            tier = PlanTier.FREE,
            current = current,
            features = listOf(
                "Up to 2 properties" to true,
                "ROI, cap rate, yields and cashflow" to true,
                "Performance and cashflow reports" to true,
                "Document library on device" to true,
                "Allocation and comparison reports" to false,
                "Editable tax assumptions" to false,
                "On-device assistant" to false
            ),
            onSelect = {
                store.setPlan(PlanTier.FREE)
                state.notify("Switched to Free")
            }
        )

        PlanPanel(
            tier = PlanTier.PRO,
            current = current,
            features = listOf(
                "Unlimited properties" to true,
                "ROI, cap rate, yields and cashflow" to true,
                "Performance and cashflow reports" to true,
                "Document library on device" to true,
                "Allocation and comparison reports" to true,
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
            PanelHeader("Manage")
            NavRow(
                "Billing history",
                glyph = Glyph.CURRENCY,
                value = "${store.payments.size}",
                supporting = "Every sandbox charge, successful or not"
            ) { showPlanHistory = !showPlanHistory }
            if (showPlanHistory) {
                if (store.payments.isEmpty()) {
                    EmptyState(
                        title = "No charges yet",
                        body = "Subscribing records a receipt here, including declines, so failed attempts are auditable too.",
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
            NavRow("Workspace and team", glyph = Glyph.ENTERPRISE, supporting = "Organisations, roles and export") {
                state.navigate(Route.ENTERPRISE)
            }
        }

        SyntheticNote(SANDBOX_NOTICE)
    }

    if (state.showPaywall) {
        ModalBottomSheetPaywall(state) { state.showPaywall = false }
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
                    if (tier == PlanTier.FREE) "Everything needed to track a small portfolio"
                    else "For portfolios past a couple of properties",
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
                    Text("per month · sandbox", style = MaterialTheme.typography.labelSmall, color = semantic.tertiaryText)
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
                SecondaryButton("Current plan", Modifier.fillMaxWidth(), enabled = false) {}
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ModalBottomSheetPaywall(state: PorticoState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PorticoTheme.semantic.panel) {
        PlanLimitState(
            limit = state.store.subscription.tier.propertyLimit,
            onUpgrade = {
                onDismiss()
                state.navigate(Route.SUBSCRIPTION)
            },
            onDismiss = onDismiss
        )
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
    val context = LocalContext.current
    val semantic = PorticoTheme.semantic
    val organization = Seed.organization
    var selectedRole by remember { mutableStateOf(OrgRole.ANALYST) }
    var importResult by remember { mutableStateOf<PorticoImport.Result?>(null) }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = PorticoImport.read(context, uri, store.properties.toList())
        if (result == null) {
            state.notify("That file couldn't be read as CSV.")
        } else {
            store.importProperties(result.imported, result.income, result.expenses)
            importResult = result
            state.notify(
                if (result.hasAnything) "Imported ${result.summary}"
                else "Nothing imported. ${result.summary}"
            )
        }
    }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Organisation")
            DataRow("Name", organization.name)
            Hairline()
            DataRow("Members", organization.memberCount.toString())
            Hairline()
            DataRow("Properties", store.properties.size.toString())
            Hairline()
            DataRow("Created", organization.createdAt)
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Members and roles", supporting = "${Seed.members.size} people")
            Seed.members.forEachIndexed { index, member ->
                if (index > 0) Hairline()
                DataRow(
                    label = member.name,
                    value = member.orgRole.label,
                    supporting = member.email,
                    valueColor = if (member.orgRole == OrgRole.OWNER) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { selectedRole = member.orgRole }
                )
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader(
                "What ${selectedRole.label} can do",
                supporting = "Tap a member above to see their permissions"
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
            PanelHeader("Operations")
            NavRow(
                "Bulk property import",
                glyph = Glyph.UPLOAD,
                supporting = "Bring a register in as CSV"
            ) {
                importResult = null
                runCatching { importPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) }
                    .onFailure { state.notify("No file picker available on this device.") }
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
                "Export portfolio data",
                glyph = Glyph.DOCUMENT,
                supporting = "Every property, income, expense and tax line as CSV"
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
            NavRow("Admin platform", glyph = Glyph.ADMIN, supporting = "Users, organisations, subscriptions and audit") {
                state.navigate(Route.ADMIN)
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Audit trail", supporting = "Every sensitive action is recorded")
            Seed.auditLog.take(5).forEachIndexed { index, entry ->
                if (index > 0) Hairline()
                DataRow(
                    label = entry.action,
                    value = "",
                    supporting = "${entry.actor} · ${entry.entity} · ${entry.timestamp}"
                )
            }
        }

        SyntheticNote(
            "Organisation, members and audit entries are illustrative. Role-based access is modelled locally; " +
                "enforcing it needs server-side rules."
        )
    }
}
