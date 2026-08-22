package com.portico.android.domain

import kotlinx.serialization.Serializable

/*
 * Entities follow the schema delivered in Milestone 1 (blueprint sections
 * 34-43), grouped by domain. Money is Double throughout because every figure
 * here is a user-entered estimate rendered to whole currency units, never a
 * settled ledger balance; ids are String so a Firestore document id can
 * replace the local uuid without touching a call site.
 */

// ------------------------------------------------------------- user domain

@Serializable
data class UserProfile(
    val userId: String,
    val name: String,
    val email: String,
    val country: String = "Uruguay",
    val currency: String = "USD",
    val taxJurisdiction: String = Jurisdiction.URUGUAY_MONTEVIDEO,
    val profileImageUri: String? = null,
    val createdAt: String = ""
)

@Serializable
data class UserPreferences(
    val theme: String = "System",
    val language: String = "English",
    val notificationsRent: Boolean = true,
    val notificationsDocuments: Boolean = true,
    val notificationsMarket: Boolean = false,
    val reducedMotion: Boolean = false,
    /**
     * Cloud analysis is off until the member turns it on.
     *
     * Defaulting this to true would quietly break a promise the app makes on
     * its own privacy screen, so the on-device analyst stays the default and
     * the toggle states exactly what leaves the device.
     */
    val cloudAssistant: Boolean = false
)

// -------------------------------------------------------- portfolio domain

enum class PropertyType(val label: String) {
    RESIDENTIAL("Residential"),
    MULTI_FAMILY("Multi-family"),
    SHORT_STAY("Short stay"),
    COMMERCIAL("Commercial"),
    LAND("Land");

    companion object {
        fun from(label: String) = entries.firstOrNull { it.label == label } ?: RESIDENTIAL
    }
}

@Serializable
data class Property(
    val id: String,
    val name: String,
    val address: String,
    val country: String,
    val region: String,
    val type: String,
    /** Interior area in square metres. */
    val sizeSqm: Double,
    val purchaseDate: String,
    val purchasePrice: Double,
    /** Cash actually put in, which is what ROI is measured against. */
    val initialInvestment: Double,
    val financingAmount: Double = 0.0,
    val currentValue: Double,
    /** The currency this property's figures were entered in. */
    val currency: String = "USD",
    val photoUris: List<String> = emptyList(),
    val note: String = ""
) {
    val location: String get() = listOf(region, country).filter { it.isNotBlank() }.joinToString(" · ")
    val appreciation: Double get() = currentValue - purchasePrice
    val propertyType: PropertyType get() = PropertyType.from(type)
}

@Serializable
data class PropertyValuation(
    val id: String,
    val propertyId: String,
    val estimatedValue: Double,
    val valuationDate: String,
    /** "Owner estimate", "Comparable model", "Broker". Never a fabricated provider. */
    val source: String
)

// -------------------------------------------------------- financial domain

enum class IncomeCategory(val label: String) {
    RENT("Rental income"),
    PARKING("Parking"),
    SERVICES("Services"),
    OTHER("Other income");

    companion object {
        fun from(label: String) = entries.firstOrNull { it.label == label } ?: OTHER
    }
}

enum class ExpenseCategory(val label: String, val isOperating: Boolean) {
    MAINTENANCE("Maintenance", true),
    INSURANCE("Insurance", true),
    MANAGEMENT("Management fees", true),
    UTILITIES("Utilities", true),
    HOA("Building fees", true),
    PROPERTY_TAX("Property tax", false),
    INCOME_TAX("Income tax", false),
    OTHER("Other expense", true);

    companion object {
        fun from(label: String) = entries.firstOrNull { it.label == label } ?: OTHER
    }
}

@Serializable
data class IncomeEntry(
    val id: String,
    val propertyId: String,
    val amount: Double,
    val category: String,
    val date: String,
    val note: String = "",
    /** True when this repeats every month, which is what yield is built from. */
    val recurring: Boolean = true
)

@Serializable
data class ExpenseEntry(
    val id: String,
    val propertyId: String,
    val amount: Double,
    val category: String,
    val date: String,
    val note: String = "",
    val recurring: Boolean = true
)

/** A point on a property's value history; drives the performance charts. */
@Serializable
data class PerformancePoint(
    val propertyId: String,
    val date: String,
    val monthIndex: Int,
    val value: Double
)

// --------------------------------------------------------- document domain

enum class DocumentCategory(val label: String) {
    CONTRACTS("Contracts"),
    DEEDS("Deeds"),
    LEASES("Leases"),
    TAXES("Taxes"),
    OTHER("Other");

    companion object {
        fun from(label: String) = entries.firstOrNull { it.label == label } ?: OTHER
    }
}

enum class DocumentStatus { READY, UPLOADING, FAILED, UNAVAILABLE, RESTRICTED }

@Serializable
data class PortfolioDocument(
    val id: String,
    val propertyId: String?,
    val fileName: String,
    val fileType: String = "PDF",
    val category: String,
    val uploadedAt: String,
    val sizeBytes: Long,
    val status: DocumentStatus = DocumentStatus.READY,
    val storagePath: String = ""
) {
    val readableSize: String
        get() = when {
            sizeBytes >= 1_048_576 -> "%.1f MB".format(java.util.Locale.ROOT, sizeBytes / 1_048_576.0)
            sizeBytes >= 1024 -> "${sizeBytes / 1024} KB"
            else -> "$sizeBytes B"
        }
}

// ----------------------------------------------------- subscription domain

