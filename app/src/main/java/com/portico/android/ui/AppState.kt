package com.portico.android.ui
import com.portico.android.R
import androidx.compose.ui.res.stringResource

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.portico.android.data.PorticoStore
import com.portico.android.domain.*

/** Where the app can be. Kept as constants so the back stack stays debuggable. */
object Route {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"

    const val DASHBOARD = "dashboard"
    const val PORTFOLIO = "portfolio"
    const val REPORTS = "reports"
    const val ASSISTANT = "assistant"
    const val PROFILE = "profile"

    const val PROPERTY = "property"
    const val ADD_PROPERTY = "add"
    const val DOCUMENTS = "documents"
    const val TAX = "tax"
    const val TAX_ASSUMPTIONS = "tax-assumptions"
    const val VALUATION = "valuation"
    const val ACQUISITION = "acquisition"
    const val SUBSCRIPTION = "subscription"
    const val CHECKOUT = "checkout"
    const val ENTERPRISE = "enterprise"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS_PREFERENCES = "settings-preferences"
    const val SETTINGS_SECURITY = "settings-security"
    const val SETTINGS_PRIVACY = "settings-privacy"
    const val ADMIN = "admin"

    val primary = listOf(DASHBOARD, PORTFOLIO, REPORTS, ASSISTANT, PROFILE)
    val public = setOf(SPLASH, ONBOARDING, LOGIN, REGISTER, FORGOT)
}

enum class SortMode(val label: String) {
    VALUE("Value"), ROI("ROI"), NET_YIELD("Net yield"), CASHFLOW("Cashflow"), NAME("Name")
}

/** Which admin surface is showing; §27's six areas. */
enum class AdminSection(val label: String) {
    OVERVIEW("Overview"),
    USERS("Users"),
    ORGANIZATIONS("Organizations"),
    SUBSCRIPTIONS("Subscriptions"),
    ANALYTICS("Analytics"),
    ACTIVITY("System activity")
}

enum class ReportSection(val label: String) {
    PERFORMANCE("Performance"),
    CASHFLOW("Cashflow"),
    ALLOCATION("Allocation"),
    COMPARISON("Compare"),
    GROSS_NET("Gross vs net")
}

enum class ChartRange(val label: String, val months: Int) {
    M1("1M", 1), M6("6M", 6), Y1("1Y", 12), Y5("5Y", 60), ALL("All", 120);

    /** "All" is the only range with a word in it; the rest are unit codes. */
    val isAll: Boolean get() = this == ALL
}

/** In-flight draft for the six-step capture. Strings because fields are text. */
data class PropertyDraft(
    val name: String = "",
    val address: String = "",
    val country: String = "Uruguay",
    val region: String = "",
    val type: String = PropertyType.RESIDENTIAL.label,
    val currency: String = "USD",
    val sizeSqm: String = "",
    val purchaseDate: String = "",
    val purchasePrice: String = "",
    val initialInvestment: String = "",
    val financing: String = "0",
    val monthlyRent: String = "",
    val otherIncome: String = "0",
    val propertyTax: String = "0",
    val maintenance: String = "0",
    val insurance: String = "0",
    val managementFees: String = "0",
    val otherExpenses: String = "0",
    val photoUris: List<String> = emptyList()
) {
    fun number(raw: String): Double = raw.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0

    val purchasePriceValue get() = number(purchasePrice)
    val investmentValue get() = number(initialInvestment).takeIf { it > 0 } ?: purchasePriceValue
    val monthlyIncomeTotal get() = number(monthlyRent) + number(otherIncome)
    val monthlyOperatingTotal get() = number(maintenance) + number(insurance) + number(managementFees) + number(otherExpenses)

    /** Step gates: Next stays disabled until the step can actually be saved. */
    fun isStepValid(step: Int): Boolean = when (step) {
        0 -> name.isNotBlank() && address.isNotBlank() && region.isNotBlank() && number(sizeSqm) > 0
        1 -> purchaseDate.isNotBlank() && purchasePriceValue > 0
        2 -> monthlyIncomeTotal >= 0
        3 -> true
        else -> true
    }

    /** Resource id rather than prose: this is not a composable scope. */
    fun validationHint(step: Int): Int? = when {
        isStepValid(step) -> null
        step == 0 -> R.string.add_a_name_address_region_and_size_to_continue
        step == 1 -> R.string.a_purchase_date_and_price_are_needed_to_calcul
        else -> null
    }
}

