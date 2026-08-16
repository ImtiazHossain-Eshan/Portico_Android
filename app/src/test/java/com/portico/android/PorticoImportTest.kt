package com.portico.android

import com.portico.android.data.PorticoImport
import com.portico.android.domain.Property
import com.portico.android.domain.PropertyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Import must never silently drop a row; anything rejected is reported. */
class PorticoImportTest {

    private val header =
        "Name,Address,Region,Country,Type,Size m2,Purchase date,Purchase price,Cash invested,Current value,Annual gross income,Annual operating expenses"

    private fun existing(name: String) = listOf(
        Property(
            id = "x", name = name, address = "", country = "Uruguay", region = "",
            type = PropertyType.RESIDENTIAL.label, sizeSqm = 10.0, purchaseDate = "01 Jan 2020",
            purchasePrice = 1.0, initialInvestment = 1.0, currentValue = 1.0
        )
    )

    @Test
    fun `valid rows import with monthly figures derived from annual`() {
        val csv = """
            $header
            "Rambla Tower","Rambla Wilson 1500","Montevideo","Uruguay","Residential",142,"09 Feb 2023",412000,150000,455000,31200,9600
        """.trimIndent()

        val result = PorticoImport.parse(csv, emptyList())

        assertEquals(1, result.imported.size)
        assertEquals("Rambla Tower", result.imported.first().name)
        assertEquals(150_000.0, result.imported.first().initialInvestment, 0.01)
        // Annual 31,200 becomes a monthly recurring line of 2,600.
        assertEquals(2_600.0, result.income.first().amount, 0.01)
        assertEquals(800.0, result.expenses.first().amount, 0.01)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `quoted commas inside a field do not split the row`() {
        val csv = """
            $header
            "Gorriti, Palermo","Gorriti 5100, Piso 3","Buenos Aires","Argentina","Short stay",62,"21 Jun 2024",168000,60000,184600,22320,18360
        """.trimIndent()

        val result = PorticoImport.parse(csv, emptyList())

        assertEquals(1, result.imported.size)
        assertEquals("Gorriti, Palermo", result.imported.first().name)
        assertEquals("Gorriti 5100, Piso 3", result.imported.first().address)
    }

    @Test
    fun `a row without a purchase price is rejected with its line number`() {
        val csv = """
            $header
            "Broken Row","No price","Somewhere","Uruguay","Residential",90,"01 Jan 2024",,,,,
        """.trimIndent()

        val result = PorticoImport.parse(csv, emptyList())

        assertTrue(result.imported.isEmpty())
        assertEquals(1, result.errors.size)
        assertEquals(2, result.errors.first().line)
        assertTrue(result.errors.first().reason.contains("purchase price"))
    }

    @Test
    fun `a property already in the register is skipped, not duplicated`() {
        val csv = """
            $header
            "Harbor House","Bulevar 2340","Montevideo","Uruguay","Residential",184,"14 Mar 2022",380000,380000,468500,39240,12240
        """.trimIndent()

        val result = PorticoImport.parse(csv, existing("Harbor House"))

        assertTrue(result.imported.isEmpty())
        assertEquals(1, result.skippedDuplicates)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `a file with no Name column fails with guidance instead of garbage`() {
        val result = PorticoImport.parse("Foo,Bar\n1,2", emptyList())

        assertTrue(result.imported.isEmpty())
        assertTrue(result.errors.first().reason.contains("Name"))
    }

    @Test
    fun `comment and blank lines are ignored`() {
        val csv = """
            $header

            # Portfolio summary
            "Cordon Loft","Colonia 1890","Montevideo","Uruguay","Short stay",68,"14 Nov 2024",178000,178000,191000,19800,7200
        """.trimIndent()

        val result = PorticoImport.parse(csv, emptyList())

        assertEquals(1, result.imported.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `columns are matched by header name, not position`() {
        val csv = """
            Purchase price,Name,Country
            412000,"Reordered","Uruguay"
        """.trimIndent()

        val result = PorticoImport.parse(csv, emptyList())

        assertEquals(1, result.imported.size)
        assertEquals("Reordered", result.imported.first().name)
        assertEquals(412_000.0, result.imported.first().purchasePrice, 0.01)
    }
}