enum class PlanTier(val label: String, val propertyLimit: Int) {
    FREE("Free", 2),
    PRO("Pro", Int.MAX_VALUE)
}

@Serializable
data class Subscription(
    val planTier: String = PlanTier.FREE.name,
    val planId: String = "plan_free",
    val status: String = "Active",
    val startDate: String = "",
    val renewsOn: String? = null,
    /** Set when the user cancels: access runs to the end of the paid period. */
    val cancelAtPeriodEnd: Boolean = false
) {
    val tier: PlanTier get() = runCatching { PlanTier.valueOf(planTier) }.getOrDefault(PlanTier.FREE)
    val isPaid: Boolean get() = tier == PlanTier.PRO
}

// ------------------------------------------------------- enterprise domain

@Serializable
data class Organization(
    val id: String,
    val name: String,
    val memberCount: Int,
    val propertyCount: Int,
    val createdAt: String
)

enum class OrgRole(val label: String, val rank: Int) {
    OWNER("Owner", 4),
    ADMIN("Administrator", 3),
    ANALYST("Analyst", 2),
    VIEWER("Viewer", 1);

    /** Permission model: a role holds every permission at or below its rank. */
    fun holds(permission: Permission) = rank >= permission.minimumRank
}

enum class Permission(val label: String, val minimumRank: Int) {
    VIEW_PORTFOLIO("View portfolio", 1),
    VIEW_REPORTS("View reports", 1),
    EDIT_PROPERTY("Edit properties", 2),
    RECORD_FINANCIALS("Record income and expenses", 2),
    MANAGE_DOCUMENTS("Manage documents", 3),
    MANAGE_MEMBERS("Manage members", 3),
    EXPORT_DATA("Export data", 3),
    BILLING("Billing and plan", 4)
}

@Serializable
data class OrganizationMember(
    val id: String,
    val organizationId: String,
    val name: String,
    val email: String,
    val role: String,
    val joinedAt: String
) {
    val orgRole: OrgRole get() = runCatching { OrgRole.valueOf(role) }.getOrDefault(OrgRole.VIEWER)
}

// --------------------------------------------------------------- ai domain

@Serializable
data class AiMessage(
    val id: String,
    val conversationId: String,
    val fromUser: Boolean,
    val content: String,
    val createdAt: String,
    /** Populated when the reply carried a calculation the user can verify. */
    val workings: List<String> = emptyList()
)

@Serializable
data class AiConversation(
    val id: String,
    val title: String,
    val contextLabel: String,
    val propertyId: String? = null,
    val createdAt: String,
    val messages: List<AiMessage> = emptyList()
)

// ---------------------------------------------------- external data domain

/**
 * Who supplied a set of market figures, and whether they were observed or
 * modelled.
 *
 * The blueprint's external-data domain starts with DataProvider for a reason:
 * a price with no provenance is worse than no price, because it looks like
 * fact. Every comparable and signal Portico renders carries this record, and
 * the UI states the basis rather than leaving the reader to assume.
 */
@Serializable
data class DataProvider(
    val id: String = "portico-reference",
    val name: String = "Portico reference set",
    val type: String = "internal",
    /** "observed" for a real feed, "modelled" for a reference set. */
    val basis: String = "modelled",
    val updated: String = ""
) {
    val isObserved: Boolean get() = basis.equals("observed", ignoreCase = true)
    val disclosure: String
        get() = if (isObserved) "Observed by $name" else "Modelled reference set, not observed market data"
}

/**
 * A provider's figures, as delivered.
 *
 * Empty means nothing has been published yet, in which case the app keeps its
 * bundled reference set. That is the only state in which Portico shows figures
 * it made up, and it says so on screen.
 */
@Serializable
data class MarketReference(
    val provider: DataProvider = DataProvider(),
    val populated: Boolean = false,
    val comparables: List<ComparableProperty> = emptyList(),
    val listings: List<PropertyListing> = emptyList(),
    val signals: List<MarketSignal> = emptyList()
)

/**
 * Comparables and market figures carry a provider and an explicit basis. No
 * price is presented as observed market truth unless a provider claims it.
 */
@Serializable
data class ComparableProperty(
    val id: String,
    val address: String,
    val region: String,
    val type: String,
    val sizeSqm: Double,
    val askingPrice: Double,
    val distanceKm: Double
) {
    val pricePerSqm: Double get() = if (sizeSqm > 0) askingPrice / sizeSqm else 0.0
}

@Serializable
data class MarketSignal(
    val region: String,
    val metric: String,
    val value: Double,
    val changePct: Double,
    val period: String
)

@Serializable
data class PropertyListing(
    val id: String,
    val address: String,
    val region: String,
    val country: String,
    val type: String,
    val sizeSqm: Double,
    val askingPrice: Double,
    val estimatedMonthlyRent: Double
)

// --------------------------------------------------------- system domain

enum class ActivityKind {
    RENT_RECEIVED, EXPENSE_ADDED, VALUATION_UPDATED, TAX_RECORDED,
    DOCUMENT_UPLOADED, PROPERTY_ADDED, PROPERTY_UPDATED, PLAN_CHANGED
}

@Serializable
data class ActivityEvent(
    val id: String,
    val kind: ActivityKind,
    val title: String,
    val detail: String,
    val amount: Double?,
    val timestamp: String,
    val propertyId: String? = null
)

@Serializable
data class Notification(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val timestamp: String,
    val read: Boolean = false
)

@Serializable
data class AuditLogEntry(
    val id: String,
    val actor: String,
    val action: String,
    val entity: String,
    val timestamp: String
)