/** Checkout: card entry, authorising, then the outcome. */
enum class CheckoutStage { DETAILS, PROCESSING, SUCCESS, DECLINED }

/** Steps of the upload flow: select property, pick file, upload, confirm. */
enum class UploadStage { PROPERTY, FILE, UPLOADING, DONE, FAILED }

class PorticoState(val store: PorticoStore) {

    // ------------------------------------------------------------ navigation

    var route by mutableStateOf(Route.SPLASH)
        private set
    private val backStack = mutableStateListOf<String>()

    val canGoBack: Boolean get() = backStack.isNotEmpty()

    fun navigate(target: String) {
        if (target == route) return
        if (target in Route.primary) backStack.clear() else backStack.add(route)
        route = target
    }

    fun selectDestination(target: String) {
        backStack.clear()
        route = target
    }

    fun replaceRoute(target: String) {
        backStack.clear()
        route = target
    }

    fun goBack(): Boolean {
        val previous = backStack.removeLastOrNull() ?: return false
        route = previous
        return true
    }

    fun openProperty(id: String) {
        selectedPropertyId = id
        navigate(Route.PROPERTY)
    }

    // --------------------------------------------------------------- session

    var signedIn by mutableStateOf(false)
    var demoMode by mutableStateOf(false)
    var sessionExpired by mutableStateOf(false)
    var offline by mutableStateOf(false)

    /** Onboarding pager position, kept so system Back can step through it. */
    var onboardingPage by mutableIntStateOf(0)

    // -------------------------------------------------------------- surfaces

    var selectedPropertyId by mutableStateOf<String?>(null)
    var propertyTab by mutableStateOf("Overview")

    var addStep by mutableIntStateOf(0)
    var draft by mutableStateOf(PropertyDraft())
    var editingPropertyId by mutableStateOf<String?>(null)

    var portfolioQuery by mutableStateOf("")
    var sortMode by mutableStateOf(SortMode.VALUE)
    var filterCountry by mutableStateOf("All")
    var filterRegion by mutableStateOf("All")
    var filterType by mutableStateOf("All")
    var filterPerformance by mutableStateOf("All")

    var reportSection by mutableStateOf(ReportSection.PERFORMANCE)
    var chartRange by mutableStateOf(ChartRange.Y1)
    var comparisonSelection = mutableStateListOf<String>()

    var documentCategory by mutableStateOf("All")
    var documentQuery by mutableStateOf("")
    var viewerDocumentId by mutableStateOf<String?>(null)
    var uploadStage by mutableStateOf(UploadStage.PROPERTY)
    var uploadPropertyId by mutableStateOf<String?>(null)
    var uploadCategory by mutableStateOf(DocumentCategory.CONTRACTS.label)
    var uploadFileName by mutableStateOf("")

    var assistantContextPropertyId by mutableStateOf<String?>(null)
    var assistantScenario by mutableStateOf(false)
    var assistantInput by mutableStateOf("")
    var assistantLoading by mutableStateOf(false)
    var assistantError by mutableStateOf<String?>(null)
    var activeConversationId by mutableStateOf<String?>(null)

    var adminSection by mutableStateOf(AdminSection.OVERVIEW)

    var valuationPropertyId by mutableStateOf<String?>(null)
    var acquisitionStep by mutableIntStateOf(0)
    var acquisitionListingId by mutableStateOf<String?>(null)
    var acquisitionRent by mutableStateOf("")

    // ------------------------------------------------------------- overlays

    var showFilterSheet by mutableStateOf(false)
    var showTransactionSheet by mutableStateOf(false)
    var transactionIsIncome by mutableStateOf(true)
    var showUploadSheet by mutableStateOf(false)
    var showValuationSheet by mutableStateOf(false)
    var showLogoutDialog by mutableStateOf(false)
    var showDeleteAccountDialog by mutableStateOf(false)
    var showDeleteIdentityDialog by mutableStateOf(false)
    var showPasswordDialog by mutableStateOf(false)
    var deleteIdentityConfirmation by mutableStateOf("")
    var deletingIdentity by mutableStateOf(false)
    var showDiscardDraftDialog by mutableStateOf(false)
    var showPaywall by mutableStateOf(false)
    var checkoutStage by mutableStateOf(CheckoutStage.DETAILS)
    var checkoutPlanId by mutableStateOf(SubscriptionPlan.PRO_MONTHLY.id)
    var pendingDeletePropertyId by mutableStateOf<String?>(null)

