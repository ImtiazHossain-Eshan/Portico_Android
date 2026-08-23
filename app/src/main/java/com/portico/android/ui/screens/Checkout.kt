package com.portico.android.ui.screens
import com.portico.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.portico.android.data.PorticoStore
import com.portico.android.domain.*
import com.portico.android.ui.CheckoutStage
import com.portico.android.ui.GatewayOutcome
import com.portico.android.ui.PaymentReturns
import com.portico.android.ui.PorticoState
import com.portico.android.ui.humanError
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

    /*
     * Gateway mode is asked of the server rather than assumed, so an offline or
     * unconfigured build never offers a payment route that cannot complete.
     */
    // null until the server answers. Defaulting to either payment flow shows the
    // member a checkout that may be about to be replaced under them.
    var gatewayMode by remember { mutableStateOf<Boolean?>(null) }
    var gatewayPending by remember { mutableStateOf(false) }
    var gatewayNotice by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        gatewayMode = runCatching { store.gatewayCheckoutAvailable() }.getOrDefault(false)
    }
    val gatewayReady = gatewayMode == true

    fun payViaGateway() {
        gatewayNotice = null
        gatewayPending = true
        scope.launch {
            runCatching { store.startGatewayCheckout(plan) }
                .onSuccess { session ->
                    openGatewayPage(context, session.gatewayPageUrl)
                }
                .onFailure { error ->
                    gatewayPending = false
                    gatewayNotice = humanError(context, error)
                }
        }
    }

    /*
     * The redirect back only says which transaction to look at. What actually
     * happened is whatever the server recorded when the gateway called it, so
     * the answer comes from a re-sync and never from the URL.
     */
    val gatewayReturn by PaymentReturns.latest.collectAsState()
    /*
     * Keyed on the transaction id, and the return is not consumed here.
     *
     * Consuming inside the effect nulls the value the effect is keyed on, which
     * makes Compose cancel the very coroutine doing the work and restart it
     * against nothing. Everything past the first suspension point was being
     * discarded: the settle retries never ran and the screen never left
     * "Authorising", because the code that clears it is at the end.
     *
     * Re-entry is prevented by remembering which transaction has been handled,
     * which is what consuming was really for.
     */
    var handledTransaction by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { PaymentReturns.consume() } }
    LaunchedEffect(gatewayReturn?.transactionId) {
        val result = gatewayReturn ?: return@LaunchedEffect
        if (handledTransaction == result.transactionId) return@LaunchedEffect
        handledTransaction = result.transactionId
        if (result.outcome == GatewayOutcome.CANCELLED) {
            gatewayPending = false
            gatewayNotice = "Payment cancelled. Nothing was charged."
            return@LaunchedEffect
        }
        state.checkoutStage = CheckoutStage.PROCESSING
        /*
         * Settling is asked for repeatedly, not once.
         *
         * The member arrives back the instant the gateway redirects, which is
         * before the gateway has made the transaction queryable on its side. A
         * single request therefore gets "nothing here yet" and, asked only once,
         * that becomes the final answer for a payment that actually succeeded.
         *
         * Every other attempt asks the server to settle; the ones in between
         * only re-read, because settling calls out to the gateway and re-reading
         * does not.
         */
        var latest: Payment? = null
        for (attempt in 0 until 10) {
            if (attempt > 0) delay(2_000)
            if (attempt % 2 == 0) {
                runCatching { store.confirmGatewayPayment(result.transactionId) }
            }
            val current = runCatching { store.refreshGatewayPayment(result.transactionId) }.getOrNull()
            if (current != null) latest = current
            // break, not continue: every attempt forces a full cloud sync, so
            // polling past a settled payment is pure delay the member watches.
            if (current != null && current.paymentStatus != PaymentStatus.PENDING) break
        }
        gatewayPending = false
        val settled = latest
        when {
            settled == null || settled.paymentStatus == PaymentStatus.PENDING -> {
                state.checkoutStage = CheckoutStage.DETAILS
                gatewayNotice =
                    "Payment is still being confirmed. Your plan updates automatically once it clears."
            }
            settled.succeeded -> {
                outcome = PaymentResult.Succeeded(settled)
                state.checkoutStage = CheckoutStage.SUCCESS
            }
            else -> {
                outcome = PaymentResult.Declined(
                    settled,
                    settled.failureReason ?: "Payment was not completed",
                    "Nothing was charged. You can try again."
                )
                state.checkoutStage = CheckoutStage.DECLINED
            }
        }
    }

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
                        recovery = humanError(context, error)
                    )
                    state.checkoutStage = CheckoutStage.DECLINED
                }
            card = CardInput()
        }
    }

    Column(modifier.padding(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

        SandboxBanner(gatewayReady)

        when (state.checkoutStage) {
            CheckoutStage.DETAILS -> if (gatewayMode == null) {
                OrderSummary(plan, showTaka = false)
                BillingModeLoading()
            } else if (gatewayReady) {
                OrderSummary(plan, showTaka = true)
                GatewayPanel(
                    plan = plan,
                    busy = gatewayPending,
                    notice = gatewayNotice,
                    onPay = ::payViaGateway
                )
            } else {
                OrderSummary(plan, showTaka = false)

                Panel(Modifier.padding(horizontal = Space.lg)) {
                    PanelHeader(stringResource(R.string.card_details), supporting = stringResource(R.string.test_cards_only_never_a_real_card))
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
                            label = stringResource(R.string.card_number),
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
                                label = stringResource(R.string.expiry),
                                placeholder = stringResource(R.string.mm_yy),
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
                                label = stringResource(R.string.cvc),
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
                            label = stringResource(R.string.name_on_card),
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
                    SecondaryButton(stringResource(R.string.cancel), Modifier.fillMaxWidth()) {
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
                // A gateway charge settles in taka, so the dollar figure would name
                // a sum nobody is paying.
                Text(
                    "Authorising " + if (gatewayReady) plan.displayPriceTaka else plan.displayPrice,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    stringResource(R.string.do_not_close_the_app),
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.tertiaryText
                )
            }

            CheckoutStage.SUCCESS -> {
                val payment = (outcome as? PaymentResult.Succeeded)?.payment
                SuccessState(
                    title = "You're on ${plan.name}",
                    body = stringResource(R.string.your_register_is_unlimited_and_every_report_is),
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
                    title = declined?.reason ?: stringResource(R.string.payment_didn_t_go_through),
                    body = declined?.recovery ?: stringResource(R.string.nothing_was_charged_try_a_different_card),
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
private fun SandboxBanner(gatewayMode: Boolean = false) {
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
            Text(stringResource(R.string.sandbox), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                // The card-table wording is wrong on a screen with no card
                // fields, and the gateway one is wrong where there is no gateway.
                if (gatewayMode) stringResource(R.string.sandbox_gateway_notice) else SANDBOX_NOTICE,
                style = MaterialTheme.typography.bodySmall,
                color = semantic.tertiaryText
            )
        }
    }
}

@Composable
private fun OrderSummary(plan: SubscriptionPlan, showTaka: Boolean) {
    // A gateway charge settles in taka. Quoting dollars above a panel that
    // charges BDT 1200 puts two prices on one purchase.
    val perInterval = if (showTaka) plan.displayPriceTakaPerInterval else plan.displayPricePerInterval
    val today = if (showTaka) plan.displayPriceTaka else plan.displayPrice
    Panel(Modifier.padding(horizontal = Space.lg), accent = true) {
        PanelHeader(stringResource(R.string.you_re_subscribing_to))
        DataRow(plan.name, perInterval, emphasise = true)
        Hairline()
        DataRow(stringResource(R.string.properties), "Unlimited")
        Hairline()
        DataRow(stringResource(R.string.billing), "Recurring ${plan.interval}ly until cancelled")
        Hairline()
        DataRow(stringResource(R.string.charged_today), today, emphasise = true)
        Spacer(Modifier.height(Space.sm))
    }
}

/** The published test numbers, tappable so nobody has to type sixteen digits. */
@Composable
private fun TestCardTable(onPick: (String) -> Unit) {
    val semantic = PorticoTheme.semantic
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(stringResource(R.string.test_cards), supporting = stringResource(R.string.tap_one_to_fill_the_form))
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
                PorticoIcon(Glyph.ADD, size = 16.dp, tint = semantic.tertiaryText, contentDescription = stringResource(R.string.use_this_card))
            }
        }
        Column(Modifier.padding(horizontal = Space.lg, vertical = Space.sm)) {
            Text(
                stringResource(R.string.any_future_expiry_and_any_3_digit_code_work),
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
        PanelHeader(stringResource(R.string.receipt), supporting = stringResource(R.string.sandbox_no_funds_moved))
        DataRow(stringResource(R.string.reference), payment.id.takeLast(12).uppercase())
        Hairline()
        DataRow(stringResource(R.string.plan), plan.name)
        Hairline()
        DataRow(stringResource(R.string.amount), payment.displayAmount, emphasise = true)
        Hairline()
        DataRow(
            stringResource(R.string.status),
            payment.paymentStatus.name.lowercase().replaceFirstChar { it.uppercase() },
            valueColor = if (payment.succeeded) semantic.gain else semantic.loss
        )
        Hairline()
        DataRow(stringResource(R.string.card), "${payment.cardBrand} ending ${payment.cardLast4}")
        Hairline()
        DataRow(stringResource(R.string.date), payment.date)
        if (renewsOn != null) {
            Hairline()
            DataRow(stringResource(R.string.renews), renewsOn)
        }
        payment.failureReason?.let {
            Hairline()
            DataRow(stringResource(R.string.reason), it, valueColor = semantic.loss)
        }
    }
}

/**
 * Hands the member off to the gateway's own page.
 *
 * A Custom Tab rather than a WebView, deliberately: the payment page is the
 * gateway's, the address bar stays visible so the member can see whose page
 * they are typing a card into, and Portico never sits between them and it.
 */
private fun openGatewayPage(context: Context, url: String) {
    val intent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    runCatching { intent.launchUrl(context, Uri.parse(url)) }
        .onFailure {
            // No browser that supports Custom Tabs. A plain VIEW intent still
            // reaches whatever browser exists, and the return hop is a deep
            // link either way.
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
}

@Composable
private fun GatewayPanel(
    plan: SubscriptionPlan,
    busy: Boolean,
    notice: String?,
    onPay: () -> Unit
) {
    val semantic = PorticoTheme.semantic
    Panel(Modifier.padding(horizontal = Space.lg)) {
        PanelHeader(
            stringResource(R.string.pay_with_sslcommerz),
            supporting = stringResource(R.string.you_will_finish_payment_on_the_gateway_page)
        )
        Column(
            Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.amount_charged_in_taka),
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantic.tertiaryText
                )
                Text(
                    plan.displayPriceTakaPerInterval,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                stringResource(R.string.sandbox_gateway_test_cards_only),
                style = MaterialTheme.typography.bodySmall,
                color = semantic.tertiaryText
            )
            if (notice != null) {
                Text(
                    notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = semantic.loss
                )
            }
            PrimaryButton(
                label = if (busy) stringResource(R.string.opening_gateway)
                else stringResource(R.string.continue_to_payment),
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                loading = busy,
                onClick = onPay
            )
        }
    }
}

/**
 * Held state while the server is asked which billing mode is active.
 *
 * Deliberately not a payment form. Guessing one and swapping it a moment later
 * means the member reads a checkout that is about to change under them.
 */
@Composable
private fun BillingModeLoading() {
    val semantic = PorticoTheme.semantic
    Panel(Modifier.padding(horizontal = Space.lg)) {
        Column(
            Modifier.fillMaxWidth().padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Text(
                stringResource(R.string.preparing_checkout),
                style = MaterialTheme.typography.bodySmall,
                color = semantic.tertiaryText
            )
        }
    }
}
