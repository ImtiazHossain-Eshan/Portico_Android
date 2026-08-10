package com.portico.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import com.portico.android.data.DemoRepository
import com.portico.android.model.ActivityItem
import com.portico.android.model.DraftProperty
import com.portico.android.model.PortfolioDocument
import com.portico.android.model.Property

class PorticoState {
    val properties: SnapshotStateList<Property> = mutableStateListOf<Property>().also { it.addAll(DemoRepository.properties) }
    val documents: SnapshotStateList<PortfolioDocument> = mutableStateListOf<PortfolioDocument>().also { it.addAll(DemoRepository.documents) }
    val activities: SnapshotStateList<ActivityItem> = mutableStateListOf<ActivityItem>().also { it.addAll(DemoRepository.activities) }

    // The route starts at the product's launch rail so the complete entry
    // journey is testable from a cold start: splash -> onboarding -> auth -> app.
    var route by mutableStateOf("splash")
        private set
    private val routeBackStack = mutableStateListOf<String>()
    var onboardingPage by mutableStateOf(0)
    var reducedMotion by mutableStateOf(false)
    var assistantLoading by mutableStateOf(false)
    var assistantError by mutableStateOf<String?>(null)
    var assistantOnline by mutableStateOf(false)
    var selectedPropertyId by mutableStateOf(1)
    var addStep by mutableStateOf(0)
    var draft by mutableStateOf(DraftProperty())
    var portfolioQuery by mutableStateOf("")
    var documentCategory by mutableStateOf("All")
    var reportSection by mutableStateOf("Performance")
    var assistantContext by mutableStateOf("Portfolio")
    var assistantInput by mutableStateOf("")
    var assistantMessages by mutableStateOf(emptyList<com.portico.android.model.ChatMessage>())
    var appearance by mutableStateOf("Light mode")
    var demoMode by mutableStateOf(false)
    var showFilterDialog by mutableStateOf(false)
    var showDocumentDialog by mutableStateOf(false)
    var showTransactionDialog by mutableStateOf(false)
    var transactionType by mutableStateOf("Income")
    var showViewerDialog by mutableStateOf(false)
    var showValuationDialog by mutableStateOf(false)
    var showUpgradeDialog by mutableStateOf(false)
    var showLogoutDialog by mutableStateOf(false)
    var authLoading by mutableStateOf(false)
    var authError by mutableStateOf<String?>(null)
    var toastMessage by mutableStateOf<String?>(null)

    val canGoBack: Boolean get() = routeBackStack.isNotEmpty() || route in childRoutes

    /** Push a contextual surface while keeping the originating screen available to Back. */
    fun navigate(target: String) {
        if (target == route) return
        if (target in primaryRoutes) {
            routeBackStack.clear()
        } else {
            routeBackStack.add(route)
        }
        route = target
    }

    /** Switch the main destination without leaving a stale child route behind. */
    fun selectDestination(target: String) {
        routeBackStack.clear()
        route = target
    }

    /** Replace the entry/auth state after a one-way transition such as splash or sign-in. */
    fun replaceRoute(target: String) {
        routeBackStack.clear()
        route = target
    }

    fun goBack(): Boolean {
        val previous = routeBackStack.removeLastOrNull()
        if (previous != null) {
            route = previous
            return true
        }
        if (route in childRoutes) {
            route = "dashboard"
            return true
        }
        return false
    }

    fun resetDraft() {
        draft = DraftProperty()
        addStep = 0
    }

    fun updateDraft(update: (DraftProperty) -> DraftProperty) {
        draft = update(draft)
    }

    private companion object {
        val primaryRoutes = setOf("dashboard", "portfolio", "reports", "assistant", "profile")
        val childRoutes = setOf("property", "add", "documents", "tax", "valuation", "acquisition", "subscription", "enterprise")
    }
}
