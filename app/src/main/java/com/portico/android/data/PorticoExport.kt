package com.portico.android.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.portico.android.domain.*
import java.io.File

/*
 * Data export, for real.
 *
 * The blueprint asks for export twice: once under privacy (section 24) and
 * once as an enterprise operation (section 26). Reporting a record count in a
 * snackbar is not export, so this writes an actual file and hands it to the
 * share sheet. It runs entirely on-device: no backend, no storage permission,
 * nothing left behind in shared storage.
 */
object PorticoExport {

    /** Human-readable CSV of the financial position, one row per property. */
    fun buildCsv(store: PorticoStore): String {
        val currency = store.profile.currency
        val results = store.financials()
        val header = listOf(
            "Name", "Address", "Region", "Country", "Type", "Size m2",
            "Purchase date", "Purchase price", "Cash invested", "Current value",
            "Annual gross income", "Annual operating expenses", "Annual tax",
            "Annual net income", "Monthly cashflow",
            "Cap rate %", "Gross yield %", "Net yield %", "Total ROI %"
        ).joinToString(",")

        fun esc(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
        fun num(value: Double) = "%.2f".format(value)

        val rows = results.map { r ->
            val p = r.property
            listOf(
                esc(p.name), esc(p.address), esc(p.region), esc(p.country), esc(p.type),
                num(p.sizeSqm), esc(p.purchaseDate),
                num(p.purchasePrice), num(p.initialInvestment), num(p.currentValue),
                num(r.annualGrossIncome), num(r.annualOperatingExpenses), num(r.annualTaxes),
                num(r.annualNetIncome), num(r.monthlyCashflow),
                num(r.capRate), num(r.grossYield), num(r.netYield), num(r.totalRoi)
            ).joinToString(",")
        }

        val portfolio = store.portfolio()
        val summary = listOf(
            "",
            "# Portfolio summary (currency: $currency)",
            "Properties,${portfolio.propertyCount}",
            "Portfolio value,${num(portfolio.portfolioValue)}",
            "Invested capital,${num(portfolio.investedCapital)}",
            "Appreciation,${num(portfolio.appreciation)}",
            "Annual gross income,${num(portfolio.annualGrossIncome)}",
            "Annual operating expenses,${num(portfolio.annualOperatingExpenses)}",
            "Annual tax,${num(portfolio.annualTaxes)}",
            "Annual net income,${num(portfolio.annualNetIncome)}",
            "Net yield %,${num(portfolio.netYield)}",
            "Total ROI %,${num(portfolio.totalRoi)}",
            "Tax jurisdiction,${esc(store.taxProfile.jurisdiction.name)}",
            "",
            "# $TAX_DISCLAIMER"
        )

        return (listOf(header) + rows + summary).joinToString("\n")
    }

    /** Line-per-record detail so income and expenses survive the round trip. */
    fun buildLedgerCsv(store: PorticoStore): String {
        fun esc(value: String) = "\"" + value.replace("\"", "\"\"") + "\""
        val header = "Property,Kind,Category,Amount,Recurring,Date,Note"
        val rows = buildList {
            store.income.forEach { entry ->
                val name = store.propertyById(entry.propertyId)?.name ?: entry.propertyId
                add("${esc(name)},Income,${esc(entry.category)},${"%.2f".format(entry.amount)},${entry.recurring},${esc(entry.date)},${esc(entry.note)}")
            }
            store.expenses.forEach { entry ->
                val name = store.propertyById(entry.propertyId)?.name ?: entry.propertyId
                add("${esc(name)},Expense,${esc(entry.category)},${"%.2f".format(entry.amount)},${entry.recurring},${esc(entry.date)},${esc(entry.note)}")
            }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    /**
     * Writes both files and returns a share intent, or null if writing failed.
     * The caller reports the failure rather than pretending it worked.
     */
    fun shareIntent(context: Context, store: PorticoStore): Intent? = runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDate.today().format().replace(" ", "-")

        val summary = File(dir, "portico-portfolio-$stamp.csv")
        summary.writeText(buildCsv(store))

        val ledger = File(dir, "portico-ledger-$stamp.csv")
        ledger.writeText(buildLedgerCsv(store))

        val authority = "${context.packageName}.fileprovider"
        val uris = arrayListOf(
            FileProvider.getUriForFile(context, authority, summary),
            FileProvider.getUriForFile(context, authority, ledger)
        )

        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/csv"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            putExtra(Intent.EXTRA_SUBJECT, "Portico portfolio export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }.getOrNull()

    /** Row count quoted in the UI so the user knows what they are exporting. */
    fun recordCount(store: PorticoStore): Int =
        store.properties.size + store.income.size + store.expenses.size + store.documents.size
}
