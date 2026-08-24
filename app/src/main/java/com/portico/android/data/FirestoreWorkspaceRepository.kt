package com.portico.android.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.portico.android.domain.ActivityEvent
import com.portico.android.domain.AiConversation
import com.portico.android.domain.ExchangeRates
import com.portico.android.domain.ExpenseEntry
import com.portico.android.domain.IncomeEntry
import com.portico.android.domain.Notification
import com.portico.android.domain.Payment
import com.portico.android.domain.PortfolioDocument
import com.portico.android.domain.Property
import com.portico.android.domain.PropertyValuation
import com.portico.android.domain.Subscription
import com.portico.android.domain.TaxProfile
import com.portico.android.domain.UserPreferences
import com.portico.android.domain.UserProfile
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

enum class CloudWorkspaceSource { NORMALIZED, LEGACY }

data class CloudWorkspace(
    val snapshot: PorticoSnapshot,
    val revision: String,
    val source: CloudWorkspaceSource
)

/**
 * Normalized Firestore persistence for one Clerk/Firebase UID.
 *
 * Every domain entity is an independently addressable Firestore document below
 * `users/{uid}`. The old `workspace/main.snapshot` blob is read only as a
 * migration source and is deliberately retained as a rollback backup.
 */
class FirestoreWorkspaceRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    suspend fun load(userId: String): CloudWorkspace? {
        val user = userDocument(userId)
        val metadata = user.get().await()
        val schemaVersion = metadata.getLong(FIELD_SCHEMA_VERSION)?.toInt() ?: 0

        if (schemaVersion >= CURRENT_SCHEMA_VERSION) {
            return loadNormalized(userId, metadata)
        }

        val legacy = user.collection(COLLECTION_WORKSPACE).document(DOCUMENT_MAIN)
            .get().await().getString(FIELD_LEGACY_SNAPSHOT)
            ?.let { runCatching { json.decodeFromString<PorticoSnapshot>(it) }.getOrNull() }
            ?: return null

