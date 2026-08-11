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
    val profile: UserProfile = UserProfile(Seed.DEMO_USER_ID, "", ""),
    val preferences: UserPreferences = UserPreferences(),
    val taxProfile: TaxProfile = TaxProfile(),
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

    var profile by mutableStateOf(UserProfile(Seed.DEMO_USER_ID, "Portico member", ""))
        private set
    var preferences by mutableStateOf(UserPreferences())
        private set
    var taxProfile by mutableStateOf(TaxProfile())
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
        profile = snapshot.profile
        preferences = snapshot.preferences
        taxProfile = snapshot.taxProfile
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
        profile = profile,
        preferences = preferences,
        taxProfile = taxProfile,
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

    fun setPlan(tier: PlanTier) {
        subscription = subscription.copy(
            planTier = tier.name,
            status = "Active",
            startDate = SimpleDate.today().format(),
            renewsOn = if (tier == PlanTier.PRO) "in 12 months" else null
        )
        logActivity(ActivityKind.PLAN_CHANGED, "Plan changed", "Now on ${tier.label}", null, null)
        persist()
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

    fun financials(): List<PropertyFinancials> =
        Finance.analyseAll(properties.toList(), income.toList(), expenses.toList(), taxProfile)

    fun financialsFor(propertyId: String?): PropertyFinancials? =
        propertyById(propertyId)?.let {
            Finance.analyse(it, income.toList(), expenses.toList(), taxProfile)
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
