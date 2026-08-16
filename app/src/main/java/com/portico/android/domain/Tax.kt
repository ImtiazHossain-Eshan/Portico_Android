package com.portico.android.domain

import kotlinx.serialization.Serializable

/*
 * Tax in Portico is a set of *assumptions the user owns*, not a ruling.
 *
 * Every rate below ships as an editable default carrying its own note, and the
 * UI states plainly that these are modelling assumptions rather than tax
 * advice. That framing is what lets the product be useful across countries
 * without pretending to be a filing tool.
 *
 * Adding a country means adding a [Country] with its jurisdictions and rules.
 * Nothing in the property model changes, which is the extensibility the
 * blueprint asks for in section 31.
 */

const val TAX_DISCLAIMER =
    "These are editable modelling assumptions, not tax advice. Confirm rates with a qualified adviser in your jurisdiction."

object Jurisdiction {
    const val URUGUAY_MONTEVIDEO = "UY-MVD"
    const val URUGUAY_CANELONES = "UY-CAN"
    const val URUGUAY_MALDONADO = "UY-MAL"
    const val ARGENTINA_CABA = "AR-CABA"
    const val ARGENTINA_BUENOS_AIRES = "AR-BA"
    const val ARGENTINA_CORDOBA = "AR-CBA"
    const val BANGLADESH_DHAKA_NORTH = "BD-DNCC"
    const val BANGLADESH_DHAKA_SOUTH = "BD-DSCC"
    const val BANGLADESH_CHATTOGRAM = "BD-CCC"
}

/** What a rate is charged against. Determines which figure it multiplies. */
enum class TaxBasis(val label: String) {
    GROSS_INCOME("gross rental income"),
    NET_OPERATING_INCOME("net operating income"),
    PROPERTY_VALUE("property value"),
    CAPITAL_GAIN("capital gain on sale")
}

enum class TaxCategory(val label: String) {
    INCOME_TAX("Income tax"),
    PROPERTY_TAX("Property tax"),
    LOCAL_TAX("Local tax"),
    FEE("Fee"),
    CAPITAL_GAINS("Capital gains")
}

@Serializable
data class TaxRule(
    val id: String,
    val label: String,
    val ratePct: Double,
    val basisName: String,
    val categoryName: String,
    val note: String
) {
    val basis: TaxBasis get() = runCatching { TaxBasis.valueOf(basisName) }.getOrDefault(TaxBasis.GROSS_INCOME)
    val category: TaxCategory get() = runCatching { TaxCategory.valueOf(categoryName) }.getOrDefault(TaxCategory.INCOME_TAX)

    /** Recurring rules are the ones that hit every year's net income. */
    val isRecurring: Boolean get() = basis != TaxBasis.CAPITAL_GAIN
}

@Serializable
data class TaxJurisdiction(
    val id: String,
    val name: String,
    val countryCode: String,
    val countryName: String,
    val currency: String,
    val rules: List<TaxRule>
)

/**
 * The active jurisdiction plus any rate the user has overridden.
 * Overrides are keyed by rule id so a jurisdiction change keeps them separable.
 */
@Serializable
data class TaxProfile(
    val jurisdictionId: String = Jurisdiction.URUGUAY_MONTEVIDEO,
    val overrides: Map<String, Double> = emptyMap()
) {
    val jurisdiction: TaxJurisdiction
        get() = TaxCatalog.byId(jurisdictionId)

    /** Rules with user overrides applied. */
    fun effectiveRules(): List<TaxRule> =
        jurisdiction.rules.map { rule ->
            overrides[rule.id]?.let { rule.copy(ratePct = it) } ?: rule
        }

    fun rate(ruleId: String): Double =
        overrides[ruleId] ?: jurisdiction.rules.firstOrNull { it.id == ruleId }?.ratePct ?: 0.0

    fun withOverride(ruleId: String, ratePct: Double): TaxProfile =
        copy(overrides = overrides + (ruleId to ratePct.coerceIn(0.0, 100.0)))

    fun clearOverrides(): TaxProfile = copy(overrides = emptyMap())

    val hasOverrides: Boolean get() = overrides.isNotEmpty()

    /** One line per recurring rule, showing what it costs on these figures. */
    fun breakdown(
        grossAnnualIncome: Double,
        operatingExpenses: Double,
        propertyValue: Double
    ): List<TaxLine> = effectiveRules()
        .filter { it.isRecurring }
        .map { rule ->
            val base = when (rule.basis) {
                TaxBasis.GROSS_INCOME -> grossAnnualIncome
                TaxBasis.NET_OPERATING_INCOME -> (grossAnnualIncome - operatingExpenses).coerceAtLeast(0.0)
                TaxBasis.PROPERTY_VALUE -> propertyValue
                TaxBasis.CAPITAL_GAIN -> 0.0
            }
            TaxLine(rule, base, base * rule.ratePct / 100.0)
        }

    fun annualTaxFor(
        grossAnnualIncome: Double,
        operatingExpenses: Double,
        propertyValue: Double
    ): Double = breakdown(grossAnnualIncome, operatingExpenses, propertyValue).sumOf { it.amount }

    /** Applied only when the user models a sale, never in the annual chain. */
    fun capitalGainsOn(gain: Double): Double {
        if (gain <= 0.0) return 0.0
        return effectiveRules()
            .filter { it.basis == TaxBasis.CAPITAL_GAIN }
            .sumOf { gain * it.ratePct / 100.0 }
    }
}

