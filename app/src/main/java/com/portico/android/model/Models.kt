package com.portico.android.model

data class Property(
    val id: Int,
    val name: String,
    val location: String,
    val type: String,
    val purchasePrice: Double,
    val currentValue: Double,
    val purchaseDate: String,
    val size: String,
    val monthlyIncome: Double,
    val monthlyExpenses: Double,
    val roi: Double,
    val capRate: Double,
    val grossYield: Double,
    val netYield: Double,
    val accent: Long,
    val note: String = "Sample portfolio record",
    val photoUris: List<String> = emptyList()
) {
    val monthlyCashflow: Double get() = monthlyIncome - monthlyExpenses
    val returnValue: Double get() = currentValue - purchasePrice
}

data class ActivityItem(
    val title: String,
    val detail: String,
    val amount: String,
    val time: String,
    val tone: ActivityTone
)

enum class ActivityTone { Positive, Neutral, Warning }

data class PortfolioDocument(
    val id: Int,
    val name: String,
    val property: String,
    val category: String,
    val date: String,
    val size: String,
    val status: DocumentStatus = DocumentStatus.Ready
)

enum class DocumentStatus { Ready, Uploading, Failed, Private }

data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val context: String? = null
)

data class AllocationSlice(val label: String, val value: Float, val color: Long)

data class DraftProperty(
    val name: String = "",
    val address: String = "",
    val country: String = "Uruguay",
    val region: String = "",
    val type: String = "Residential",
    val size: String = "",
    val purchaseDate: String = "",
    val purchasePrice: String = "",
    val initialInvestment: String = "",
    val financing: String = "",
    val monthlyRent: String = "",
    val otherIncome: String = "0",
    val propertyTaxes: String = "0",
    val maintenance: String = "0",
    val insurance: String = "0",
    val fees: String = "0",
    val recurringExpenses: String = "0",
    val photoUris: List<String> = emptyList()
)
