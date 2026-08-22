package com.portico.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tax engine, checked against arithmetic worked out by hand.
 *
 * Every figure below was computed independently of the code and written as a
 * literal, so a change to a rate has to be a deliberate change to a test rather
 * than something that silently re-baselines. The notes shipped with each rule
 * are checked too: a rule whose explanation says "0.75 x 12% = 9%" while
 * charging something else is worse than one with no explanation at all.
 */
class TaxTest {

    // A deliberately round set of figures so every expectation is checkable
    // in your head: 12,000 gross, 3,000 operating costs, 100,000 of value.
    private val gross = 12_000.0
    private val opex = 3_000.0
    private val value = 100_000.0

    private fun tax(id: String) = TaxProfile(id).annualTaxFor(gross, opex, value)

    // ------------------------------------------------------------- catalogue

    @Test
    fun `nine jurisdictions across three countries`() {
        assertEquals(9, TaxCatalog.all.size)
        assertEquals(listOf("Uruguay", "Argentina", "Bangladesh"), TaxCatalog.countries)
        assertEquals(3, TaxCatalog.all.count { it.countryCode == "UY" })
        assertEquals(3, TaxCatalog.all.count { it.countryCode == "AR" })
        assertEquals(3, TaxCatalog.all.count { it.countryCode == "BD" })
    }

    @Test
    fun `every jurisdiction is reachable by its own id`() {
        TaxCatalog.all.forEach { jurisdiction ->
            assertEquals(jurisdiction.id, TaxCatalog.byId(jurisdiction.id).id)
        }
    }

    @Test
    fun `an unknown jurisdiction falls back rather than crashing`() {
        assertEquals(Jurisdiction.URUGUAY_MONTEVIDEO, TaxCatalog.byId("XX-NOWHERE").id)
    }

    @Test
    fun `every rule carries a note and a sane rate`() {
        TaxCatalog.all.forEach { j ->
            j.rules.forEach { r ->
                assertTrue("${j.id}/${r.id} has no note", r.note.length > 20)
                assertTrue("${j.id}/${r.id} rate out of range", r.ratePct in 0.0..100.0)
            }
        }
    }

    // ------------------------------------------------------------ Bangladesh

    @Test
    fun `Dhaka North totals the three recurring rules`() {
        // income   12,000 x 11.25% = 1,350
        // holding  12,000 x  9.00% = 1,080
        // land dev 100,000 x 0.03% =    30
        //                            -----
        //                            2,460
        assertEquals(2_460.0, tax(Jurisdiction.BANGLADESH_DHAKA_NORTH), 0.01)
    }

    @Test
    fun `Dhaka South charges the same as Dhaka North`() {
        assertEquals(
            tax(Jurisdiction.BANGLADESH_DHAKA_NORTH),
            tax(Jurisdiction.BANGLADESH_DHAKA_SOUTH),
            0.01
        )
    }

    @Test
    fun `Chattogram charges less because its holding rate is lower`() {
        // holding 12,000 x 8.25% = 990, which is 90 less than Dhaka's 1,080.
        val dhaka = tax(Jurisdiction.BANGLADESH_DHAKA_NORTH)
        val ctg = tax(Jurisdiction.BANGLADESH_CHATTOGRAM)
        assertEquals(2_370.0, ctg, 0.01)
        assertEquals(90.0, dhaka - ctg, 0.01)
    }

    @Test
    fun `the Bangladesh notes state arithmetic that matches the rates charged`() {
        val dhaka = TaxCatalog.bangladeshDhakaNorth

        // "0.75 x 15% = 11.25%" -- the 25% repair allowance times a 15% slab.
        val income = dhaka.rules.first { it.id == "bd-income-tax" }
        assertEquals(0.75 * 15.0, income.ratePct, 0.001)

        // "0.75 x 12% = 9%" -- the same allowance against a 12% holding rate.
        val holding = dhaka.rules.first { it.id == "bd-holding-tax" }
        assertEquals(0.75 * 12.0, holding.ratePct, 0.001)

        // Chattogram's note claims 11%, not 12%.
        val ctgHolding = TaxCatalog.bangladeshChattogram.rules.first { it.id == "bd-holding-tax" }
        assertEquals(0.75 * 11.0, ctgHolding.ratePct, 0.001)
    }