data class TaxLine(val rule: TaxRule, val base: Double, val amount: Double)

object TaxCatalog {

    private fun rule(
        id: String,
        label: String,
        rate: Double,
        basis: TaxBasis,
        category: TaxCategory,
        note: String
    ) = TaxRule(id, label, rate, basis.name, category.name, note)

    val uruguayMontevideo = TaxJurisdiction(
        id = Jurisdiction.URUGUAY_MONTEVIDEO,
        name = "Montevideo",
        countryCode = "UY",
        countryName = "Uruguay",
        currency = "UYU",
        rules = listOf(
            rule(
                "uy-irpf", "Rental income tax (IRPF Cat. I)", 10.5,
                TaxBasis.GROSS_INCOME, TaxCategory.INCOME_TAX,
                "Applied to gross rent. Uruguay allows deductions that vary by situation, so this is modelled as an effective rate."
            ),
            rule(
                "uy-contribucion", "Contribución Inmobiliaria", 0.50,
                TaxBasis.PROPERTY_VALUE, TaxCategory.PROPERTY_TAX,
                "Municipal property tax, assessed on cadastral value. Portico models it against current value."
            ),
            rule(
                "uy-primaria", "Impuesto de Primaria", 0.15,
                TaxBasis.PROPERTY_VALUE, TaxCategory.LOCAL_TAX,
                "Primary education levy on property, charged annually."
            ),
            rule(
                "uy-capital-gains", "Capital gains on sale", 12.0,
                TaxBasis.CAPITAL_GAIN, TaxCategory.CAPITAL_GAINS,
                "Charged on disposal only. Excluded from annual net income."
            )
        )
    )

    val uruguayCanelones = uruguayMontevideo.copy(
        id = Jurisdiction.URUGUAY_CANELONES,
        name = "Canelones",
        rules = uruguayMontevideo.rules.map {
            if (it.id == "uy-contribucion") it.copy(ratePct = 0.42) else it
        }
    )

    val uruguayMaldonado = uruguayMontevideo.copy(
        id = Jurisdiction.URUGUAY_MALDONADO,
        name = "Maldonado",
        rules = uruguayMontevideo.rules.map {
            if (it.id == "uy-contribucion") it.copy(ratePct = 0.60) else it
        }
    )

    val argentinaCaba = TaxJurisdiction(
        id = Jurisdiction.ARGENTINA_CABA,
        name = "Ciudad de Buenos Aires",
        countryCode = "AR",
        countryName = "Argentina",
        currency = "ARS",
        rules = listOf(
            rule(
                "ar-ganancias", "Income tax (Ganancias)", 21.0,
                TaxBasis.NET_OPERATING_INCOME, TaxCategory.INCOME_TAX,
                "Progressive in law; modelled here as an effective rate on net operating income."
            ),
            rule(
                "ar-ingresos-brutos", "Ingresos Brutos", 1.50,
                TaxBasis.GROSS_INCOME, TaxCategory.LOCAL_TAX,
                "Provincial turnover tax on rental receipts. Rate varies by province and activity."
            ),
            rule(
                "ar-abl", "Inmobiliario / ABL", 0.60,
                TaxBasis.PROPERTY_VALUE, TaxCategory.PROPERTY_TAX,
                "Municipal property and services charge, assessed on fiscal valuation."
            ),
            rule(
                "ar-capital-gains", "Capital gains on sale", 15.0,
                TaxBasis.CAPITAL_GAIN, TaxCategory.CAPITAL_GAINS,
                "Charged on disposal only. Excluded from annual net income."
            )
        )
    )