    var toast by mutableStateOf<String?>(null)

    fun notify(message: String) { toast = message }

    /**
     * Toasts by resource id.
     *
     * Click handlers are not composable scopes, so stringResource cannot be
     * called inside one. Resolving through the store's context works anywhere
     * and keeps every message translatable.
     */
    fun notify(resId: Int) { toast = store.string(resId) }
    fun notify(resId: Int, vararg args: Any) { toast = store.string(resId, *args) }

    // ---------------------------------------------------------------- draft

    fun updateDraft(update: (PropertyDraft) -> PropertyDraft) { draft = update(draft) }

    fun resetDraft() {
        draft = PropertyDraft()
        addStep = 0
        editingPropertyId = null
    }

    /** Load an existing property into the draft so Edit reuses the same flow. */
    fun loadDraftFrom(property: Property) {
        val propertyIncome = store.incomeFor(property.id)
        val propertyExpenses = store.expensesFor(property.id)
        fun expense(category: ExpenseCategory) =
            propertyExpenses.filter { it.category == category.label }.sumOf { it.amount }.toString()

        editingPropertyId = property.id
        addStep = 0
        draft = PropertyDraft(
            name = property.name,
            address = property.address,
            country = property.country,
            region = property.region,
            type = property.type,
            currency = property.currency,
            sizeSqm = property.sizeSqm.toInt().toString(),
            purchaseDate = property.purchaseDate,
            purchasePrice = property.purchasePrice.toInt().toString(),
            initialInvestment = property.initialInvestment.toInt().toString(),
            financing = property.financingAmount.toInt().toString(),
            monthlyRent = propertyIncome.filter { it.category == IncomeCategory.RENT.label }
                .sumOf { it.amount }.toInt().toString(),
            otherIncome = propertyIncome.filter { it.category != IncomeCategory.RENT.label }
                .sumOf { it.amount }.toInt().toString(),
            propertyTax = expense(ExpenseCategory.PROPERTY_TAX),
            maintenance = expense(ExpenseCategory.MAINTENANCE),
            insurance = expense(ExpenseCategory.INSURANCE),
            managementFees = expense(ExpenseCategory.MANAGEMENT),
            otherExpenses = expense(ExpenseCategory.OTHER),
            photoUris = property.photoUris
        )
    }

    /** Analysis for step 5 and the review step, computed from the live draft. */
    fun draftFinancials(): PropertyFinancials {
        val provisional = Property(
            id = editingPropertyId ?: "draft",
            name = draft.name.ifBlank { "New property" },
            address = draft.address,
            country = draft.country,
            region = draft.region,
            type = draft.type,
            sizeSqm = draft.number(draft.sizeSqm),
            purchaseDate = draft.purchaseDate.ifBlank { SimpleDate.today().format() },
            purchasePrice = draft.purchasePriceValue,
            initialInvestment = draft.investmentValue,
            financingAmount = draft.number(draft.financing),
            currentValue = draft.purchasePriceValue,
            currency = draft.currency,
            photoUris = draft.photoUris
        )
        val draftIncome = buildList {
            if (draft.number(draft.monthlyRent) > 0)
                add(IncomeEntry("d-i1", provisional.id, draft.number(draft.monthlyRent), IncomeCategory.RENT.label, provisional.purchaseDate))
            if (draft.number(draft.otherIncome) > 0)
                add(IncomeEntry("d-i2", provisional.id, draft.number(draft.otherIncome), IncomeCategory.OTHER.label, provisional.purchaseDate))
        }
        val draftExpenses = buildList {
            fun add(amount: String, category: ExpenseCategory, id: String) {
                if (draft.number(amount) > 0)
                    add(ExpenseEntry(id, provisional.id, draft.number(amount), category.label, provisional.purchaseDate))
            }
            add(draft.maintenance, ExpenseCategory.MAINTENANCE, "d-e1")
            add(draft.insurance, ExpenseCategory.INSURANCE, "d-e2")
            add(draft.managementFees, ExpenseCategory.MANAGEMENT, "d-e3")
            add(draft.otherExpenses, ExpenseCategory.OTHER, "d-e4")
            add(draft.propertyTax, ExpenseCategory.PROPERTY_TAX, "d-e5")
        }
        return Finance.analyse(provisional, draftIncome, draftExpenses, store.taxProfile)
    }

