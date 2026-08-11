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
                DataRow("Started", store.subscription.startDate.ifBlank { "—" })
                Hairline()
                DataRow("Renews", store.subscription.renewsOn ?: "—")
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
                store.setPlan(PlanTier.PRO)
                state.notify("Pro features unlocked")
            }
        )

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Manage")
            NavRow("Billing history", glyph = Glyph.CURRENCY, supporting = "No payments recorded") {
                state.notify("No billing provider is connected in this build.")
            }
            Hairline()
            NavRow("Workspace and team", glyph = Glyph.ENTERPRISE, supporting = "Organisations, roles and export") {
                state.navigate(Route.ENTERPRISE)
            }
        }

        SyntheticNote(
            "Pricing is not set in this build and no payment provider is connected. " +
                "Switching plans changes feature access locally so the restriction behaviour can be reviewed."
        )
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
                    Text("Price not set", style = MaterialTheme.typography.titleMedium, color = semantic.tertiaryText)
                    Text("placeholder", style = MaterialTheme.typography.labelSmall, color = semantic.tertiaryText)
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
                    if (tier == PlanTier.PRO) "Switch to Pro" else "Switch to Free",
                    Modifier.fillMaxWidth(),
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
 * the operations a team needs that an individual does not — bulk import and
 * export. Permissions are derived from the role model rather than listed by
 * hand, so the table cannot drift from the rules.
 */
@Composable
fun EnterpriseScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val semantic = PorticoTheme.semantic
    val organization = Seed.organization
    var selectedRole by remember { mutableStateOf(OrgRole.ANALYST) }

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
            NavRow("Bulk property import", glyph = Glyph.UPLOAD, supporting = "Bring a normalised register in as CSV") {
                state.notify("Bulk import needs a backend service. Not connected in this build.")
            }
            Hairline()
            NavRow("Export portfolio data", glyph = Glyph.DOCUMENT, supporting = "Every property, income, expense and tax line") {
                state.notify("Export prepared locally — ${store.properties.size} properties, ${store.income.size + store.expenses.size} records.")
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