    // --------------------------------------------------------------- Uruguay

    @Test
    fun `Montevideo mixes an income rate with two property rates`() {
        // IRPF        12,000 x 10.50% = 1,260
        // Contribucion 100,000 x 0.50% =  500
        // Primaria     100,000 x 0.15% =  150
        //                                -----
        //                                1,910
        assertEquals(1_910.0, tax(Jurisdiction.URUGUAY_MONTEVIDEO), 0.01)
    }

    @Test
    fun `the Uruguayan departments differ only in Contribucion`() {
        // Montevideo 0.50%, Canelones 0.42%, Maldonado 0.60% on 100,000.
        assertEquals(1_910.0, tax(Jurisdiction.URUGUAY_MONTEVIDEO), 0.01)
        assertEquals(1_830.0, tax(Jurisdiction.URUGUAY_CANELONES), 0.01)
        assertEquals(2_010.0, tax(Jurisdiction.URUGUAY_MALDONADO), 0.01)
    }

    // ------------------------------------------------------------- Argentina

    @Test
    fun `CABA taxes net operating income rather than gross`() {
        // Ganancias      (12,000 - 3,000) x 21.00% = 1,890
        // Ingresos Brutos       12,000    x  1.50% =   180
        // ABL                  100,000    x  0.60% =   600
        //                                            -----
        //                                            2,670
        assertEquals(2_670.0, tax(Jurisdiction.ARGENTINA_CABA), 0.01)
    }

    @Test
    fun `the Argentine provinces differ in turnover and property rates`() {
        // Buenos Aires: IIBB 3.50% -> 420, ABL 0.75% -> 750, Ganancias 1,890
        assertEquals(3_060.0, tax(Jurisdiction.ARGENTINA_BUENOS_AIRES), 0.01)
        // Cordoba: IIBB 4.75% -> 570, ABL 0.55% -> 550, Ganancias 1,890
        assertEquals(3_010.0, tax(Jurisdiction.ARGENTINA_CORDOBA), 0.01)
    }

    @Test
    fun `a net-operating-income rule never charges on a loss`() {
        // Costs above gross would make the base negative; it clamps at zero, so
        // only the gross and value rules are charged.
        val profile = TaxProfile(Jurisdiction.ARGENTINA_CABA)
        val onLoss = profile.annualTaxFor(
            grossAnnualIncome = 5_000.0,
            operatingExpenses = 9_000.0,
            propertyValue = value
        )
        // Ganancias 0, IIBB 5,000 x 1.5% = 75, ABL 600.
        assertEquals(675.0, onLoss, 0.01)
        assertTrue("tax must never be negative", onLoss >= 0.0)
    }

    // --------------------------------------------------------- capital gains

    @Test
    fun `capital gains stay out of the annual chain`() {
        TaxCatalog.all.forEach { j ->
            val recurring = TaxProfile(j.id).breakdown(gross, opex, value)
            assertFalse(
                "${j.id} let a capital-gains rule into the annual breakdown",
                recurring.any { it.rule.basis == TaxBasis.CAPITAL_GAIN }
            )
        }
    }

    @Test
    fun `capital gains are charged only on a real gain`() {
        val bd = TaxProfile(Jurisdiction.BANGLADESH_DHAKA_NORTH)
        assertEquals(15_000.0, bd.capitalGainsOn(100_000.0), 0.01)   // 15%
        assertEquals(0.0, bd.capitalGainsOn(0.0), 0.01)
        assertEquals(0.0, bd.capitalGainsOn(-50_000.0), 0.01)        // a loss is not a gain

        val uy = TaxProfile(Jurisdiction.URUGUAY_MONTEVIDEO)
        assertEquals(12_000.0, uy.capitalGainsOn(100_000.0), 0.01)   // 12%
    }

