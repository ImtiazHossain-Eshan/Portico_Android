package com.portico.android.domain

import kotlinx.serialization.Serializable

/*
 * Currency conversion.
 *
 * A portfolio that spans Dhaka, Montevideo and Buenos Aires holds three
 * currencies, and summing them without converting produces a number that means
 * nothing. This converts every property into the display currency before any
 * aggregation happens.
 *
 * Rates are *the user's assumptions*, exactly like tax rates: editable, dated,
 * and never presented as a live feed, because no market data provider is
 * connected. A stale rate the user set and can see beats a plausible-looking
 * one they cannot check.
 */

const val FX_NOTICE =
    "Rates are yours to set, not a live feed. Update them when they drift. Every converted figure moves with them."

/**
 * Rates expressed as units of the currency per 1 USD, which keeps the table
 * readable (a taka rate of 122 rather than 0.0081).
 */
@Serializable
data class ExchangeRates(
    val perUsd: Map<String, Double> = defaults,
    val updated: String = ""
) {
    fun rate(currency: String): Double =
        if (currency == BASE) 1.0 else perUsd[currency] ?: defaults[currency] ?: 1.0

    /** Convert between any two currencies via the USD base. */
    fun convert(amount: Double, from: String, to: String): Double {
        if (from == to || amount == 0.0) return amount
        val fromRate = rate(from)
        val toRate = rate(to)
        if (fromRate <= 0.0) return amount
        return amount / fromRate * toRate
    }

    fun withRate(currency: String, value: Double, today: String): ExchangeRates =
        copy(
            perUsd = perUsd + (currency to value.coerceAtLeast(0.0001)),
            updated = today
        )

    fun reset(): ExchangeRates = ExchangeRates()

    val isEdited: Boolean get() = perUsd != defaults

    companion object {
        const val BASE = "USD"

        /**
         * Starting points, not quotes. Rounded deliberately so nobody mistakes
         * them for a market rate.
         */
        val defaults = mapOf(
            "USD" to 1.0,
            "EUR" to 0.92,
            "GBP" to 0.79,
            "BDT" to 122.0,
            "UYU" to 40.0,
            "ARS" to 1_050.0
        )
    }
}

/** One row of the rate editor. */
data class RateRow(val currency: String, val symbol: String, val perUsd: Double, val isBase: Boolean)

fun ExchangeRates.rows(): List<RateRow> = Money.currencies.map { code ->
    RateRow(
        currency = code,
        symbol = Money.symbolFor(code),
        perUsd = rate(code),
        isBase = code == ExchangeRates.BASE
    )
}
