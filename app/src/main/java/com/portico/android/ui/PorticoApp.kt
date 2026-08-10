package com.portico.android.ui

/*
 * PORTICO DESIGN CONTRACT
 * THESIS: Turn property performance into a warm editorial decision desk: the
 * investor sees value, return, and the next move in one calm scan.
 * OWN-WORLD: Off-white paper surfaces, terracotta/orange decision signals,
 * coffee-brown dark mode, faceted portfolio geometry, and motion that explains state.
 * STORY: Launch into a guided cockpit, authenticate, scan the whole perimeter,
 * then move from a number to its property, record, report, or decision.
 * FIRST VIEWPORT: A rotating portfolio orbit anchors value, return, cashflow,
 * and the primary add-property command above the fold on phone and tablet.
 * FORM: Grounded editorial instrument direction, adapted to Android Material
 * 3 navigation, safe insets, dynamic type, TalkBack semantics, and touch-first controls.
 * FINISH: Every existing product route remains reachable, while the shared
 * visual layer supplies the new logo, icon language, depth, motion, and states.
 */

import android.app.Activity
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import com.clerk.api.Clerk
import com.portico.android.BuildConfig
import com.portico.android.ui.screens.AuthScreen
import com.portico.android.ui.screens.OnboardingScreen
import com.portico.android.ui.screens.PorticoMotionProvider
import com.portico.android.ui.screens.PorticoShell
import com.portico.android.ui.screens.SplashScreen
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.launch

@Composable
fun PorticoApp() {
    val state = remember { PorticoState() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val view = LocalView.current
    val clerkConfigured = BuildConfig.CLERK_PUBLISHABLE_KEY.isNotBlank()
    val clerkInitialized by Clerk.isInitialized.collectAsState(initial = false)
    val clerkUser by Clerk.userFlow.collectAsState(initial = null)
    val systemMotionEnabled = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }.getOrDefault(true)
    }

    val accountName = listOfNotNull(clerkUser?.firstName, clerkUser?.lastName)
        .joinToString(" ")
        .ifBlank { "Portico member" }
    val accountEmail = clerkUser?.primaryEmailAddress?.emailAddress ?: "Verified Clerk account"

    LaunchedEffect(clerkConfigured, clerkInitialized, clerkUser, state.route) {
        if (clerkConfigured && clerkInitialized && !state.demoMode) {
            val publicRoutes = setOf("splash", "onboarding", "login", "register", "forgot", "verify")
            if (clerkUser != null && state.route in publicRoutes) {
                state.replaceRoute("dashboard")
            } else if (clerkUser == null && state.route !in publicRoutes) {
                state.replaceRoute("onboarding")
            }
        }
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            state.toastMessage = null
        }
    }

    PorticoTheme(appearance = state.appearance) {
        val darkTheme = when (state.appearance) {
            "Dark mode" -> true
            "Light mode" -> false
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
        BackHandler(enabled = state.canGoBack || (state.route == "onboarding" && state.onboardingPage > 0)) {
            if (state.route == "onboarding" && state.onboardingPage > 0) {
                state.onboardingPage--
            } else {
                state.goBack()
            }
        }
        PorticoMotionProvider(enabled = systemMotionEnabled && !state.reducedMotion) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (state.route) {
                    "splash" -> SplashScreen { state.replaceRoute("onboarding") }
                    "onboarding" -> OnboardingScreen(
                        state = state,
                        onSignIn = {
                            state.demoMode = false
                            state.replaceRoute("login")
                        },
                        onOpenDemo = {
                            state.demoMode = true
                            state.authError = null
                            state.replaceRoute("dashboard")
                        }
                    )
                    "login", "register", "forgot", "verify" -> AuthScreen(
                        route = state.route,
                        onRouteChange = { state.navigate(it) },
                        onAuthComplete = {
                            state.demoMode = false
                            state.authError = null
                            state.replaceRoute("dashboard")
                        },
                        authLoading = state.authLoading,
                        authError = state.authError,
                        clerkConfigured = clerkConfigured
                    )
                    else -> PorticoShell(
                        state = state,
                        accountName = accountName,
                        accountEmail = accountEmail,
                        onSignOut = {
                            scope.launch {
                                if (clerkConfigured && clerkInitialized) Clerk.auth.signOut()
                                state.demoMode = false
                                state.replaceRoute("onboarding")
                            }
                        }
                    )
                }
                SnackbarHost(hostState = snackbarHostState)
            }
        }
    }
}
