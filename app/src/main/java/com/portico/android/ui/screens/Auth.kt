package com.portico.android.ui.screens
import com.portico.android.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clerk.api.Clerk
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.signin.SignIn
import com.clerk.api.signin.resetPassword
import com.clerk.api.signin.sendResetPasswordCode
import com.clerk.api.signin.verifyCode
import com.clerk.api.signup.SignUp
import com.clerk.api.signup.sendEmailCode
import com.clerk.api.signup.verifyCode as verifySignUpCode
import com.clerk.api.sso.OAuthProvider
import com.portico.android.BuildConfig
import com.portico.android.ui.PorticoState
import com.portico.android.ui.Route
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.launch

/*
 * Sign-in on the product's own ground rather than a generic centred card.
 *
 * The rules this instance actually enforces are stated before the user types,
 * not discovered after a rejected submit. The previous build advertised an
 * eight-character minimum against a server requiring fifteen, which made
 * account creation impossible for anyone who followed its own instructions.
 */

/**
 * Mirrors the workspace's configured policy. Kept in one place so the
 * validator, the hint and the live checklist cannot disagree with each other.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 15

    fun failures(password: String): List<String> = buildList {
        if (password.length < MIN_LENGTH) add("At least $MIN_LENGTH characters")
    }

    fun isValid(password: String) = failures(password).isEmpty()

    val summary = "Your workspace requires at least $MIN_LENGTH characters."
}

private enum class AuthPhase { FORM, VERIFY_EMAIL, SIGN_IN_CODE, RESET_CODE, RESET_PASSWORD }

@Composable
fun AuthScreen(
    state: PorticoState,
    route: String,
    onAuthenticated: () -> Unit,
    onUseDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val widthClass = LocalWidthClass.current

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (widthClass.isExpanded) {
            Row(Modifier.fillMaxSize()) {
                AuthBrandPanel(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(PorticoTheme.semantic.panel)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(Space.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    AuthForm(state, route, onAuthenticated, onUseDemo, Modifier.widthIn(max = 440.dp))
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = Space.lg, vertical = Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(Space.lg))
                PorticoLockup(markSize = 44.dp)
                Spacer(Modifier.height(Space.xxl))
                AuthForm(state, route, onAuthenticated, onUseDemo, Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.xl))
            }
        }
    }
}

/** The wide-layout side panel: what the product is, stated plainly. */
@Composable
private fun AuthBrandPanel(modifier: Modifier = Modifier) {
    val semantic = PorticoTheme.semantic
    Column(
        modifier.padding(Space.xxl),
        verticalArrangement = Arrangement.Center
    ) {
        PorticoLockup(markSize = 52.dp)
        Spacer(Modifier.height(Space.xxl))
        Text(
            stringResource(R.string.know_what_your_nproperties_actually_nearn),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(Space.lg))
        Text(
            stringResource(R.string.portico_works_out_roi_cap_rate_yields_and_cash_2),
            style = MaterialTheme.typography.bodyLarge,
            color = semantic.tertiaryText,
            modifier = Modifier.widthIn(max = 420.dp)
        )
        Spacer(Modifier.height(Space.xxl))
        listOf(
            stringResource(R.string.gross_to_net_on_every_property),
            stringResource(R.string.tax_modelling_for_bangladesh_uruguay_and_argen),
            stringResource(R.string.documents_and_records_sync_to_your_private_wor)
        ).forEach { line ->
            Row(Modifier.padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                PorticoIcon(Glyph.CHECK, size = 15.dp, tint = MaterialTheme.colorScheme.primary, contentDescription = null)
                Spacer(Modifier.width(Space.md))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AuthForm(
    state: PorticoState,
    route: String,
    onAuthenticated: () -> Unit,
    onUseDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val semantic = PorticoTheme.semantic
    val configured = BuildConfig.CLERK_PUBLISHABLE_KEY.isNotBlank()

    var phase by remember(route) { mutableStateOf(AuthPhase.FORM) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var recoveryAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var termsAccepted by remember { mutableStateOf(false) }

    var signInFlow by remember { mutableStateOf<SignIn?>(null) }
    var signUpFlow by remember { mutableStateOf<SignUp?>(null) }

    val isRegister = route == Route.REGISTER
    val isRecovery = route == Route.FORGOT

    LaunchedEffect(route) {
        phase = AuthPhase.FORM
        loading = false
        error = null
        code = ""
        signInFlow = null
        signUpFlow = null
        recoveryAction = null
    }

    /**
     * Turns a Clerk failure into something the user can act on. The old build
     * mapped a handful of codes and told a locked-out user nothing at all.
     */
    fun explain(failure: ClerkResult.Failure<*>, signingIn: Boolean): String {
        val response = failure.error as? ClerkErrorResponse
        val apiError = response?.errors?.firstOrNull()
        val throwableMessage = failure.throwable?.message

        // Network trouble surfaces as a transport exception with no API error.
        if (apiError == null && throwableMessage != null) {
            val looksOffline = listOf(state.store.string(R.string.unable_to_resolve_host), "failed to connect", "timeout", "network")
                .any { throwableMessage.contains(it, ignoreCase = true) }
            if (looksOffline) {
                recoveryAction = state.store.string(R.string.continue_with_demo_data) to onUseDemo
                return state.store.string(R.string.can_t_reach_the_sign_in_service_check_your_con)
            }
        }

        return when (apiError?.code) {
            "form_password_length" ->
                "That password is too short. ${PasswordPolicy.summary}"

            "form_password_pwned" -> if (signingIn) {
                recoveryAction = "Reset password" to { state.navigate(Route.FORGOT) }
                state.store.string(R.string.this_password_has_appeared_in_a_known_data_bre_2)
            } else {
                state.store.string(R.string.this_password_has_appeared_in_a_known_data_bre)
            }

            "form_identifier_exists" -> {
                recoveryAction = "Sign in instead" to { state.navigate(Route.LOGIN) }
                state.store.string(R.string.an_account_already_exists_for_that_email)
            }

            "form_identifier_not_found" -> {
                recoveryAction = "Create an account" to { state.navigate(Route.REGISTER) }
                state.store.string(R.string.no_account_found_for_that_email)
            }

            "form_password_incorrect", "form_param_format_invalid" ->
                state.store.string(R.string.that_email_and_password_don_t_match_check_both)

            "user_locked", "too_many_requests", "account_locked" -> {
                recoveryAction = state.store.string(R.string.continue_with_demo_data) to onUseDemo
                state.store.string(R.string.too_many_failed_attempts_so_this_account_is_lo)
            }

            "session_exists" -> {
                onAuthenticated()
                state.store.string(R.string.you_re_already_signed_in)
            }

            "form_param_missing" -> state.store.string(R.string.fill_in_every_required_field_and_try_again)

            "verification_expired", "verification_failed" ->
                state.store.string(R.string.that_code_has_expired_ask_for_a_new_one)

            "form_code_incorrect" -> state.store.string(R.string.that_code_doesn_t_match_check_your_email_and_t)

            else -> apiError?.longMessage
                ?: apiError?.message
                ?: throwableMessage
                ?: state.store.string(R.string.that_didn_t_go_through_check_the_details_and_t)
        }
    }

    fun run(signingIn: Boolean = true, block: suspend () -> Unit) {
        scope.launch {
            loading = true
            error = null
            recoveryAction = null
            try {
                block()
            } catch (throwable: Throwable) {
                recoveryAction = state.store.string(R.string.continue_with_demo_data) to onUseDemo
                error = state.store.string(R.string.something_went_wrong_reaching_the_sign_in_serv)
            } finally {
                loading = false
            }
        }
    }

    fun completeOrExplain(status: SignIn.Status) {
        when (status) {
            SignIn.Status.COMPLETE -> onAuthenticated()
            SignIn.Status.NEEDS_NEW_PASSWORD -> phase = AuthPhase.RESET_PASSWORD
            SignIn.Status.NEEDS_FIRST_FACTOR -> phase = AuthPhase.SIGN_IN_CODE
            else -> error = state.store.string(R.string.this_account_needs_another_step_before_it_can)
        }
    }

    fun oauth(provider: OAuthProvider) {
        run {
            val result = if (isRegister) Clerk.auth.signUpWithOAuth(provider)
            else Clerk.auth.signInWithOAuth(provider)
            when (result) {
                is ClerkResult.Success -> {
                    val signIn = result.value.signIn
                    val signUp = result.value.signUp
                    when {
                        signIn?.status == SignIn.Status.COMPLETE -> onAuthenticated()
                        signUp?.status == SignUp.Status.COMPLETE -> onAuthenticated()
                        Clerk.user != null -> onAuthenticated()
                        else -> error = state.store.string(R.string.that_provider_didn_t_finish_signing_you_in_try)
                    }
                }
                is ClerkResult.Failure<*> -> error = explain(result, signingIn = !isRegister)
            }
        }
    }

    fun submit() {
        val cleanEmail = email.trim()
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            error = state.store.string(R.string.enter_a_valid_email_address)
            return
        }
        if (isRecovery) {
            run {
                when (val started = Clerk.auth.signIn { this.email = cleanEmail }) {
                    is ClerkResult.Success -> {
                        signInFlow = started.value
                        when (val sent = started.value.sendResetPasswordCode { this.email = cleanEmail }) {
                            is ClerkResult.Success -> { signInFlow = sent.value; phase = AuthPhase.RESET_CODE }
                            is ClerkResult.Failure<*> -> error = explain(sent, true)
                        }
                    }
                    is ClerkResult.Failure<*> -> error = explain(started, true)
                }
            }
            return
        }

        if (!PasswordPolicy.isValid(password)) {
            error = "That password is too short. ${PasswordPolicy.summary}"
            return
        }

        if (isRegister) {
            if (firstName.trim().length < 2) {
                error = state.store.string(R.string.add_your_first_name_so_the_workspace_knows_who)
                return
            }
            if (!termsAccepted) {
                error = state.store.string(R.string.accept_the_terms_and_privacy_notice_to_create)
                return
            }
            run(signingIn = false) {
                when (val created = Clerk.auth.signUp {
                    this.email = cleanEmail
                    this.password = password
                    this.firstName = firstName.trim()
                    this.lastName = lastName.trim().ifBlank { null }
                    this.legalAccepted = termsAccepted
                }) {
                    is ClerkResult.Success -> {
                        signUpFlow = created.value
                        if (created.value.status == SignUp.Status.COMPLETE) {
                            onAuthenticated()
                        } else {
                            when (val sent = created.value.sendEmailCode()) {
                                is ClerkResult.Success -> { signUpFlow = sent.value; phase = AuthPhase.VERIFY_EMAIL }
                                is ClerkResult.Failure<*> -> error = explain(sent, false)
                            }
                        }
                    }
                    is ClerkResult.Failure<*> -> error = explain(created, false)
                }
            }
        } else {
            run {
                when (val attempt = Clerk.auth.signInWithPassword {
                    this.identifier = cleanEmail
                    this.password = password
                }) {
                    is ClerkResult.Success -> { signInFlow = attempt.value; completeOrExplain(attempt.value.status) }
                    is ClerkResult.Failure<*> -> error = explain(attempt, true)
                }
            }
        }
    }

    fun sendEmailCode() {
        val cleanEmail = email.trim()
        if (!cleanEmail.contains("@")) {
            error = state.store.string(R.string.enter_the_email_on_your_workspace_first)
            return
        }
        run {
            when (val started = Clerk.auth.signInWithOtp { this.email = cleanEmail }) {
                is ClerkResult.Success -> { signInFlow = started.value; phase = AuthPhase.SIGN_IN_CODE }
                is ClerkResult.Failure<*> -> error = explain(started, true)
            }
        }
    }

    fun verify() {
        if (code.trim().length < 6) {
            error = state.store.string(R.string.enter_the_six_digit_code_from_your_email)
            return
        }
        run {
            when (phase) {
                AuthPhase.VERIFY_EMAIL -> when (val result = signUpFlow?.verifySignUpCode(code.trim(), com.clerk.api.auth.types.VerificationType.EMAIL)) {
                    is ClerkResult.Success -> if (result.value.status == SignUp.Status.COMPLETE) onAuthenticated()
                    else error = state.store.string(R.string.one_more_account_detail_is_needed_before_this)
                    is ClerkResult.Failure<*> -> error = explain(result, false)
                    null -> error = state.store.string(R.string.that_sign_up_session_expired_start_again)
                }
                AuthPhase.SIGN_IN_CODE -> when (val result = signInFlow?.verifyCode(code.trim())) {
                    is ClerkResult.Success -> completeOrExplain(result.value.status)
                    is ClerkResult.Failure<*> -> error = explain(result, true)
                    null -> error = state.store.string(R.string.that_session_expired_start_sign_in_again)
                }
                AuthPhase.RESET_CODE -> when (val result = signInFlow?.verifyCode(code.trim())) {
                    is ClerkResult.Success -> { signInFlow = result.value; phase = AuthPhase.RESET_PASSWORD }
                    is ClerkResult.Failure<*> -> error = explain(result, true)
                    null -> error = state.store.string(R.string.that_recovery_session_expired_start_again)
                }
                else -> error = state.store.string(R.string.enter_the_code_to_continue)
            }
        }
    }

    fun applyNewPassword() {
        if (!PasswordPolicy.isValid(password)) {
            error = "That password is too short. ${PasswordPolicy.summary}"
            return
        }
        run {
            when (val result = signInFlow?.resetPassword(password, signOutOfOtherSessions = true)) {
                is ClerkResult.Success -> { onAuthenticated() }
                is ClerkResult.Failure<*> -> error = explain(result, true)
                null -> error = state.store.string(R.string.that_recovery_session_expired_start_again)
            }
        }
    }

    // ---------------------------------------------------------------- render

    Panel(modifier) {
        Column(Modifier.padding(Space.xl), verticalArrangement = Arrangement.spacedBy(Space.lg)) {

            Column {
                Text(
                    when {
                        phase == AuthPhase.VERIFY_EMAIL -> "Verify your email"
                        phase == AuthPhase.SIGN_IN_CODE -> "Check your inbox"
                        phase == AuthPhase.RESET_CODE -> stringResource(R.string.enter_the_recovery_code)
                        phase == AuthPhase.RESET_PASSWORD -> stringResource(R.string.choose_a_new_password)
                        isRecovery -> "Recover your account"
                        isRegister -> "Create your account"
                        else -> "Sign in"
                    },
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    when {
                        phase == AuthPhase.VERIFY_EMAIL || phase == AuthPhase.SIGN_IN_CODE ->
                            "We sent a six-digit code to ${email.trim()}."
                        phase == AuthPhase.RESET_CODE -> "A recovery code is on its way to ${email.trim()}."
                        phase == AuthPhase.RESET_PASSWORD -> PasswordPolicy.summary
                        isRecovery -> stringResource(R.string.we_ll_email_you_a_code_to_set_a_new_password)
                        isRegister -> stringResource(R.string.your_private_workspace_is_created_after_verifi)
                        else -> "Welcome back."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = semantic.tertiaryText
                )
            }

            if (!configured) {
                // Not a dead end: the product still works without an account.
                InlineError(stringResource(R.string.account_sign_in_isn_t_configured_in_this_build))
                PrimaryButton(stringResource(R.string.continue_with_demo_data), Modifier.fillMaxWidth(), glyph = Glyph.FORWARD, onClick = onUseDemo)
                return@Column
            }

            when (phase) {
                AuthPhase.FORM -> {
                    if (!isRecovery) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            SecondaryButton(stringResource(R.string.google), Modifier.weight(1f), enabled = !loading) { oauth(OAuthProvider.GOOGLE) }
                            SecondaryButton(stringResource(R.string.github), Modifier.weight(1f), enabled = !loading) { oauth(OAuthProvider.GITHUB) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(Modifier.weight(1f), color = semantic.hairline)
                            Text(
                                stringResource(R.string.or),
                                Modifier.padding(horizontal = Space.md),
                                style = MaterialTheme.typography.labelSmall,
                                color = semantic.tertiaryText
                            )
                            HorizontalDivider(Modifier.weight(1f), color = semantic.hairline)
                        }
                    }

                    if (isRegister) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            PorticoField(firstName, { firstName = it }, "First name", Modifier.weight(1f))
                            PorticoField(lastName, { lastName = it }, "Last name", Modifier.weight(1f))
                        }
                    }

                    PorticoField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = stringResource(R.string.email),
                        keyboardType = KeyboardType.Email
                    )

                    if (!isRecovery) {
                        PorticoField(
                            value = password,
                            onValueChange = { password = it; error = null },
                            label = stringResource(R.string.password),
                            keyboardType = KeyboardType.Password,
                            isPassword = true,
                            supporting = if (!isRegister) PasswordPolicy.summary else null
                        )
                        // Live criteria on sign-up, so the rule is visible while
                        // typing rather than after a rejected submit.
                        if (isRegister) {
                            PasswordCriteria(password)
                        }
                    }

                    if (isRegister) {
                        TermsCheckbox(termsAccepted) { termsAccepted = it }
                    }

                    error?.let { message ->
                        InlineError(message)
                        recoveryAction?.let { (label, action) ->
                            SecondaryButton(label, Modifier.fillMaxWidth(), onClick = action)
                        }
                    }

                    PrimaryButton(
                        label = when {
                            isRecovery -> "Send recovery code"
                            isRegister -> "Create account"
                            else -> "Sign in"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        loading = loading,
                        onClick = ::submit
                    )

                    if (!isRegister && !isRecovery) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = ::sendEmailCode, enabled = !loading) { Text(stringResource(R.string.email_me_a_code)) }
                            TextButton(onClick = { state.navigate(Route.FORGOT) }) { Text(stringResource(R.string.forgot_password)) }
                        }
                    }
                }

                AuthPhase.RESET_PASSWORD -> {
                    PorticoField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = stringResource(R.string.new_password),
                        keyboardType = KeyboardType.Password,
                        isPassword = true
                    )
                    PasswordCriteria(password)
                    error?.let { InlineError(it) }
                    PrimaryButton(stringResource(R.string.set_password_and_continue), Modifier.fillMaxWidth(), loading = loading, onClick = ::applyNewPassword)
                }

                else -> {
                    PorticoField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6); error = null },
                        label = stringResource(R.string.six_digit_code),
                        keyboardType = KeyboardType.Number
                    )
                    error?.let { InlineError(it) }
                    PrimaryButton(stringResource(R.string.verify_and_continue), Modifier.fillMaxWidth(), loading = loading, onClick = ::verify)
                    TextButton(
                        onClick = { phase = AuthPhase.FORM; code = ""; error = null },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text(stringResource(R.string.use_a_different_account)) }
                }
            }

            if (phase == AuthPhase.FORM) {
                HorizontalDivider(color = semantic.hairline)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isRegister) stringResource(R.string.already_have_an_account) else "New to Portico?",
                        style = MaterialTheme.typography.bodySmall,
                        color = semantic.tertiaryText
                    )
                    TextButton(onClick = {
                        state.navigate(if (isRegister) Route.LOGIN else Route.REGISTER)
                    }) { Text(if (isRegister) "Sign in" else "Create an account") }
                }
                SecondaryButton(
                    stringResource(R.string.explore_with_demo_data),
                    Modifier.fillMaxWidth(),
                    glyph = Glyph.PORTFOLIO,
                    onClick = onUseDemo
                )
                Text(
                    stringResource(R.string.demo_data_is_a_sample_portfolio_nothing_you_en),
                    style = MaterialTheme.typography.labelSmall,
                    color = semantic.tertiaryText,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

/** Live policy checklist. Turns a rejected submit into a visible rule. */
@Composable
private fun PasswordCriteria(password: String) {
    val semantic = PorticoTheme.semantic
    val failures = PasswordPolicy.failures(password)
    val met = password.isNotEmpty() && failures.isEmpty()

    Row(verticalAlignment = Alignment.CenterVertically) {
        PorticoIcon(
            if (met) Glyph.CHECK else Glyph.INFO,
            size = 14.dp,
            tint = if (met) semantic.gain else semantic.tertiaryText,
            contentDescription = null
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            if (met) stringResource(R.string.meets_your_workspace_policy)
            else "At least ${PasswordPolicy.MIN_LENGTH} characters (${password.length}/${PasswordPolicy.MIN_LENGTH})",
            style = MaterialTheme.typography.bodySmall,
            color = if (met) semantic.gain else semantic.tertiaryText
        )
    }
}

/** Real consent, driving the value actually sent to the identity provider. */
@Composable
private fun TermsCheckbox(accepted: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!accepted) }
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = accepted, onCheckedChange = onChange)
        Spacer(Modifier.width(Space.sm))
        Text(
            stringResource(R.string.i_accept_the_terms_of_use_and_privacy_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
