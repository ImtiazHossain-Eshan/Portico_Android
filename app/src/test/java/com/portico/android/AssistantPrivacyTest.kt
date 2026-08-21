package com.portico.android

import com.portico.android.domain.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fact sheet is the only thing that leaves the device when cloud analysis
 * is enabled, so what it may and may not contain is a product promise rather
 * than an implementation detail.
 *
 * The privacy screen tells the member that addresses, documents and notes are
 * never sent. These tests are what make that sentence true a year from now,
 * when somebody adds a field to Property and reaches for the obvious
 * `appendLine(property)`.
 */
class AssistantPrivacyTest {

    private val taxProfile = TaxProfile(Jurisdiction.URUGUAY_MONTEVIDEO)
    private val rates = ExchangeRates()

    private val secretAddress = "17b Rua Confidencial, Apartment 9"
    private val secretNote = "Tenant is behind on rent, considering eviction"

    private val property = Property(
        id = "p1",
        name = "Harbor House",
        address = secretAddress,
        country = "Uruguay",
        region = "Montevideo",
        type = PropertyType.RESIDENTIAL.label,
        sizeSqm = 120.0,
        purchaseDate = "01 Jan 2021",
        purchasePrice = 300_000.0,
        initialInvestment = 90_000.0,
        currentValue = 380_000.0,
        currency = "USD",
        note = secretNote
    )

    private val income = listOf(
        IncomeEntry("i1", "p1", 2_000.0, IncomeCategory.RENT.label, "01 Jan 2026")
    )
    private val expenses = listOf(
        ExpenseEntry("e1", "p1", 300.0, ExpenseCategory.MAINTENANCE.label, "01 Jan 2026")
    )

    private fun sheet(): String {
        val results = Finance.analyseAll(listOf(property), income, expenses, taxProfile, rates, "USD")
        val portfolio = Finance.portfolio(results)
        return Analyst.factSheet(results, portfolio, taxProfile, "USD")
    }

    // --------------------------------------------------------------- excluded

    @Test
    fun `the street address never reaches the fact sheet`() {
        assertFalse(
            "a street address identifies a building and a person, and no question about yield needs one",
            sheet().contains(secretAddress)
        )
        assertFalse(sheet().contains("17b"))
        assertFalse(sheet().contains("Rua Confidencial"))
    }

    @Test
    fun `private notes never reach the fact sheet`() {
        assertFalse(
            "notes are free text and can contain anything, including third parties",
            sheet().contains(secretNote)
        )
        assertFalse(sheet().contains("eviction"))
    }

    @Test
    fun `no record identifiers leak`() {
        val text = sheet()
        assertFalse("internal ids are useless to the model and identify rows", text.contains("p1"))
        assertFalse(text.contains("i1"))
        assertFalse(text.contains("e1"))
    }

    // --------------------------------------------------------------- included

    @Test
    fun `the figures needed to answer a question are present`() {
        val text = sheet()
        assertTrue("the property must be nameable for comparisons", text.contains("Harbor House"))
        assertTrue(text.contains("Montevideo"))
        assertTrue(text.contains("Uruguay"))
        assertTrue("net yield is the headline the product is built around", text.contains("net yield"))
        assertTrue(text.contains("cap rate"))
        assertTrue(text.contains("Monthly cashflow"))
    }

    @Test
    fun `the sheet states its currency and jurisdiction`() {
        val text = sheet()
        assertTrue("without this the model invents a currency", text.contains("Currency: USD"))
        assertTrue(text.contains("Montevideo"))
    }

    @Test
    fun `an empty portfolio produces a sheet rather than an exception`() {
        val results = Finance.analyseAll(emptyList(), emptyList(), emptyList(), taxProfile, rates, "USD")
        val portfolio = Finance.portfolio(results)
        val text = Analyst.factSheet(results, portfolio, taxProfile, "USD")
        assertTrue(text.contains("PORTFOLIO (0 properties)"))
    }

    @Test
    fun `the sheet stays small enough for the endpoint to accept it`() {
        // The bridge rejects a context over 4,000 characters. Ten properties is
        // far past the Free plan and still has to fit.
        val many = (1..10).map { property.copy(id = "p$it", name = "Property $it") }
        val manyIncome = (1..10).map {
            IncomeEntry("i$it", "p$it", 2_000.0, IncomeCategory.RENT.label, "01 Jan 2026")
        }
        val results = Finance.analyseAll(many, manyIncome, emptyList(), taxProfile, rates, "USD")
        val portfolio = Finance.portfolio(results)
        val text = Analyst.factSheet(results, portfolio, taxProfile, "USD")
        assertTrue(
            "ten properties produced ${text.length} characters, over the 4,000 the bridge accepts",
            text.length < 4_000
        )
    }
}