    val argentinaBuenosAires = argentinaCaba.copy(
        id = Jurisdiction.ARGENTINA_BUENOS_AIRES,
        name = "Provincia de Buenos Aires",
        rules = argentinaCaba.rules.map {
            when (it.id) {
                "ar-ingresos-brutos" -> it.copy(ratePct = 3.50)
                "ar-abl" -> it.copy(ratePct = 0.75)
                else -> it
            }
        }
    )

    val argentinaCordoba = argentinaCaba.copy(
        id = Jurisdiction.ARGENTINA_CORDOBA,
        name = "Córdoba",
        rules = argentinaCaba.rules.map {
            when (it.id) {
                "ar-ingresos-brutos" -> it.copy(ratePct = 4.75)
                "ar-abl" -> it.copy(ratePct = 0.55)
                else -> it
            }
        }
    )

    /*
     * Bangladesh. Rental income is taxed as "income from house property":
     * a standard 25% repair-and-maintenance allowance comes off gross rent
     * first, then the balance is taxed at the individual's slab rate. Both
     * rates below are therefore expressed as effective rates on gross rent,
     * with the working written into each note so the assumption is auditable.
     */
    val bangladeshDhakaNorth = TaxJurisdiction(
        id = Jurisdiction.BANGLADESH_DHAKA_NORTH,
        name = "Dhaka North (DNCC)",
        countryCode = "BD",
        countryName = "Bangladesh",
        currency = "BDT",
        rules = listOf(
            rule(
                "bd-income-tax", "Income tax on house property", 11.25,
                TaxBasis.GROSS_INCOME, TaxCategory.INCOME_TAX,
                "Gross rent less the standard 25% repair allowance, taxed at a 15% slab: 0.75 × 15% ≈ 11.25% effective. Change this if your slab differs."
            ),
            rule(
                "bd-holding-tax", "Holding tax (City Corporation)", 9.0,
                TaxBasis.GROSS_INCOME, TaxCategory.PROPERTY_TAX,
                "City Corporation holding tax is 12% of annual value, and annual value is assessed after a 25% maintenance allowance: 0.75 × 12% = 9% of gross rent."
            ),
            rule(
                "bd-land-dev-tax", "Land development tax", 0.03,
                TaxBasis.PROPERTY_VALUE, TaxCategory.LOCAL_TAX,
                "Assessed per katha of land rather than on value; modelled here as a nominal share of property value."
            ),
            rule(
                "bd-capital-gains", "Capital gains on transfer", 15.0,
                TaxBasis.CAPITAL_GAIN, TaxCategory.CAPITAL_GAINS,
                "Individuals disposing of immovable property held over five years. Often collected at source on the deed value at registration. Excluded from annual net income."
            )
        )
    )

    val bangladeshDhakaSouth = bangladeshDhakaNorth.copy(
        id = Jurisdiction.BANGLADESH_DHAKA_SOUTH,
        name = "Dhaka South (DSCC)"
    )

    val bangladeshChattogram = bangladeshDhakaNorth.copy(
        id = Jurisdiction.BANGLADESH_CHATTOGRAM,
        name = "Chattogram (CCC)",
        rules = bangladeshDhakaNorth.rules.map {
            if (it.id == "bd-holding-tax") it.copy(
                ratePct = 8.25,
                note = "Chattogram City Corporation assesses at 11% of annual value: 0.75 × 11% = 8.25% of gross rent."
            ) else it
        }
    )

    val all = listOf(
        uruguayMontevideo, uruguayCanelones, uruguayMaldonado,
        argentinaCaba, argentinaBuenosAires, argentinaCordoba,
        bangladeshDhakaNorth, bangladeshDhakaSouth, bangladeshChattogram
    )

    /** Grouped for the country -> jurisdiction picker. */
    val byCountry: List<Pair<String, List<TaxJurisdiction>>>
        get() = all.groupBy { it.countryName }.toList()

    fun byId(id: String): TaxJurisdiction = all.firstOrNull { it.id == id } ?: uruguayMontevideo

    val countries = all.map { it.countryName }.distinct()
}
