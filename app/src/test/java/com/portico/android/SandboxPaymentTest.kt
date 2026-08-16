package com.portico.android

import com.portico.android.domain.CardInput
import com.portico.android.domain.PaymentResult
import com.portico.android.domain.PaymentStatus
import com.portico.android.domain.SandboxProcessor
import com.portico.android.domain.SimpleDate
import com.portico.android.domain.SubscriptionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card numbers here are the published, non-functional test numbers every
 * processor documents. They authorise nothing and belong to no one.
 */
class SandboxPaymentTest {

    private val plan = SubscriptionPlan.PRO_MONTHLY
    private val futureExpiry: String
        get() = SimpleDate.today().let { "12/${(it.year + 2) % 100}" }

    private fun card(number: String) = CardInput(
        number = number,
        expiry = futureExpiry,
        cvc = "123",
        name = "Test Cardholder"
    )

    // ------------------------------------------------------------ validation

    @Test
    fun `a number failing the mod-10 check is rejected`() {
        val result = SandboxProcessor.validate(card("4242 4242 4242 4243"))
        assertNotNull(result.numberError)
        assertTrue(result.numberError!!.contains("not valid"))
    }

    @Test
    fun `a valid test number passes every check`() {
        val result = SandboxProcessor.validate(card("4242 4242 4242 4242"))
        assertNull(result.numberError)
        assertNull(result.expiryError)
        assertNull(result.cvcError)
        assertNull(result.nameError)
        assertTrue(result.isValid)
    }

    @Test
    fun `a past expiry is rejected`() {
        val past = CardInput("4242424242424242", "01/20", "123", "Test Cardholder")
        val result = SandboxProcessor.validate(past)
        assertEquals("That card has expired.", result.expiryError)
    }

    @Test
    fun `an impossible month is rejected`() {
        val bad = CardInput("4242424242424242", "13/30", "123", "Test Cardholder")
        assertNotNull(SandboxProcessor.validate(bad).expiryError)
    }

    @Test
    fun `a short security code is rejected`() {
        val bad = card("4242 4242 4242 4242").copy(cvc = "12")
        assertNotNull(SandboxProcessor.validate(bad).cvcError)
    }

    @Test
    fun `a missing cardholder name is rejected`() {
        val bad = card("4242 4242 4242 4242").copy(name = "")
        assertNotNull(SandboxProcessor.validate(bad).nameError)
    }

    // --------------------------------------------------------- authorisation

    @Test
    fun `the success test card authorises`() {
        val result = SandboxProcessor.authorise(card("4242 4242 4242 4242"), plan, "pay_1")
        assertTrue(result is PaymentResult.Succeeded)
        val payment = (result as PaymentResult.Succeeded).payment
        assertEquals(PaymentStatus.SUCCEEDED, payment.paymentStatus)
        assertEquals(plan.priceMinor, payment.amountMinor)
        assertNull(payment.failureReason)
    }

    @Test
    fun `the decline test card is declined with a recovery route`() {
        val result = SandboxProcessor.authorise(card("4000 0000 0000 0002"), plan, "pay_2")
        assertTrue(result is PaymentResult.Declined)
        result as PaymentResult.Declined
        assertEquals(PaymentStatus.DECLINED, result.payment.paymentStatus)
        assertTrue("a decline must tell the user nothing was charged", result.recovery.contains("nothing was charged"))
    }

    @Test
    fun `insufficient funds and processing errors are distinguished`() {
        val funds = SandboxProcessor.authorise(card("4000 0000 0000 9995"), plan, "pay_3")
        val processing = SandboxProcessor.authorise(card("4000 0000 0000 0119"), plan, "pay_4")

        assertEquals(PaymentStatus.DECLINED, (funds as PaymentResult.Declined).payment.paymentStatus)
        assertEquals(PaymentStatus.FAILED, (processing as PaymentResult.Declined).payment.paymentStatus)
        assertTrue(funds.reason != processing.reason)
    }

    @Test
    fun `a declined attempt still produces an auditable record`() {
        val result = SandboxProcessor.authorise(card("4000 0000 0000 0002"), plan, "pay_5")
        val payment = (result as PaymentResult.Declined).payment

        assertEquals("pay_5", payment.id)
        assertNotNull("a failed charge must record why", payment.failureReason)
        assertEquals("0002", payment.cardLast4)
    }

    // ----------------------------------------------------------- card safety

    @Test
    fun `only the last four digits are ever retained`() {
        val result = SandboxProcessor.authorise(card("4242 4242 4242 4242"), plan, "pay_6")
        val payment = (result as PaymentResult.Succeeded).payment

        assertEquals("4242", payment.cardLast4)
        // The full number must appear nowhere in the persisted record.
        assertTrue(
            "the full PAN must never reach a stored record",
            !payment.toString().contains("4242424242424242")
        )
    }

    @Test
    fun `brand is derived from the number prefix`() {
        assertEquals("Visa", CardInput("4242424242424242").brand)
        assertEquals("Mastercard", CardInput("5555555555554444").brand)
        assertEquals("Amex", CardInput("378282246310005").brand)
    }

    @Test
    fun `the number is grouped in fours as it is typed`() {
        assertEquals("4242 4242 4242 4242", CardInput("4242424242424242").formattedNumber())
        assertEquals("4242 42", CardInput("424242").formattedNumber())
    }

    // ---------------------------------------------------------------- prices

    @Test
    fun `prices format from minor units without floating point`() {
        assertEquals("$12.00", SubscriptionPlan.PRO_MONTHLY.displayPrice)
        assertEquals("$120.00", SubscriptionPlan.PRO_YEARLY.displayPrice)
        assertEquals("$12.00 / month", SubscriptionPlan.PRO_MONTHLY.displayPricePerInterval)
    }
}
