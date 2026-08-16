package com.portico.android.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.portico.android.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "portico")
private val SNAPSHOT_KEY = stringPreferencesKey("snapshot_v1")

/** Everything that survives a restart, in one serialisable envelope. */
@Serializable
data class PorticoSnapshot(
    val properties: List<Property> = emptyList(),
    val income: List<IncomeEntry> = emptyList(),
    val expenses: List<ExpenseEntry> = emptyList(),
    val documents: List<PortfolioDocument> = emptyList(),
    val valuations: List<PropertyValuation> = emptyList(),
    val activity: List<ActivityEvent> = emptyList(),
    val notifications: List<Notification> = emptyList(),
    val conversations: List<AiConversation> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val profile: UserProfile = UserProfile(Seed.DEMO_USER_ID, "", ""),
    val preferences: UserPreferences = UserPreferences(),
    val taxProfile: TaxProfile = TaxProfile(),
    val exchangeRates: ExchangeRates = ExchangeRates(),
    val subscription: Subscription = Subscription(),
    val seeded: Boolean = false
)

/**
 * Single owner of Portico's data.
 *
 * State is exposed as Compose snapshot collections so screens recompose
 * naturally, and every mutation funnels through a method that also schedules a
 * persist. Nothing writes to these lists directly from the UI.
 */
