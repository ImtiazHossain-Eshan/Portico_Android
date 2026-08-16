package com.portico.android.domain

import kotlin.math.abs

/*
 * Every figure Portico shows is derived here. Nothing in the UI stores a
 * return, a yield, or a cap rate — the screens read this file's output, so a
 * property the user types in on Tuesday is analysed by exactly the same rules
 * as the seeded ones.
 *
 * The chain the blueprint asks to be legible end to end (section 20):
 *
 *   gross income -> operating expenses -> taxes & fees
 *                -> net income -> net yield -> net ROI
 *
 * Each step below is a named property on [PropertyFinancials] so the waterfall
 * on screen and the arithmetic here cannot drift apart.
 */

private const val MONTHS = 12

/** Guarded division: a portfolio with no capital in it yields 0, never NaN. */
private fun ratio(numerator: Double, denominator: Double): Double =
    if (denominator <= 0.0 || denominator.isNaN()) 0.0 else numerator / denominator

private fun pct(numerator: Double, denominator: Double): Double = ratio(numerator, denominator) * 100.0

/**
 * A single property resolved into every figure the product displays.
 *
 * Yields are expressed against **current value** rather than purchase price so
 * that gross and net yield answer the same question — "what is this asset
 * returning now" — and stay comparable across properties bought years apart.
 * Returns are expressed against **initial investment**, the cash actually put
 * in, which is what the investor is measuring performance of.
 */
data class PropertyFinancials(
    val property: Property,

    // --- step 1: gross
    val monthlyGrossIncome: Double,
    val annualGrossIncome: Double,

    // --- step 2: operating expenses (everything except tax)
    val monthlyOperatingExpenses: Double,
    val annualOperatingExpenses: Double,

    // --- step 3: taxes and fees
    val monthlyTaxes: Double,
    val annualTaxes: Double,

    // --- step 4: net
    val monthlyNetIncome: Double,
    val annualNetIncome: Double,

    // --- derived rates
    val netOperatingIncome: Double,
    val capRate: Double,
    val grossYield: Double,
    val netYield: Double,
    val capitalRoi: Double,
    val cashOnCash: Double,
    val totalRoi: Double,
    val appreciation: Double,
    val holdingYears: Double
) {
    /** What lands in the investor's pocket each month; negative is a real case. */
    val monthlyCashflow: Double get() = monthlyNetIncome
    val annualCashflow: Double get() = annualNetIncome
    val isCashflowPositive: Boolean get() = monthlyNetIncome >= 0

    /** Total money made on this property: value growth plus a year of net income. */
    val totalReturn: Double get() = appreciation + annualNetIncome

    /** Ordered rows for the gross-to-net waterfall, ready to draw. */
    fun waterfall(): List<WaterfallStep> = listOf(
        WaterfallStep("Gross income", annualGrossIncome, WaterfallKind.OPENING),
        WaterfallStep("Operating expenses", -annualOperatingExpenses, WaterfallKind.DEDUCTION),
        WaterfallStep("Taxes and fees", -annualTaxes, WaterfallKind.DEDUCTION),
        WaterfallStep("Net income", annualNetIncome, WaterfallKind.TOTAL)
    )
}

enum class WaterfallKind { OPENING, DEDUCTION, TOTAL }

data class WaterfallStep(val label: String, val amount: Double, val kind: WaterfallKind)

/** Portfolio-level roll-up. Sums the parts rather than averaging the rates. */
data class PortfolioFinancials(
    val propertyCount: Int,
    val portfolioValue: Double,
    val investedCapital: Double,
    val purchaseTotal: Double,
    val appreciation: Double,
    val annualGrossIncome: Double,
    val annualOperatingExpenses: Double,
    val annualTaxes: Double,
    val annualNetIncome: Double,
    val monthlyCashflow: Double,
    val capRate: Double,
    val grossYield: Double,
    val netYield: Double,
    val capitalRoi: Double,
    val cashOnCash: Double,
    val totalRoi: Double
) {
    val totalReturn: Double get() = appreciation + annualNetIncome
    val isEmpty: Boolean get() = propertyCount == 0

    fun waterfall(): List<WaterfallStep> = listOf(
        WaterfallStep("Gross income", annualGrossIncome, WaterfallKind.OPENING),
        WaterfallStep("Operating expenses", -annualOperatingExpenses, WaterfallKind.DEDUCTION),
        WaterfallStep("Taxes and fees", -annualTaxes, WaterfallKind.DEDUCTION),
        WaterfallStep("Net income", annualNetIncome, WaterfallKind.TOTAL)
    )
}

object Finance {

