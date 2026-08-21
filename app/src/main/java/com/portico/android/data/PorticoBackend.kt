package com.portico.android.data

import com.portico.android.BuildConfig
import com.portico.android.domain.CardInput
import com.portico.android.domain.ExpenseEntry
import com.portico.android.domain.IncomeEntry
import com.portico.android.domain.MarketReference
import com.portico.android.domain.Payment
import com.portico.android.domain.Property
import com.portico.android.domain.Subscription
import com.portico.android.domain.SubscriptionPlan
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PorticoBackendException(
    val status: Int,
    val code: String,
    override val message: String
) : Exception(message)

@Serializable
data class AssistantRequest(val question: String, val context: String)

@Serializable
data class OrganizationRequest(
    val action: String,
    val organizationId: String? = null,
    val name: String? = null,
    val email: String? = null,
    val role: String? = null,
    val userId: String? = null,
    val memberName: String? = null,
    val memberEmail: String? = null
)

@Serializable
data class OrganizationMemberRecord(
    val userId: String = "",
    val organizationId: String = "",
    val role: String = "VIEWER",
    val name: String = "",
    val email: String = "",
    val joinedAt: String = ""
)

@Serializable
data class OrganizationRecord(
    val id: String = "",
    val name: String = "",
    val ownerId: String = "",
    val createdAt: String = "",
    /** The signed-in member's own role in this organization. */
    val role: String = "VIEWER",
    val members: List<OrganizationMemberRecord> = emptyList()
)

@Serializable
private data class OrganizationListResponse(val organizations: List<OrganizationRecord> = emptyList())

@Serializable
private data class MemberListResponse(
    val members: List<OrganizationMemberRecord> = emptyList(),
    val left: Boolean = false
)

/** The server refuses deletion unless this exact word arrives with it. */
@Serializable
data class AccountDeletionRequest(val confirm: String = "DELETE")

@Serializable
data class PropertyCreateBundle(
    val property: Property,
    val incomeEntries: List<IncomeEntry>,
    val expenseEntries: List<ExpenseEntry>
)

@Serializable
data class BillingResult(
    val payment: Payment? = null,
    val subscription: Subscription? = null
)

@Serializable
private data class PropertyCreateRequest(val records: List<PropertyCreateBundle>)

@Serializable
private data class CheckoutRequest(
    val action: String = "checkout",
    val planId: String,
    val sandboxToken: String
)

@Serializable
private data class SubscriptionActionRequest(val action: String)

@Serializable
private data class DeviceRequest(
    val installationId: String,
    val platform: String = "android",
    val appVersion: String = BuildConfig.VERSION_NAME
)

/**
 * Authenticated calls whose decisions must not be made by the APK: property
 * quota enforcement, billing state and push-device registration.
 */
object PorticoBackend {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun createProperties(records: List<PropertyCreateBundle>) {
        check(records.isNotEmpty()) { "At least one property is required" }
        request(
            path = "properties",
            method = "POST",
            payload = json.encodeToString(PropertyCreateRequest(records))
        )
    }

    suspend fun deleteProperty(propertyId: String) {
        val encoded = URLEncoder.encode(propertyId, StandardCharsets.UTF_8.toString())
        request(path = "properties?id=$encoded", method = "DELETE")
    }

    suspend fun checkout(plan: SubscriptionPlan, card: CardInput): BillingResult =
        json.decodeFromString(
            request(
                path = "subscription",
                method = "POST",
                payload = json.encodeToString(
                    CheckoutRequest(planId = plan.id, sandboxToken = sandboxToken(card.digits))
                )
            )
        )

    private fun sandboxToken(digits: String): String = when (digits) {
        "4242424242424242" -> "succeeds"
        "4000000000000002" -> "declined"
        "4000000000009995" -> "insufficient"
        "4000000000000069" -> "expired"
        "4000000000000119" -> "processing_error"
        else -> throw PorticoBackendException(
            400,
            "sandbox_card_required",
            "Use one of the displayed sandbox cards; never enter a real card."
        )
    }

    suspend fun changeSubscription(action: String): Subscription =
        json.decodeFromString<BillingResult>(
            request(
                path = "subscription",
                method = "POST",
                payload = json.encodeToString(SubscriptionActionRequest(action))
            )
        ).subscription ?: error("Subscription service returned no plan")

    suspend fun registerDevice(installationId: String) {
        request(
            path = "notifications",
            method = "POST",
            payload = json.encodeToString(DeviceRequest(installationId))
        )
    }

