package com.portico.android.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The assistant's answers, checked against the figures it claims to be reading.
 *
 * The thing worth protecting here is not phrasing, which will change, but that
 * an answer is derived from the workspace rather than asserted. So these tests
 * check that the reply names the property the arithmetic actually points at,
 * that a question it cannot answer says so instead of guessing, and that every
 * answer carries the working it used.
 */
class AnalystTest {

    private val taxProfile = TaxProfile(Jurisdiction.BANGLADESH_DHAKA_NORTH)
    private val rates = ExchangeRates()

    // Strong performer: cheap to run, high rent.
    private val strong = Property(
        id = "strong", name = "Harbour Row", address = "1 Dock St",
        country = "Bangladesh", region = "Dhaka", type = PropertyType.RESIDENTIAL.label,
        sizeSqm = 100.0, purchaseDate = "01 Jan 2020",
        purchasePrice = 200_000.0, initialInvestment = 200_000.0,
        currentValue = 300_000.0, currency = "USD"
    )

    // Weak performer: expensive to run, thin rent, loses money monthly.
    private val weak = Property(
        id = "weak", name = "Quarry Lodge", address = "2 Hill Rd",
        country = "Bangladesh", region = "Chattogram", type = PropertyType.COMMERCIAL.label,
        sizeSqm = 100.0, purchaseDate = "01 Jan 2020",
        purchasePrice = 200_000.0, initialInvestment = 200_000.0,
        currentValue = 190_000.0, currency = "USD"
    )

    private val income = listOf(
        IncomeEntry("i1", "strong", 3_000.0, IncomeCategory.RENT.label, "01 Jan 2026"),
        IncomeEntry("i2", "weak", 400.0, IncomeCategory.RENT.label, "01 Jan 2026")
    )
    private val expenses = listOf(
        ExpenseEntry("e1", "strong", 200.0, ExpenseCategory.MAINTENANCE.label, "01 Jan 2026"),
        ExpenseEntry("e2", "weak", 900.0, ExpenseCategory.MAINTENANCE.label, "01 Jan 2026")
    )

    private val results =
        Finance.analyseAll(listOf(strong, weak), income, expenses, taxProfile, rates, "USD")
    private val portfolio = Finance.portfolio(results)

    private fun ask(question: String) =
        Analyst.answer(question, results, portfolio, taxProfile, "USD")

    // ------------------------------------------------------------- extremes

    @Test
    fun `the worst performer is the one the arithmetic says is worst`() {
        val actualWorst = results.minByOrNull { it.netYield }!!.property.name
        val reply = ask("which property is performing worst?")
        assertTrue(
            "expected the reply to name $actualWorst, got: ${reply.text}",
            reply.text.contains(actualWorst)
        )
        assertTrue(reply.understood)
    }

    @Test
    fun `the best performer is named and is not the worst one`() {
        val best = results.maxByOrNull { it.netYield }!!.property.name
        val worst = results.minByOrNull { it.netYield }!!.property.name
        assertNotEquals("the fixture must have a clear winner", best, worst)

        val reply = ask("which is my best property?")
        assertTrue("got: ${reply.text}", reply.text.contains(best))
    }

    // ------------------------------------------------------------------ tax

    @Test
    fun `the tax answer states the jurisdiction it assumed`() {
        val reply = ask("how much tax am I paying?")
        assertTrue(reply.understood)
        assertTrue(
            "an unattributed tax figure is not auditable: ${reply.text}",
            reply.text.contains(taxProfile.jurisdiction.name)
        )
    }

    @Test
    fun `the tax share is consistent with the computed totals`() {
        // Whatever wording it uses, the percentage quoted must be the real one.
        val share = portfolio.annualTaxes / portfolio.annualGrossIncome * 100.0
        val reply = ask("what share of my income goes to tax?")
        val quoted = Regex("([0-9]+\\.[0-9])%").findAll(reply.text)
            .map { it.groupValues[1].toDouble() }
            .toList()
        assertTrue("no percentage in: ${reply.text}", quoted.isNotEmpty())
        assertTrue(
            "quoted $quoted but the portfolio's tax share is ${"%.1f".format(share)}%",
            quoted.any { kotlin.math.abs(it - share) < 0.15 }
        )
    }

    // ------------------------------------------------------------- scenarios

    @Test
    fun `a rent scenario changes the answer from the current position`() {
        val current = ask("what is my monthly cashflow?")
        val scenario = ask("what if I rented it for \$9000 a month?")
        assertTrue(scenario.understood)
        assertNotEquals(
            "a what-if that returns today's figures has not modelled anything",
            current.text, scenario.text
        )
    }

    @Test
    fun `an expense scenario is understood as a scenario`() {
        val reply = ask("what if maintenance rose 20%?")
        assertTrue(reply.understood)
        assertTrue("expected the working to be shown", reply.workings.isNotEmpty())
    }

    // ------------------------------------------------------- honest failure

    @Test
    fun `a question outside the records is refused rather than guessed`() {
        val reply = ask("should I buy bitcoin instead?")
        assertFalse("this must not be reported as understood", reply.understood)
        assertTrue(
            "a refusal should say what it can do instead: ${reply.text}",
            reply.text.contains("value") || reply.text.contains("yield")
        )
        assertTrue("a refusal should still offer somewhere to go", reply.followUps.isNotEmpty())
    }

    @Test
    fun `an empty workspace says so instead of dividing by zero`() {
        val none = Finance.analyseAll(emptyList(), emptyList(), emptyList(), taxProfile, rates, "USD")
        val reply = Analyst.answer("what is my return?", none, Finance.portfolio(none), taxProfile, "USD")
        assertTrue(reply.understood)
        assertTrue("got: ${reply.text}", reply.text.contains("no properties", ignoreCase = true))
    }

    // ------------------------------------------------------------- coverage

    @Test
    fun `every documented capability is answered, not refused`() {
        val questions = listOf(
            "what is my portfolio worth?",
            "what is my total return?",
            "what is my net yield?",
            "what is my cap rate?",
            "what is my monthly cashflow?",
            "how much tax do I pay?",
            "how diversified am I?",
            "compare Harbour Row versus Quarry Lodge"
        )
        questions.forEach { q ->
            val reply = ask(q)
            assertTrue("refused a documented capability: \"$q\" -> ${reply.text}", reply.understood)
        }
    }

    @Test
    fun `answers show their arithmetic`() {
        listOf(
            "what is my net yield?",
            "how much tax do I pay?",
            "what is my monthly cashflow?"
        ).forEach { q ->
            assertTrue("\"$q\" answered with no working shown", ask(q).workings.isNotEmpty())
        }
    }

    @Test
    fun `the answer respects the display currency it is given`() {
        val inTaka = Analyst.answer(
            "what is my portfolio worth?", results, portfolio, taxProfile, "BDT"
        )
        assertTrue("expected a taka symbol in: ${inTaka.text}", inTaka.text.contains("৳"))
        assertFalse("a dollar sign leaked into a taka answer", inTaka.text.contains("$"))
    }
}
