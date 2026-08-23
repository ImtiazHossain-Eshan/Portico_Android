package com.portico.android.data

import kotlinx.serialization.Serializable

/*
 * Platform administration, as the server reports it.
 *
 * Every figure here is counted across all tenants by the bridge, which is the
 * only component that can see them: the Firestore rules deny a client any read
 * outside its own account, and that is deliberate. So this file describes a
 * response, never a source of truth the app could compute for itself.
 */

@Serializable
data class PlatformOrders(
    val total: Int = 0,
    val settled: Int = 0,
    val pending: Int = 0,
    val rejected: Int = 0
)

@Serializable
data class PlatformOverview(
    val users: Int = 0,
    val properties: Int = 0,
    val documents: Int = 0,
    val payments: Int = 0,
    val proSubscriptions: Int = 0,
    val orders: PlatformOrders = PlatformOrders(),
    val gatewayVolumeBdt: Double = 0.0
)

@Serializable
data class PlatformUser(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val planTier: String = "FREE",
    val properties: Int = 0,
    val banned: Boolean = false,
    val lastSignInAt: String = "",
    val createdAt: String = ""
) {
    val isPro: Boolean get() = planTier == "PRO"
    val displayName: String get() = name.ifBlank { email.substringBefore("@").ifBlank { userId } }
}

@Serializable
data class PlatformSnapshot(
    val overview: PlatformOverview = PlatformOverview(),
    val users: List<PlatformUser> = emptyList(),
    val actingAs: String = "",
    /** False until PORTICO_ADMIN_USER_IDS names at least one administrator. */
    val configured: Boolean = true,
    /** False when an allowlist exists but does not include this account. */
    val isAdministrator: Boolean = true,
    /** Returned only while unconfigured, so an operator can enable themselves. */
    val yourUserId: String = "",
    val hint: String = ""
)

@Serializable
internal data class PlatformActionRequest(
    val action: String,
    val userId: String,
    val tier: String? = null
)
