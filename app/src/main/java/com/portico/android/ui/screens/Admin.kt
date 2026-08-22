package com.portico.android.ui.screens
import com.portico.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.portico.android.domain.*
import com.portico.android.ui.AdminSection
import com.portico.android.ui.PorticoState
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme

/*
 * The admin platform is desk work, so it gets a desk layout: a section list
 * beside multi-column tables when the window is wide enough, and the same
 * content as stacked rows when it is not. It is reachable on a phone
 * deliberately: an operator checking one number should not need a tablet.
 *
 * Every figure on these screens is illustrative platform data. This build has
 * no server, so nothing here reflects real users.
 */
@Composable
fun AdminScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val widthClass = LocalWidthClass.current

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
    SyntheticNote(
        stringResource(R.string.platform_figures_are_illustrative_this_build_h)
    )
}

// ----------------------------------------------------------------- overview

@Composable
private fun AdminOverview(state: PorticoState) {
    val semantic = PorticoTheme.semantic
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.platform), supporting = stringResource(R.string.last_30_days))
        MetricGrid(
            metrics = listOf(
                Metric(stringResource(R.string.total_users), "1,284", "+8.4%", 8.4),
                Metric(stringResource(R.string.active_users), "742", "+3.1%", 3.1),
                Metric(stringResource(R.string.properties_tracked), "4,918", "+11.2%", 11.2),
                Metric(stringResource(R.string.paid_subscriptions), "213", "+5.7%", 5.7),
                Metric(stringResource(R.string.documents_stored), "9,340", "+14.0%", 14.0),
                Metric(stringResource(R.string.assistant_queries), "2,106", "-2.3%", -2.3)
            ),
            columns = if (LocalWidthClass.current.isAtLeastMedium) 3 else 2
        )
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.signups), supporting = stringResource(R.string.weekly_trailing_quarter))
        Spacer(Modifier.height(Space.sm))
        CashflowColumns(
            values = listOf(42.0, 55.0, 48.0, 61.0, 73.0, 66.0, 81.0, 94.0, 88.0, 102.0, 97.0, 116.0),
            labels = listOf("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8", "W9", "W10", "W11", "W12"),
            currency = "USD"
        )
        Spacer(Modifier.height(Space.md))
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.needs_attention))
        DataRow(stringResource(R.string.failed_payments), "6", supporting = stringResource(R.string.retry_scheduled), valueColor = semantic.loss)
        Hairline()
        DataRow(stringResource(R.string.accounts_locked), "3", supporting = stringResource(R.string.ten_failed_sign_ins), valueColor = semantic.loss)
        Hairline()
        DataRow(stringResource(R.string.storage_above_quota), "2 organisations", valueColor = MaterialTheme.colorScheme.primary)
    }
}

// -------------------------------------------------------------------- users

private data class AdminUser(
    val name: String,
    val email: String,
    val plan: String,
    val properties: Int,
    val status: String,
    val lastActive: String
)

/*
 * Illustrative platform accounts. Held in app state rather than as a constant
 * so the admin actions below actually change something the operator can see,
 * a control that only reports is the pattern this build set out to remove.
 */
private val seedAdminUsers = listOf(
    AdminUser("Imtiaz Hossain", "imtiaz@ramblacapital.uy", "Pro", 2, "Active", "Today"),
    AdminUser("Sofía Márquez", "sofia@ramblacapital.uy", "Pro", 7, "Active", "Today"),
    AdminUser("Diego Ferrer", "diego@ramblacapital.uy", "Free", 2, "Active", "Yesterday"),
    AdminUser("Lucía Benítez", "lucia@ramblacapital.uy", "Free", 1, "Locked", "18 Mar"),
    AdminUser("Marco Silveira", "marco@costainvest.uy", "Pro", 12, "Active", "2 days ago"),
    AdminUser("Ana Rodríguez", "ana@plataestate.ar", "Free", 2, "Dormant", "11 Feb")
)

