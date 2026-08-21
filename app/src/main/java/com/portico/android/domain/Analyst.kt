package com.portico.android.domain

import kotlin.math.abs

/*
 * The assistant runs on-device.
 *
 * No API key ships in this app and none is called, so rather than mock a chat
 * that returns prose it cannot justify, this answers from the portfolio the
 * user actually has: it reads intent from the question, computes the figure,
 * and returns the working so the answer can be checked. When it cannot answer
 * a question it says so and offers what it can do instead, which is more
 * useful than a confident guess about someone's money.
 */

data class AnalystReply(
    val text: String,
    val workings: List<String> = emptyList(),
    val followUps: List<String> = emptyList(),
    val understood: Boolean = true
)

object Analyst {

    /**
     * The figures a cloud model is allowed to see.
     *
     * Computed results only. No street address, no document, no note, no
     * tenant: those identify a person and a building, and none of them are
     * needed to answer a question about return or yield. Property names are
     * included because comparison questions are unanswerable without a label,
     * and a name is the member's own wording rather than a locator.
     */
    fun factSheet(
        results: List<PropertyFinancials>,
        portfolio: PortfolioFinancials,
        taxProfile: TaxProfile,
        currency: String,
        focus: Property? = null
    ): String = buildString {
        fun money(value: Double) = Money.format(value, currency)
        fun rate(value: Double) = Money.percent(value)

        appendLine("Currency: $currency")
        appendLine("Jurisdiction: ${taxProfile.jurisdiction.name}, ${taxProfile.jurisdiction.countryName}")
        appendLine()
        appendLine("PORTFOLIO (${portfolio.propertyCount} properties)")
        appendLine("  Value ${money(portfolio.portfolioValue)}; capital invested ${money(portfolio.investedCapital)}")
        appendLine("  Annual gross ${money(portfolio.annualGrossIncome)}; operating costs ${money(portfolio.annualOperatingExpenses)}; tax ${money(portfolio.annualTaxes)}; net ${money(portfolio.annualNetIncome)}")
        appendLine("  Monthly cashflow ${money(portfolio.monthlyCashflow)}")
        appendLine("  Cap rate ${rate(portfolio.capRate)}; gross yield ${rate(portfolio.grossYield)}; net yield ${rate(portfolio.netYield)}")
        appendLine("  Total return ${rate(portfolio.totalRoi)}; appreciation ${money(portfolio.appreciation)}")
        appendLine()
        appendLine("PROPERTIES")
        results.forEach { r ->
            val p = r.property
            appendLine("  ${p.name} (${p.type}, ${p.region}, ${p.country})")
            appendLine("    Value ${money(p.currentValue)}; bought ${money(p.purchasePrice)} on ${p.purchaseDate}; capital in ${money(p.initialInvestment)}")
            appendLine("    Annual gross ${money(r.annualGrossIncome)}; costs ${money(r.annualOperatingExpenses)}; tax ${money(r.annualTaxes)}; net ${money(r.annualNetIncome)}")
            appendLine("    Monthly cashflow ${money(r.monthlyCashflow)}; cap rate ${rate(r.capRate)}; net yield ${rate(r.netYield)}; total return ${rate(r.totalRoi)}")
        }
        if (focus != null) {
            appendLine()
            appendLine("The member is currently looking at: ${focus.name}")
        }
    }

