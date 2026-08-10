package com.portico.android.data

import com.portico.android.model.ActivityItem
import com.portico.android.model.ActivityTone
import com.portico.android.model.AllocationSlice
import com.portico.android.model.PortfolioDocument
import com.portico.android.model.Property

object DemoRepository {
    val properties = listOf(
        Property(
            id = 1,
            name = "Harbor House",
            location = "Montevideo · Pocitos",
            type = "Residential",
            purchasePrice = 380_000.0,
            currentValue = 468_500.0,
            purchaseDate = "14 Mar 2022",
            size = "184 m²",
            monthlyIncome = 3_150.0,
            monthlyExpenses = 1_020.0,
            roi = 23.3,
            capRate = 5.4,
            grossYield = 9.9,
            netYield = 6.7,
            accent = 0xFFE3744BL,
            note = "Stable residential cashflow"
        ),
        Property(
            id = 2,
            name = "Lakeside Duplex",
            location = "Canelones · Ciudad de la Costa",
            type = "Multi-family",
            purchasePrice = 290_000.0,
            currentValue = 331_200.0,
            purchaseDate = "02 Sep 2023",
            size = "236 m²",
            monthlyIncome = 2_480.0,
            monthlyExpenses = 740.0,
            roi = 14.2,
            capRate = 6.3,
            grossYield = 10.3,
            netYield = 7.2,
            accent = 0xFF3C8D87L,
            note = "Two-unit rental with upside"
        ),
        Property(
            id = 3,
            name = "Northline Studio",
            location = "Buenos Aires · Palermo",
            type = "Short stay",
            purchasePrice = 168_000.0,
            currentValue = 184_600.0,
            purchaseDate = "21 Jun 2024",
            size = "62 m²",
            monthlyIncome = 1_860.0,
            monthlyExpenses = 910.0,
            roi = 9.9,
            capRate = 6.8,
            grossYield = 13.3,
            netYield = 6.8,
            accent = 0xFF8E6EAEL,
            note = "Higher turnover, strong demand"
        )
    )

    val activities = listOf(
        ActivityItem("Rent received", "Harbor House · March", "+\$3,150", "Today", ActivityTone.Positive),
        ActivityItem("Expense added", "Lakeside Duplex · Maintenance", "−\$420", "Yesterday", ActivityTone.Warning),
        ActivityItem("Valuation updated", "Northline Studio · Market estimate", "+\$8,600", "18 Mar", ActivityTone.Positive),
        ActivityItem("Tax recorded", "Harbor House · Property tax", "−\$680", "14 Mar", ActivityTone.Neutral),
        ActivityItem("Document uploaded", "Lease agreement · Harbor House", "PDF", "12 Mar", ActivityTone.Neutral)
    )

    val documents = listOf(
        PortfolioDocument(1, "Harbor House lease 2026", "Harbor House", "Leases", "12 Mar 2026", "2.4 MB"),
        PortfolioDocument(2, "Annual property tax receipt", "Lakeside Duplex", "Taxes", "14 Mar 2026", "840 KB"),
        PortfolioDocument(3, "Purchase deed · Northline", "Northline Studio", "Deeds", "08 Feb 2026", "4.1 MB"),
        PortfolioDocument(4, "Insurance renewal", "Harbor House", "Other", "22 Jan 2026", "1.1 MB")
    )

    val allocation = listOf(
        AllocationSlice("Montevideo", 0.53f, 0xFFE3744BL),
        AllocationSlice("Canelones", 0.28f, 0xFF3C8D87L),
        AllocationSlice("Buenos Aires", 0.19f, 0xFF8E6EAEL)
    )
}
