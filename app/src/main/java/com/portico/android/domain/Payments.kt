package com.portico.android.domain

import kotlinx.serialization.Serializable

/*
 * Sandbox payments.
 *
 * No money moves here and no payment provider is contacted. What this does is
 * model a real checkout faithfully: validation, the round trip, the decline
 * paths, the receipt, the subscription lifecycle, so the product's §25 and
 * §38 surfaces can be built and reviewed against something that behaves like
 * the real thing.
 *
 * Outcomes are driven by the card-network test numbers processors publish for
 * exactly this purpose, rather than by rules invented here. The sandbox then
 * behaves the way a developer already expects, and swapping in a real
 * processor later means replacing SandboxProcessor.authorise and nothing else.
 *
 * Every surface that shows a payment says SANDBOX. This must never be mistaken
 * for a real charge.
 */

const val SANDBOX_NOTICE =
    "Sandbox mode. No card is charged and no payment provider is contacted. Use one of the test cards below."

@Serializable
data class SubscriptionPlan(
    val id: String,
    val name: String,
    val priceMinor: Long,
    val currency: String,
    val interval: String,
    val propertyLimit: Int
) {
    /** Formatted from minor units, so no floating point ever touches money. */
    val displayPrice: String
        get() = Money.symbolFor(currency) +
            "${priceMinor / 100}.${(priceMinor % 100).toString().padStart(2, '0')}"

    val displayPricePerInterval: String get() = "$displayPrice / $interval"

    companion object {
        /** Sandbox pricing. Not a commercial offer; the UI says so. */
        val FREE = SubscriptionPlan("plan_free", "Free", 0, "USD", "month", 2)
        val PRO_MONTHLY = SubscriptionPlan("plan_pro_monthly", "Pro", 1_200, "USD", "month", Int.MAX_VALUE)
        val PRO_YEARLY = SubscriptionPlan("plan_pro_yearly", "Pro annual", 12_000, "USD", "year", Int.MAX_VALUE)

        val paid = listOf(PRO_MONTHLY, PRO_YEARLY)
        fun byId(id: String) = (paid + FREE).firstOrNull { it.id == id }
    }
}

enum class PaymentStatus { SUCCEEDED, DECLINED, FAILED, REFUNDED }

@Serializable
data class Payment(
    val id: String,
    val planId: String,
    val amountMinor: Long,
    val currency: String,
    val status: String,
    val date: String,
    /** Last four only. A full number is never stored, even in a sandbox. */
    val cardLast4: String,
    val cardBrand: String,
    val failureReason: String? = null
) {
    val paymentStatus: PaymentStatus
        get() = runCatching { PaymentStatus.valueOf(status) }.getOrDefault(PaymentStatus.FAILED)

    val displayAmount: String
        get() = Money.symbolFor(currency) +
            "${amountMinor / 100}.${(amountMinor % 100).toString().padStart(2, '0')}"

    val succeeded: Boolean get() = paymentStatus == PaymentStatus.SUCCEEDED
}

/** What the user typed. Held only long enough to authorise; never persisted. */
data class CardInput(
    val number: String = "",
    val expiry: String = "",
    val cvc: String = "",
    val name: String = ""
) {
    val digits: String get() = number.filter(Char::isDigit)
    val last4: String get() = digits.takeLast(4)

    val brand: String
        get() = when {
            digits.startsWith("4") -> "Visa"
            digits.take(2).toIntOrNull() in 51..55 -> "Mastercard"
            digits.startsWith("34") || digits.startsWith("37") -> "Amex"
            else -> "Card"
        }

    /** Groups as the user types: 4242 4242 4242 4242. */
    fun formattedNumber(): String = digits.take(16).chunked(4).joinToString(" ")
}

data class CardValidation(
    val numberError: String? = null,
    val expiryError: String? = null,
    val cvcError: String? = null,
    val nameError: String? = null
) {
    val isValid: Boolean
        get() = numberError == null && expiryError == null && cvcError == null && nameError == null
}