    fun answer(
        question: String,
        results: List<PropertyFinancials>,
        portfolio: PortfolioFinancials,
        taxProfile: TaxProfile,
        currency: String,
        focus: Property? = null
    ): AnalystReply {
        val q = question.lowercase()

        if (results.isEmpty()) {
            return AnalystReply(
                "There are no properties in this workspace yet, so there's nothing for me to analyse. Add one and I can work out its return, yield and cashflow.",
                followUps = listOf("What does net yield mean?", "What does cap rate mean?"),
                understood = true
            )
        }

        // Scenario: "what if the rent were $X"
        rentScenario(q)?.let { newRent ->
            val target = focus ?: results.maxByOrNull { it.property.currentValue }!!.property
            return rentScenarioReply(target, newRent, results, taxProfile, currency)
        }

        // Scenario: "if maintenance/expenses rose X%"
        expenseScenario(q)?.let { pct ->
            return expenseScenarioReply(pct, results, portfolio, currency)
        }

        return when {
            matches(q, "worst", "least", "lowest", "losing", "loses", "negative", "underperform") ->
                extremeReply(results, currency, best = false)

            matches(q, "best", "most", "highest", "top", "strongest") ->
                extremeReply(results, currency, best = true)

            matches(q, "tax", "taxes", "taxation") ->
                taxReply(portfolio, taxProfile, currency)

            matches(q, "compare", "versus", " vs ", "against") ->
                compareReply(results, q, currency)

            matches(q, "cashflow", "cash flow", "monthly", "income") ->
                cashflowReply(portfolio, results, currency)

            matches(q, "yield") ->
                yieldReply(portfolio, results, currency)

            matches(q, "roi", "return") ->
                returnReply(portfolio, currency)

            matches(q, "cap rate", "caprate") ->
                capRateReply(portfolio, results, currency)

            matches(q, "worth", "value", "portfolio") ->
                valueReply(portfolio, results, currency)

            matches(q, "diversif", "allocation", "spread", "concentrat") ->
                allocationReply(results, portfolio, currency)

            else -> AnalystReply(
                "I can't answer that one from your records. I work with the figures in this workspace: value, return, yield, cashflow, tax, and simple what-if scenarios on rent or costs.",
                followUps = defaultQuestions(results),
                understood = false
            )
        }
    }

    // ------------------------------------------------------------- intents

    private fun matches(question: String, vararg keys: String) = keys.any { question.contains(it) }

    /** Pulls a figure out of "if I rented it for $2,400 a month". */
    private fun rentScenario(q: String): Double? {
        if (!matches(q, "rent", "rented", "let it", "charge")) return null
        if (!matches(q, "if", "were", "was", "what would", "raise", "increase to")) return null
        return firstAmount(q)
    }

    private fun expenseScenario(q: String): Double? {
        if (!matches(q, "maintenance", "expense", "cost", "outgoing")) return null
        if (!matches(q, "rise", "rose", "increase", "up ", "higher", "%")) return null
        return firstPercent(q)
    }

