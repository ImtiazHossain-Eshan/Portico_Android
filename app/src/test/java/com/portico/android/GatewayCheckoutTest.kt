package com.portico.android

import com.portico.android.domain.Payment
import com.portico.android.domain.PaymentStatus
import com.portico.android.domain.SubscriptionPlan
import com.portico.android.ui.GatewayOutcome
import com.portico.android.ui.PaymentReturns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gateway return hop and the pending state it creates.
 *
 * A hosted gateway settles out of band, so between the member coming back and
 * the server hearing from the gateway there is a window where the truthful
 * answer is "not yet". These tests exist because the tempting shortcuts in that
 * window, treating the redirect as proof or treating pending as failure, are
 * both wrong in ways a member would feel.
 */
class GatewayCheckoutTest {

    private fun payment(status: PaymentStatus) = Payment(
        id = "ptc_1",
        planId = SubscriptionPlan.PRO_MONTHLY.id,
        amountMinor = 120_000,
        currency = "BDT",
        status = status.name,
        date = "23 Aug 2026",
        cardLast4 = "1111",
        cardBrand = "VISA"
    )

    @Test
    fun `success redirect is accepted`() {
        val parsed = PaymentReturns.parse("portico", "payment", "ptc_abc", "success")
        assertNotNull(parsed)
        assertEquals("ptc_abc", parsed!!.transactionId)
        assertEquals(GatewayOutcome.SUCCESS, parsed.outcome)
    }

    @Test
    fun `cancel and fail redirects are distinguished`() {
        assertEquals(
            GatewayOutcome.CANCELLED,
            PaymentReturns.parse("portico", "payment", "ptc_abc", "cancel")?.outcome
        )
        assertEquals(
            GatewayOutcome.FAILED,
            PaymentReturns.parse("portico", "payment", "ptc_abc", "fail")?.outcome
        )
    }

    /** An absent or unexpected result must never be read as payment. */
    @Test
    fun `unknown result is treated as failure not success`() {
        assertEquals(
            GatewayOutcome.FAILED,
            PaymentReturns.parse("portico", "payment", "ptc_abc", null)?.outcome
        )
        assertEquals(
            GatewayOutcome.FAILED,
            PaymentReturns.parse("portico", "payment", "ptc_abc", "anything")?.outcome
        )
    }

    @Test
    fun `foreign links are rejected`() {
        assertNull(PaymentReturns.parse("https", "payment", "ptc_abc", "success"))
        assertNull(PaymentReturns.parse("portico", "oauth", "ptc_abc", "success"))
    }

    /** Without a transaction id there is nothing to verify against. */
    @Test
    fun `redirect without a transaction id is rejected`() {
        assertNull(PaymentReturns.parse("portico", "payment", null, "success"))
        assertNull(PaymentReturns.parse("portico", "payment", "   ", "success"))
    }

    @Test
    fun `pending is not success and not failure`() {
        val pending = payment(PaymentStatus.PENDING)
        assertEquals(PaymentStatus.PENDING, pending.paymentStatus)
        assertFalse(pending.succeeded)
    }

    /**
     * An unrecognised status falls back to FAILED. That default matters: a
     * server that grows a new status must never have it silently read as paid.
     */
    @Test
    fun `unknown status falls back to failed`() {
        val odd = payment(PaymentStatus.PENDING).copy(status = "SOMETHING_NEW")
        assertEquals(PaymentStatus.FAILED, odd.paymentStatus)
        assertFalse(odd.succeeded)
    }

    @Test
    fun `settled gateway payment reads as paid`() {
        assertTrue(payment(PaymentStatus.SUCCEEDED).succeeded)
    }

    /*
     * Taka prices are stated, not converted. The gateway settles in BDT and
     * would convert a dollar figure at its own rate, which would leave the
     * amount charged different from the amount shown and make the server's
     * amount check impossible to write as an equality.
     */
    @Test
    fun `paid plans carry a taka price inside the gateway limits`() {
        SubscriptionPlan.paid.forEach { plan ->
            assertTrue("${plan.id} needs a taka price", plan.priceTaka > 0)
            assertTrue("${plan.id} below gateway minimum", plan.priceTaka >= 10)
            assertTrue("${plan.id} above gateway maximum", plan.priceTaka <= 500_000)
        }
    }

    @Test
    fun `taka price renders with the taka sign and no conversion`() {
        assertEquals("৳1200", SubscriptionPlan.PRO_MONTHLY.displayPriceTaka)
        assertEquals("৳12000", SubscriptionPlan.PRO_YEARLY.displayPriceTaka)
        assertEquals("৳1200 / month", SubscriptionPlan.PRO_MONTHLY.displayPriceTakaPerInterval)
    }

    @Test
    fun `free plan has no taka price to charge`() {
        assertEquals(0L, SubscriptionPlan.FREE.priceTaka)
    }
}
