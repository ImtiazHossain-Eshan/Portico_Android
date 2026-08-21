package com.portico.android.ui

/*
 * PORTICO: DIRECTION CONTRACT
 *
 * THESIS: A property portfolio read as an instrument, not a brochure. Portico
 * refuses the category's card-per-metric dashboard and puts the arithmetic on
 * screen: gross rent falling through expenses and tax to the net number that
 * actually matters.
 *
 * OWN-WORLD: Neutral graphite and bone grounds, hairline-ruled panels instead
 * of floating cards, tabular figures so money columns align, and one amber
 * accent (bright on graphite, bronze on bone) reserved for action and
 * selection so green and red mean only gain and loss.
 *
 * STORY: The investor lands on their position, opens any figure to the records
 * beneath it, and leaves knowing which property to act on.
 *
 * FIRST VIEWPORT: Portfolio value set large in tabular figures, a signed delta
 * beneath it, a hairline value chart, then the gross-to-net waterfall, the
 * product's whole argument above the fold.
 *
 * FORM: Trading-terminal instrument field, fused. Candidate 3 of the grounded
 * list; seed key 16363a61, scope direction, mode operate.
 *
 * FINISH: unreviewed and undocumented is unfinished; this build ends with the
 * finish review, the verdict, and DESIGN.md.
 */

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import com.clerk.api.Clerk
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.installations.FirebaseInstallations
import com.portico.android.BuildConfig
import com.portico.android.PorticoMessagingService
import com.portico.android.data.FirebaseBackend
import com.portico.android.data.FirebaseConnection
import com.portico.android.data.PorticoStore
import com.portico.android.data.PorticoBackend
import com.portico.android.data.await
import com.portico.android.ui.screens.AuthScreen
import com.portico.android.ui.screens.OnboardingScreen
import com.portico.android.ui.screens.SplashScreen
import com.portico.android.ui.theme.Appearance
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.launch

@Composable
fun PorticoApp() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val store = remember { PorticoStore(context.applicationContext, scope) }
    val state = remember { PorticoState(store) }
    val snackbarHostState = remember { SnackbarHostState() }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) state.notify("Notifications remain off. You can enable them from Android settings.")
    }

    val clerkConfigured = BuildConfig.CLERK_PUBLISHABLE_KEY.isNotBlank()
    val clerkInitialized by Clerk.isInitialized.collectAsState(initial = false)
    val clerkUser by Clerk.userFlow.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        store.load()
        state.offline = !context.hasNetwork()
    }

    // Keep the app's own record in step with the identity provider's session.
    LaunchedEffect(clerkUser, clerkInitialized) {
        if (clerkConfigured && clerkInitialized) {
            val user = clerkUser
            if (user != null) {
                state.signedIn = true
                state.demoMode = false
                val name = listOfNotNull(user.firstName, user.lastName).joinToString(" ").trim()
                val email = user.primaryEmailAddress?.emailAddress.orEmpty()
                store.loadAccount(user.id, name, email)
                if (BuildConfig.FIREBASE_CONFIGURED) {
                    if (FirebaseBackend.connect(context.applicationContext) is FirebaseConnection.Connected) {
                        store.syncCloud()
                        // Market figures are context, not records: fetched once
                        // per session, never persisted, never blocking.
                        store.refreshMarket()
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        runCatching {
                            FirebaseMessaging.getInstance().register().await()
                            val installationId = PorticoMessagingService.pendingToken(context)
                                ?: FirebaseInstallations.getInstance().id.await()
                            PorticoBackend.registerDevice(installationId)
                            PorticoMessagingService.clearPendingToken(context)
                        }
                    }
                }
                if (state.route in Route.public) state.replaceRoute(Route.DASHBOARD)
            } else if (state.signedIn) {
                // The session ended outside the app.
                state.signedIn = false
                FirebaseBackend.disconnect()
                store.closeWorkspace()
                if (!state.demoMode) state.sessionExpired = true
            }
        }
    }

    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            state.toast = null
        }
    }

    PorticoTheme(appearance = store.preferences.theme) {
        val darkTheme = when (store.preferences.theme) {
            Appearance.DARK -> true
            Appearance.LIGHT -> false
            else -> isSystemInDarkTheme()
        }
        SideEffect {
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }

        // System Back never traps the user: it steps onboarding, unwinds the
        // stack, and otherwise falls through to the platform.
        BackHandler(
            enabled = state.canGoBack ||
                (state.route == Route.ONBOARDING && state.onboardingPage > 0) ||
                state.route in setOf(Route.REGISTER, Route.FORGOT)
        ) {
            when {
                state.route == Route.ONBOARDING && state.onboardingPage > 0 -> state.onboardingPage--
                state.route in setOf(Route.REGISTER, Route.FORGOT) -> state.replaceRoute(Route.LOGIN)
                else -> state.goBack()
            }
        }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                when (state.route) {
                    Route.SPLASH -> SplashScreen(
                        onFinished = {
                            state.replaceRoute(
                                if (clerkConfigured && clerkUser != null) Route.DASHBOARD else Route.ONBOARDING
                            )
                        }
                    )

                    Route.ONBOARDING -> OnboardingScreen(
                        state = state,
                        onSignIn = {
                            state.demoMode = false
                            state.replaceRoute(Route.LOGIN)
                        },
                        onUseDemo = {
                            scope.launch {
                                store.loadDemo()
                                state.demoMode = true
                                state.replaceRoute(Route.DASHBOARD)
                            }
                        }
                    )

                    Route.LOGIN, Route.REGISTER, Route.FORGOT -> AuthScreen(
                        state = state,
                        route = state.route,
                        onAuthenticated = {
                            scope.launch {
                                Clerk.user?.let { user ->
                                    val name = listOfNotNull(user.firstName, user.lastName)
                                        .joinToString(" ")
                                        .trim()
                                    store.loadAccount(
                                        user.id,
                                        name,
                                        user.primaryEmailAddress?.emailAddress.orEmpty()
                                    )
                                }
                                state.signedIn = true
                                state.demoMode = false
                                state.sessionExpired = false
                                state.replaceRoute(Route.DASHBOARD)
                            }
                        },
                        onUseDemo = {
                            scope.launch {
                                store.loadDemo()
                                state.demoMode = true
                                state.sessionExpired = false
                                state.replaceRoute(Route.DASHBOARD)
                            }
                        }
                    )

                    else -> PorticoShell(
                        state = state,
                        onSignOut = {
                            scope.launch {
                                if (clerkConfigured && clerkInitialized) {
                                    runCatching { Clerk.auth.signOut() }
                                }
                                state.signedIn = false
                                state.demoMode = false
                                FirebaseBackend.disconnect()
                                store.closeWorkspace()
                                state.replaceRoute(Route.ONBOARDING)
                                state.onboardingPage = 0
                            }
                        }
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

private fun Context.hasNetwork(): Boolean = runCatching {
    val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}.getOrDefault(true)