    /**
     * Resolve one property.
     *
     * [taxProfile] supplies the jurisdiction's assumed rates; anything the user
     * has already recorded as a tax expense is counted instead of assumed, so
     * the two never double-count.
     */
    fun analyse(
        property: Property,
        income: List<IncomeEntry>,
        expenses: List<ExpenseEntry>,
        taxProfile: TaxProfile,
        today: SimpleDate = SimpleDate.today(),
        rates: ExchangeRates = ExchangeRates(),
        displayCurrency: String = property.currency
    ): PropertyFinancials {
        val mine = { id: String -> id == property.id }

        /*
         * Conversion happens once, here at the boundary. Everything downstream
         * (the waterfall, the rates, the roll-up) then works in a single
         * currency without knowing conversion exists. Rates and yields are
         * ratios and come out identical either way; only the money moves.
         */
        val fx = { amount: Double -> rates.convert(amount, property.currency, displayCurrency) }
        val converted = property.copy(
            purchasePrice = fx(property.purchasePrice),
            initialInvestment = fx(property.initialInvestment),
            financingAmount = fx(property.financingAmount),
            currentValue = fx(property.currentValue),
            currency = displayCurrency
        )

        val monthlyGross = income.filter { mine(it.propertyId) }
            .sumOf { fx(if (it.recurring) it.amount else it.amount / MONTHS) }
        val annualGross = monthlyGross * MONTHS

        val propertyExpenses = expenses.filter { mine(it.propertyId) }

        val monthlyOperating = propertyExpenses
            .filter { ExpenseCategory.from(it.category).isOperating }
            .sumOf { fx(if (it.recurring) it.amount else it.amount / MONTHS) }

        // Taxes the user actually recorded.
        val recordedAnnualTax = propertyExpenses
            .filter { !ExpenseCategory.from(it.category).isOperating }
            .sumOf { fx(if (it.recurring) it.amount * MONTHS else it.amount) }

        // Taxes the jurisdiction implies, used only where nothing was recorded.
        val assumedAnnualTax = taxProfile.annualTaxFor(
            grossAnnualIncome = annualGross,
            operatingExpenses = monthlyOperating * MONTHS,
            propertyValue = converted.currentValue
        )
        val annualTax = if (recordedAnnualTax > 0.0) recordedAnnualTax else assumedAnnualTax

        val annualOperating = monthlyOperating * MONTHS
        val annualNet = annualGross - annualOperating - annualTax
        val noi = annualGross - annualOperating

        val holdingYears = SimpleDate.parse(property.purchaseDate)
            ?.let { maxOf(it.yearsUntil(today), 0.08) } ?: 1.0

        return PropertyFinancials(
            property = converted,
            monthlyGrossIncome = monthlyGross,
            annualGrossIncome = annualGross,
            monthlyOperatingExpenses = monthlyOperating,
            annualOperatingExpenses = annualOperating,
            monthlyTaxes = annualTax / MONTHS,
            annualTaxes = annualTax,
            monthlyNetIncome = annualNet / MONTHS,
            annualNetIncome = annualNet,
            netOperatingIncome = noi,
            capRate = pct(noi, converted.currentValue),
            grossYield = pct(annualGross, converted.currentValue),
            netYield = pct(annualNet, converted.currentValue),
            capitalRoi = pct(converted.appreciation, converted.initialInvestment),
            cashOnCash = pct(annualNet, converted.initialInvestment),
            totalRoi = pct(converted.appreciation + annualNet, converted.initialInvestment),
            appreciation = converted.appreciation,
            holdingYears = holdingYears
        )
    }

    fun analyseAll(
        properties: List<Property>,
        income: List<IncomeEntry>,
        expenses: List<ExpenseEntry>,
        taxProfile: TaxProfile,
        rates: ExchangeRates = ExchangeRates(),
        displayCurrency: String = ExchangeRates.BASE
    ): List<PropertyFinancials> =
        properties.map {
            analyse(it, income, expenses, taxProfile, SimpleDate.today(), rates, displayCurrency)
        }

    /** Roll individual results up. Rates are recomputed from totals, not averaged. */
    fun portfolio(results: List<PropertyFinancials>): PortfolioFinancials {
        val value = results.sumOf { it.property.currentValue }
        val invested = results.sumOf { it.property.initialInvestment }
        val purchase = results.sumOf { it.property.purchasePrice }
        val gross = results.sumOf { it.annualGrossIncome }
        val operating = results.sumOf { it.annualOperatingExpenses }
        val taxes = results.sumOf { it.annualTaxes }
        val net = gross - operating - taxes
        val appreciation = value - purchase

        return PortfolioFinancials(
            propertyCount = results.size,
            portfolioValue = value,
            investedCapital = invested,
            purchaseTotal = purchase,
            appreciation = appreciation,
            annualGrossIncome = gross,
            annualOperatingExpenses = operating,
            annualTaxes = taxes,
            annualNetIncome = net,
            monthlyCashflow = net / MONTHS,
            capRate = pct(gross - operating, value),
            grossYield = pct(gross, value),
            netYield = pct(net, value),
            capitalRoi = pct(appreciation, invested),
            cashOnCash = pct(net, invested),
            totalRoi = pct(appreciation + net, invested)
        )
    }

