package com.portico.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.portico.android.data.PorticoStore
import com.portico.android.domain.*
import com.portico.android.ui.CheckoutStage
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Checkout: plan → card → processing → outcome.
 *
 * Modelled on a real one down to the decline paths, because those are the
 * states a payment screen actually lives or dies on. Nothing is charged and no
 * provider is contacted; the banner says so on every step, and the card is held
 * in composition only; only the brand and last four are ever persisted.
 */
@Composable
fun CheckoutScreen(state: PorticoState, modifier: Modifier = Modifier) {
    val store = state.store
    val scope = rememberCoroutineScope()
    val semantic = PorticoTheme.semantic

    val plan = SubscriptionPlan.byId(state.checkoutPlanId) ?: SubscriptionPlan.PRO_MONTHLY
    var card by remember { mutableStateOf(CardInput()) }
    var validation by remember { mutableStateOf(CardValidation()) }
    var submitted by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<PaymentResult?>(null) }

    fun pay() {
        submitted = true
        val checked = SandboxProcessor.validate(card)
        validation = checked
        if (!checked.isValid) return

        state.checkoutStage = CheckoutStage.PROCESSING
        scope.launch {
            delay(500)
            runCatching { store.checkout(plan, card) }
                .onSuccess { result ->
                    outcome = result
                    state.checkoutStage = when (result) {
                        is PaymentResult.Succeeded -> CheckoutStage.SUCCESS
                        is PaymentResult.Declined -> CheckoutStage.DECLINED
                    }
                }
                .onFailure { error ->
                    outcome = PaymentResult.Declined(
                        payment = Payment(
                            id = PorticoStore.newId("pay"),
                            planId = plan.id,
                            amountMinor = plan.priceMinor,
                            currency = plan.currency,
                            status = PaymentStatus.FAILED.name,
                            date = SimpleDate.today().format(),
                            cardLast4 = card.last4,
                            cardBrand = card.brand,
                            failureReason = error.message
                        ),
                        reason = "Checkout unavailable",
                        recovery = error.message ?: "Nothing was charged. Check your connection and try again."
                    )
                    state.checkoutStage = CheckoutStage.DECLINED
                }
            card = CardInput()
        }
    }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        SandboxBanner()

        when (state.checkoutStage) {
            CheckoutStage.DETAILS -> {
                OrderSummary(plan)

                Panel(Modifier.padding(horizontal = Space.lg)) {
                    PanelHeader("Card details", supporting = "Test cards only, never a real card")
                    Column(
                        Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.md)
                    ) {
                        PorticoField(
                            value = card.formattedNumber(),
                            onValueChange = { raw ->
                                card = card.copy(number = raw.filter(Char::isDigit).take(19))
                                if (submitted) validation = SandboxProcessor.validate(card)
                            },
                            label = "Card number",
                            placeholder = "4242 4242 4242 4242",
                            keyboardType = KeyboardType.Number,
                            error = if (submitted) validation.numberError else null,
                            supporting = if (card.digits.length >= 2) card.brand else null
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                            PorticoField(
                                value = card.expiry,
                                onValueChange = { raw ->
                                    val digits = raw.filter(Char::isDigit).take(4)
                                    val shown = if (digits.length > 2) "${digits.take(2)}/${digits.drop(2)}" else digits
                                    card = card.copy(expiry = shown)
                                    if (submitted) validation = SandboxProcessor.validate(card)
                                },
                                label = "Expiry",
                                placeholder = "MM/YY",
                                keyboardType = KeyboardType.Number,
                                error = if (submitted) validation.expiryError else null,
                                modifier = Modifier.weight(1f)
                            )
                            PorticoField(
                                value = card.cvc,
                                onValueChange = { raw ->
                                    card = card.copy(cvc = raw.filter(Char::isDigit).take(4))
                                    if (submitted) validation = SandboxProcessor.validate(card)
                                },
                                label = "CVC",
                                placeholder = "123",
                                keyboardType = KeyboardType.Number,
                                isPassword = true,
                                error = if (submitted) validation.cvcError else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        PorticoField(
                            value = card.name,
                            onValueChange = {
                                card = card.copy(name = it)
                                if (submitted) validation = SandboxProcessor.validate(card)
                            },
                            label = "Name on card",
                            error = if (submitted) validation.nameError else null
                        )
                    }
                    Spacer(Modifier.height(Space.sm))
                }

                TestCardTable { number ->
                    card = card.copy(
                        number = number.filter(Char::isDigit),
                        expiry = card.expiry.ifBlank { "12/34" },
                        cvc = card.cvc.ifBlank { "123" },
                        name = card.name.ifBlank { store.profile.name.ifBlank { "Test Cardholder" } }
                    )
                    if (submitted) validation = SandboxProcessor.validate(card)
                }

                Column(Modifier.padding(horizontal = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    PrimaryButton(
                        "Pay ${plan.displayPrice} (sandbox)",
                        Modifier.fillMaxWidth(),
                        glyph = Glyph.LOCK,
                        onClick = ::pay
                    )
                    SecondaryButton("Cancel", Modifier.fillMaxWidth()) {
                        state.checkoutStage = CheckoutStage.DETAILS
                        if (!state.goBack()) state.selectDestination(Route.SUBSCRIPTION)
                    }
                }
            }

            CheckoutStage.PROCESSING -> Column(
                Modifier.fillMaxWidth().padding(Space.xxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 2.dp)
                Spacer(Modifier.height(Space.lg))
                Text("Authorising ${plan.displayPrice}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Space.xs))
                Text(
                    "Do not close the app",
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.tertiaryText
                )
            }

            CheckoutStage.SUCCESS -> {
                val payment = (outcome as? PaymentResult.Succeeded)?.payment
                SuccessState(
                    title = "You're on ${plan.name}",
                    body = "Your register is unlimited and every report is unlocked. Nothing was charged. This is sandbox mode.",
                    actionLabel = "Back to Portico",
                    onAction = {
                        state.checkoutStage = CheckoutStage.DETAILS
                        state.selectDestination(Route.DASHBOARD)
                    },
                    secondaryLabel = "View plan",
                    onSecondary = {
                        state.checkoutStage = CheckoutStage.DETAILS
                        state.selectDestination(Route.SUBSCRIPTION)
                    }
                )
                if (payment != null) ReceiptPanel(payment, plan, store.subscription.renewsOn)
            }

            CheckoutStage.DECLINED -> {
                val declined = outcome as? PaymentResult.Declined
                ErrorState(
                    title = declined?.reason ?: "Payment didn't go through",
                    body = declined?.recovery ?: "Nothing was charged. Try a different card.",
                    retryLabel = "Try another card",
                    onRetry = {
                        outcome = null
                        submitted = false
                        validation = CardValidation()
                        state.checkoutStage = CheckoutStage.DETAILS
                    },
                    secondaryLabel = "Stay on Free",
                    onSecondary = {
                        state.checkoutStage = CheckoutStage.DETAILS
                        state.selectDestination(Route.SUBSCRIPTION)
                    }
                )
                declined?.payment?.let { ReceiptPanel(it, plan, null) }
            }
        }
    }
}

@Composable
private fun SandboxBanner() {
    val semantic = PorticoTheme.semantic
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        PorticoIcon(Glyph.INFO, size = 16.dp, tint = MaterialTheme.colorScheme.primary, contentDescription = null)
        Column {
            Text("SANDBOX", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                SANDBOX_NOTICE,
                style = MaterialTheme.typography.bodySmall,
                color = semantic.tertiaryText
            )
        }
    }
}

