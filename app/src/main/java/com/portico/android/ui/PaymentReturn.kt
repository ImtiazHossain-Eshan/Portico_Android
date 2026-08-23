package com.portico.android.ui

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the gateway redirect told us on the way back into the app.
 *
 * Deliberately thin. The browser hop is a navigation event, not evidence: a
 * member can close the tab early, or hand-edit the URL, and either way the
 * money question is settled server side by the IPN. So this carries only enough
 * to know which transaction to watch and whether to expect good news.
 */
enum class GatewayOutcome { SUCCESS, FAILED, CANCELLED }

data class PaymentReturn(
    val transactionId: String,
    val outcome: GatewayOutcome
)

object PaymentReturns {
    private val _latest = MutableStateFlow<PaymentReturn?>(null)
    val latest: StateFlow<PaymentReturn?> = _latest.asStateFlow()

    /** True when the URI was ours, so the caller knows not to pass it on. */
    fun accept(uri: Uri): Boolean {
        val parsed = parse(
            scheme = uri.scheme,
            host = uri.host,
            transactionId = uri.getQueryParameter("tran_id"),
            result = uri.getQueryParameter("result")
        ) ?: return false
        _latest.value = parsed
        return true
    }

    /**
     * The decision itself, kept free of android.net.Uri so it can be tested on
     * the JVM. An unknown result is treated as failure rather than success,
     * because a redirect that does not say it worked is not evidence that it
     * did.
     */
    fun parse(
        scheme: String?,
        host: String?,
        transactionId: String?,
        result: String?
    ): PaymentReturn? {
        if (scheme != SCHEME || host != HOST) return null
        if (transactionId.isNullOrBlank()) return null
        return PaymentReturn(
            transactionId = transactionId,
            outcome = when (result) {
                "success" -> GatewayOutcome.SUCCESS
                "cancel" -> GatewayOutcome.CANCELLED
                else -> GatewayOutcome.FAILED
            }
        )
    }

    /** Cleared once a screen has acted on it, so returning later is not replayed. */
    fun consume() {
        _latest.value = null
    }

    const val SCHEME = "portico"
    const val HOST = "payment"
}
