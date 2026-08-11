package com.portico.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.portico.android.domain.*
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme
import kotlin.math.abs

/*
 * Valuation compares three numbers that investors habitually conflate: what
 * was paid, what the market suggests, and what the owner believes. The estimate
 * is a transparent model — median price per square metre from the comparables
 * on file — and the screen says so, because an unexplained valuation is worth
 * nothing to someone deciding whether to sell.
 */
@Composable
fun ValuationScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val currency = store.profile.currency
    val semantic = PorticoTheme.semantic

    val property = store.propertyById(state.valuationPropertyId ?: state.selectedPropertyId)
        ?: store.properties.firstOrNull()

    if (property == null) {
        EmptyState(
            title = "Nothing to value",
            body = "Add a property first and Portico can compare it against the market.",
            glyph = Glyph.VALUATION,
            actionLabel = "Add a property",
            onAction = { state.resetDraft(); state.navigate(Route.ADD_PROPERTY) },
            modifier = modifier
        )
        return
    }

    val result = store.financialsFor(property.id)!!
    val comparables = Seed.comparables.filter {
        it.region.substringBefore(" ·") == property.region.substringBefore(" ·")
    }.ifEmpty { Seed.comparables }

    val medianPerSqm = comparables.map { it.pricePerSqm }.sorted().let { sorted ->
        if (sorted.isEmpty()) 0.0
        else if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
    }
    val estimatedValue = medianPerSqm * property.sizeSqm
    val estimatedRent = result.monthlyGrossIncome.takeIf { it > 0 } ?: (estimatedValue * 0.006)
    val estimatedYield = if (estimatedValue > 0) estimatedRent * 12 / estimatedValue * 100 else 0.0

    val peak = maxOf(property.purchasePrice, property.currentValue, estimatedValue).takeIf { it > 0 } ?: 1.0

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        Column(Modifier.padding(horizontal = Space.lg)) {
            SectionLabel(property.location)
            Spacer(Modifier.height(Space.xs))
            Text(property.name, style = MaterialTheme.typography.titleLarge)
        }

        if (store.properties.size > 1) {
            SegmentedRow(
                options = store.properties.map { it.name },
                selected = property.name
            ) { name ->
                state.valuationPropertyId = store.properties.first { it.name == name }.id
            }
        }

        // ---- the three numbers ------------------------------------------------
        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Estimated value", supporting = "Median price per m² from ${comparables.size} comparables")
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
                Text(Money.format(estimatedValue, currency), style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(Space.xs))
                DeltaLine(
                    delta = estimatedValue - property.currentValue,
                    text = "${Money.signed(estimatedValue - property.currentValue, currency)} against your recorded value"
                )
            }
            Spacer(Modifier.height(Space.md))

            ValuationBar("Purchase price", property.purchasePrice, peak, currency, semantic.neutral)
            ValuationBar("Your recorded value", property.currentValue, peak, currency, MaterialTheme.colorScheme.primary)
            ValuationBar("Comparable estimate", estimatedValue, peak, currency, semantic.gain)
            Spacer(Modifier.height(Space.md))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("If you let it at market")
            DataRow("Estimated monthly rent", Money.format(estimatedRent, currency))
            Hairline()
            DataRow("Gross rental yield", Money.percent(estimatedYield))
            Hairline()
            DataRow("Your current net yield", Money.percent(result.netYield), emphasise = true)
            Hairline()
            DataRow("Price per m²", Money.format(
                if (property.sizeSqm > 0) property.currentValue / property.sizeSqm else 0.0, currency
            ))
            Hairline()
            DataRow("Market median per m²", Money.format(medianPerSqm, currency))
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Comparable properties", supporting = "Nearby, similar size and type")
            comparables.forEachIndexed { index, comparable ->
                if (index > 0) Hairline()
                DataRow(
                    label = comparable.address,
                    value = Money.format(comparable.askingPrice, currency),
                    supporting = "${comparable.sizeSqm.toInt()} m² · ${Money.format(comparable.pricePerSqm, currency)}/m² · ${"%.1f".format(comparable.distanceKm)} km"
                )
            }
        }

        Panel(Modifier.padding(horizontal = Space.lg)) {
            PanelHeader("Market signals", supporting = "Trailing twelve months")
            Seed.marketSignals
                .filter { it.region.substringBefore(" ·") == property.region.substringBefore(" ·") }
                .ifEmpty { Seed.marketSignals.take(2) }
                .forEachIndexed { index, signal ->
                    if (index > 0) Hairline()
                    DataRow(
                        label = signal.metric,
                        value = Money.format(signal.value, currency),
                        supporting = signal.region,
                        trailing = {
                            DeltaLine(
                                delta = signal.changePct,
                                text = Money.signedPercent(signal.changePct),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
        }

        Box(Modifier.padding(horizontal = Space.lg)) {
            PrimaryButton(
                "Use estimate as current value",
                Modifier.fillMaxWidth(),
                glyph = Glyph.CHECK
            ) {
                store.updateValuation(property.id, estimatedValue, "Comparable model")
                state.notify("Valuation updated to comparable estimate")
            }
        }

        SyntheticNote(
            "Comparables and market signals are illustrative sample data, not a live feed. " +
                "The estimate is a median price-per-m² model, shown so you can judge it."
        )
    }
}

@Composable
private fun ValuationBar(
    label: String,
    value: Double,
    peak: Double,
    currency: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                Money.format(value, currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PorticoTheme.semantic.panelSunk)
        ) {
            Box(
                Modifier
                    .fillMaxWidth((value / peak).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

// --------------------------------------------------------------- acquisition

private val acquisitionSteps = listOf("Search", "Property", "Comparables", "Analysis", "Decision")

/*
 * Acquisition runs the same engine against a property the investor does not
 * own yet, which is the point: the number that decides a purchase should be
 * produced the same way as the number that judges a holding.
 */
@Composable
fun AcquisitionScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val currency = store.profile.currency
    val semantic = PorticoTheme.semantic
    val step = state.acquisitionStep.coerceIn(0, acquisitionSteps.lastIndex)

    var query by remember { mutableStateOf("") }
    val listings = Seed.listings.filter {
        query.isBlank() || it.address.contains(query, true) || it.region.contains(query, true)
    }
    val listing = Seed.listings.firstOrNull { it.id == state.acquisitionListingId }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        SegmentedRow(acquisitionSteps, acquisitionSteps[step]) { label ->
            val target = acquisitionSteps.indexOf(label)
            if (target == 0 || state.acquisitionListingId != null) state.acquisitionStep = target
        }

        when (step) {
            0 -> {
                Column(Modifier.padding(horizontal = Space.lg)) {
                    PorticoField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Search listings",
                        placeholder = "Region, street or city"
                    )
                }
                Panel(Modifier.padding(horizontal = Space.lg)) {
                    PanelHeader("Available listings", supporting = "${listings.size} on file")
                    if (listings.isEmpty()) {
                        EmptyState(
                            title = "No listings match",
                            body = "Try a different region or clear the search.",
                            glyph = Glyph.SEARCH,
                            actionLabel = "Clear search",
                            onAction = { query = "" }
                        )
                    } else {
                        listings.forEachIndexed { index, item ->
                            if (index > 0) Hairline()
                            DataRow(
                                label = item.address,
                                value = Money.format(item.askingPrice, currency),
                                supporting = "${item.region} · ${item.type} · ${item.sizeSqm.toInt()} m²",
                                onClick = {
                                    state.acquisitionListingId = item.id
                                    state.acquisitionRent = item.estimatedMonthlyRent.toInt().toString()
                                    state.acquisitionStep = 1
                                }
                            )
                        }
                    }
                }
                SyntheticNote("Listings are illustrative sample data. No property portal is connected.")
            }

            else -> {
                if (listing == null) {
                    EmptyState(
                        title = "Choose a listing first",
                        body = "Pick a property from the search step to analyse it.",
                        glyph = Glyph.ACQUISITION,
                        actionLabel = "Back to search",
                        onAction = { state.acquisitionStep = 0 }
                    )
                    return@Column
                }

                val rent = state.acquisitionRent.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
                val candidate = Property(
                    id = "candidate",
                    name = listing.address,
                    address = listing.address,
                    country = listing.country,
                    region = listing.region,
                    type = listing.type,
                    sizeSqm = listing.sizeSqm,
                    purchaseDate = SimpleDate.today().format(),
                    purchasePrice = listing.askingPrice,
                    initialInvestment = listing.askingPrice,
                    currentValue = listing.askingPrice
                )
                // Assume running costs at a quarter of gross, the region's rough norm.
                val candidateAnalysis = Finance.analyse(
                    candidate,
                    listOf(IncomeEntry("c-i", "candidate", rent, IncomeCategory.RENT.label, candidate.purchaseDate)),
                    listOf(ExpenseEntry("c-e", "candidate", rent * 0.25, ExpenseCategory.OTHER.label, candidate.purchaseDate)),
                    store.taxProfile
                )
                val comparables = Seed.comparables.filter {
                    it.region.substringBefore(" ·") == listing.region.substringBefore(" ·")
                }.ifEmpty { Seed.comparables }
                val medianPerSqm = comparables.map { it.pricePerSqm }.average()
                val modelValue = medianPerSqm * listing.sizeSqm

                when (step) {
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                        Panel(Modifier.padding(horizontal = Space.lg)) {
                            PanelHeader("Listing")
                            DataRow("Address", listing.address)
                            Hairline()
                            DataRow("Region", listing.region)
                            Hairline()
                            DataRow("Type", listing.type)
                            Hairline()
                            DataRow("Size", "${listing.sizeSqm.toInt()} m²")
                            Hairline()
                            DataRow("Asking price", Money.format(listing.askingPrice, currency), emphasise = true)
                            Hairline()
                            DataRow("Price per m²", Money.format(listing.askingPrice / listing.sizeSqm, currency))
                        }
                        Box(Modifier.padding(horizontal = Space.lg)) {
                            CurrencyField(
                                value = state.acquisitionRent,
                                onValueChange = { state.acquisitionRent = it },
                                label = "Rent you expect to charge",
                                currency = currency,
                                supporting = "Listing suggests ${Money.format(listing.estimatedMonthlyRent, currency)}"
                            )
                        }
                    }

                    2 -> Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                        Panel(Modifier.padding(horizontal = Space.lg)) {
                            PanelHeader("Asking against comparables")
                            val peak = maxOf(listing.askingPrice, modelValue)
                            ValuationBar("Asking price", listing.askingPrice, peak, currency, MaterialTheme.colorScheme.primary)
                            ValuationBar("Comparable estimate", modelValue, peak, currency, semantic.gain)
                            Spacer(Modifier.height(Space.sm))
                            DataRow(
                                if (listing.askingPrice > modelValue) "Asking above comparables" else "Asking below comparables",
                                Money.signed(modelValue - listing.askingPrice, currency),
                                valueColor = semantic.forDelta(modelValue - listing.askingPrice)
                            )
                        }
                        Panel(Modifier.padding(horizontal = Space.lg)) {
                            PanelHeader("Comparables", supporting = "${comparables.size} nearby")
                            comparables.forEachIndexed { index, comparable ->
                                if (index > 0) Hairline()
                                DataRow(
                                    comparable.address,
                                    Money.format(comparable.askingPrice, currency),
                                    supporting = "${comparable.sizeSqm.toInt()} m² · ${Money.format(comparable.pricePerSqm, currency)}/m²"
                                )
                            }
                        }
                    }

                    3 -> Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                        Panel(Modifier.padding(horizontal = Space.lg)) {
                            PanelHeader("If you bought at asking", supporting = "Costs assumed at 25% of gross rent")
                            WaterfallLedger(
                                steps = candidateAnalysis.waterfall(),
                                currency = currency,
                                animate = !store.preferences.reducedMotion
                            )
                            Spacer(Modifier.height(Space.md))
                        }
                        Panel(Modifier.padding(horizontal = Space.lg)) {
                            PanelHeader("Return")
                            MetricGrid(
                                metrics = listOf(
                                    Metric("Cap rate", Money.percent(candidateAnalysis.capRate), "on asking price"),
                                    Metric("Gross yield", Money.percent(candidateAnalysis.grossYield), "before costs"),
                                    Metric("Net yield", Money.percent(candidateAnalysis.netYield), "after costs and tax"),
                                    rateMetric("Cash on cash", candidateAnalysis.cashOnCash)
                                ),
                                columns = 2
                            )
                        }
                    }

                    else -> {
                        val portfolio = store.portfolio()
                        val beatsPortfolio = candidateAnalysis.netYield >= portfolio.netYield
                        val underAsking = modelValue >= listing.askingPrice
                        val positiveCash = candidateAnalysis.monthlyCashflow >= 0

                        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                            Panel(Modifier.padding(horizontal = Space.lg), accent = true) {
                                Column(Modifier.padding(Space.lg)) {
                                    SectionLabel("On these assumptions")
                                    Spacer(Modifier.height(Space.xs))
                                    Text(
                                        when {
                                            beatsPortfolio && positiveCash && underAsking -> "Worth a closer look"
                                            positiveCash -> "Covers its costs, but doesn't beat what you hold"
                                            else -> "Would draw on your cash"
                                        },
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    Spacer(Modifier.height(Space.sm))
                                    Text(
                                        "At ${Money.format(listing.askingPrice, currency)} with rent of ${Money.format(rent, currency)}, " +
                                            "this returns ${Money.percent(candidateAnalysis.netYield)} net against your portfolio's ${Money.percent(portfolio.netYield)}, " +
                                            "and ${Money.format(candidateAnalysis.monthlyCashflow, currency)} a month.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = PorticoTheme.semantic.tertiaryText
                                    )
                                }
                            }

                            Panel(Modifier.padding(horizontal = Space.lg)) {
                                PanelHeader("Decision checks")
                                DecisionRow("Beats your portfolio net yield", beatsPortfolio)
                                Hairline()
                                DecisionRow("Positive monthly cashflow", positiveCash)
                                Hairline()
                                DecisionRow("Priced at or below comparables", underAsking)
                            }

                            Box(Modifier.padding(horizontal = Space.lg)) {
                                PrimaryButton("Add to portfolio", Modifier.fillMaxWidth(), glyph = Glyph.ADD) {
                                    if (!store.canAddProperty) {
                                        state.showPaywall = true
                                        return@PrimaryButton
                                    }
                                    state.resetDraft()
                                    state.updateDraft {
                                        it.copy(
                                            name = listing.address,
                                            address = listing.address,
                                            country = listing.country,
                                            region = listing.region,
                                            type = listing.type,
                                            sizeSqm = listing.sizeSqm.toInt().toString(),
                                            purchaseDate = SimpleDate.today().format(),
                                            purchasePrice = listing.askingPrice.toInt().toString(),
                                            initialInvestment = listing.askingPrice.toInt().toString(),
                                            monthlyRent = rent.toInt().toString()
                                        )
                                    }
                                    state.navigate(Route.ADD_PROPERTY)
                                }
                            }
                            SyntheticNote(
                                "A decision aid built from illustrative listings and your own tax assumptions. Not investment advice."
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.lg),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    SecondaryButton("Back", Modifier.weight(1f)) { state.acquisitionStep = (step - 1).coerceAtLeast(0) }
                    if (step < acquisitionSteps.lastIndex) {
                        PrimaryButton("Next", Modifier.weight(1f)) { state.acquisitionStep = step + 1 }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecisionRow(label: String, passes: Boolean) {
    val semantic = PorticoTheme.semantic
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PorticoIcon(
            if (passes) Glyph.CHECK else Glyph.CLOSE,
            size = 18.dp,
            tint = if (passes) semantic.gain else semantic.loss,
            contentDescription = if (passes) "Yes" else "No"
        )
        Spacer(Modifier.width(Space.md))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (passes) "Yes" else "No",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (passes) semantic.gain else semantic.loss
        )
    }
}
