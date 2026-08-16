package com.portico.android.data

import android.content.Context
import android.net.Uri
import com.portico.android.domain.*

/*
 * Bulk property import — the mirror of PorticoExport.
 *
 * This needs no server: a register arrives as CSV, gets parsed and validated
 * row by row, and lands as real properties with their income and expense
 * lines. Rows that cannot be read are reported with their line number rather
 * than silently dropped, because a partial import you cannot audit is worse
 * than a failed one.
 *
 * The accepted columns are exactly the ones PorticoExport writes, so a
 * portfolio can round-trip out of one workspace and into another.
 */
object PorticoImport {

    data class RowError(val line: Int, val reason: String)

    data class Result(
        val imported: List<Property>,
        val income: List<IncomeEntry>,
        val expenses: List<ExpenseEntry>,
        val errors: List<RowError>,
        val skippedDuplicates: Int
    ) {
        val hasAnything: Boolean get() = imported.isNotEmpty()
        val summary: String
            get() = buildString {
                append("${imported.size} propert${if (imported.size == 1) "y" else "ies"}")
                if (skippedDuplicates > 0) append(", $skippedDuplicates duplicate skipped")
                if (errors.isNotEmpty()) append(", ${errors.size} row${if (errors.size == 1) "" else "s"} rejected")
            }
    }

    /** Column headers understood, matched case- and space-insensitively. */
    private const val NAME = "name"
    private const val ADDRESS = "address"
    private const val REGION = "region"
    private const val COUNTRY = "country"
    private const val TYPE = "type"
    private const val SIZE = "sizem2"
    private const val PURCHASE_DATE = "purchasedate"
    private const val PURCHASE_PRICE = "purchaseprice"
    private const val INVESTED = "cashinvested"
    private const val CURRENT_VALUE = "currentvalue"
    private const val ANNUAL_INCOME = "annualgrossincome"
    private const val ANNUAL_OPEX = "annualoperatingexpenses"

    val expectedHeader = listOf(
        "Name", "Address", "Region", "Country", "Type", "Size m2",
        "Purchase date", "Purchase price", "Cash invested", "Current value",
        "Annual gross income", "Annual operating expenses"
    ).joinToString(",")

    fun read(context: Context, uri: Uri, existing: List<Property>): Result? = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return@runCatching null
        parse(text, existing)
    }.getOrNull()

    fun parse(text: String, existing: List<Property>): Result {
        val lines = text.lines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        if (lines.isEmpty()) {
            return Result(emptyList(), emptyList(), emptyList(), listOf(RowError(0, "The file is empty.")), 0)
        }

        val headerCells = splitCsvLine(lines.first()).map { it.normalise() }
        if (!headerCells.contains(NAME)) {
            return Result(
                emptyList(), emptyList(), emptyList(),
                listOf(RowError(1, "No \"Name\" column found. Expected header: $expectedHeader")),
                0
            )
        }
        fun index(key: String) = headerCells.indexOf(key)

        val properties = mutableListOf<Property>()
        val income = mutableListOf<IncomeEntry>()
        val expenses = mutableListOf<ExpenseEntry>()
        val errors = mutableListOf<RowError>()
        var duplicates = 0

        val existingNames = existing.map { it.name.trim().lowercase() }.toMutableSet()

        lines.drop(1).forEachIndexed { offset, raw ->
            val lineNumber = offset + 2
            val cells = splitCsvLine(raw)
            fun cell(key: String): String {
                val i = index(key)
                return if (i in cells.indices) cells[i].trim().trim('"') else ""
            }
            fun number(key: String): Double =
                cell(key).replace(",", "").filter { it.isDigit() || it == '.' || it == '-' }.toDoubleOrNull() ?: 0.0

            val name = cell(NAME)
            if (name.isBlank()) {
                errors.add(RowError(lineNumber, "Missing property name."))
                return@forEachIndexed
            }
            if (!existingNames.add(name.trim().lowercase())) {
                duplicates++
                return@forEachIndexed
            }

            val purchasePrice = number(PURCHASE_PRICE)
            if (purchasePrice <= 0) {
                errors.add(RowError(lineNumber, "\"$name\" has no purchase price, so no return can be calculated."))
                return@forEachIndexed
            }

            val size = number(SIZE)
            val currentValue = number(CURRENT_VALUE).takeIf { it > 0 } ?: purchasePrice
            val invested = number(INVESTED).takeIf { it > 0 } ?: purchasePrice
            val id = PorticoStore.newId("p")

            properties.add(
                Property(
                    id = id,
                    name = name,
                    address = cell(ADDRESS),
                    country = cell(COUNTRY).ifBlank { "Uruguay" },
                    region = cell(REGION),
                    type = PropertyType.from(cell(TYPE)).label,
                    sizeSqm = size,
                    purchaseDate = cell(PURCHASE_DATE).ifBlank { SimpleDate.today().format() },
                    purchasePrice = purchasePrice,
                    initialInvestment = invested,
                    currentValue = currentValue,
                    note = "Imported"
                )
            )

            // Annual figures in the file become the monthly recurring lines the
            // rest of the app works from.
            val annualIncome = number(ANNUAL_INCOME)
            if (annualIncome > 0) {
                income.add(
                    IncomeEntry(
                        PorticoStore.newId("i"), id, annualIncome / 12,
                        IncomeCategory.RENT.label, SimpleDate.today().format(), "Imported"
                    )
                )
            }
            val annualOpex = number(ANNUAL_OPEX)
            if (annualOpex > 0) {
                expenses.add(
                    ExpenseEntry(
                        PorticoStore.newId("e"), id, annualOpex / 12,
                        ExpenseCategory.OTHER.label, SimpleDate.today().format(), "Imported"
                    )
                )
            }
        }

        return Result(properties, income, expenses, errors, duplicates)
    }

    private fun String.normalise() = trim().trim('"').lowercase().replace(" ", "").replace("_", "")

    /** Minimal RFC-4180 split: honours quoted fields containing commas. */
    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { cells.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        cells.add(current.toString())
        return cells
    }
}