@Composable
private fun AdminUsers(state: PorticoState) {
    val semantic = PorticoTheme.semantic
    val users = remember { mutableStateListOf<AdminUser>().apply { addAll(seedAdminUsers) } }
    var selected by remember { mutableStateOf<AdminUser?>(null) }

    fun setStatus(user: AdminUser, status: String, message: String) {
        val index = users.indexOfFirst { it.email == user.email }
        if (index >= 0) {
            users[index] = users[index].copy(status = status)
            selected = users[index]
            state.notify(message)
        }
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(
            stringResource(R.string.users),
            supporting = "${users.size} accounts · ${users.count { it.status == "Locked" }} locked"
        )
        AdminTable(
            headers = listOf("Name", "Plan", "Properties", "Status", "Last active"),
            weights = listOf(2.4f, 1f, 1.2f, 1.2f, 1.3f),
            rows = users.map { user ->
                listOf(user.name, user.plan, user.properties.toString(), user.status, user.lastActive)
            },
            supportingByRow = users.map { it.email },
            statusColumn = 3,
            statusTone = { value ->
                when (value) {
                    "Locked" -> semantic.loss
                    "Suspended" -> semantic.loss
                    "Dormant" -> semantic.tertiaryText
                    else -> semantic.gain
                }
            }
        )
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(
            stringResource(R.string.account_actions),
            supporting = selected?.let { "${it.name} · ${it.status}" } ?: stringResource(R.string.choose_an_account_below)
        )
        users.forEach { user ->
            DataRow(
                label = user.name,
                value = user.status,
                supporting = user.email,
                valueColor = when (user.status) {
                    "Locked", "Suspended" -> semantic.loss
                    "Dormant" -> semantic.tertiaryText
                    else -> semantic.gain
                },
                onClick = { selected = user }
            )
            Hairline()
        }

        val target = selected
        Row(
            Modifier.fillMaxWidth().padding(Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            SecondaryButton(
                stringResource(R.string.unlock),
                Modifier.weight(1f),
                glyph = Glyph.KEY,
                enabled = target != null && target.status != "Active"
            ) {
                target?.let { setStatus(it, "Active", "${it.name} unlocked, sign-in counter cleared") }
            }
            SecondaryButton(
                stringResource(R.string.suspend),
                Modifier.weight(1f),
                glyph = Glyph.LOCK,
                destructive = true,
                enabled = target != null && target.status != "Suspended"
            ) {
                target?.let { setStatus(it, "Suspended", "${it.name} suspended") }
            }
        }
    }
}

// ------------------------------------------------------------ organizations

@Composable
private fun AdminOrganizations(state: PorticoState) {
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.organisations))
        AdminTable(
            headers = listOf("Organisation", "Members", "Properties", "Created"),
            weights = listOf(2.4f, 1.2f, 1.4f, 1.4f),
            rows = listOf(
                listOf("Rambla Capital", "4", "2", "12 Jan 2025"),
                listOf("Costa Invest", "9", "24", "03 Mar 2025"),
                listOf("Plata Estate", "3", "8", "22 Jul 2025")
            )
        )
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.roles_in_use), supporting = stringResource(R.string.across_all_organisations))
        OrgRole.entries.forEachIndexed { index, role ->
            if (index > 0) Hairline()
            DataRow(
                role.label,
                "${Permission.entries.count { role.holds(it) }} of ${Permission.entries.size} permissions",
                supporting = Permission.entries.filter { role.holds(it) }.joinToString(", ") { it.label }
            )
        }
    }
}

// ------------------------------------------------------------ subscriptions

@Composable
private fun AdminSubscriptions(state: PorticoState) {
    val semantic = PorticoTheme.semantic
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.plans))
        AdminTable(
            headers = listOf("Plan", "Property limit", "Subscribers", "Share"),
            weights = listOf(1.6f, 1.6f, 1.4f, 1.2f),
            rows = listOf(
                listOf("Free", "2", "1,071", "83%"),
                listOf("Pro", "Unlimited", "213", "17%")
            )
        )
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.recent_subscription_events))
        listOf(
            Triple("Upgraded to Pro", "marco@costainvest.uy", "Today"),
            Triple("Payment failed", "ana@plataestate.ar", "Yesterday"),
            Triple("Downgraded to Free", "diego@ramblacapital.uy", "16 Mar"),
            Triple("Upgraded to Pro", "sofia@ramblacapital.uy", "12 Mar")
        ).forEachIndexed { index, (event, who, when_) ->
            if (index > 0) Hairline()
            DataRow(
                label = event,
                value = when_,
                supporting = who,
                valueColor = if (event.contains("failed")) semantic.loss else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.payments))
        EmptyState(
            title = stringResource(R.string.no_payment_provider_connected),
            body = stringResource(R.string.transactions_invoices_and_refunds_appear_here),
            glyph = Glyph.CURRENCY
        )
    }
}

// --------------------------------------------------------------- analytics

@Composable
private fun AdminAnalytics(state: PorticoState) {
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.feature_usage), supporting = stringResource(R.string.share_of_active_users_last_30_days))
        listOf(
            "Dashboard" to 0.98,
            "Portfolio register" to 0.87,
            "Reports" to 0.61,
            "Tax module" to 0.44,
            "Documents" to 0.39,
            "Assistant" to 0.33,
            "Valuation" to 0.21,
            "Acquisition" to 0.12
        ).forEach { (feature, share) ->
            MagnitudeBar(
                label = feature,
                value = share * 100,
                maxValue = 100.0,
                color = MaterialTheme.colorScheme.primary,
                format = { Money.percent(it, 0) }
            )
        }
        Spacer(Modifier.height(Space.sm))
    }

    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.retention))
        MetricGrid(
            metrics = listOf(
                Metric(stringResource(R.string.day_1), "68%"),
                Metric(stringResource(R.string.day_7), "41%"),
                Metric(stringResource(R.string.day_30), "27%"),
                Metric(stringResource(R.string.median_properties), "3")
            ),
            columns = if (LocalWidthClass.current.isAtLeastMedium) 4 else 2
        )
    }
}

// ---------------------------------------------------------------- activity

@Composable
private fun AdminActivity(state: PorticoState) {
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