    /**
     * Asks the Gemma-backed analyst a question about the supplied figures.
     *
     * Returns null whenever cloud analysis is unavailable for any reason at
     * all: no key on the server, no network, rate limited, model down. The
     * caller answers from the device instead, so the feature never surfaces as
     * a failure the member has to understand.
     */
    suspend fun askAssistant(question: String, context: String): String? = runCatching {
        val body = request(
            path = "assistant",
            method = "POST",
            payload = json.encodeToString(AssistantRequest(question, context))
        )
        json.parseToJsonElement(body).jsonObject["answer"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }.getOrNull()

    /**
     * Fetches the published market reference set.
     *
     * Null on any failure, and the caller keeps whatever it already had. Market
     * figures are context, never the basis of a stored record, so a fetch that
     * fails costs the member nothing but freshness.
     */
    suspend fun market(): MarketReference? = runCatching {
        json.decodeFromString<MarketReference>(request(path = "market", method = "GET"))
    }.getOrNull()

    // ------------------------------------------------------- organizations

    /**
     * Organizations the signed-in member belongs to, with their roster.
     *
     * Every mutation returns the fresh roster rather than a status, so the
     * screen never has to guess what the server decided. Rank rules live on the
     * server; these calls surface its refusals verbatim.
     */
    suspend fun organizations(): List<OrganizationRecord> = runCatching {
        json.decodeFromString<OrganizationListResponse>(
            request(path = "organizations", method = "GET")
        ).organizations
    }.getOrDefault(emptyList())

    suspend fun createOrganization(name: String, memberName: String, memberEmail: String): OrganizationRecord =
        json.decodeFromString(
            request(
                path = "organizations",
                method = "POST",
                payload = json.encodeToString(
                    OrganizationRequest(
                        action = "create", name = name,
                        memberName = memberName, memberEmail = memberEmail
                    )
                )
            )
        )

    suspend fun inviteMember(organizationId: String, email: String, role: String): List<OrganizationMemberRecord> =
        json.decodeFromString<MemberListResponse>(
            request(
                path = "organizations",
                method = "POST",
                payload = json.encodeToString(
                    OrganizationRequest(action = "invite", organizationId = organizationId, email = email, role = role)
                )
            )
        ).members

    suspend fun setMemberRole(organizationId: String, userId: String, role: String): List<OrganizationMemberRecord> =
        json.decodeFromString<MemberListResponse>(
            request(
                path = "organizations",
                method = "POST",
                payload = json.encodeToString(
                    OrganizationRequest(action = "role", organizationId = organizationId, userId = userId, role = role)
                )
            )
        ).members

    suspend fun removeMember(organizationId: String, userId: String): List<OrganizationMemberRecord> =
        json.decodeFromString<MemberListResponse>(
            request(
                path = "organizations",
                method = "POST",
                payload = json.encodeToString(
                    OrganizationRequest(action = "remove", organizationId = organizationId, userId = userId)
                )
            )
        ).members

    suspend fun eraseWorkspace() {
        request(path = "workspace", method = "DELETE")
    }

    /**
     * Deletes the account itself: every record, every private file, and both
     * the Firebase and Clerk identities.
     *
     * The confirmation word is re-checked on the server, so a call that reaches
     * the endpoint by accident cannot destroy an account. Returns true when the
     * server confirmed both identities were removed; a false means the records
     * are gone but an identity outlived them, which is worth telling the user.
     */
    suspend fun deleteAccount(): Boolean {
        val body = request(
            path = "account",
            method = "DELETE",
            payload = json.encodeToString(AccountDeletionRequest())
        )
        return runCatching {
            json.parseToJsonElement(body).jsonObject["complete"]?.jsonPrimitive?.content == "true"
        }.getOrDefault(false)
    }

    private suspend fun request(path: String, method: String, payload: String? = null): String =
        withContext(Dispatchers.IO) {
            val connection = (URL(endpoint(path)).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer ${FirebaseBackend.sessionToken()}")
                setRequestProperty("Accept", "application/json")
                if (payload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (payload != null) {
                    connection.outputStream.use { output ->
                        output.write(payload.toByteArray(StandardCharsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                if (status !in 200..299) {
                    val error = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                    val code = error?.get("error")?.jsonPrimitive?.content ?: "request_failed"
                    val message = error?.get("message")?.jsonPrimitive?.content
                        ?: when (code) {
                            "plan_limit_reached" -> "Upgrade to Pro to add another property."
                            "sandbox_card_required" -> "Use a displayed sandbox card; never enter a real card."
                            else -> "The secure Portico service could not complete that request."
                        }
                    throw PorticoBackendException(status, code, message)
                }
                body
            } finally {
                connection.disconnect()
            }
        }

    private fun endpoint(path: String): String {
        val tokenEndpoint = BuildConfig.FIREBASE_TOKEN_BRIDGE_URL.trim().trimEnd('/')
        check(tokenEndpoint.isNotBlank()) { "Portico backend URL is missing" }
        val base = tokenEndpoint.removeSuffix("/firebase-token").removeSuffix("/api")
        return "$base/api/$path"
    }
}
