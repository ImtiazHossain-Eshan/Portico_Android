package com.portico.android.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.firestore.ListenerRegistration
import com.portico.android.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "portico")
private val LEGACY_SNAPSHOT_KEY = stringPreferencesKey("snapshot_v1")

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
    val updatedAtEpochMillis: Long = 0,
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

    private var activeSnapshotKey: Preferences.Key<String>? = null
    private var activeOwnerId: String? = null
    private var activeUpdatedAtEpochMillis = 0L
    private var cloudSyncEnabled = false
    private var lastCloudSnapshot: PorticoSnapshot? = null
    private var activeCloudRevision = ""
    private var cloudListener: ListenerRegistration? = null
    private val persistenceMutex = Mutex()

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

    /** Starts signed out with no portfolio attached to the device. */
    suspend fun load() {
        cloudListener?.remove()
        cloudListener = null
        activeSnapshotKey = null
        activeOwnerId = null
        activeUpdatedAtEpochMillis = 0L
        cloudSyncEnabled = false
        lastCloudSnapshot = null
        activeCloudRevision = ""
        apply(emptySnapshot())
        loaded = true
    }

    /**
     * Loads a device cache scoped to one Clerk account. A legacy snapshot is
     * migrated only when its saved email matches the account signing in; this
     * prevents a newly-created account from inheriting another member's data.
     */
    suspend fun loadAccount(userId: String, name: String, email: String) {
        if (activeOwnerId == userId) {
            setProfile {
                it.copy(
                    userId = userId,
                    name = name.ifBlank { it.name },
                    email = email.ifBlank { it.email }
                )
            }
            return
        }
        loaded = false
        cloudSyncEnabled = false
        cloudListener?.remove()
        cloudListener = null
        lastCloudSnapshot = null
        activeCloudRevision = ""
        val key = workspaceKey(userId)
        val stored = withContext(Dispatchers.IO) {
            runCatching {
                val preferences = appContext.dataStore.data.first()
                val account = preferences[key]?.let { json.decodeFromString<PorticoSnapshot>(it) }
                val legacy = preferences[LEGACY_SNAPSHOT_KEY]
                    ?.let { json.decodeFromString<PorticoSnapshot>(it) }
                    ?.takeIf {
                        email.isNotBlank() && it.profile.email.equals(email, ignoreCase = true)
                    }
                account ?: legacy
            }.getOrNull()
        }

        activeSnapshotKey = key
        activeOwnerId = userId
        val base = stored ?: emptySnapshot(userId, name, email)
        apply(
            base.copy(
                profile = base.profile.copy(
                    userId = userId,
                    name = name.ifBlank { base.profile.name.ifBlank { "Portico member" } },
                    email = email.ifBlank { base.profile.email }
                ),
                seeded = false
            )
        )
        loaded = true
        persist()
    }

    /** Demo data is isolated from every authenticated account. */
    suspend fun loadDemo() {
        loaded = false
        cloudSyncEnabled = false
        cloudListener?.remove()
        cloudListener = null
        lastCloudSnapshot = null
        activeCloudRevision = ""
        val key = workspaceKey("demo")
        val stored = withContext(Dispatchers.IO) {
            runCatching {
                appContext.dataStore.data.first()[key]
                    ?.let { json.decodeFromString<PorticoSnapshot>(it) }
            }.getOrNull()
        }
        activeSnapshotKey = key
        activeOwnerId = "demo"
        apply(stored ?: seedSnapshot())
        loaded = true
        if (stored == null) persist()
    }

    fun closeWorkspace() {
        cloudListener?.remove()
        cloudListener = null
        activeSnapshotKey = null
        activeOwnerId = null
        activeUpdatedAtEpochMillis = 0L
        cloudSyncEnabled = false
        lastCloudSnapshot = null
        activeCloudRevision = ""
        apply(emptySnapshot())
        loaded = true
    }

    private fun emptySnapshot(
        userId: String = "",
        name: String = "",
        email: String = ""
    ) = PorticoSnapshot(profile = UserProfile(userId, name, email), seeded = false)

    private fun workspaceKey(ownerId: String): Preferences.Key<String> {
        val safeOwnerId = ownerId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return stringPreferencesKey("snapshot_v2_$safeOwnerId")
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
        activeUpdatedAtEpochMillis = snapshot.updatedAtEpochMillis
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
        updatedAtEpochMillis = activeUpdatedAtEpochMillis,
        seeded = activeOwnerId == "demo"
    )

    /**
     * Reconciles the encrypted-on-device workspace with normalized Firestore
     * collections. Existing schema-v1 snapshot blobs migrate on first sign-in;
     * demo data is never uploaded.
     */
    suspend fun syncCloud(force: Boolean = false): Boolean {
        val ownerId = activeOwnerId?.takeUnless { it == "demo" } ?: return false
        return runCatching {
            val cloudWorkspace = FirebaseBackend.loadWorkspace(ownerId)
            val cloudSnapshot = cloudWorkspace?.snapshot

            if (
                cloudSnapshot != null &&
                (force || cloudSnapshot.updatedAtEpochMillis > activeUpdatedAtEpochMillis)
            ) {
                val safeSnapshot = cloudSnapshot.copy(
                    profile = cloudSnapshot.profile.copy(userId = ownerId),
                    seeded = false
                )
                apply(safeSnapshot)
                val key = activeSnapshotKey ?: return@runCatching false
                val safePayload = json.encodeToString(safeSnapshot)
                withContext(Dispatchers.IO) {
                    persistenceMutex.withLock {
                        appContext.dataStore.edit { it[key] = safePayload }
                    }
                }
            }

            activeCloudRevision = cloudWorkspace?.revision.orEmpty()
            lastCloudSnapshot = cloudSnapshot
                ?.takeIf { cloudWorkspace.source == CloudWorkspaceSource.NORMALIZED }
            cloudSyncEnabled = true
            if (
                cloudSnapshot == null ||
                cloudWorkspace.source == CloudWorkspaceSource.LEGACY ||
                activeUpdatedAtEpochMillis > cloudSnapshot.updatedAtEpochMillis
            ) {
                persist()
            }
            observeCloud(ownerId)
            true
        }.getOrDefault(false)
    }

    private fun observeCloud(ownerId: String) {
        cloudListener?.remove()
        cloudListener = FirebaseBackend.observeWorkspace(
            userId = ownerId,
            onRevision = revision@ { revision, _ ->
                if (revision == activeCloudRevision) return@revision
                scope.launch { refreshFromCloud(ownerId, revision) }
            }
        )
    }

    private suspend fun refreshFromCloud(ownerId: String, revision: String) {
        persistenceMutex.withLock {
            if (
                activeOwnerId != ownerId ||
                !cloudSyncEnabled ||
                revision == activeCloudRevision
            ) return@withLock

            val cloud = runCatching { FirebaseBackend.loadWorkspace(ownerId) }.getOrNull()
                ?: return@withLock
            if (cloud.source != CloudWorkspaceSource.NORMALIZED) return@withLock

            val safeSnapshot = cloud.snapshot.copy(
                profile = cloud.snapshot.profile.copy(userId = ownerId),
                seeded = false
            )
            apply(safeSnapshot)
            activeUpdatedAtEpochMillis = safeSnapshot.updatedAtEpochMillis
            lastCloudSnapshot = safeSnapshot
            activeCloudRevision = cloud.revision

            val key = activeSnapshotKey ?: return@withLock
            val payload = json.encodeToString(safeSnapshot)
            withContext(Dispatchers.IO) {
                runCatching { appContext.dataStore.edit { it[key] = payload } }
            }
        }
    }

    private fun persist() {
        val key = activeSnapshotKey ?: return
        val ownerId = activeOwnerId
        val shouldSyncCloud = cloudSyncEnabled && ownerId != null && ownerId != "demo"
        activeUpdatedAtEpochMillis = System.currentTimeMillis()
        val currentSnapshot = snapshot()
        val payload = runCatching { json.encodeToString(currentSnapshot) }.getOrNull() ?: return
        scope.launch(Dispatchers.IO) {
            persistenceMutex.withLock {
                runCatching { appContext.dataStore.edit { it[key] = payload } }
                if (shouldSyncCloud) {
                    runCatching {
                        FirebaseBackend.saveWorkspace(
                            userId = ownerId,
                            previous = lastCloudSnapshot,
                            current = currentSnapshot
                        )
                    }.onSuccess { revision ->
                        lastCloudSnapshot = currentSnapshot
                        activeCloudRevision = revision
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------- properties

    suspend fun addProperty(property: Property, incomeEntries: List<IncomeEntry>, expenseEntries: List<ExpenseEntry>) {
        if (usesSecureBackend) {
            PorticoBackend.createProperties(
                listOf(PropertyCreateBundle(property, incomeEntries, expenseEntries))
            )
            check(syncCloud(force = true)) { "Property was saved but the workspace could not refresh" }
        } else {
            properties.add(property)
            income.addAll(incomeEntries)
            expenses.addAll(expenseEntries)
            logActivity(ActivityKind.PROPERTY_ADDED, "Property added", property.name, null, property.id)
            persist()
        }
    }

    fun updateProperty(property: Property) {
        val index = properties.indexOfFirst { it.id == property.id }
        if (index >= 0) {
            properties[index] = property
            logActivity(ActivityKind.PROPERTY_UPDATED, "Property updated", property.name, null, property.id)
            persist()
        }
    }

    suspend fun deleteProperty(propertyId: String) {
        if (usesSecureBackend) {
            PorticoBackend.deleteProperty(propertyId)
            check(syncCloud(force = true)) { "Property was deleted but the workspace could not refresh" }
        } else {
            deleteStoredFiles(documents.filter { it.propertyId == propertyId }.map { it.storagePath })
            properties.removeAll { it.id == propertyId }
            income.removeAll { it.propertyId == propertyId }
            expenses.removeAll { it.propertyId == propertyId }
            documents.removeAll { it.propertyId == propertyId }
            valuations.removeAll { it.propertyId == propertyId }
            activity.removeAll { it.propertyId == propertyId }
            conversations.removeAll { it.propertyId == propertyId }
            persist()
        }
    }

    /** Bulk insert from an import. One activity entry, not one per row. */
    suspend fun importProperties(
        newProperties: List<Property>,
        newIncome: List<IncomeEntry>,
        newExpenses: List<ExpenseEntry>
    ) {
        if (newProperties.isEmpty()) return
        if (usesSecureBackend) {
            val records = newProperties.map { property ->
                PropertyCreateBundle(
                    property,
                    newIncome.filter { it.propertyId == property.id },
                    newExpenses.filter { it.propertyId == property.id }
                )
            }
            PorticoBackend.createProperties(records)
            check(syncCloud(force = true)) { "Import completed but the workspace could not refresh" }
        } else {
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
    }

    /** Resource lookup for code that runs outside a composition. */
    fun string(resId: Int): String = appContext.getString(resId)
    fun string(resId: Int, vararg args: Any): String = appContext.getString(resId, *args)

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

    suspend fun removeDocument(id: String) {
        val document = documents.firstOrNull { it.id == id } ?: return
        PorticoFiles.delete(document.storagePath)
        documents.removeAll { it.id == id }
        persist()
    }

    /**
     * Stores a picked document, on the server when there is one and on the
     * device when there is not.
     *
     * The caller does not choose. Which of the two applies depends on whether
     * this workspace is signed in, which is the store's business and not a
     * screen's, and the Free plan promises a document library either way.
     */
    suspend fun storeDocument(source: Uri, documentId: String): StoredPortfolioFile =
        if (usesSecureBackend) {
            PorticoFiles.upload(appContext, source, documentId)
        } else {
            PorticoFiles.storeLocally(appContext, source, documentId)
        }

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
    private fun activatePlanLocally(plan: SubscriptionPlan, payment: Payment) {
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
    suspend fun checkout(plan: SubscriptionPlan, card: CardInput): PaymentResult {
        if (!usesSecureBackend) {
            val result = SandboxProcessor.authorise(card, plan, newId("pay"))
            activatePlanLocally(
                plan,
                (result as? PaymentResult.Succeeded)?.payment
                    ?: (result as PaymentResult.Declined).payment
            )
            return result
        }

        val billing = PorticoBackend.checkout(plan, card)
        syncCloud(force = true)
        val payment = billing.payment ?: error("Billing service returned no payment")
        return if (payment.succeeded) {
            PaymentResult.Succeeded(payment)
        } else {
            PaymentResult.Declined(
                payment,
                payment.failureReason ?: "Payment could not be processed",
                "Nothing was charged. Use another sandbox card and try again."
            )
        }
    }

    /**
     * True only when the server is configured to take real gateway payments.
     * Offline or unconfigured, checkout stays on the built-in sandbox, so the
     * app never offers a payment route that cannot complete.
     */
    // ---------------------------------------------------- platform admin

    var platform by mutableStateOf<PlatformSnapshot?>(null)
        private set
    var platformLoading by mutableStateOf(false)
        private set

    /** Loads the console. Failures surface, because an empty admin screen that
     *  looks like a working one is worse than an error. */
    suspend fun refreshPlatform() {
        if (!usesSecureBackend) {
            platform = null
            return
        }
        platformLoading = true
        try {
            platform = PorticoBackend.platformAdmin()
        } finally {
            platformLoading = false
        }
    }

    suspend fun platformAction(action: String, userId: String, tier: String? = null) {
        check(usesSecureBackend) { "Platform administration needs the secure backend" }
        platform = PorticoBackend.platformAction(action, userId, tier)
    }

    suspend fun gatewayCheckoutAvailable(): Boolean =
        usesSecureBackend && PorticoBackend.gatewayBillingEnabled()

    /** Opens a gateway session. The returned page is where the member pays. */
    suspend fun startGatewayCheckout(plan: SubscriptionPlan): GatewaySession {
        check(usesSecureBackend) { "Gateway checkout needs the secure backend" }
        val session = PorticoBackend.startGatewayCheckout(plan)
        // The pending receipt is already written server side; pull it in so the
        // billing history shows the attempt even if the member never returns.
        syncCloud(force = true)
        return session
    }

    /**
     * Re-reads billing state after the member comes back from the gateway.
     *
     * Returns the payment if one is recorded, whatever its state. A PENDING
     * result is a real answer here and not a failure: the callback that decides
     * it may still be in flight, and reporting failure early would be a lie the
     * member acts on.
     */
    suspend fun refreshGatewayPayment(transactionId: String): Payment? {
        if (!usesSecureBackend) return null
        syncCloud(force = true)
        return payments.firstOrNull { it.id == transactionId }
    }

    /**
     * Settle the transaction now instead of waiting for the gateway callback.
     *
     * The callback is a push and can be late or, in the sandbox, absent. Asking
     * on return turns "wait and hope" into a question with an answer.
     */
    suspend fun confirmGatewayPayment(transactionId: String): String {
        if (!usesSecureBackend) return "unavailable"
        return PorticoBackend.confirmGatewayPayment(transactionId)
    }

    suspend fun cancelSubscription() {
        if (!subscription.isPaid) return
        if (usesSecureBackend) {
            PorticoBackend.changeSubscription("cancel")
            syncCloud(force = true)
        } else {
            subscription = subscription.copy(status = "Cancels at period end", cancelAtPeriodEnd = true)
            logActivity(ActivityKind.PLAN_CHANGED, "Subscription cancelled", "Access continues until ${subscription.renewsOn ?: "the period ends"}", null, null)
            persist()
        }
    }

    suspend fun resumeSubscription() {
        if (!subscription.cancelAtPeriodEnd) return
        if (usesSecureBackend) {
            PorticoBackend.changeSubscription("resume")
            syncCloud(force = true)
        } else {
            subscription = subscription.copy(status = "Active", cancelAtPeriodEnd = false)
            logActivity(ActivityKind.PLAN_CHANGED, "Subscription resumed", "Renews ${subscription.renewsOn ?: "next period"}", null, null)
            persist()
        }
    }

    /** Immediate downgrade, used by the Free option. */
    suspend fun setPlan(tier: PlanTier) {
        require(tier == PlanTier.FREE) { "Paid plans must go through checkout" }
        if (usesSecureBackend) {
            PorticoBackend.changeSubscription("downgrade")
            syncCloud(force = true)
        } else {
            subscription = Subscription(
                planTier = tier.name,
                planId = SubscriptionPlan.FREE.id,
                status = "Active",
                startDate = SimpleDate.today().format(),
                renewsOn = null
            )
            logActivity(ActivityKind.PLAN_CHANGED, "Plan changed", "Now on ${tier.label}", null, null)
            persist()
        }
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

    val usesSecureBackend: Boolean
        get() = activeOwnerId != null && activeOwnerId != "demo"

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

    /**
     * Restore only the illustrative portfolio. Account identity, preferences,
     * tax choices, exchange rates, subscription and payment history belong to
     * the member and must survive a demo-data reset.
     */
    fun resetToSeed() {
        check(!usesSecureBackend) {
            "The sample portfolio stays in the demo cockpit so it cannot be mixed with your private records."
        }
        deleteStoredFiles(documents.map { it.storagePath })
        val seed = seedSnapshot().copy(
            profile = profile,
            preferences = preferences,
            taxProfile = taxProfile,
            exchangeRates = exchangeRates,
            subscription = subscription,
            payments = payments.toList()
        )
        apply(seed)
        persist()
    }

    suspend fun clearEverything() {
        if (usesSecureBackend) {
            PorticoBackend.eraseWorkspace()
            check(syncCloud(force = true)) { "Records were erased but the workspace could not refresh" }
        } else {
            deleteStoredFiles(documents.map { it.storagePath })
            apply(PorticoSnapshot(profile = profile.copy(name = profile.name), seeded = true))
            persist()
        }
    }

    /**
     * Deletes the account and everything in it, then empties the device cache.
     *
     * The local wipe happens whatever the server said. Leaving a cached
     * portfolio on the handset after the user asked to be deleted is the one
     * outcome that would be indefensible, so a server failure is reported but
     * never blocks the local erase.
     */
    suspend fun deleteAccount(): Boolean {
        val serverComplete = if (usesSecureBackend) {
            runCatching { PorticoBackend.deleteAccount() }.getOrElse { failure ->
                wipeLocalState()
                throw failure
            }
        } else {
            true
        }
        wipeLocalState()
        return serverComplete
    }

    private suspend fun wipeLocalState() {
        deleteStoredFiles(documents.map { it.storagePath })
        cloudSyncEnabled = false
        cloudListener?.remove()
        cloudListener = null
        apply(PorticoSnapshot())
        withContext(Dispatchers.IO) {
            appContext.dataStore.edit { it.clear() }
        }
    }

    /**
     * Moves freshly picked photographs into private storage.
     *
     * A picker URI is a grant to one device, so storing it in a synced record
     * would hand every other device a reference it cannot open. References that
     * are already stored pass through untouched, and an upload that fails keeps
     * the local URI: a picture that works on one device beats losing it.
     */
    suspend fun storePhotos(propertyId: String, references: List<String>): List<String> {
        if (!usesSecureBackend) return references
        return references.map { reference ->
            if (PorticoFiles.isStoredReference(reference)) {
                reference
            } else {
                runCatching {
                    PorticoFiles.upload(
                        context = appContext,
                        source = Uri.parse(reference),
                        resourceId = propertyId,
                        kind = "photos"
                    ).pathname
                }.getOrDefault(reference)
            }
        }
    }

    /** Resolves a stored photograph for display, or null when it cannot be read. */
    suspend fun photoUri(reference: String): Uri? = when {
        reference.isBlank() -> null
        !PorticoFiles.isStoredReference(reference) -> Uri.parse(reference)
        !usesSecureBackend -> null
        else -> runCatching { PorticoFiles.photo(appContext, reference) }.getOrNull()
    }

    /**
     * The market reference set currently in effect.
     *
     * Starts as the bundled sample so the first launch has something to
     * compare against, and is replaced once the provider answers. Never
     * persisted: stale market figures presented as current are the exact
     * failure this whole domain is trying to avoid.
     */
    var market by mutableStateOf(
        MarketReference(
            provider = DataProvider(name = "Portico bundled sample", basis = "modelled"),
            populated = false,
            comparables = Seed.comparables,
            listings = Seed.listings,
            signals = Seed.marketSignals
        )
    )
        private set

    /** Refreshes market data if a provider is reachable; silent when it is not. */
    suspend fun refreshMarket() {
        if (!usesSecureBackend) return
        val published = PorticoBackend.market() ?: return
        if (published.populated) market = published
    }

    // ------------------------------------------------------- organizations

    /**
     * The organization the member belongs to, if any.
     *
     * Null is the honest default: most members are individuals, and showing a
     * fabricated firm to every one of them was the old behaviour this replaces.
     * Not persisted, because membership is decided by the server and a cached
     * role is a stale permission.
     */
    var organization by mutableStateOf<OrganizationRecord?>(null)
        private set

    var organizationsLoading by mutableStateOf(false)
        private set

    val myRole: OrgRole
        get() = organization?.let { record ->
            runCatching { OrgRole.valueOf(record.role) }.getOrDefault(OrgRole.VIEWER)
        } ?: OrgRole.OWNER

    /** True when the signed-in member may perform [permission] here. */
    fun may(permission: Permission): Boolean = myRole.holds(permission)

    suspend fun refreshOrganizations() {
        if (!usesSecureBackend) return
        organizationsLoading = true
        organization = PorticoBackend.organizations().firstOrNull()
        organizationsLoading = false
    }

    suspend fun createOrganization(name: String) {
        organization = PorticoBackend.createOrganization(
            name = name,
            memberName = profile.name,
            memberEmail = profile.email
        )
    }

    suspend fun inviteMember(email: String, role: OrgRole) {
        val current = organization ?: error("Create an organization first")
        val members = PorticoBackend.inviteMember(current.id, email, role.name)
        organization = current.copy(members = members)
    }

    suspend fun setMemberRole(userId: String, role: OrgRole) {
        val current = organization ?: return
        val members = PorticoBackend.setMemberRole(current.id, userId, role.name)
        organization = current.copy(members = members)
    }

    suspend fun removeMember(userId: String) {
        val current = organization ?: return
        val members = PorticoBackend.removeMember(current.id, userId)
        organization = if (userId == profile.userId) null else current.copy(members = members)
    }

    /**
     * The audit trail, derived from activity actually recorded in this
     * workspace rather than from a fixture.
     *
     * Every entry names the signed-in member because that is who performed it:
     * a workspace has one actor today. When an organization shares a workspace,
     * the actor becomes the member id already carried on each record, and this
     * mapping is the only thing that changes.
     */
    fun auditTrail(limit: Int = 50): List<AuditLogEntry> =
        activity.sortedByDescending { it.timestamp }.take(limit).map { event ->
            AuditLogEntry(
                id = event.id,
                actor = profile.email.ifBlank { profile.name.ifBlank { "This workspace" } },
                action = event.title,
                entity = event.detail,
                timestamp = event.timestamp
            )
        }

    private fun deleteStoredFiles(paths: List<String>) {
        paths.filter { it.isNotBlank() && !it.startsWith("content://") }
            .distinct()
            .forEach { pathname ->
                scope.launch(Dispatchers.IO) { runCatching { PorticoFiles.delete(pathname) } }
            }
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