        return CloudWorkspace(
            snapshot = legacy.copy(
                profile = legacy.profile.copy(userId = userId),
                seeded = false
            ),
            revision = "legacy-${legacy.updatedAtEpochMillis}",
            source = CloudWorkspaceSource.LEGACY
        )
    }

    suspend fun save(
        userId: String,
        previous: PorticoSnapshot?,
        current: PorticoSnapshot
    ): String {
        val safeCurrent = current.copy(
            profile = current.profile.copy(userId = userId),
            seeded = false
        )
        val operations = mutableListOf<WriteOperation>()
        val user = userDocument(userId)

        collectEntityDiff(
            user.collection(COLLECTION_PROPERTIES),
            previous?.properties,
            safeCurrent.properties,
            Property.serializer(),
            operations
        ) { it.id }
        collectEntityDiff(
            user.collection(COLLECTION_INCOME),
            previous?.income,
            safeCurrent.income,
            IncomeEntry.serializer(),
            operations
        ) { it.id }
        collectEntityDiff(
            user.collection(COLLECTION_EXPENSES),
            previous?.expenses,
            safeCurrent.expenses,
            ExpenseEntry.serializer(),
            operations
        ) { it.id }
        collectEntityDiff(
            user.collection(COLLECTION_VALUATIONS),
            previous?.valuations,
            safeCurrent.valuations,
            PropertyValuation.serializer(),
            operations
        ) { it.id }
        collectEntityDiff(
            user.collection(COLLECTION_DOCUMENTS),
            previous?.documents,
            safeCurrent.documents,
            PortfolioDocument.serializer(),
            operations
        ) { it.id }
        collectEntityDiff(
            user.collection(COLLECTION_ACTIVITY),
            previous?.activity,
            safeCurrent.activity,
            ActivityEvent.serializer(),
            operations
        ) { it.id }
        collectEntityDiff(
            user.collection(COLLECTION_NOTIFICATIONS),
            previous?.notifications,
            safeCurrent.notifications,
            Notification.serializer(),
            operations
        ) { it.id }
        collectEntityDiff(
            user.collection(COLLECTION_CONVERSATIONS),
            previous?.conversations,
            safeCurrent.conversations,
            AiConversation.serializer(),
            operations
        ) { it.id }
        collectEntityDiff(
            user.collection(COLLECTION_PAYMENTS),
            previous?.payments,
            safeCurrent.payments,
            Payment.serializer(),
            operations
        ) { it.id }

        collectSingleton(
            user.collection(COLLECTION_SETTINGS).document(DOCUMENT_PROFILE),
            previous?.profile,
            safeCurrent.profile,
            UserProfile.serializer(),
            operations
        )
        collectSingleton(
            user.collection(COLLECTION_SETTINGS).document(DOCUMENT_PREFERENCES),
            previous?.preferences,
            safeCurrent.preferences,
            UserPreferences.serializer(),
            operations
        )
        collectSingleton(
            user.collection(COLLECTION_SETTINGS).document(DOCUMENT_TAX_PROFILE),
            previous?.taxProfile,
            safeCurrent.taxProfile,
            TaxProfile.serializer(),
            operations
        )
        collectSingleton(
            user.collection(COLLECTION_SETTINGS).document(DOCUMENT_EXCHANGE_RATES),
            previous?.exchangeRates,
            safeCurrent.exchangeRates,
            ExchangeRates.serializer(),
            operations
        )
        collectSingleton(
            user.collection(COLLECTION_SETTINGS).document(DOCUMENT_SUBSCRIPTION),
            previous?.subscription,
            safeCurrent.subscription,
            Subscription.serializer(),
            operations
        )

        commitInChunks(operations)

        val revision = UUID.randomUUID().toString()
        user.set(
            mapOf(
                FIELD_SCHEMA_VERSION to CURRENT_SCHEMA_VERSION,
                FIELD_REVISION to revision,
                FIELD_UPDATED_AT to FieldValue.serverTimestamp(),
                FIELD_UPDATED_AT_EPOCH to safeCurrent.updatedAtEpochMillis,
                FIELD_ENTITY_COUNTS to mapOf(
                    COLLECTION_PROPERTIES to safeCurrent.properties.size,
                    COLLECTION_INCOME to safeCurrent.income.size,
                    COLLECTION_EXPENSES to safeCurrent.expenses.size,
                    COLLECTION_VALUATIONS to safeCurrent.valuations.size,
                    COLLECTION_DOCUMENTS to safeCurrent.documents.size,
                    COLLECTION_ACTIVITY to safeCurrent.activity.size,
                    COLLECTION_NOTIFICATIONS to safeCurrent.notifications.size,
                    COLLECTION_CONVERSATIONS to safeCurrent.conversations.size,
                    COLLECTION_PAYMENTS to safeCurrent.payments.size
                )
            ),
            SetOptions.merge()
        ).await()
        return revision
    }

    fun observeRevision(
        userId: String,
        onRevision: (revision: String, updatedAtEpochMillis: Long) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration = userDocument(userId).addSnapshotListener { snapshot, error ->
        if (error != null) {
            onError(error)
            return@addSnapshotListener
        }
        val revision = snapshot?.getString(FIELD_REVISION).orEmpty()
        if (revision.isNotBlank()) {
            onRevision(revision, snapshot?.getLong(FIELD_UPDATED_AT_EPOCH) ?: 0L)
        }
    }

    private suspend fun loadNormalized(
        userId: String,
        metadata: DocumentSnapshot
    ): CloudWorkspace = coroutineScope {
        val user = userDocument(userId)
        val properties = async { readCollection(user.collection(COLLECTION_PROPERTIES), Property.serializer()) }
        val income = async { readCollection(user.collection(COLLECTION_INCOME), IncomeEntry.serializer()) }
        val expenses = async { readCollection(user.collection(COLLECTION_EXPENSES), ExpenseEntry.serializer()) }
        val documents = async { readCollection(user.collection(COLLECTION_DOCUMENTS), PortfolioDocument.serializer()) }
        val valuations = async { readCollection(user.collection(COLLECTION_VALUATIONS), PropertyValuation.serializer()) }
        val activity = async { readCollection(user.collection(COLLECTION_ACTIVITY), ActivityEvent.serializer()) }
        val notifications = async { readCollection(user.collection(COLLECTION_NOTIFICATIONS), Notification.serializer()) }
        val conversations = async { readCollection(user.collection(COLLECTION_CONVERSATIONS), AiConversation.serializer()) }
        val payments = async { readCollection(user.collection(COLLECTION_PAYMENTS), Payment.serializer()) }
        val profile = async {
            readSingleton(
                user.collection(COLLECTION_SETTINGS).document(DOCUMENT_PROFILE),
                UserProfile.serializer()
            ) ?: UserProfile(userId, "", "")
        }
        val preferences = async {
            readSingleton(
                user.collection(COLLECTION_SETTINGS).document(DOCUMENT_PREFERENCES),
                UserPreferences.serializer()
            ) ?: UserPreferences()
        }
        val taxProfile = async {
            readSingleton(
                user.collection(COLLECTION_SETTINGS).document(DOCUMENT_TAX_PROFILE),
                TaxProfile.serializer()
            ) ?: TaxProfile()
        }
        val exchangeRates = async {
            readSingleton(
                user.collection(COLLECTION_SETTINGS).document(DOCUMENT_EXCHANGE_RATES),
                ExchangeRates.serializer()
            ) ?: ExchangeRates()
        }
        val subscription = async {
            readSingleton(
                user.collection(COLLECTION_SETTINGS).document(DOCUMENT_SUBSCRIPTION),
                Subscription.serializer()
            ) ?: Subscription()
        }

        val snapshot = PorticoSnapshot(
            properties = properties.await(),
            income = income.await(),
            expenses = expenses.await(),
            documents = documents.await(),
            valuations = valuations.await(),
            activity = activity.await(),
            notifications = notifications.await(),
            conversations = conversations.await(),
            payments = payments.await(),
            profile = profile.await().copy(userId = userId),
            preferences = preferences.await(),
            taxProfile = taxProfile.await(),
            exchangeRates = exchangeRates.await(),
            subscription = subscription.await(),
            updatedAtEpochMillis = metadata.getLong(FIELD_UPDATED_AT_EPOCH) ?: 0L,
            seeded = false
        )

        CloudWorkspace(
            snapshot = snapshot,
            revision = metadata.getString(FIELD_REVISION).orEmpty(),
            source = CloudWorkspaceSource.NORMALIZED
        )
    }

    private suspend fun <T> readCollection(
        collection: com.google.firebase.firestore.CollectionReference,
        serializer: KSerializer<T>
    ): List<T> = collection.get().await().documents.mapNotNull { document ->
        decode(document.data, serializer)
    }

    private suspend fun <T> readSingleton(
        document: DocumentReference,
        serializer: KSerializer<T>
    ): T? = decode(document.get().await().data, serializer)

    /**
     * Turns one collection's local state into writes, and deletes only what it
     * can prove was removed.
     *
     * A deletion needs evidence that a record was here and is now gone, and the
     * baseline is the only thing carrying it. Without one, absence from the
     * local snapshot is equally well explained by a cloud read that never
     * landed, and this used to resolve that ambiguity by listing the whole
     * remote collection and deleting everything the local copy lacked. So a
     * signed-in workspace that came up empty erased the records it was supposed
     * to be restoring: a first read failing is common, and the next edit turned
     * that failure into data loss.
     *
     * With no baseline it now writes what it holds and removes nothing. A real
     * deletion still reaches the server on the following sync, by which point a
     * baseline exists. The cost is a stale document living one sync longer; the
     * alternative cost was somebody's portfolio.
     */
    private fun <T> collectEntityDiff(
        collection: com.google.firebase.firestore.CollectionReference,
        previous: List<T>?,
        current: List<T>,
        serializer: KSerializer<T>,
        operations: MutableList<WriteOperation>,
        id: (T) -> String
    ) {
        val previousById = previous?.associateBy(id)
        val currentById = current.associateBy(id)

        staleIds(previousById, currentById).forEach { staleId ->
            operations += WriteOperation.Delete(collection.document(staleId))
        }

        currentById.forEach { (entityId, entity) ->
            if (previousById == null || previousById[entityId] != entity) {
                operations += WriteOperation.Set(collection.document(entityId), encode(entity, serializer))
            }
        }
    }

    private fun <T> collectSingleton(
        document: DocumentReference,
        previous: T?,
        current: T,
        serializer: KSerializer<T>,
        operations: MutableList<WriteOperation>
    ) {
        if (previous == null || previous != current) {
            operations += WriteOperation.Set(document, encode(current, serializer))
        }
    }

    private suspend fun commitInChunks(operations: List<WriteOperation>) {
        operations.chunked(MAX_BATCH_OPERATIONS).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { operation ->
                when (operation) {
                    is WriteOperation.Set -> batch.set(operation.document, operation.data)
                    is WriteOperation.Delete -> batch.delete(operation.document)
                }
            }
            batch.commit().await()
        }
    }

    private fun <T> encode(value: T, serializer: KSerializer<T>): Map<String, Any?> =
        json.encodeToJsonElement(serializer, value).jsonObject.toFirestoreMap()

    private fun <T> decode(data: Map<String, Any>?, serializer: KSerializer<T>): T? {
        if (data == null) return null
        return runCatching {
            json.decodeFromJsonElement(serializer, data.toJsonObject())
        }.getOrNull()
    }

    private fun JsonObject.toFirestoreMap(): Map<String, Any?> =
        entries.associate { (key, value) -> key to value.toFirestoreValue() }

    private fun JsonElement.toFirestoreValue(): Any? = when (this) {
        JsonNull -> null
        is JsonObject -> toFirestoreMap()
        is JsonArray -> map { it.toFirestoreValue() }
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            longOrNull != null -> longOrNull
            doubleOrNull != null -> doubleOrNull
            else -> contentOrNull
        }
    }

    private fun Map<String, Any>.toJsonObject(): JsonObject = JsonObject(
        entries
            .filterNot { it.key.startsWith("_") }
            .associate { (key, value) -> key to value.toJsonElement() }
    )

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Byte, is Short, is Int, is Long -> JsonPrimitive((this as Number).toLong())
        is Float, is Double -> JsonPrimitive((this as Number).toDouble())
        is Number -> JsonPrimitive(toDouble())
        is String -> JsonPrimitive(this)
        is Map<*, *> -> JsonObject(
            entries.mapNotNull { (key, value) ->
                (key as? String)?.let { it to value.toJsonElement() }
            }.toMap()
        )
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }

    private fun userDocument(userId: String) = firestore.collection(COLLECTION_USERS).document(userId)

    private sealed interface WriteOperation {
        data class Set(val document: DocumentReference, val data: Map<String, Any?>) : WriteOperation
        data class Delete(val document: DocumentReference) : WriteOperation
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        private const val MAX_BATCH_OPERATIONS = 400

        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_WORKSPACE = "workspace"
        private const val COLLECTION_SETTINGS = "settings"
        private const val COLLECTION_PROPERTIES = "properties"
        private const val COLLECTION_INCOME = "income"
        private const val COLLECTION_EXPENSES = "expenses"
        private const val COLLECTION_VALUATIONS = "valuations"
        private const val COLLECTION_DOCUMENTS = "documents"
        private const val COLLECTION_ACTIVITY = "activity"
        private const val COLLECTION_NOTIFICATIONS = "notifications"
        private const val COLLECTION_CONVERSATIONS = "conversations"
        private const val COLLECTION_PAYMENTS = "payments"

        private const val DOCUMENT_MAIN = "main"
        private const val DOCUMENT_PROFILE = "profile"
        private const val DOCUMENT_PREFERENCES = "preferences"
        private const val DOCUMENT_TAX_PROFILE = "taxProfile"
        private const val DOCUMENT_EXCHANGE_RATES = "exchangeRates"
        private const val DOCUMENT_SUBSCRIPTION = "subscription"

        private const val FIELD_SCHEMA_VERSION = "schemaVersion"
        private const val FIELD_REVISION = "revision"
        private const val FIELD_UPDATED_AT = "updatedAt"
        private const val FIELD_UPDATED_AT_EPOCH = "updatedAtEpochMillis"
        private const val FIELD_ENTITY_COUNTS = "entityCounts"
        private const val FIELD_LEGACY_SNAPSHOT = "snapshot"
    }
}

/**
 * Which remote records a sync is entitled to delete.
 *
 * Separate from the repository, and free of Firestore, because this is the rule
 * that lost a portfolio and a rule worth being able to test directly. [previous]
 * is the last snapshot known to be on the server; null means there isn't one.
 *
 * Returns nothing without a baseline. An id missing from [current] proves a
 * deletion only against a known prior state; against nothing it proves only
 * that the local copy is empty, which a failed read explains just as well.
 */
internal fun <T> staleIds(previous: Map<String, T>?, current: Map<String, T>): Set<String> {
    if (previous == null) return emptySet()
    return previous.keys - current.keys
}
