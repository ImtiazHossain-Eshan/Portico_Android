package com.portico.android.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.portico.android.ui.PorticoState
import com.portico.android.ui.theme.PorticoMuted
import com.portico.android.ui.theme.PorticoOrange
import com.portico.android.ui.theme.PorticoTeal
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val motionEnabled = LocalPorticoMotion.current
    LaunchedEffect(Unit) {
        delay(if (motionEnabled) 1100 else 220)
        onFinished()
    }
    CockpitBackdrop(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PorticoLogoMark(Modifier.size(132.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                "PORTICO",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    letterSpacing = 2.8.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "PROPERTY CAPITAL / 01",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .58f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "A clearer flight path for property capital",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = .12.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .70f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))
            SystemReadyBadge()
        }
    }
}

@Composable
private fun SystemReadyBadge() {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, PorticoTeal.copy(alpha = .28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(PorticoTeal))
            Text("SYSTEM", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp), color = PorticoMuted)
            Box(Modifier.width(1.dp).height(14.dp).background(PorticoTeal.copy(alpha = .28f)))
            Text("READY", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = .9.sp), color = PorticoTeal)
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(state: PorticoState, onSignIn: () -> Unit, onOpenDemo: () -> Unit) {
    val pages = listOf(
        Triple("See the whole orbit", "A live cockpit for value, yield, cashflow, tax exposure, and the properties behind every number.", PorticoGlyphType.Overview),
        Triple("Make the next move legible", "Run valuation and acquisition scenarios without losing the context that made you trust the decision.", PorticoGlyphType.Valuation),
        Triple("Keep every record in range", "Private documents, tax jurisdictions, and assistant conversations stay attached to the portfolio perimeter.", PorticoGlyphType.Security)
    )
    CockpitBackdrop(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        PorticoLogoLockup(showTagline = true)
                        Text("Property intelligence,\nwith altitude.", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
                        Text("Portico turns a scattered property register into a decision surface you can actually navigate.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .72f))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            InstrumentBadge("PRIVATE", "BY DEFAULT", PorticoTeal)
                            InstrumentBadge("SCENARIOS", "ON DEMAND", PorticoOrange)
                        }
                    }
                    Column(modifier = Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        PortfolioOrbit(Modifier.fillMaxWidth().height(220.dp), progress = .74f, label = "PORTFOLIO ORBIT / 01")
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            repeat(pages.size) { index ->
                                Box(Modifier.size(if (state.onboardingPage == index) 30.dp else 9.dp, 8.dp).background(if (state.onboardingPage == index) PorticoTeal else PorticoMuted.copy(alpha = .45f)))
                            }
                        }
                        OnboardingCopy(state, pages)
                        OnboardingActions(state, pages, onSignIn, onOpenDemo)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    PorticoLogoLockup(showTagline = true)
                    PortfolioOrbit(Modifier.fillMaxWidth().height(180.dp), progress = .74f, label = "PORTFOLIO ORBIT / 01")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        repeat(pages.size) { index ->
                            Box(Modifier.size(if (state.onboardingPage == index) 30.dp else 9.dp, 8.dp).background(if (state.onboardingPage == index) PorticoTeal else PorticoMuted.copy(alpha = .45f)))
                        }
                    }
                    OnboardingCopy(state, pages)
                    OnboardingActions(state, pages, onSignIn, onOpenDemo)
                    Surface(modifier = Modifier.align(Alignment.CenterHorizontally), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .56f), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PorticoGlyph(PorticoGlyphType.Security, Modifier.size(20.dp), PorticoTeal)
                            Column {
                                Text("PRIVATE BY DEFAULT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text("Illustrative workspace · records stay local", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingCopy(state: PorticoState, pages: List<Triple<String, String, PorticoGlyphType>>) {
    AnimatedContent(targetState = state.onboardingPage, transitionSpec = { androidx.compose.animation.fadeIn(tween(260)) togetherWith androidx.compose.animation.fadeOut(tween(160)) }, label = "onboarding-copy") { page ->
        val current = pages[page]
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PorticoGlyph(current.third, Modifier.size(32.dp), PorticoTeal)
            Text(current.first, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(current.second, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .72f))
        }
    }
}

@Composable
private fun OnboardingActions(state: PorticoState, pages: List<Triple<String, String, PorticoGlyphType>>, onSignIn: () -> Unit, onOpenDemo: () -> Unit) {
    Button(onClick = {
        if (state.onboardingPage == pages.lastIndex) onOpenDemo() else state.onboardingPage += 1
    }, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.onboardingPage == pages.lastIndex) "Enter the demo cockpit" else "Continue")
    }
    FilledTonalButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text("Sign in / Sign up") }
    Text("You can change appearance and motion preferences from Profile.", style = MaterialTheme.typography.labelSmall, color = PorticoMuted)
}