    // -------------------------------------------------------------- overrides

    @Test
    fun `an override replaces the shipped rate`() {
        val edited = TaxProfile(Jurisdiction.BANGLADESH_DHAKA_NORTH)
            .withOverride("bd-income-tax", 20.0)
        // income 12,000 x 20% = 2,400, holding 1,080, land dev 30.
        assertEquals(3_510.0, edited.annualTaxFor(gross, opex, value), 0.01)
        assertEquals(20.0, edited.rate("bd-income-tax"), 0.001)
        assertTrue(edited.hasOverrides)
    }

    @Test
    fun `an override cannot be set outside nought to a hundred percent`() {
        val profile = TaxProfile(Jurisdiction.BANGLADESH_DHAKA_NORTH)
        assertEquals(0.0, profile.withOverride("bd-income-tax", -40.0).rate("bd-income-tax"), 0.001)
        assertEquals(100.0, profile.withOverride("bd-income-tax", 900.0).rate("bd-income-tax"), 0.001)
    }

    @Test
    fun `clearing overrides restores every shipped rate`() {
        val edited = TaxProfile(Jurisdiction.URUGUAY_MONTEVIDEO)
            .withOverride("uy-irpf", 30.0)
            .withOverride("uy-contribucion", 5.0)
        assertTrue(edited.hasOverrides)

        val restored = edited.clearOverrides()
        assertFalse(restored.hasOverrides)
        assertEquals(1_910.0, restored.annualTaxFor(gross, opex, value), 0.01)
    }

    @Test
    fun `overrides survive a jurisdiction change without being applied to it`() {
        // Overrides are keyed by rule id, so a Uruguayan override is inert once
        // the profile points at Bangladesh, and comes back if you return.
        val edited = TaxProfile(Jurisdiction.URUGUAY_MONTEVIDEO).withOverride("uy-irpf", 30.0)
        val moved = edited.copy(jurisdictionId = Jurisdiction.BANGLADESH_DHAKA_NORTH)

        assertEquals(2_460.0, moved.annualTaxFor(gross, opex, value), 0.01)
        val back = moved.copy(jurisdictionId = Jurisdiction.URUGUAY_MONTEVIDEO)
        // IRPF at 30% = 3,600, plus 650 of property charges.
        assertEquals(4_250.0, back.annualTaxFor(gross, opex, value), 0.01)
    }

    // -------------------------------------------------------------- breakdown

    @Test
    fun `the breakdown sums to the total it reports`() {
        TaxCatalog.all.forEach { j ->
            val profile = TaxProfile(j.id)
            val lines = profile.breakdown(gross, opex, value)
            assertEquals(
                "${j.id} breakdown does not reconcile to its own total",
                profile.annualTaxFor(gross, opex, value),
                lines.sumOf { it.amount },
                0.0001
            )
        }
    }

    @Test
    fun `each line multiplies the base its rule names`() {
        val lines = TaxProfile(Jurisdiction.ARGENTINA_CABA).breakdown(gross, opex, value)
        lines.forEach { line ->
            val expectedBase = when (line.rule.basis) {
                TaxBasis.GROSS_INCOME -> gross
                TaxBasis.NET_OPERATING_INCOME -> gross - opex
                TaxBasis.PROPERTY_VALUE -> value
                TaxBasis.CAPITAL_GAIN -> 0.0
            }
            assertEquals(line.rule.id, expectedBase, line.base, 0.01)
            assertEquals(line.rule.id, line.base * line.rule.ratePct / 100.0, line.amount, 0.01)
        }
    }

    @Test
    fun `zero figures produce zero tax rather than an error`() {
        TaxCatalog.all.forEach { j ->
            assertEquals(j.id, 0.0, TaxProfile(j.id).annualTaxFor(0.0, 0.0, 0.0), 0.001)
        }
    }
}
