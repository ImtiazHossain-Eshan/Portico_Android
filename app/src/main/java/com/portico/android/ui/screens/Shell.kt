@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.portico.android.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.portico.android.ui.PorticoState
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoNavy
import com.portico.android.ui.theme.PorticoTeal

private data class NavItem(val route: String, val label: String, val glyph: PorticoGlyphType)

private val navigationItems = listOf(
    NavItem("dashboard", "Overview", PorticoGlyphType.Overview),
    NavItem("portfolio", "Portfolio", PorticoGlyphType.Portfolio),
    NavItem("reports", "Reports", PorticoGlyphType.Reports),
    NavItem("assistant", "Assistant", PorticoGlyphType.Assistant),
    NavItem("profile", "Profile", PorticoGlyphType.Profile)
)

@Composable
fun PorticoShell(
    state: PorticoState,
    accountName: String,
    accountEmail: String,
    onSignOut: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val expanded = maxWidth >= 600.dp
        if (expanded) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    header = {
                        PorticoLogoMark(Modifier.padding(top = 20.dp, bottom = 22.dp).size(48.dp))
                    }
                ) {
                    navigationItems.forEach { item ->
                        NavigationRailItem(
                            selected = state.route == item.route,
                            onClick = { state.selectDestination(item.route) },
                            icon = { PorticoGlyph(item.glyph, tint = if (state.route == item.route) MaterialTheme.colorScheme.primary else PorticoMuted) },
                            label = { Text(item.label) },
                            modifier = Modifier.semantics { contentDescription = "Open ${item.label}" }
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                    AppTopBar(state)
                    ScreenContent(state, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp), accountName, accountEmail, onSignOut)
                }
            }
        } else {
            Scaffold(
                topBar = { AppTopBar(state) },
                bottomBar = {
                    NavigationBar(modifier = Modifier.navigationBarsPadding(), containerColor = MaterialTheme.colorScheme.surface) {
                        navigationItems.forEach { item ->
                            NavigationBarItem(
                                selected = state.route == item.route,
                                onClick = { state.selectDestination(item.route) },
                                icon = { PorticoGlyph(item.glyph, tint = if (state.route == item.route) MaterialTheme.colorScheme.primary else PorticoMuted) },
                                label = { Text(item.label) },
                                modifier = Modifier.semantics { contentDescription = "Open ${item.label}" }
                            )
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                ScreenContent(state, Modifier.fillMaxSize().padding(padding), accountName, accountEmail, onSignOut)
            }
        }
    }
}

@Composable
private fun AppTopBar(state: PorticoState) {
    val childRoute = state.route !in navigationItems.map { it.route }
    val title = when (state.route) {
        "dashboard" -> "Good morning, Imtiaz"
        "portfolio" -> "Portfolio register"
        "reports" -> "Reports & analysis"
        "assistant" -> "Portico intelligence"
        "profile" -> "Account & settings"
        "property" -> state.properties.firstOrNull { it.id == state.selectedPropertyId }?.name ?: "Property detail"
        "add" -> "Add property"
        "documents" -> "Document library"
        "tax" -> "Tax position"
        "valuation" -> "Property valuation"
        "acquisition" -> "Acquisition lab"
        "subscription" -> "Portico plans"
        "enterprise" -> "Enterprise workspace"
        else -> "Portico"
    }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!childRoute) PorticoLogoMark(Modifier.size(30.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    if (state.route == "dashboard") Text("MONDAY · 21 MAR 2026  /  LOCAL DEMO", style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
                }
            }
        },
        navigationIcon = {
            if (childRoute) IconButton(onClick = { if (!state.goBack()) state.selectDestination("dashboard") }, modifier = Modifier.semantics { contentDescription = "Go back" }) { PorticoGlyph(PorticoGlyphType.Back) }
        },
        actions = {
            if (!childRoute) {
                IconButton(onClick = { state.toastMessage = "No new alerts" }, modifier = Modifier.semantics { contentDescription = "Notifications" }) { Icon(Icons.Outlined.NotificationsNone, contentDescription = null) }
            } else {
                IconButton(onClick = { state.toastMessage = "More actions are available from this surface" }, modifier = Modifier.semantics { contentDescription = "More actions" }) { Icon(Icons.Outlined.MoreVert, contentDescription = null) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ScreenContent(
    state: PorticoState,
    modifier: Modifier = Modifier,
    accountName: String,
    accountEmail: String,
    onSignOut: () -> Unit
) {
    val contentModifier = modifier.verticalScroll(rememberScrollState())
    Crossfade(targetState = state.route, animationSpec = tween(320), label = "screen-transition") { route ->
        when (route) {
            "dashboard" -> DashboardScreen(state, contentModifier)
            "portfolio" -> PortfolioScreen(state, contentModifier)
            "reports" -> ReportsScreen(state, contentModifier)
            "assistant" -> AssistantScreen(state, contentModifier)
            "profile" -> ProfileScreen(state, accountName, accountEmail, onSignOut, contentModifier)
            "property" -> PropertyDetailScreen(state, contentModifier)
            "add" -> AddPropertyScreen(state, contentModifier)
            "documents" -> DocumentsScreen(state, contentModifier)
            "tax" -> TaxScreen(state, contentModifier)
            "valuation" -> ValuationScreen(state, contentModifier)
            "acquisition" -> AcquisitionScreen(state, contentModifier)
            "subscription" -> SubscriptionScreen(state, contentModifier)
            "enterprise" -> EnterpriseScreen(state, contentModifier)
            else -> DashboardScreen(state, contentModifier)
        }
    }
}

@Composable
fun AuthScreen(
    route: String,
    onRouteChange: (String) -> Unit,
    onAuthComplete: () -> Unit,
    authLoading: Boolean,
    authError: String?,
    clerkConfigured: Boolean
) {
    CockpitBackdrop(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        PorticoLogoLockup()
                        Text("Navigate the\nproperty orbit.", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
                        Text("A private command deck for value, yield, documents, tax, and the decision in front of you.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .72f))
                        PortfolioOrbit(Modifier.fillMaxWidth(.72f).size(260.dp), label = "SECURE WORKSPACE / 01")
                    }
                    PorticoAuthForm(
                        route = route,
                        onRouteChange = onRouteChange,
                        onAuthComplete = onAuthComplete,
                        clerkConfigured = clerkConfigured,
                        modifier = Modifier.width(430.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 28.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PorticoAuthForm(
                        route = route,
                        onRouteChange = onRouteChange,
                        onAuthComplete = onAuthComplete,
                        clerkConfigured = clerkConfigured,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthForm(
    route: String,
    onRouteChange: (String) -> Unit,
    onAuthComplete: () -> Unit,
    authLoading: Boolean,
    authError: String?,
    clerkConfigured: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PorticoLogoLockup(showTagline = false)
            val title = when (route) {
                "register" -> "Create your flight plan"
                "forgot" -> "Reset access securely"
                "verify" -> "Verify your workspace"
                else -> "Welcome back"
            }
            val description = when (route) {
                "register" -> "Create a real Portico account with Clerk. Email verification and password security stay inside the native app flow."
                "forgot" -> "Clerk will guide you through password recovery and any required email verification inside the app."
                "verify" -> "Verification is handled by Clerk so the code, session, and account state stay connected."
                else -> "Sign in to your protected workspace. Clerk supports the configured password, email, social, and MFA methods."
            }
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!clerkConfigured) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        "Clerk setup required: add CLERK_PUBLISHABLE_KEY to Gradle before using account access.",
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            authError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clerk setup required")
            }
            if (route == "register") {
                Text(
                    "Already have an account? Sign in",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .clickable { onRouteChange("login") }
                        .padding(8.dp)
                )
            } else if (route == "login") {
                Text(
                    "Forgot password?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.End)
                        .clickable { onRouteChange("forgot") }
                        .padding(5.dp)
                )
                Text(
                    "New to Portico? Create account",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .clickable { onRouteChange("register") }
                        .padding(8.dp)
                )
            } else {
                Text(
                    "Back to sign in",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .clickable { onRouteChange("login") }
                        .padding(8.dp)
                )
            }
            Text(
                "By continuing, you agree to keep financial decisions grounded in verified records. Clerk manages the authentication session; Portico never stores your password.",
                style = MaterialTheme.typography.labelSmall,
                color = PorticoMuted
            )
        }
    }
}

@Composable
private fun LegacyAuthForm(
    route: String,
    onRouteChange: (String) -> Unit,
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("imtiaz@example.com") }
    var password by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("password") }
    var name by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var code by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    Surface(modifier = modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp, border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PorticoLogoLockup(showTagline = false)
            when (route) {
                "forgot" -> {
                    Text("Reset access", style = MaterialTheme.typography.headlineMedium)
                    Text("We’ll send a secure recovery link to the email on your workspace.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { onRouteChange("verify") }, modifier = Modifier.fillMaxWidth()) { Text("Send recovery link") }
                    Text("Back to sign in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally).semantics { contentDescription = "Back to sign in" }.padding(8.dp))
                }
                "register" -> {
                    Text("Create your flight plan", style = MaterialTheme.typography.headlineMedium)
                    Text("Start with a private portfolio workspace.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { onRouteChange("verify") }, modifier = Modifier.fillMaxWidth()) { Text("Create account") }
                    Text("Already have an account? Sign in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onRouteChange("login") }.padding(8.dp))
                }
                "verify" -> {
                    Text("Verify your workspace", style = MaterialTheme.typography.headlineMedium)
                    Text("Enter the six-digit code from your email. Demo code: 123456.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(code, { code = it.take(6) }, label = { Text("Verification code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = onAuthenticated, modifier = Modifier.fillMaxWidth()) { Text("Verify and enter") }
                    Text("Use demo workspace instead", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onAuthenticated).padding(8.dp))
                }
                else -> {
                    Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
                    Text("Your next decision is already in range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Forgot password?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.End).clickable { onRouteChange("forgot") }.padding(5.dp))
                    Button(onClick = onAuthenticated, modifier = Modifier.fillMaxWidth()) { Text("Sign in") }
                    FilledTonalButton(onClick = onAuthenticated, modifier = Modifier.fillMaxWidth()) { Text("Continue with demo portfolio") }
                    Text("New to Portico? Create account", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onRouteChange("register") }.padding(8.dp))
                }
            }
            Text("By continuing, you agree to keep financial decisions grounded in verified records.", style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
        }
    }
}