    /**
     * Project a property's value backwards from today so the performance chart
     * has a defensible shape: linear interpolation between purchase price at
     * the purchase date and current value now. It is an interpolation, not a
     * price history, and the UI says so.
     */
    fun valueSeries(
        property: Property,
        points: Int = 24,
        rates: ExchangeRates = ExchangeRates(),
        displayCurrency: String = property.currency
    ): List<Double> {
        val fx = { amount: Double -> rates.convert(amount, property.currency, displayCurrency) }
        if (points <= 1) return listOf(fx(property.currentValue))
        val from = fx(property.purchasePrice)
        val to = fx(property.currentValue)
        return List(points) { index ->
            val t = index.toDouble() / (points - 1)
            // Slight ease so the line reads as a market, not a ruler.
            val eased = t * t * (3 - 2 * t)
            from + (to - from) * eased
        }
    }

    fun portfolioValueSeries(
        properties: List<Property>,
        points: Int = 24,
        rates: ExchangeRates = ExchangeRates(),
        displayCurrency: String = ExchangeRates.BASE
    ): List<Double> {
        if (properties.isEmpty()) return List(points) { 0.0 }
        val series = properties.map { valueSeries(it, points, rates, displayCurrency) }
        return List(points) { index -> series.sumOf { it[index] } }
    }

    /** Allocation shares that always total 1, grouped by an arbitrary key. */
    fun <T> allocation(
        properties: List<Property>,
        rates: ExchangeRates = ExchangeRates(),
        displayCurrency: String = ExchangeRates.BASE,
        key: (Property) -> T
    ): List<Pair<T, Double>> {
        fun value(p: Property) = rates.convert(p.currentValue, p.currency, displayCurrency)
        val total = properties.sumOf { value(it) }
        if (total <= 0.0) return emptyList()
        return properties
            .groupBy(key)
            .map { (group, items) -> group to items.sumOf { value(it) } / total }
            .sortedByDescending { it.second }
    }
}

/** Whole-day date maths without pulling in a desugaring dependency. */
data class SimpleDate(val year: Int, val month: Int, val day: Int) {

    fun yearsUntil(other: SimpleDate): Double {
        val months = (other.year - year) * 12 + (other.month - month)
        val dayFraction = (other.day - day) / 30.0
        return (months + dayFraction) / 12.0
    }

    fun format(): String = "%02d %s %d".format(day, MONTH_NAMES[(month - 1).coerceIn(0, 11)], year)

    companion object {
        val MONTH_NAMES = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )

        fun today(): SimpleDate {
            val calendar = java.util.Calendar.getInstance()
            return SimpleDate(
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH) + 1,
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            )
        }

        /** Accepts "14 Mar 2022" and "2022-03-14". */
        fun parse(raw: String): SimpleDate? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            runCatching {
                val parts = text.split(" ")
                if (parts.size == 3) {
                    val monthIndex = MONTH_NAMES.indexOfFirst { it.equals(parts[1], ignoreCase = true) }
                    if (monthIndex >= 0) {
                        return SimpleDate(parts[2].toInt(), monthIndex + 1, parts[0].toInt())
                    }
                }
                val dashed = text.split("-")
                if (dashed.size == 3) {
                    return SimpleDate(dashed[0].toInt(), dashed[1].toInt(), dashed[2].toInt())
                }
            }
            return null
        }
    }
}

// ------------------------------------------------------------- formatting

object Money {
    /** "$1,240,500" — no cents, because no figure in this product needs them. */
    fun format(value: Double, currency: String = "USD"): String {
        val symbol = symbolFor(currency)
        val sign = if (value < 0) "-" else ""
        return "$sign$symbol${grouped(abs(value))}"
    }

    /** Signed for deltas: "+$184,200" / "-$4,820". */
    fun signed(value: Double, currency: String = "USD"): String {
        val symbol = symbolFor(currency)
        val sign = if (value < 0) "-" else "+"
        return "$sign$symbol${grouped(abs(value))}"
    }

    /** "$1.24M" for chart axes and dense rows. */
    fun compact(value: Double, currency: String = "USD"): String {
        val symbol = symbolFor(currency)
        val sign = if (value < 0) "-" else ""
        val magnitude = abs(value)
        return when {
            magnitude >= 1_000_000 -> "$sign$symbol%.2fM".format(magnitude / 1_000_000)
            magnitude >= 1_000 -> "$sign$symbol%.1fk".format(magnitude / 1_000)
            else -> "$sign$symbol${grouped(magnitude)}"
        }
    }

    fun percent(value: Double, decimals: Int = 1): String = "%.${decimals}f%%".format(value)

    fun signedPercent(value: Double, decimals: Int = 1): String {
        val sign = if (value < 0) "" else "+"
        return "$sign%.${decimals}f%%".format(value)
    }

    private fun grouped(value: Double): String {
        val whole = value.toLong()
        return whole.toString().reversed().chunked(3).joinToString(",").reversed()
    }

    fun symbolFor(currency: String): String = when (currency) {
        "USD" -> "$"
        "EUR" -> "€"
        "UYU" -> "\$U"
        "ARS" -> "AR$"
        "BDT" -> "৳"
        "GBP" -> "£"
        else -> "$"
    }

    val currencies = listOf("USD", "EUR", "BDT", "UYU", "ARS", "GBP")
}
