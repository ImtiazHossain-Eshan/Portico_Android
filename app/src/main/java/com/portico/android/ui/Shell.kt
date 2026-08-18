@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.portico.android.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.portico.android.domain.PlanTier
import com.portico.android.ui.design.*
import com.portico.android.ui.screens.*
import com.portico.android.ui.theme.PorticoTheme

/*
 * Navigation follows Material's size rules rather than shipping one phone
 * layout everywhere: a bottom bar on a phone, a rail from 600dp, and a wider
 * working area with two content columns from 840dp. Admin only makes sense as
 * a desk tool, so it gets the expanded table layout when there is room and a
 * single-column version when there is not.
 */

private data class Destination(val route: String, val label: String, val glyph: Glyph)

private val destinations = listOf(
    Destination(Route.DASHBOARD, "Overview", Glyph.OVERVIEW),
    Destination(Route.PORTFOLIO, "Portfolio", Glyph.PORTFOLIO),
    Destination(Route.REPORTS, "Reports", Glyph.REPORTS),
    Destination(Route.ASSISTANT, "Assistant", Glyph.ASSISTANT),
    Destination(Route.PROFILE, "Profile", Glyph.PROFILE)
)

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun PorticoShell(
    state: PorticoState,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val store = state.store

    // Measured from the container rather than the screen, so split-screen and
    // freeform windows get the layout that fits the space actually given.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val widthClass = WidthClass.from(maxWidth)

    CompositionLocalProvider(LocalWidthClass provides widthClass) {
        if (widthClass.isAtLeastMedium) {
            Row(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                PorticoRail(state)
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    ShellTopBar(state)
                    if (state.offline) OfflineBanner(onRetry = { state.offline = false })
                    ShellContent(
                        state = state,
                        onSignOut = onSignOut,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        } else {
            Scaffold(
                modifier = modifier,
                topBar = {
                    Column {
                        ShellTopBar(state)
                        if (state.offline) OfflineBanner(onRetry = { state.offline = false })
                    }
                },
                bottomBar = { PorticoNavBar(state) },
                containerColor = MaterialTheme.colorScheme.background,
                floatingActionButton = {
                    // Only the register gets a FAB. On the dashboard a floating
                    // control would sit over the figures it is meant to serve,
                    // and reading, not adding, is that screen's job.
                    if (state.route == Route.PORTFOLIO) {
                        FloatingActionButton(
                            onClick = {
                                if (store.canAddProperty) {
                                    state.resetDraft()
                                    state.navigate(Route.ADD_PROPERTY)
                                } else {
                                    state.showPaywall = true
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                        ) {
                            PorticoIcon(
                                Glyph.ADD,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                contentDescription = "Add property"
                            )
                        }
                    }
                }
            ) { padding ->
                ShellContent(
                    state = state,
                    onSignOut = onSignOut,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
        }

        if (state.showPaywall) {
            PropertyLimitDialog(state)
        }
    }
    }
}

@Composable
private fun PropertyLimitDialog(state: PorticoState) {
    val limit = state.store.subscription.tier.propertyLimit

    AlertDialog(
        onDismissRequest = { state.showPaywall = false },
        icon = {
            PorticoIcon(
                Glyph.SUBSCRIPTION,
                size = 28.dp,
                tint = MaterialTheme.colorScheme.primary,
                contentDescription = null
            )
        },
        title = { Text("Upgrade to add another property") },
        text = {
            Text(
                "Your Free plan includes $limit properties, and both are in use. " +
                    "Upgrade to Pro to remove the property limit and keep growing your portfolio."
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.showPaywall = false
                    state.navigate(Route.SUBSCRIPTION)
                }
            ) {
                Text("View Pro plan")
            }
        },
        dismissButton = {
            TextButton(onClick = { state.showPaywall = false }) {
                Text("Not now")
            }
        },
        containerColor = PorticoTheme.semantic.panel
    )
}

@Composable
private fun PorticoNavBar(state: PorticoState) {
    NavigationBar(
        containerColor = PorticoTheme.semantic.panel,
        tonalElevation = 0.dp
    ) {
        destinations.forEach { destination ->
            val selected = state.route == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { state.selectDestination(destination.route) },
                icon = {
                    PorticoIcon(
                        destination.glyph,
                        size = 22.dp,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = null
                    )
                },
                label = { Text(destination.label, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun PorticoRail(state: PorticoState) {
    val store = state.store
    NavigationRail(
        containerColor = PorticoTheme.semantic.panel,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                Spacer(Modifier.height(Space.lg))
                PorticoMark(size = 34.dp)
                FloatingActionButton(
                    onClick = {
                        if (store.canAddProperty) {
                            state.resetDraft()
                            state.navigate(Route.ADD_PROPERTY)
                        } else state.showPaywall = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                ) {
                    PorticoIcon(
                        Glyph.ADD,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        contentDescription = "Add property"
                    )
                }
                Spacer(Modifier.height(Space.sm))
            }
        }
    ) {
        destinations.forEach { destination ->
            val selected = state.route == destination.route
            NavigationRailItem(
                selected = selected,
                onClick = { state.selectDestination(destination.route) },
                icon = {
                    PorticoIcon(
                        destination.glyph,
                        size = 22.dp,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        contentDescription = null
                    )
                },
                label = { Text(destination.label, maxLines = 1) },
                colors = NavigationRailItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    selectedTextColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ShellTopBar(state: PorticoState) {
    val store = state.store
    val isChild = state.route !in Route.primary
    val property = store.propertyById(state.selectedPropertyId)

    val title = when (state.route) {
        Route.DASHBOARD -> store.profile.name.substringBefore(" ").ifBlank { "Overview" }
        Route.PORTFOLIO -> "Portfolio"
        Route.REPORTS -> "Reports"
        Route.ASSISTANT -> "Assistant"
        Route.PROFILE -> "Profile"
        Route.PROPERTY -> property?.name ?: "Property"
        Route.ADD_PROPERTY -> if (state.editingPropertyId != null) "Edit property" else "Add property"
        Route.DOCUMENTS -> "Documents"
        Route.TAX -> "Tax position"
        Route.TAX_ASSUMPTIONS -> "Tax assumptions"
        Route.VALUATION -> "Valuation"
        Route.ACQUISITION -> "Acquisition"
        Route.SUBSCRIPTION -> "Plans"
        Route.CHECKOUT -> "Checkout"
        Route.ENTERPRISE -> "Workspace"
        Route.NOTIFICATIONS -> "Notifications"
        Route.SETTINGS_PREFERENCES -> "Preferences"
        Route.SETTINGS_SECURITY -> "Security"
        Route.SETTINGS_PRIVACY -> "Privacy"
        Route.ADMIN -> "Admin"
        else -> "Portico"
    }

    val subtitle = when (state.route) {
        Route.DASHBOARD -> if (state.demoMode) "Demo workspace" else store.profile.email
        Route.PORTFOLIO -> "${store.properties.size} properties"
        else -> null
    }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                if (!isChild && LocalWidthClass.current == WidthClass.COMPACT) {
                    PorticoMark(size = 28.dp, contentDescription = null)
                }
                Column {
                    Text(
                        if (state.route == Route.DASHBOARD) "Good day, $title" else title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = PorticoTheme.semantic.tertiaryText,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        navigationIcon = {
            if (isChild) {
                GlyphButton(Glyph.BACK, "Go back") {
                    if (!state.goBack()) state.selectDestination(Route.DASHBOARD)
                }
            }
        },
        actions = {
            if (!isChild) {
                GlyphButton(
                    Glyph.NOTIFICATION,
                    "Notifications",
                    badge = store.unreadNotifications
                ) { state.navigate(Route.NOTIFICATIONS) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
private fun ShellContent(
    state: PorticoState,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val store = state.store

    // Fade-through between destinations: Material's own pattern, 200ms, and
    // short enough that a user in a task never waits on choreography.
    AnimatedContent(
        targetState = state.route,
        transitionSpec = {
            (fadeIn(tween(180)) togetherWith fadeOut(tween(120)))
        },
        label = "route",
        modifier = modifier
    ) { route ->
        val scroll = rememberScrollState()
        val pageModifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)

        when {
            !store.loaded -> LoadingScreen(Modifier.fillMaxSize())
            state.sessionExpired -> SessionExpiredState(
                modifier = Modifier.fillMaxSize(),
                onSignIn = { state.sessionExpired = false; state.replaceRoute(Route.LOGIN) },
                onUseDemo = { state.sessionExpired = false; state.demoMode = true }
            )
            else -> when (route) {
                Route.DASHBOARD -> DashboardScreen(state, pageModifier)
                Route.PORTFOLIO -> PortfolioScreen(state, pageModifier)
                Route.REPORTS -> ReportsScreen(state, pageModifier)
                Route.ASSISTANT -> AssistantScreen(state, Modifier.fillMaxSize())
                Route.PROFILE -> ProfileScreen(state, onSignOut, pageModifier)
                Route.PROPERTY -> PropertyDetailScreen(state, pageModifier)
                Route.ADD_PROPERTY -> AddPropertyScreen(state, pageModifier)
                Route.DOCUMENTS -> DocumentsScreen(state, pageModifier)
                Route.TAX -> TaxScreen(state, pageModifier)
                Route.TAX_ASSUMPTIONS -> TaxAssumptionsScreen(state, pageModifier)
                Route.VALUATION -> ValuationScreen(state, pageModifier)
                Route.ACQUISITION -> AcquisitionScreen(state, pageModifier)
                Route.SUBSCRIPTION -> SubscriptionScreen(state, pageModifier)
                Route.CHECKOUT -> CheckoutScreen(state, pageModifier)
                Route.ENTERPRISE -> EnterpriseScreen(state, pageModifier)
                Route.NOTIFICATIONS -> NotificationsScreen(state, pageModifier)
                Route.SETTINGS_PREFERENCES -> PreferencesScreen(state, pageModifier)
                Route.SETTINGS_SECURITY -> SecurityScreen(state, pageModifier)
                Route.SETTINGS_PRIVACY -> PrivacyScreen(state, pageModifier)
                Route.ADMIN -> AdminScreen(state, Modifier.fillMaxSize())
                else -> DashboardScreen(state, pageModifier)
            }
        }
    }
}
