package com.portico.android

import com.portico.android.domain.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The property that matters most here is invariance: converting a portfolio
 * into a different display currency must move every amount and leave every
 * *rate* untouched. A net yield that changes when you switch from dollars to
 * taka would be a bug that quietly invalidates the whole product.
 */
class ExchangeTest {

    private val rates = ExchangeRates()
    private val montevideo = TaxProfile(Jurisdiction.URUGUAY_MONTEVIDEO)

    private fun property(currency: String) = Property(
        id = "t1", name = "Test", address = "", country = "Bangladesh", region = "Dhaka",
        type = PropertyType.RESIDENTIAL.label, sizeSqm = 100.0,
        purchaseDate = "01 Jan 2020",
        purchasePrice = 100_000.0, initialInvestment = 100_000.0,
        currentValue = 120_000.0, currency = currency
    )

    private val income = listOf(IncomeEntry("i1", "t1", 1_000.0, IncomeCategory.RENT.label, "01 Jan 2026"))
    private val expenses = listOf(ExpenseEntry("e1", "t1", 200.0, ExpenseCategory.MAINTENANCE.label, "01 Jan 2026"))

    // ----------------------------------------------------------- conversion

    @Test
    fun `same currency is a no-op`() {
        assertEquals(500.0, rates.convert(500.0, "USD", "USD"), 0.001)
        assertEquals(500.0, rates.convert(500.0, "BDT", "BDT"), 0.001)
    }

    @Test
    fun `converting through the USD base works both directions`() {
        // 122 BDT per USD by default.
        assertEquals(12_200.0, rates.convert(100.0, "USD", "BDT"), 0.01)
        assertEquals(100.0, rates.convert(12_200.0, "BDT", "USD"), 0.01)
    }

    @Test
    fun `a round trip returns the original amount`() {
        val original = 987_654.0
        val there = rates.convert(original, "BDT", "EUR")
        val back = rates.convert(there, "EUR", "BDT")
        assertEquals(original, back, 0.01)
    }

    @Test
    fun `cross-currency conversion does not pass through a wrong base`() {
        // BDT -> ARS should equal BDT -> USD -> ARS.
        val direct = rates.convert(1_000.0, "BDT", "ARS")
        val viaUsd = rates.convert(rates.convert(1_000.0, "BDT", "USD"), "USD", "ARS")
        assertEquals(viaUsd, direct, 0.01)
    }

    @Test
    fun `an unknown currency falls back rather than zeroing the amount`() {
        assertEquals(250.0, rates.convert(250.0, "XYZ", "XYZ"), 0.001)
        assertTrue(rates.convert(250.0, "USD", "XYZ") > 0.0)
    }

    // ------------------------------------------------------------ invariance

    @Test
    fun `rates and yields are identical whichever currency is displayed`() {
        val inUsd = Finance.analyse(
            property("USD"), income, expenses, montevideo,
            SimpleDate.today(), rates, "USD"
        )
        val inBdt = Finance.analyse(
            property("USD"), income, expenses, montevideo,
            SimpleDate.today(), rates, "BDT"
        )

        assertEquals(inUsd.capRate, inBdt.capRate, 0.0001)
        assertEquals(inUsd.grossYield, inBdt.grossYield, 0.0001)
        assertEquals(inUsd.netYield, inBdt.netYield, 0.0001)
        assertEquals(inUsd.totalRoi, inBdt.totalRoi, 0.0001)
        assertEquals(inUsd.capitalRoi, inBdt.capitalRoi, 0.0001)
    }

    @Test
    fun `amounts do move when the display currency changes`() {
        val inUsd = Finance.analyse(
            property("USD"), income, expenses, montevideo,
            SimpleDate.today(), rates, "USD"
        )
        val inBdt = Finance.analyse(
            property("USD"), income, expenses, montevideo,
            SimpleDate.today(), rates, "BDT"
        )

        assertNotEquals(inUsd.annualGrossIncome, inBdt.annualGrossIncome, 1.0)
        assertEquals(inUsd.annualGrossIncome * 122.0, inBdt.annualGrossIncome, 1.0)
        assertEquals("BDT", inBdt.property.currency)
    }

    @Test
    fun `a property held in another currency reports the display currency`() {
        val result = Finance.analyse(
            property("BDT"), income, expenses, montevideo,
            SimpleDate.today(), rates, "USD"
        )
        // 120,000 BDT at 122 per USD.
        assertEquals(120_000.0 / 122.0, result.property.currentValue, 0.01)
        assertEquals("USD", result.property.currency)
    }

    // ------------------------------------------------------------ aggregation

    @Test
    fun `a mixed-currency portfolio converts before summing`() {
        val usdProperty = property("USD").copy(id = "usd")
        val bdtProperty = property("BDT").copy(id = "bdt")
        val allIncome = listOf(
            IncomeEntry("i-usd", "usd", 1_000.0, IncomeCategory.RENT.label, "01 Jan 2026"),
            IncomeEntry("i-bdt", "bdt", 122_000.0, IncomeCategory.RENT.label, "01 Jan 2026")
        )

        val results = Finance.analyseAll(
            listOf(usdProperty, bdtProperty), allIncome, emptyList(),
            montevideo, rates, "USD"
        )
        val portfolio = Finance.portfolio(results)

        // 122,000 BDT is 1,000 USD, so both contribute the same gross.
        assertEquals(2_000.0 * 12, portfolio.annualGrossIncome, 1.0)
        // 120,000 USD plus 120,000 BDT (≈ 983 USD).
        assertEquals(120_000.0 + 120_000.0 / 122.0, portfolio.portfolioValue, 1.0)
    }

    @Test
    fun `allocation shares convert before comparing`() {
        val usdProperty = property("USD").copy(id = "usd", country = "Uruguay")
        val bdtProperty = property("BDT").copy(id = "bdt", country = "Bangladesh")

        val shares = Finance.allocation(listOf(usdProperty, bdtProperty), rates, "USD") { it.country }
        val uruguay = shares.first { it.first == "Uruguay" }.second

        // Without conversion the BDT figure would dwarf the USD one; with it,
        // Uruguay should hold the overwhelming majority.
        assertTrue("Uruguay should dominate once converted, was $uruguay", uruguay > 0.98)
        assertEquals(1.0, shares.sumOf { it.second }, 0.001)
    }

    // ----------------------------------------------------------- user edits

    @Test
    fun `editing a rate moves every converted figure`() {
        val edited = rates.withRate("BDT", 150.0, "01 Jan 2026")
        assertEquals(15_000.0, edited.convert(100.0, "USD", "BDT"), 0.01)
        assertTrue(edited.isEdited)
        assertEquals("01 Jan 2026", edited.updated)
    }

    @Test
    fun `reset restores the defaults`() {
        val edited = rates.withRate("BDT", 150.0, "01 Jan 2026")
        assertEquals(ExchangeRates.defaults, edited.reset().perUsd)
        assertTrue(!edited.reset().isEdited)
    }

    @Test
    fun `a rate can never be set to zero and divide by nothing`() {
        val edited = rates.withRate("BDT", 0.0, "01 Jan 2026")
        assertTrue(edited.rate("BDT") > 0.0)
        assertTrue(edited.convert(100.0, "BDT", "USD").isFinite())
    }
}