    private fun firstAmount(q: String): Double? =
        Regex("""\$?\s?([0-9][0-9,.]{1,12})""").find(q)
            ?.groupValues?.get(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?.takeIf { it > 0 }

    private fun firstPercent(q: String): Double? =
        Regex("""([0-9]{1,3}(?:\.[0-9]+)?)\s?%""").find(q)
            ?.groupValues?.get(1)
            ?.toDoubleOrNull()

    // ------------------------------------------------------------ replies

    private fun rentScenarioReply(
        property: Property,
        newRent: Double,
        results: List<PropertyFinancials>,
        taxProfile: TaxProfile,
        currency: String
    ): AnalystReply {
        val current = results.first { it.property.id == property.id }
        val projectedIncome = listOf(
            IncomeEntry("scenario", property.id, newRent, IncomeCategory.RENT.label, property.purchaseDate)
        )
        val existingExpenses = current.let { res ->
            // Rebuild expense entries from the resolved monthly totals so the
            // scenario keeps the same cost base.
            listOf(
                ExpenseEntry("s-op", property.id, res.monthlyOperatingExpenses, ExpenseCategory.OTHER.label, property.purchaseDate)
            )
        }
        val projected = Finance.analyse(property, projectedIncome, existingExpenses, taxProfile)

        val deltaCashflow = projected.monthlyCashflow - current.monthlyCashflow
        return AnalystReply(
            text = buildString {
                append("At ${Money.format(newRent, currency)} a month, ${property.name} would net ")
                append("${Money.format(projected.monthlyCashflow, currency)} a month after costs and tax, ")
                append(
                    if (deltaCashflow >= 0) "${Money.signed(deltaCashflow, currency)} better than today. "
                    else "${Money.signed(deltaCashflow, currency)} against today. "
                )
                append("Net yield would be ${Money.percent(projected.netYield)} and cash-on-cash ${Money.percent(projected.cashOnCash)}.")
            },
            workings = listOf(
                "Gross income   ${Money.format(projected.annualGrossIncome, currency)}",
                "Operating      ${Money.format(-projected.annualOperatingExpenses, currency)}",
                "Tax            ${Money.format(-projected.annualTaxes, currency)}",
                "Net income     ${Money.format(projected.annualNetIncome, currency)}",
                "Net yield      ${Money.percent(projected.netYield)}"
            ),
            followUps = listOf(
                "What rent would break even?",
                "How does that compare to my other properties?"
            )
        )
    }

    private fun expenseScenarioReply(
        pct: Double,
        results: List<PropertyFinancials>,
        portfolio: PortfolioFinancials,
        currency: String
    ): AnalystReply {
        val extra = portfolio.annualOperatingExpenses * pct / 100.0
        val newNet = portfolio.annualNetIncome - extra
        val newYield = if (portfolio.portfolioValue > 0) newNet / portfolio.portfolioValue * 100 else 0.0
        val flipped = results.filter {
            it.monthlyCashflow >= 0 &&
                (it.annualNetIncome - it.annualOperatingExpenses * pct / 100.0) < 0
        }

        return AnalystReply(
            text = buildString {
                append("If operating costs rose ${Money.percent(pct, 0)}, that's ${Money.format(extra, currency)} more a year. ")
                append("Portfolio net income would fall to ${Money.format(newNet, currency)} and net yield to ${Money.percent(newYield)}. ")
                if (flipped.isNotEmpty()) {
                    append("${flipped.joinToString(" and ") { it.property.name }} would turn cashflow negative.")
                } else {
                    append("Every property would still cover its costs.")
                }
            },
            workings = listOf(
                "Current net    ${Money.format(portfolio.annualNetIncome, currency)}",
                "Added cost     ${Money.format(-extra, currency)}",
                "New net        ${Money.format(newNet, currency)}",
                "New net yield  ${Money.percent(newYield)}"
            ),
            followUps = listOf("Which property is most exposed?", "How much goes to tax?")
        )
    }

    private fun extremeReply(
        results: List<PropertyFinancials>,
        currency: String,
        best: Boolean
    ): AnalystReply {
        val sorted = results.sortedBy { it.netYield }
        val pick = if (best) sorted.last() else sorted.first()
        val other = if (best) sorted.first() else sorted.last()

        return AnalystReply(
            text = buildString {
                append("${pick.property.name} is your ${if (best) "strongest" else "weakest"} holding on net yield at ${Money.percent(pick.netYield)}, ")
                append("returning ${Money.format(pick.monthlyCashflow, currency)} a month after costs and tax. ")
                if (!best && pick.monthlyCashflow < 0) {
                    append("It currently costs you money each month, but the value has still grown ${Money.format(pick.appreciation, currency)}, so it may be worth holding, but it draws on cash. ")
                }
                append("For contrast, ${other.property.name} sits at ${Money.percent(other.netYield)}.")
            },
            workings = listOf(
                "Gross income   ${Money.format(pick.annualGrossIncome, currency)}",
                "Operating      ${Money.format(-pick.annualOperatingExpenses, currency)}",
                "Tax            ${Money.format(-pick.annualTaxes, currency)}",
                "Net income     ${Money.format(pick.annualNetIncome, currency)}"
            ),
            followUps = listOf(
                "What rent would ${pick.property.name} need to break even?",
                "How much of my income goes to tax?"
            )
        )
    }

    private fun taxReply(
        portfolio: PortfolioFinancials,
        taxProfile: TaxProfile,
        currency: String
    ): AnalystReply {
        val share = if (portfolio.annualGrossIncome > 0)
            portfolio.annualTaxes / portfolio.annualGrossIncome * 100 else 0.0
        val lines = taxProfile.breakdown(
            portfolio.annualGrossIncome,
            portfolio.annualOperatingExpenses,
            portfolio.portfolioValue
        )
        return AnalystReply(
            text = buildString {
                append("Tax takes ${Money.format(portfolio.annualTaxes, currency)} a year, ")
                append("${Money.percent(share)} of your gross rental income, under ${taxProfile.jurisdiction.name} assumptions. ")
                append("That's the gap between a ${Money.percent(portfolio.grossYield)} gross yield and ${Money.percent(portfolio.netYield)} net.")
            },
            workings = lines.map { line ->
                "${line.rule.label.padEnd(28).take(28)} ${Money.format(line.amount, currency)}"
            } + "Total tax                    ${Money.format(portfolio.annualTaxes, currency)}",
            followUps = listOf(
                "What if I changed jurisdiction?",
                "Which property carries the most tax?"
            )
        )
    }

    private fun compareReply(
        results: List<PropertyFinancials>,
        q: String,
        currency: String
    ): AnalystReply {
        val named = results.filter { q.contains(it.property.name.lowercase().split(" ").first()) }
        val pair = if (named.size >= 2) named.take(2) else results.take(2)
        if (pair.size < 2) {
            return AnalystReply(
                "I need at least two properties to compare. Right now there's only ${results.size}.",
                followUps = defaultQuestions(results)
            )
        }
        val (a, b) = pair
        val better = if (a.netYield >= b.netYield) a else b
        return AnalystReply(
            text = buildString {
                append("${a.property.name} yields ${Money.percent(a.netYield)} net against ${b.property.name} at ${Money.percent(b.netYield)}. ")
                append("On cashflow it's ${Money.format(a.monthlyCashflow, currency)} versus ${Money.format(b.monthlyCashflow, currency)} a month. ")
                append("${better.property.name} is the stronger income asset on these figures.")
            },
            workings = listOf(
                "                ${a.property.name.take(12).padEnd(14)}${b.property.name.take(12)}",
                "Net yield       ${Money.percent(a.netYield).padEnd(14)}${Money.percent(b.netYield)}",
                "Cap rate        ${Money.percent(a.capRate).padEnd(14)}${Money.percent(b.capRate)}",
                "Total ROI       ${Money.percent(a.totalRoi).padEnd(14)}${Money.percent(b.totalRoi)}",
                "Monthly net     ${Money.compact(a.monthlyCashflow, currency).padEnd(14)}${Money.compact(b.monthlyCashflow, currency)}"
            ),
            followUps = listOf("Which earns least after tax?", "How much goes to tax?")
        )
    }

    private fun cashflowReply(
        portfolio: PortfolioFinancials,
        results: List<PropertyFinancials>,
        currency: String
    ): AnalystReply {
        val negative = results.filter { it.monthlyCashflow < 0 }
        return AnalystReply(
            text = buildString {
                append("Your portfolio nets ${Money.format(portfolio.monthlyCashflow, currency)} a month ")
                append("(${Money.format(portfolio.annualNetIncome, currency)} a year) after operating costs and tax. ")
                if (negative.isEmpty()) append("Every property covers its own costs.")
                else append("${negative.joinToString(" and ") { it.property.name }} runs at a monthly loss.")
            },
            workings = results.map {
                "${it.property.name.take(20).padEnd(22)}${Money.format(it.monthlyCashflow, currency)}"
            } + "Portfolio             ${Money.format(portfolio.monthlyCashflow, currency)}",
            followUps = listOf(
                "What if maintenance rose 20%?",
                "Which property earns least after tax?"
            )
        )
    }

    private fun yieldReply(
        portfolio: PortfolioFinancials,
        results: List<PropertyFinancials>,
        currency: String
    ): AnalystReply = AnalystReply(
        text = "Gross yield across the portfolio is ${Money.percent(portfolio.grossYield)}; net yield after costs and tax is ${Money.percent(portfolio.netYield)}. " +
            "The ${Money.percent(portfolio.grossYield - portfolio.netYield)} difference is what running and taxing these properties costs you.",
        workings = results.map {
            "${it.property.name.take(20).padEnd(22)}gross ${Money.percent(it.grossYield).padEnd(8)}net ${Money.percent(it.netYield)}"
        },
        followUps = listOf("Which property has the best net yield?", "How much goes to tax?")
    )

    private fun returnReply(portfolio: PortfolioFinancials, currency: String) = AnalystReply(
        text = "You've put ${Money.format(portfolio.investedCapital, currency)} in and the portfolio is worth ${Money.format(portfolio.portfolioValue, currency)}. " +
            "That's ${Money.signed(portfolio.appreciation, currency)} of value growth plus ${Money.format(portfolio.annualNetIncome, currency)} of net income a year, " +
            "a total return of ${Money.percent(portfolio.totalRoi)} on the cash you invested.",
        workings = listOf(
            "Invested       ${Money.format(portfolio.investedCapital, currency)}",
            "Current value  ${Money.format(portfolio.portfolioValue, currency)}",
            "Appreciation   ${Money.signed(portfolio.appreciation, currency)}",
            "Annual net     ${Money.format(portfolio.annualNetIncome, currency)}",
            "Total ROI      ${Money.percent(portfolio.totalRoi)}"
        ),
        followUps = listOf("How much goes to tax?", "Which property performs best?")
    )

    private fun capRateReply(
        portfolio: PortfolioFinancials,
        results: List<PropertyFinancials>,
        currency: String
    ) = AnalystReply(
        text = "Portfolio cap rate is ${Money.percent(portfolio.capRate)}: net operating income of " +
            "${Money.format(portfolio.annualGrossIncome - portfolio.annualOperatingExpenses, currency)} against " +
            "${Money.format(portfolio.portfolioValue, currency)} of value. Cap rate ignores financing and income tax, so it compares the assets themselves.",
        workings = results.map {
            "${it.property.name.take(20).padEnd(22)}${Money.percent(it.capRate)}"
        },
        followUps = listOf("What's my net yield?", "Which property earns least after tax?")
    )

    private fun valueReply(
        portfolio: PortfolioFinancials,
        results: List<PropertyFinancials>,
        currency: String
    ) = AnalystReply(
        text = "The portfolio holds ${results.size} properties worth ${Money.format(portfolio.portfolioValue, currency)} today, " +
            "against ${Money.format(portfolio.purchaseTotal, currency)} paid. That's ${Money.signed(portfolio.appreciation, currency)} of appreciation.",
        workings = results.map {
            "${it.property.name.take(20).padEnd(22)}${Money.format(it.property.currentValue, currency)}"
        },
        followUps = listOf("What's my total return?", "How is the portfolio allocated?")
    )

    private fun allocationReply(
        results: List<PropertyFinancials>,
        portfolio: PortfolioFinancials,
        currency: String
    ): AnalystReply {
        val byCountry = Finance.allocation(results.map { it.property }) { it.country }
        val largest = results.maxByOrNull { it.property.currentValue }
        val concentration = largest?.let {
            if (portfolio.portfolioValue > 0) it.property.currentValue / portfolio.portfolioValue * 100 else 0.0
        } ?: 0.0

        return AnalystReply(
            text = buildString {
                append("Your capital sits in ${byCountry.size} ${if (byCountry.size == 1) "country" else "countries"}: ")
                append(byCountry.joinToString(", ") { "${it.first} ${Money.percent(it.second * 100, 0)}" })
                append(". ")
                if (largest != null && concentration > 40) {
                    append("${largest.property.name} alone is ${Money.percent(concentration, 0)} of portfolio value, which is a concentrated position.")
                } else {
                    append("No single property dominates the portfolio.")
                }
            },
            workings = byCountry.map { "${it.first.padEnd(22)}${Money.percent(it.second * 100)}" },
            followUps = listOf("Which property performs best?", "What's my total return?")
        )
    }

    fun defaultQuestions(results: List<PropertyFinancials>): List<String> = buildList {
        add("Which property earns least after tax?")
        add("How much of my income goes to tax?")
        add("What is my portfolio's net yield?")
        results.firstOrNull()?.let {
            add("What ROI would ${it.property.name} get at \$2,400 a month?")
        }
        add("What if maintenance rose 20%?")
    }
}
