package com.portico.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clerk.api.Clerk
import com.clerk.api.auth.types.MfaType
import com.clerk.api.auth.types.VerificationType
import com.clerk.api.network.model.error.ClerkErrorResponse
import com.clerk.api.network.serialization.ClerkResult
import com.clerk.api.signin.SignIn
import com.clerk.api.signin.resetPassword
import com.clerk.api.signin.sendMfaEmailCode
import com.clerk.api.signin.sendResetPasswordCode
import com.clerk.api.signin.verifyCode
import com.clerk.api.signin.verifyMfaCode
import com.clerk.api.signup.SignUp
import com.clerk.api.signup.sendEmailCode
import com.clerk.api.signup.verifyCode as verifySignUpCode
import com.portico.android.BuildConfig
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoOrange
import kotlinx.coroutines.launch

private enum class PorticoAuthPhase {
    FORM,
    VERIFY_EMAIL,
    SIGN_IN_EMAIL_CODE,
    VERIFY_MFA,
    RESET_CODE,
    RESET_PASSWORD
}

@Composable
fun PorticoAuthForm(
    route: String,
    onRouteChange: (String) -> Unit,
    onAuthComplete: () -> Unit,
    clerkConfigured: Boolean,
    modifier: Modifier = Modifier
) {
    if (!clerkConfigured) {
        SetupRequiredCard(modifier)
        return
    }

    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(PorticoAuthPhase.FORM) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var passwordValue by remember { mutableStateOf("") }
    var firstNameValue by remember { mutableStateOf("") }
    var lastNameValue by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var signInFlow by remember { mutableStateOf<SignIn?>(null) }
    var signUpFlow by remember { mutableStateOf<SignUp?>(null) }

    val signUp = route == "register"
    val reset = route == "forgot"

    LaunchedEffect(route) {
        phase = PorticoAuthPhase.FORM
        loading = false
        error = null
        code = ""
        signInFlow = null
        signUpFlow = null
    }

    fun message(failure: ClerkResult.Failure<*>): String {
        val response = failure.error as? ClerkErrorResponse
        val apiError = response?.errors?.firstOrNull()
        val code = apiError?.code
        val friendly = when (code) {
            "form_identifier_exists" -> "An account with this email already exists. Switch to Sign in or recover the password."
            "form_password_length" -> "Choose a longer password that matches the workspace security policy."
            "form_password_pwned" -> "Choose a different password to keep this workspace secure."
            "form_param_format_invalid" -> "Check the highlighted account details and try again."
            "form_param_missing" -> "Complete the required account details and try again."
            else -> null
        }
        return listOf(
            friendly,
            apiError?.longMessage,
            apiError?.message,
            failure.throwable?.message
        ).firstOrNull { !it.isNullOrBlank() }
            ?: "Clerk could not complete that request. Check the details and try again."
    }

    fun completeOrExplain(status: SignIn.Status) {
        when (status) {
            SignIn.Status.COMPLETE -> onAuthComplete()
            SignIn.Status.NEEDS_SECOND_FACTOR -> phase = PorticoAuthPhase.VERIFY_MFA
            else -> error = "This account needs another verification step before it can open."
        }
    }

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            loading = true
            error = null
            try {
                action()
            } catch (throwable: Throwable) {
                error = throwable.message ?: "The request could not be completed."
            } finally {
                loading = false
            }
        }
    }

    fun submitForm() {
        val cleanEmail = email.trim()
        if (!cleanEmail.contains("@") || (!reset && passwordValue.length < 8)) {
            error = if (reset) "Use the email attached to your Portico workspace." else "Use a valid email and a password with at least 8 characters."
            return
        }
        if (signUp && firstNameValue.trim().length < 2) {
            error = "Add your first name so the workspace knows who is entering."
            return
        }
        if (reset) {
            runAction {
                when (val result = Clerk.auth.signIn { email = cleanEmail }) {
                    is ClerkResult.Success<SignIn> -> {
                        signInFlow = result.value
                        when (val resetResult = result.value.sendResetPasswordCode { email = cleanEmail }) {
                            is ClerkResult.Success<SignIn> -> phase = PorticoAuthPhase.RESET_CODE
                            is ClerkResult.Failure<*> -> error = message(resetResult)
                        }
                    }
                    is ClerkResult.Failure<*> -> error = message(result)
                }
            }
            return
        }
        if (signUp) {
            runAction {
                when (val result = Clerk.auth.signUp {
                    email = cleanEmail
                    password = passwordValue
                    firstName = firstNameValue.trim()
                    lastName = lastNameValue.trim().ifBlank { null }
                    legalAccepted = true
                }) {
                    is ClerkResult.Success<SignUp> -> {
                        signUpFlow = result.value
                        if (result.value.status == SignUp.Status.COMPLETE) {
                            onAuthComplete()
                        } else {
                            when (val codeResult = result.value.sendEmailCode()) {
                                is ClerkResult.Success<SignUp> -> {
                                    signUpFlow = codeResult.value
                                    phase = PorticoAuthPhase.VERIFY_EMAIL
                                }
                                is ClerkResult.Failure<*> -> error = message(codeResult)
                            }
                        }
                    }
                    is ClerkResult.Failure<*> -> error = message(result)
                }
            }
        } else {
            runAction {
                when (val result = Clerk.auth.signInWithPassword {
                    identifier = cleanEmail
                    password = passwordValue
                }) {
                    is ClerkResult.Success<SignIn> -> {
                        signInFlow = result.value
                        completeOrExplain(result.value.status)
                    }
                    is ClerkResult.Failure<*> -> error = message(result)
                }
            }
        }
    }

    fun sendSignInCode() {
        val cleanEmail = email.trim()
        if (!cleanEmail.contains("@")) {
            error = "Use the email attached to your Portico workspace."
            return
        }
        runAction {
            when (val result = Clerk.auth.signInWithOtp { email = cleanEmail }) {
                is ClerkResult.Success<SignIn> -> {
                    signInFlow = result.value
                    phase = PorticoAuthPhase.SIGN_IN_EMAIL_CODE
                }
                is ClerkResult.Failure<*> -> error = message(result)
            }
        }
    }

    fun verify() {
        if (code.trim().length < 6) {
            error = "Enter the six-digit code from your email."
            return
        }
        runAction {
            when (phase) {
                PorticoAuthPhase.VERIFY_EMAIL -> when (val result = signUpFlow?.verifySignUpCode(code.trim(), VerificationType.EMAIL)) {
                    is ClerkResult.Success<SignUp> -> if (result.value.status == SignUp.Status.COMPLETE) onAuthComplete() else error = "One more account detail is required."
                    is ClerkResult.Failure<*> -> error = message(result)
                    null -> error = "That sign-up session expired. Start again."
                }
                PorticoAuthPhase.SIGN_IN_EMAIL_CODE -> when (val result = signInFlow?.verifyCode(code.trim())) {
                    is ClerkResult.Success<SignIn> -> completeOrExplain(result.value.status)
                    is ClerkResult.Failure<*> -> error = message(result)
                    null -> error = "That email-code session expired. Start sign-in again."
                }
                PorticoAuthPhase.VERIFY_MFA -> when (val result = signInFlow?.verifyMfaCode(code.trim(), MfaType.EMAIL_CODE)) {
                    is ClerkResult.Success<SignIn> -> completeOrExplain(result.value.status)
                    is ClerkResult.Failure<*> -> error = message(result)
                    null -> error = "That verification session expired. Start sign-in again."
                }
                PorticoAuthPhase.RESET_CODE -> when (val result = signInFlow?.verifyCode(code.trim())) {
                    is ClerkResult.Success<SignIn> -> {
                        signInFlow = result.value
                        phase = PorticoAuthPhase.RESET_PASSWORD
                    }
                    is ClerkResult.Failure<*> -> error = message(result)
                    null -> error = "That recovery session expired. Start again."
                }
                else -> error = "Enter the code to continue."
            }
        }
    }

    fun resetPassword() {
        if (passwordValue.length < 8) {
            error = "Use a new password with at least 8 characters."
            return
        }
        runAction {
            when (val result = signInFlow?.resetPassword(passwordValue, signOutOfOtherSessions = true)) {
                is ClerkResult.Success<SignIn> -> {
                    phase = PorticoAuthPhase.FORM
                    passwordValue = ""
                    error = null
                    onRouteChange("login")
                }
                is ClerkResult.Failure<*> -> error = message(result)
                null -> error = "That recovery session expired. Start again."
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            PorticoLogoMark(Modifier.size(58.dp), contentDescription = "Portico property investment mark")
            Text(
                when {
                    phase == PorticoAuthPhase.VERIFY_EMAIL -> "Verify your email"
                    phase == PorticoAuthPhase.SIGN_IN_EMAIL_CODE -> "Check your inbox"
                    phase == PorticoAuthPhase.VERIFY_MFA -> "Confirm your sign-in"
                    phase == PorticoAuthPhase.RESET_CODE -> "Verify account recovery"
                    phase == PorticoAuthPhase.RESET_PASSWORD -> "Choose a new password"
                    reset -> "Recover access to PORTICO"
                    signUp -> "Create your PORTICO account"
                    else -> "Sign in to PORTICO"
                },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center
            )
            Text(
                when {
                    phase == PorticoAuthPhase.VERIFY_EMAIL -> "We sent a six-digit code to ${email.trim()}."
                    phase == PorticoAuthPhase.SIGN_IN_EMAIL_CODE -> "Enter the six-digit code sent to ${email.trim()}."
                    phase == PorticoAuthPhase.VERIFY_MFA -> "Use the code sent to your verified email."
                    phase == PorticoAuthPhase.RESET_CODE -> "A recovery code is on its way to ${email.trim()}."
                    phase == PorticoAuthPhase.RESET_PASSWORD -> "Create a password that keeps your property records private."
                    reset -> "Recover access with verification that stays in the Portico flow."
                    signUp -> "Welcome. Set up the secure workspace behind your property portfolio."
                    else -> "Welcome back. Sign in to continue."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (phase == PorticoAuthPhase.FORM && !reset) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuthProviderButton("GH", "GitHub", Modifier.weight(1f)) {
                        error = "GitHub sign-in is not enabled for this native workspace yet. Use email to stay inside Portico."
                    }
                    AuthProviderButton("G", "Google", Modifier.weight(1f)) {
                        error = "Google sign-in is not enabled for this native workspace yet. Use email to stay inside Portico."
                    }
                }
                AuthDivider()
            }

            if (phase == PorticoAuthPhase.FORM) {
                if (signUp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AuthField(firstNameValue, { firstNameValue = it }, "First name", Modifier.weight(1f))
                        AuthField(lastNameValue, { lastNameValue = it }, "Last name", Modifier.weight(1f))
                    }
                }
                AuthField(email, { email = it }, "Email address", Modifier.fillMaxWidth(), KeyboardType.Email)
                if (!reset) {
                    AuthField(passwordValue, { passwordValue = it }, "Password", Modifier.fillMaxWidth(), KeyboardType.Password, passwordField = true)
                }
                if (!signUp && !reset) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = ::sendSignInCode, enabled = !loading) {
                            Text("Use an email code")
                        }
                        TextButton(onClick = { onRouteChange("forgot") }) {
                            Text("Forgot password?")
                        }
                    }
                }
                error?.let { AuthError(it) }
                AuthPrimaryButton(
                    label = if (reset) "Send recovery code" else "Continue",
                    loading = loading,
                    enabled = !loading,
                    onClick = ::submitForm
                )
            } else if (phase == PorticoAuthPhase.RESET_PASSWORD) {
                AuthField(passwordValue, { passwordValue = it }, "New password", Modifier.fillMaxWidth(), KeyboardType.Password, passwordField = true)
                error?.let { AuthError(it) }
                AuthPrimaryButton("Continue", loading, !loading, ::resetPassword)
            } else {
                AuthField(code, { code = it.filter(Char::isDigit).take(6) }, "Six-digit code", Modifier.fillMaxWidth(), KeyboardType.Number)
                error?.let { AuthError(it) }
                AuthPrimaryButton("Verify and continue", loading, !loading, ::verify)
                TextButton(onClick = { phase = PorticoAuthPhase.FORM; error = null; code = "" }) { Text("Use a different account") }
            }

            if (phase == PorticoAuthPhase.FORM && !reset) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (signUp) "Already have an account?" else "Don't have an account?", color = PorticoMuted)
                    TextButton(onClick = { onRouteChange(if (signUp) "login" else "register") }) {
                        Text(if (signUp) "Sign in" else "Sign up")
                    }
                }
            } else if (reset) {
                TextButton(onClick = { onRouteChange("login") }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Back to sign in")
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = PorticoOrange, modifier = Modifier.size(16.dp))
                    Text("Secured workspace · verification stays in Portico", style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
                }
                Text(if (BuildConfig.DEBUG) "Development mode" else "Secure workspace", style = MaterialTheme.typography.labelSmall, color = PorticoOrange)
            }
        }
}

@Composable
private fun AuthProviderButton(mark: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(PorticoOrange.copy(alpha = .12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(mark, color = PorticoOrange, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AuthDivider() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text("or", modifier = Modifier.padding(horizontal = 14.dp), color = PorticoMuted, style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AuthPrimaryButton(label: String, loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PorticoOrange, contentColor = Color.White)
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = Color.White)
        } else {
            Text(label)
            Spacer(Modifier.size(8.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    passwordField: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (passwordField) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(15.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PorticoOrange,
            focusedLabelColor = PorticoOrange,
            cursorColor = PorticoOrange
        )
    )
}

@Composable
private fun AuthError(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .24f))
    ) { Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun SetupRequiredCard(modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PorticoLogoLockup(showTagline = false)
            Text("Connect your secure workspace", style = MaterialTheme.typography.headlineSmall)
            Text("Add CLERK_PUBLISHABLE_KEY to enable real in-app sign-in and sign-up.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
