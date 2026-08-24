package com.portico.android

import com.portico.android.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The finance engine is the product's whole claim, so it is the thing worth
 * testing. These lock the gross-to-net chain and the rates derived from it.
 */
class FinanceTest {

    private val montevideo = TaxProfile(Jurisdiction.URUGUAY_MONTEVIDEO)

    private fun property(
        price: Double = 400_000.0,
        invested: Double = 400_000.0,
        value: Double = 500_000.0
    ) = Property(
        id = "t1", name = "Test", address = "", country = "Uruguay", region = "Montevideo",
        type = PropertyType.RESIDENTIAL.label, sizeSqm = 100.0,
        purchaseDate = "01 Jan 2020", purchasePrice = price,
        initialInvestment = invested, currentValue = value
    )

    private fun income(amount: Double) = listOf(
        IncomeEntry("i1", "t1", amount, IncomeCategory.RENT.label, "01 Jan 2026")
    )

    private fun expense(amount: Double, category: ExpenseCategory = ExpenseCategory.MAINTENANCE) = listOf(
        ExpenseEntry("e1", "t1", amount, category.label, "01 Jan 2026")
    )

    @Test
    fun `gross to net subtracts operating expenses then tax`() {
        val result = Finance.analyse(property(), income(2_000.0), expense(500.0), montevideo)

        assertEquals(24_000.0, result.annualGrossIncome, 0.01)
        assertEquals(6_000.0, result.annualOperatingExpenses, 0.01)
        // Net must equal gross minus both deductions, exactly.
        assertEquals(
            result.annualGrossIncome - result.annualOperatingExpenses - result.annualTaxes,
            result.annualNetIncome,
            0.01
        )
    }

    @Test
    fun `waterfall rows reconcile to the net figure`() {
        val result = Finance.analyse(property(), income(2_000.0), expense(500.0), montevideo)
        val steps = result.waterfall()

        val opening = steps.first { it.kind == WaterfallKind.OPENING }.amount
        val deductions = steps.filter { it.kind == WaterfallKind.DEDUCTION }.sumOf { it.amount }
        val total = steps.first { it.kind == WaterfallKind.TOTAL }.amount

        // Deductions are stored negative, so opening + deductions == total.
        assertEquals(total, opening + deductions, 0.01)
    }

    @Test
    fun `cap rate ignores tax but net yield does not`() {
        val result = Finance.analyse(property(value = 500_000.0), income(2_000.0), expense(500.0), montevideo)

        // NOI / value
        assertEquals((24_000.0 - 6_000.0) / 500_000.0 * 100, result.capRate, 0.01)
        assertEquals(result.annualNetIncome / 500_000.0 * 100, result.netYield, 0.01)
        assertTrue("net yield must sit below cap rate once tax applies", result.netYield < result.capRate)
    }

    @Test
    fun `negative cashflow is reported, not clamped`() {
        // Costs deliberately exceed rent.
        val result = Finance.analyse(property(), income(500.0), expense(1_200.0), montevideo)

        assertTrue("monthly cashflow should be negative", result.monthlyCashflow < 0)
        assertTrue("annual net should be negative", result.annualNetIncome < 0)
        assertEquals(false, result.isCashflowPositive)
    }

    @Test
    fun `recorded tax replaces the jurisdiction assumption rather than stacking`() {
        val assumed = Finance.analyse(property(), income(2_000.0), expense(500.0), montevideo)
        val recorded = Finance.analyse(
            property(),
            income(2_000.0),
            expense(500.0) + ExpenseEntry("e2", "t1", 100.0, ExpenseCategory.PROPERTY_TAX.label, "01 Jan 2026"),
            montevideo
        )

        assertEquals(1_200.0, recorded.annualTaxes, 0.01)
        assertTrue("recorded tax should differ from the assumed figure", assumed.annualTaxes != recorded.annualTaxes)
    }

    @Test
    fun `roi is measured against cash invested, not purchase price`() {
        val leveraged = Finance.analyse(
            property(price = 400_000.0, invested = 100_000.0, value = 500_000.0),
            income(2_000.0), expense(500.0), montevideo
        )
        // 100k appreciation on 100k cash in.
        assertEquals(100.0, leveraged.capitalRoi, 0.01)
    }

    @Test
    fun `empty portfolio yields zero rather than NaN`() {
        val portfolio = Finance.portfolio(emptyList())
        assertEquals(0.0, portfolio.netYield, 0.001)
        assertEquals(0.0, portfolio.totalRoi, 0.001)
        assertTrue(portfolio.isEmpty)
    }

    @Test
    fun `portfolio rates are recomputed from totals, not averaged`() {
        val small = Finance.analyse(
            property(price = 100_000.0, invested = 100_000.0, value = 100_000.0),
            income(1_000.0), expense(100.0), montevideo
        )
        val large = Finance.analyse(
            property(price = 900_000.0, invested = 900_000.0, value = 900_000.0),
            income(3_000.0), expense(300.0), montevideo
        )
        val portfolio = Finance.portfolio(listOf(small, large))

        val expected = portfolio.annualNetIncome / portfolio.portfolioValue * 100
        assertEquals(expected, portfolio.netYield, 0.01)
        // A naive mean of the two would land elsewhere.
        val naiveMean = (small.netYield + large.netYield) / 2
        assertTrue("weighted result must differ from the naive mean", kotlin.math.abs(naiveMean - portfolio.netYield) > 0.01)
    }

    /**
     * The range selector used to vary only the sample count, which resamples an
     * identical curve: 1M and All reported the same low, high and change, and
     * nothing here noticed for the life of the project.
     */
    @Test
    fun `a chart range windows the series rather than resampling it`() {
        val subject = property(price = 400_000.0, value = 500_000.0)

        val month = Finance.valueSeries(subject, points = 12, windowMonths = 1)
        val everything = Finance.valueSeries(subject, points = 12, windowMonths = Int.MAX_VALUE)

        // Both windows end at today, so today's value is common to them.
        assertEquals(everything.last(), month.last(), 0.01)

        // The full history opens at the purchase price. One month opens just
        // short of today, because that is all the interval covers.
        assertEquals(400_000.0, everything.first(), 0.01)
        assertTrue(
            "a one month window should open near the current value",
            month.first() > 495_000.0
        )

        // The reported change is what a member reads off the chart.
        assertTrue(
            "a shorter range must report a smaller change",
            (month.last() - month.first()) < (everything.last() - everything.first())
        )
    }

    /** A property younger than the window is shown whole, never stretched. */
    @Test
    fun `a window longer than the holding period shows the full history`() {
        val subject = property(price = 400_000.0, value = 500_000.0)
        val fiveYears = Finance.valueSeries(subject, points = 12, windowMonths = 600)
        assertEquals(400_000.0, fiveYears.first(), 0.01)
        assertEquals(500_000.0, fiveYears.last(), 0.01)
    }
}