sealed interface PaymentResult {
    data class Succeeded(val payment: Payment) : PaymentResult
    data class Declined(val payment: Payment, val reason: String, val recovery: String) : PaymentResult
}

object SandboxProcessor {

    /**
     * The published test numbers, surfaced in the UI so a reviewer can drive
     * every branch without guessing.
     */
    val testCards = listOf(
        TestCard("4242 4242 4242 4242", "Payment succeeds", PaymentStatus.SUCCEEDED),
        TestCard("4000 0000 0000 0002", "Card declined", PaymentStatus.DECLINED),
        TestCard("4000 0000 0000 9995", "Insufficient funds", PaymentStatus.DECLINED),
        TestCard("4000 0000 0000 0069", "Expired card", PaymentStatus.DECLINED),
        TestCard("4000 0000 0000 0119", "Processing error", PaymentStatus.FAILED)
    )

    data class TestCard(val number: String, val behaviour: String, val outcome: PaymentStatus)

    fun validate(card: CardInput): CardValidation {
        val digits = card.digits

        val numberError = when {
            digits.isEmpty() -> "Enter a card number."
            digits.length < 13 -> "That card number is too short."
            digits.length > 19 -> "That card number is too long."
            !luhn(digits) -> "That card number is not valid."
            else -> null
        }

        val expiryError = run {
            val parts = card.expiry.filter { it.isDigit() }
            if (parts.length < 4) {
                "Use MM/YY."
            } else {
                val month = parts.take(2).toIntOrNull() ?: 0
                val year = 2000 + (parts.drop(2).take(2).toIntOrNull() ?: 0)
                val today = SimpleDate.today()
                when {
                    month !in 1..12 -> "That month does not exist."
                    year < today.year -> "That card has expired."
                    year == today.year && month < today.month -> "That card has expired."
                    else -> null
                }
            }
        }

        val cvcError = when {
            card.cvc.length < 3 -> "The security code is 3 digits, or 4 on Amex."
            card.cvc.length > 4 -> "The security code is too long."
            !card.cvc.all(Char::isDigit) -> "Digits only."
            else -> null
        }

        val nameError = if (card.name.trim().length < 2) "Enter the name on the card." else null

        return CardValidation(numberError, expiryError, cvcError, nameError)
    }

    /**
     * Resolves the outcome the way a processor would, from the card presented.
     * Replacing this single function with a provider call is the integration.
     */
    fun authorise(card: CardInput, plan: SubscriptionPlan, paymentId: String): PaymentResult {
        val normalised = card.digits
        val matched = testCards.firstOrNull { it.number.filter(Char::isDigit) == normalised }
        val outcome = matched?.outcome ?: PaymentStatus.SUCCEEDED
        val label = matched?.behaviour ?: "Payment succeeds"

        val payment = Payment(
            id = paymentId,
            planId = plan.id,
            amountMinor = plan.priceMinor,
            currency = plan.currency,
            status = outcome.name,
            date = SimpleDate.today().format(),
            cardLast4 = card.last4,
            cardBrand = card.brand,
            failureReason = if (outcome == PaymentStatus.SUCCEEDED) null else label
        )

        return when (outcome) {
            PaymentStatus.SUCCEEDED -> PaymentResult.Succeeded(payment)
            PaymentStatus.DECLINED -> PaymentResult.Declined(
                payment,
                reason = label,
                recovery = "Your bank turned this down, and nothing was charged. Try another card."
            )
            else -> PaymentResult.Declined(
                payment,
                reason = label,
                recovery = "The payment could not be processed, and nothing was charged. Try again shortly."
            )
        }
    }

    /** Standard mod-10 check, the same one a processor runs first. */
    private fun luhn(digits: String): Boolean {
        if (digits.length < 2) return false
        var sum = 0
        var alternate = false
        for (i in digits.lastIndex downTo 0) {
            var n = digits[i] - '0'
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }
}