@Composable
private fun OrderSummary(plan: SubscriptionPlan) {
    Panel(Modifier.padding(horizontal = Space.lg), accent = true) {
        PanelHeader("You're subscribing to")
        DataRow(plan.name, plan.displayPricePerInterval, emphasise = true)
        Hairline()
        DataRow("Properties", "Unlimited")
        Hairline()
        DataRow("Billing", "Recurring ${plan.interval}ly until cancelled")
        Hairline()
        DataRow("Charged today", plan.displayPrice, emphasise = true)
        Spacer(Modifier.height(Space.sm))
    }
}

/** The published test numbers, tappable so nobody has to type sixteen digits. */
@Composable
private fun TestCardTable(onPick: (String) -> Unit) {
    val semantic = PorticoTheme.semantic
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader("Test cards", supporting = "Tap one to fill the form")
        SandboxProcessor.testCards.forEachIndexed { index, testCard ->
            if (index > 0) Hairline()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(testCard.number) }
                    .heightIn(min = 52.dp)
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(testCard.number, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        testCard.behaviour,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testCard.outcome == PaymentStatus.SUCCEEDED) semantic.gain else semantic.loss
                    )
                }
                PorticoIcon(Glyph.ADD, size = 16.dp, tint = semantic.tertiaryText, contentDescription = "Use this card")
            }
        }
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
            Text(
                "Any future expiry and any 3-digit code work.",
                style = MaterialTheme.typography.labelSmall,
                color = semantic.tertiaryText
            )
        }
    }
}

@Composable
private fun ReceiptPanel(payment: Payment, plan: SubscriptionPlan, renewsOn: String?) {
    val semantic = PorticoTheme.semantic
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader("Receipt", supporting = "Sandbox, no funds moved")
        DataRow("Reference", payment.id.takeLast(12).uppercase())
        Hairline()
        DataRow("Plan", plan.name)
        Hairline()
        DataRow("Amount", payment.displayAmount, emphasise = true)
        Hairline()
        DataRow(
            "Status",
            payment.paymentStatus.name.lowercase().replaceFirstChar { it.uppercase() },
            valueColor = if (payment.succeeded) semantic.gain else semantic.loss
        )
        Hairline()
        DataRow("Card", "${payment.cardBrand} ending ${payment.cardLast4}")
        Hairline()
        DataRow("Date", payment.date)
        if (renewsOn != null) {
            Hairline()
            DataRow("Renews", renewsOn)
        }
        payment.failureReason?.let {
            Hairline()
            DataRow("Reason", it, valueColor = semantic.loss)
        }
    }
}
