package com.portico.android.domain

/*
 * Illustrative portfolio. Every figure here is an *input*: purchase price,
 * rent, an expense line. Never a result. ROI, yields, cap rate and cashflow
 * are computed from these by Finance.kt, which is why the seeded properties
 * and a property the user adds behave identically.
 *
 * The seed stays within the Free plan limit so first-run data and plan usage
 * always agree.
 */
object Seed {

    const val DEMO_USER_ID = "demo-user"

    val properties = listOf(
        Property(
            id = "p-harbor",
            name = "Harbor House",
            address = "Bulevar España 2340",
            country = "Uruguay",
            region = "Montevideo · Pocitos",
            type = PropertyType.RESIDENTIAL.label,
            sizeSqm = 184.0,
            purchaseDate = "14 Mar 2022",
            purchasePrice = 380_000.0,
            initialInvestment = 380_000.0,
            financingAmount = 0.0,
            currentValue = 468_500.0,
            note = "Long-term residential tenancy, renewed through 2027."
        ),
        Property(
            id = "p-lakeside",
            name = "Lakeside Duplex",
            address = "Av. de las Américas 4180",
            country = "Uruguay",
            region = "Canelones · Ciudad de la Costa",
            type = PropertyType.MULTI_FAMILY.label,
            sizeSqm = 236.0,
            purchaseDate = "02 Sep 2023",
            purchasePrice = 290_000.0,
            initialInvestment = 290_000.0,
            financingAmount = 0.0,
            currentValue = 331_200.0,
            note = "Two independent units; upper unit re-let in January."
        )
    )

    val income = listOf(
        IncomeEntry("i-1", "p-harbor", 3_150.0, IncomeCategory.RENT.label, "01 Mar 2026", "Monthly tenancy"),
        IncomeEntry("i-2", "p-lakeside", 1_340.0, IncomeCategory.RENT.label, "01 Mar 2026", "Unit A"),
        IncomeEntry("i-3", "p-lakeside", 1_140.0, IncomeCategory.RENT.label, "01 Mar 2026", "Unit B"),
        IncomeEntry("i-5", "p-harbor", 120.0, IncomeCategory.PARKING.label, "01 Mar 2026", "Garage space")
    )

    val expenses = listOf(
        // Harbor House: unleveraged, well managed.
        ExpenseEntry("e-1", "p-harbor", 420.0, ExpenseCategory.MAINTENANCE.label, "05 Mar 2026"),
        ExpenseEntry("e-2", "p-harbor", 145.0, ExpenseCategory.INSURANCE.label, "05 Mar 2026"),
        ExpenseEntry("e-3", "p-harbor", 190.0, ExpenseCategory.MANAGEMENT.label, "05 Mar 2026"),
        ExpenseEntry("e-4", "p-harbor", 265.0, ExpenseCategory.HOA.label, "05 Mar 2026"),

        // Lakeside: two units, modest running cost.
        ExpenseEntry("e-5", "p-lakeside", 310.0, ExpenseCategory.MAINTENANCE.label, "05 Mar 2026"),
        ExpenseEntry("e-6", "p-lakeside", 120.0, ExpenseCategory.INSURANCE.label, "05 Mar 2026"),
        ExpenseEntry("e-7", "p-lakeside", 150.0, ExpenseCategory.MANAGEMENT.label, "05 Mar 2026"),
        ExpenseEntry("e-8", "p-lakeside", 160.0, ExpenseCategory.UTILITIES.label, "05 Mar 2026")
    )

    val documents = listOf(
        PortfolioDocument("d-1", "p-harbor", "Harbor House lease 2026", "PDF", DocumentCategory.LEASES.label, "12 Mar 2026", 2_516_582),
        PortfolioDocument("d-2", "p-lakeside", "Contribución Inmobiliaria receipt", "PDF", DocumentCategory.TAXES.label, "14 Mar 2026", 860_160),
        PortfolioDocument("d-4", "p-harbor", "Building insurance renewal", "PDF", DocumentCategory.CONTRACTS.label, "22 Jan 2026", 1_153_434),
        PortfolioDocument("d-5", "p-lakeside", "Unit B tenancy agreement", "PDF", DocumentCategory.LEASES.label, "09 Jan 2026", 1_887_436)
    )

    val activity = listOf(
        ActivityEvent("a-1", ActivityKind.RENT_RECEIVED, "Rent received", "Harbor House · March", 3_150.0, "Today", "p-harbor"),
        ActivityEvent("a-2", ActivityKind.EXPENSE_ADDED, "Expense added", "Lakeside Duplex · Maintenance", -310.0, "Yesterday", "p-lakeside"),
        ActivityEvent("a-4", ActivityKind.TAX_RECORDED, "Tax recorded", "Harbor House · Contribución", -2_342.0, "14 Mar", "p-harbor"),
        ActivityEvent("a-5", ActivityKind.DOCUMENT_UPLOADED, "Document uploaded", "Lease agreement · Harbor House", null, "12 Mar", "p-harbor"),
        ActivityEvent("a-6", ActivityKind.RENT_RECEIVED, "Rent received", "Lakeside Duplex · Unit A", 1_340.0, "08 Mar", "p-lakeside")
    )

    val notifications = listOf(
        Notification("n-1", "Rent", "Rent posted", "Harbor House received $3,150 for March.", "Today"),
        Notification("n-2", "Tax", "Tax assumption changed", "Ingresos Brutos updated for Ciudad de Buenos Aires.", "2 days ago"),
        Notification("n-3", "Document", "Lease expiring", "Lakeside Duplex Unit B lease ends in 45 days.", "5 days ago", read = true)
    )