    /** Commit the draft as a new property, or as an edit of an existing one. */
    suspend fun commitDraft() {
        val id = editingPropertyId ?: PorticoStore.newId("p")
        // Photographs move to private storage before the record references them.
        val storedPhotos = store.storePhotos(id, draft.photoUris)
        val property = Property(
            id = id,
            name = draft.name.trim(),
            address = draft.address.trim(),
            country = draft.country,
            region = draft.region.trim(),
            type = draft.type,
            sizeSqm = draft.number(draft.sizeSqm),
            purchaseDate = draft.purchaseDate.ifBlank { SimpleDate.today().format() },
            purchasePrice = draft.purchasePriceValue,
            initialInvestment = draft.investmentValue,
            financingAmount = draft.number(draft.financing),
            currentValue = store.propertyById(id)?.currentValue ?: draft.purchasePriceValue,
            currency = draft.currency,
            photoUris = storedPhotos,
            note = ""
        )

        val incomeEntries = buildList {
            if (draft.number(draft.monthlyRent) > 0)
                add(IncomeEntry(PorticoStore.newId("i"), id, draft.number(draft.monthlyRent), IncomeCategory.RENT.label, property.purchaseDate))
            if (draft.number(draft.otherIncome) > 0)
                add(IncomeEntry(PorticoStore.newId("i"), id, draft.number(draft.otherIncome), IncomeCategory.OTHER.label, property.purchaseDate))
        }
        val expenseEntries = buildList {
            fun push(amount: String, category: ExpenseCategory) {
                if (draft.number(amount) > 0)
                    add(ExpenseEntry(PorticoStore.newId("e"), id, draft.number(amount), category.label, property.purchaseDate))
            }
            push(draft.maintenance, ExpenseCategory.MAINTENANCE)
            push(draft.insurance, ExpenseCategory.INSURANCE)
            push(draft.managementFees, ExpenseCategory.MANAGEMENT)
            push(draft.otherExpenses, ExpenseCategory.OTHER)
            push(draft.propertyTax, ExpenseCategory.PROPERTY_TAX)
        }

        if (editingPropertyId != null) {
            store.updateProperty(property)
            store.incomeFor(id).forEach { store.removeIncome(it.id) }
            store.expensesFor(id).forEach { store.removeExpense(it.id) }
            incomeEntries.forEach(store::addIncome)
            expenseEntries.forEach(store::addExpense)
        } else {
            store.addProperty(property, incomeEntries, expenseEntries)
        }
        selectedPropertyId = id
        resetDraft()
    }

    // ------------------------------------------------------------- filtering

    /** Search, filter and sort applied together over computed financials. */
    fun visibleProperties(): List<PropertyFinancials> {
        val all = store.financials()
        return all
            .filter { result ->
                val property = result.property
                val matchesQuery = portfolioQuery.isBlank() ||
                    property.name.contains(portfolioQuery, ignoreCase = true) ||
                    property.region.contains(portfolioQuery, ignoreCase = true) ||
                    property.address.contains(portfolioQuery, ignoreCase = true)
                val matchesCountry = filterCountry == "All" || property.country == filterCountry
                val matchesRegion = filterRegion == "All" || property.region == filterRegion
                val matchesType = filterType == "All" || property.type == filterType
                val matchesPerformance = when (filterPerformance) {
                    "Positive cashflow" -> result.monthlyCashflow >= 0
                    "Negative cashflow" -> result.monthlyCashflow < 0
                    else -> true
                }
                matchesQuery && matchesCountry && matchesRegion && matchesType && matchesPerformance
            }
            .sortedWith(
                when (sortMode) {
                    SortMode.VALUE -> compareByDescending { it.property.currentValue }
                    SortMode.ROI -> compareByDescending { it.totalRoi }
                    SortMode.NET_YIELD -> compareByDescending { it.netYield }
                    SortMode.CASHFLOW -> compareByDescending { it.monthlyCashflow }
                    SortMode.NAME -> compareBy { it.property.name }
                }
            )
    }

    val hasActiveFilters: Boolean
        get() = filterCountry != "All" || filterRegion != "All" ||
            filterType != "All" || filterPerformance != "All"

    fun clearFilters() {
        filterCountry = "All"
        filterRegion = "All"
        filterType = "All"
        filterPerformance = "All"
    }
}