class PorticoStore(private val appContext: Context, private val scope: CoroutineScope) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val properties: SnapshotStateList<Property> = mutableStateListOf()
    val income: SnapshotStateList<IncomeEntry> = mutableStateListOf()
    val expenses: SnapshotStateList<ExpenseEntry> = mutableStateListOf()
    val documents: SnapshotStateList<PortfolioDocument> = mutableStateListOf()
    val valuations: SnapshotStateList<PropertyValuation> = mutableStateListOf()
    val activity: SnapshotStateList<ActivityEvent> = mutableStateListOf()
    val notifications: SnapshotStateList<Notification> = mutableStateListOf()
    val conversations: SnapshotStateList<AiConversation> = mutableStateListOf()
    val payments: SnapshotStateList<Payment> = mutableStateListOf()

    var profile by mutableStateOf(UserProfile(Seed.DEMO_USER_ID, "Portico member", ""))
        private set
    var preferences by mutableStateOf(UserPreferences())
        private set
    var taxProfile by mutableStateOf(TaxProfile())
        private set
    var exchangeRates by mutableStateOf(ExchangeRates())
        private set
    var subscription by mutableStateOf(Subscription())
        private set

    /** False until the first read completes, so screens can show skeletons. */
    var loaded by mutableStateOf(false)
        private set

    // ------------------------------------------------------------ lifecycle

    suspend fun load() {
        val stored = withContext(Dispatchers.IO) {
            runCatching {
                appContext.dataStore.data.first()[SNAPSHOT_KEY]
                    ?.let { json.decodeFromString<PorticoSnapshot>(it) }
            }.getOrNull()
        }
        apply(stored ?: seedSnapshot())
        loaded = true
        if (stored == null) persist()
    }

    private fun seedSnapshot() = PorticoSnapshot(
        properties = Seed.properties,
        income = Seed.income,
        expenses = Seed.expenses,
        documents = Seed.documents,
        activity = Seed.activity,
        notifications = Seed.notifications,
        conversations = Seed.conversations,
        profile = UserProfile(
            userId = Seed.DEMO_USER_ID,
            name = "Imtiaz Hossain",
            email = "imtiaz@ramblacapital.uy",
            country = "Uruguay",
            currency = "USD",
            taxJurisdiction = Jurisdiction.URUGUAY_MONTEVIDEO
        ),
        seeded = true
    )

    private fun apply(snapshot: PorticoSnapshot) {
        properties.replaceAll(snapshot.properties)
        income.replaceAll(snapshot.income)
        expenses.replaceAll(snapshot.expenses)
        documents.replaceAll(snapshot.documents)
        valuations.replaceAll(snapshot.valuations)
        activity.replaceAll(snapshot.activity)
        notifications.replaceAll(snapshot.notifications)
        conversations.replaceAll(snapshot.conversations)
        payments.replaceAll(snapshot.payments)
        profile = snapshot.profile
        preferences = snapshot.preferences
        taxProfile = snapshot.taxProfile
        exchangeRates = snapshot.exchangeRates
        subscription = snapshot.subscription
    }

    private fun snapshot() = PorticoSnapshot(
        properties = properties.toList(),
        income = income.toList(),
        expenses = expenses.toList(),
        documents = documents.toList(),
        valuations = valuations.toList(),
        activity = activity.toList(),
        notifications = notifications.toList(),
        conversations = conversations.toList(),
        payments = payments.toList(),
        profile = profile,
        preferences = preferences,
        taxProfile = taxProfile,
        exchangeRates = exchangeRates,
        subscription = subscription,
        seeded = true
    )

    private fun persist() {
        val payload = runCatching { json.encodeToString(snapshot()) }.getOrNull() ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { appContext.dataStore.edit { it[SNAPSHOT_KEY] = payload } }
        }
    }

    // ----------------------------------------------------------- properties

    fun addProperty(property: Property, incomeEntries: List<IncomeEntry>, expenseEntries: List<ExpenseEntry>) {
        properties.add(property)
        income.addAll(incomeEntries)
        expenses.addAll(expenseEntries)
        logActivity(ActivityKind.PROPERTY_ADDED, "Property added", property.name, null, property.id)
        persist()
    }

    fun updateProperty(property: Property) {
        val index = properties.indexOfFirst { it.id == property.id }
        if (index >= 0) {
            properties[index] = property
            logActivity(ActivityKind.PROPERTY_UPDATED, "Property updated", property.name, null, property.id)
            persist()
        }
    }

    fun deleteProperty(propertyId: String) {
        properties.removeAll { it.id == propertyId }
        income.removeAll { it.propertyId == propertyId }
        expenses.removeAll { it.propertyId == propertyId }
        documents.removeAll { it.propertyId == propertyId }
        valuations.removeAll { it.propertyId == propertyId }
        persist()
    }

    /** Bulk insert from an import. One activity entry, not one per row. */
    fun importProperties(
        newProperties: List<Property>,
        newIncome: List<IncomeEntry>,
        newExpenses: List<ExpenseEntry>
    ) {
        if (newProperties.isEmpty()) return
        properties.addAll(newProperties)
        income.addAll(newIncome)
        expenses.addAll(newExpenses)
        logActivity(
            ActivityKind.PROPERTY_ADDED,
            "Bulk import",
            "${newProperties.size} properties added from CSV",
            null,
            null
        )
        persist()
    }

    fun propertyById(id: String?): Property? = properties.firstOrNull { it.id == id }

    fun updateValuation(propertyId: String, newValue: Double, source: String) {
        val property = propertyById(propertyId) ?: return
        val delta = newValue - property.currentValue
        updatePropertyValueSilently(property.copy(currentValue = newValue))
        valuations.add(
            PropertyValuation(newId("v"), propertyId, newValue, SimpleDate.today().format(), source)
        )
        logActivity(ActivityKind.VALUATION_UPDATED, "Valuation updated", "${property.name} · $source", delta, propertyId)
        persist()
    }

    private fun updatePropertyValueSilently(property: Property) {
        val index = properties.indexOfFirst { it.id == property.id }
        if (index >= 0) properties[index] = property
    }

    // ------------------------------------------------------------ financial

    fun addIncome(entry: IncomeEntry) {
        income.add(entry)
        val name = propertyById(entry.propertyId)?.name ?: "Property"
        logActivity(ActivityKind.RENT_RECEIVED, "Income recorded", "$name · ${entry.category}", entry.amount, entry.propertyId)
        persist()
    }

    fun addExpense(entry: ExpenseEntry) {
        expenses.add(entry)
        val name = propertyById(entry.propertyId)?.name ?: "Property"
        val kind = if (ExpenseCategory.from(entry.category).isOperating) ActivityKind.EXPENSE_ADDED else ActivityKind.TAX_RECORDED
        logActivity(kind, "Expense recorded", "$name · ${entry.category}", -entry.amount, entry.propertyId)
        persist()
    }

    fun removeIncome(id: String) { income.removeAll { it.id == id }; persist() }
    fun removeExpense(id: String) { expenses.removeAll { it.id == id }; persist() }

    fun incomeFor(propertyId: String) = income.filter { it.propertyId == propertyId }
    fun expensesFor(propertyId: String) = expenses.filter { it.propertyId == propertyId }

    // ------------------------------------------------------------ documents

    fun addDocument(document: PortfolioDocument) {
        documents.add(document)
        logActivity(ActivityKind.DOCUMENT_UPLOADED, "Document uploaded", document.fileName, null, document.propertyId)
        persist()
    }

    fun updateDocumentStatus(id: String, status: DocumentStatus) {
        val index = documents.indexOfFirst { it.id == id }
        if (index >= 0) {
            documents[index] = documents[index].copy(status = status)
            persist()
        }
    }

    fun removeDocument(id: String) { documents.removeAll { it.id == id }; persist() }

    fun documentsFor(propertyId: String) = documents.filter { it.propertyId == propertyId }

    // -------------------------------------------------------- preferences

    fun setProfile(update: (UserProfile) -> UserProfile) { profile = update(profile); persist() }
    fun setPreferences(update: (UserPreferences) -> UserPreferences) { preferences = update(preferences); persist() }
    fun setTaxProfile(update: (TaxProfile) -> TaxProfile) { taxProfile = update(taxProfile); persist() }
    fun setExchangeRates(update: (ExchangeRates) -> ExchangeRates) { exchangeRates = update(exchangeRates); persist() }

    /**
     * Activates a paid plan against a settled payment. Both move together,
     * a subscription is never activated without the payment that paid for it.
     */
    fun activatePlan(plan: SubscriptionPlan, payment: Payment) {
        payments.add(0, payment)
        if (!payment.succeeded) {
            logActivity(
                ActivityKind.PLAN_CHANGED,
                "Payment ${payment.paymentStatus.name.lowercase()}",
                "${plan.name} · ${payment.displayAmount} · ${payment.cardBrand} ending ${payment.cardLast4}",
                null, null
            )
            persist()
            return
        }
        subscription = Subscription(
            planTier = PlanTier.PRO.name,
            planId = plan.id,
            status = "Active",
            startDate = SimpleDate.today().format(),
            renewsOn = nextRenewal(plan.interval),
            cancelAtPeriodEnd = false
        )
        logActivity(
            ActivityKind.PLAN_CHANGED,
            "Subscribed to ${plan.name}",
            "${payment.displayAmount} · ${payment.cardBrand} ending ${payment.cardLast4}",
            null, null
        )
        persist()
    }

    /** Cancel keeps access to the end of the period, as a real one would. */
    fun cancelSubscription() {
        if (!subscription.isPaid) return
        subscription = subscription.copy(status = "Cancels at period end", cancelAtPeriodEnd = true)
        logActivity(ActivityKind.PLAN_CHANGED, "Subscription cancelled", "Access continues until ${subscription.renewsOn ?: "the period ends"}", null, null)
        persist()
    }

    fun resumeSubscription() {
        if (!subscription.cancelAtPeriodEnd) return
        subscription = subscription.copy(status = "Active", cancelAtPeriodEnd = false)
        logActivity(ActivityKind.PLAN_CHANGED, "Subscription resumed", "Renews ${subscription.renewsOn ?: "next period"}", null, null)
        persist()
    }

    /** Immediate downgrade, used by the Free option. */
    fun setPlan(tier: PlanTier) {
        subscription = Subscription(
            planTier = tier.name,
            planId = if (tier == PlanTier.PRO) SubscriptionPlan.PRO_MONTHLY.id else SubscriptionPlan.FREE.id,
            status = "Active",
            startDate = SimpleDate.today().format(),
            renewsOn = if (tier == PlanTier.PRO) nextRenewal("month") else null
        )
        logActivity(ActivityKind.PLAN_CHANGED, "Plan changed", "Now on ${tier.label}", null, null)
        persist()
    }

    private fun nextRenewal(interval: String): String {
        val today = SimpleDate.today()
        return if (interval == "year") {
            SimpleDate(today.year + 1, today.month, today.day).format()
        } else {
            val month = if (today.month == 12) 1 else today.month + 1
            val year = if (today.month == 12) today.year + 1 else today.year
            SimpleDate(year, month, today.day).format()
        }
    }

    /** Free tier caps the register; Pro removes the cap. */
    val canAddProperty: Boolean
        get() = properties.size < subscription.tier.propertyLimit

    // ----------------------------------------------------------- assistant

    fun saveConversation(conversation: AiConversation) {
        val index = conversations.indexOfFirst { it.id == conversation.id }
        if (index >= 0) conversations[index] = conversation else conversations.add(0, conversation)
        persist()
    }

    fun removeConversation(id: String) { conversations.removeAll { it.id == id }; persist() }

    // ------------------------------------------------------ notifications

    fun markNotificationRead(id: String) {
        val index = notifications.indexOfFirst { it.id == id }
        if (index >= 0) { notifications[index] = notifications[index].copy(read = true); persist() }
    }

    fun markAllNotificationsRead() {
        for (i in notifications.indices) notifications[i] = notifications[i].copy(read = true)
        persist()
    }

    val unreadNotifications: Int get() = notifications.count { !it.read }

    // ------------------------------------------------------------- activity

    private fun logActivity(
        kind: ActivityKind,
        title: String,
        detail: String,
        amount: Double?,
        propertyId: String?
    ) {
        activity.add(0, ActivityEvent(newId("a"), kind, title, detail, amount, "Just now", propertyId))
        if (activity.size > 60) activity.removeRange(60, activity.size)
    }

    // ------------------------------------------------------------ analysis

    fun financials(): List<PropertyFinancials> = Finance.analyseAll(
        properties.toList(), income.toList(), expenses.toList(),
        taxProfile, exchangeRates, profile.currency
    )

    fun financialsFor(propertyId: String?): PropertyFinancials? =
        propertyById(propertyId)?.let {
            Finance.analyse(
                it, income.toList(), expenses.toList(), taxProfile,
                SimpleDate.today(), exchangeRates, profile.currency
            )
        }

    fun portfolio(): PortfolioFinancials = Finance.portfolio(financials())

    // --------------------------------------------------------------- reset

    /** Account deletion and "reset demo data" share this path. */
    fun resetToSeed() { apply(seedSnapshot()); persist() }

    fun clearEverything() {
        apply(PorticoSnapshot(profile = profile.copy(name = profile.name), seeded = true))
        persist()
    }

    companion object {
        private var counter = 0
        fun newId(prefix: String): String = "$prefix-${System.currentTimeMillis()}-${counter++}"
    }
}

private fun <T> SnapshotStateList<T>.replaceAll(items: List<T>) {
    clear()
    addAll(items)
}