    val comparables = listOf(
        ComparableProperty("c-1", "Bulevar España 2210", "Montevideo · Pocitos", "Residential", 176.0, 452_000.0, 0.4),
        ComparableProperty("c-2", "Br. Artigas 1180", "Montevideo · Pocitos", "Residential", 195.0, 498_000.0, 1.1),
        ComparableProperty("c-3", "Av. Brasil 2740", "Montevideo · Pocitos", "Residential", 168.0, 441_500.0, 0.9),
        ComparableProperty("c-4", "Chaná 2050", "Montevideo · Cordón", "Residential", 181.0, 409_000.0, 2.6),
        ComparableProperty("c-5", "Road 11, Banani", "Dhaka · Banani", "Residential", 204.0, 351_000.0, 0.6),
        ComparableProperty("c-6", "Road 27, Dhanmondi", "Dhaka · Dhanmondi", "Residential", 186.0, 298_000.0, 1.4),
        ComparableProperty("c-7", "Gulshan Avenue", "Dhaka · Gulshan", "Residential", 232.0, 437_000.0, 1.1),
        ComparableProperty("c-8", "Bashundhara Block C", "Dhaka · Bashundhara", "Residential", 167.0, 214_000.0, 5.2)
    )

    val marketSignals = listOf(
        MarketSignal("Montevideo · Pocitos", "Median price / m²", 2_540.0, 4.2, "12 months"),
        MarketSignal("Montevideo · Pocitos", "Median rent / month", 1_180.0, 6.8, "12 months"),
        MarketSignal("Canelones · Costa", "Median price / m²", 1_680.0, 5.6, "12 months"),
        MarketSignal("Buenos Aires · Palermo", "Median price / m²", 2_890.0, -1.8, "12 months"),
        MarketSignal("Dhaka · Gulshan", "Median price / m²", 1_880.0, 7.4, "12 months"),
        MarketSignal("Dhaka · Gulshan", "Median rent / month", 1_240.0, 9.1, "12 months"),
        MarketSignal("Dhaka · Dhanmondi", "Median price / m²", 1_600.0, 5.9, "12 months"),
        MarketSignal("Chattogram · Khulshi", "Median price / m²", 1_140.0, 4.3, "12 months")
    )

    val listings = listOf(
        PropertyListing("l-1", "Rambla República del Perú 1420", "Montevideo · Pocitos", "Uruguay", "Residential", 142.0, 395_000.0, 2_450.0),
        PropertyListing("l-2", "Av. Italia 3890", "Montevideo · Malvín", "Uruguay", "Multi-family", 210.0, 318_000.0, 2_310.0),
        PropertyListing("l-3", "Solís 640", "Maldonado · Punta del Este", "Uruguay", "Short stay", 88.0, 268_000.0, 1_980.0),
        PropertyListing("l-4", "Thames 1870", "Buenos Aires · Palermo", "Argentina", "Residential", 96.0, 189_000.0, 1_240.0),
        PropertyListing("l-5", "Road 12, Banani", "Dhaka · Banani", "Bangladesh", "Residential", 195.0, 336_000.0, 1_450.0),
        PropertyListing("l-6", "Satmasjid Road, Dhanmondi", "Dhaka · Dhanmondi", "Bangladesh", "Multi-family", 240.0, 372_000.0, 1_920.0),
        PropertyListing("l-7", "Khulshi Hills", "Chattogram · Khulshi", "Bangladesh", "Residential", 178.0, 198_000.0, 940.0)
    )

    val organization = Organization("org-1", "Rambla Capital", 4, 2, "12 Jan 2025")

    val members = listOf(
        OrganizationMember("m-1", "org-1", "Imtiaz Hossain", "imtiaz@ramblacapital.uy", OrgRole.OWNER.name, "12 Jan 2025"),
        OrganizationMember("m-2", "org-1", "Sofía Márquez", "sofia@ramblacapital.uy", OrgRole.ADMIN.name, "03 Feb 2025"),
        OrganizationMember("m-3", "org-1", "Diego Ferrer", "diego@ramblacapital.uy", OrgRole.ANALYST.name, "21 Apr 2025"),
        OrganizationMember("m-4", "org-1", "Lucía Benítez", "lucia@ramblacapital.uy", OrgRole.VIEWER.name, "09 Sep 2025")
    )

    val auditLog = listOf(
        AuditLogEntry("al-1", "imtiaz@ramblacapital.uy", "Updated tax assumption", "TaxProfile · AR-CABA", "Today 09:41"),
        AuditLogEntry("al-2", "sofia@ramblacapital.uy", "Uploaded document", "Document · d-5", "Yesterday 16:02"),
        AuditLogEntry("al-3", "diego@ramblacapital.uy", "Viewed report", "Report · Gross vs net", "Yesterday 11:27"),
        AuditLogEntry("al-5", "lucia@ramblacapital.uy", "Sign-in", "Session", "18 Mar 08:15")
    )

    /** Starter prompts for the assistant, phrased the way an investor asks. */
    val suggestedQuestions = listOf(
        "Which property earns least after tax?",
        "How would a 5% vacancy rate affect my portfolio?",
        "How much of my income goes to tax?",
        "Compare Harbor House and Lakeside on net yield.",
        "What happens to cashflow if maintenance rises 20%?"
    )

    val conversations = emptyList<AiConversation>()
}
