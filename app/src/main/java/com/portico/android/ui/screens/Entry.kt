package com.portico.android.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.portico.android.ui.PorticoState
import com.portico.android.ui.design.*
import com.portico.android.ui.theme.PorticoTheme
import kotlinx.coroutines.delay

/**
 * Cold start hands off from the system splash to this, so the mark never
 * blinks out between the two. It waits only as long as the store needs.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(320),
        label = "splash-fade"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(700)
        onFinished()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(alpha)) {
            PorticoMark(size = 72.dp)
            Spacer(Modifier.height(Space.lg))
            Text(
                "PORTICO",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 4.sp8()),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "Property capital, measured",
                style = MaterialTheme.typography.labelSmall,
                color = PorticoTheme.semantic.tertiaryText
            )
        }
    }
}

private fun Int.sp8() = androidx.compose.ui.unit.TextUnit(
    toFloat(), androidx.compose.ui.unit.TextUnitType.Sp
)

private data class OnboardingPage(
    val glyph: Glyph,
    val title: String,
    val body: String
)

private val onboardingPages = listOf(
    OnboardingPage(
        Glyph.PORTFOLIO,
        "Every property in one register",
        "Purchase price, current value, rent and running costs — the facts each decision rests on, kept together."
    ),
    OnboardingPage(
        Glyph.REPORTS,
        "Return worked out, not guessed",
        "ROI, cap rate, gross and net yield and cashflow are computed from what you enter, so they move when your records do."
    ),
    OnboardingPage(
        Glyph.TAX,
        "What you keep, not what you collect",
        "Gross rent falls through expenses and tax to a net figure, modelled for Uruguay and Argentina and editable by you."
    ),
    OnboardingPage(
        Glyph.ASSISTANT,
        "Answers with the working shown",
        "Ask which property earns least after tax, or what a rent change would do. Every answer comes with the arithmetic."
    )
)

@Composable
fun OnboardingScreen(
    state: PorticoState,
    onSignIn: () -> Unit,
    onUseDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = PorticoTheme.semantic
    val page = state.onboardingPage.coerceIn(0, onboardingPages.lastIndex)
    val current = onboardingPages[page]
    val isLast = page == onboardingPages.lastIndex

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Space.lg)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PorticoLockup(markSize = 30.dp, showTagline = false)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSignIn) { Text("Skip") }
        }

        // Weighted rather than centred: content sits in the upper-middle so the
        // page does not open on a band of empty ground.
        Spacer(Modifier.weight(0.5f))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                PorticoIcon(current.glyph, size = 34.dp, tint = MaterialTheme.colorScheme.primary, contentDescription = null)
            }
            Spacer(Modifier.height(Space.xxl))
            Text(
                current.title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Space.md))
            Text(
                current.body,
                style = MaterialTheme.typography.bodyLarge,
                color = semantic.tertiaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp)
            )
        }
        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.lg),
            horizontalArrangement = Arrangement.Center
        ) {
            onboardingPages.indices.forEach { index ->
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(4.dp)
                        .width(if (index == page) 22.dp else 8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index == page) MaterialTheme.colorScheme.primary else semantic.panelSunk
                        )
                )
            }
        }

        if (isLast) {
            PrimaryButton("Sign in or create an account", Modifier.fillMaxWidth(), onClick = onSignIn)
            Spacer(Modifier.height(Space.sm))
            SecondaryButton("Explore with demo data", Modifier.fillMaxWidth(), onClick = onUseDemo)
        } else {
            PrimaryButton("Next", Modifier.fillMaxWidth(), glyph = Glyph.FORWARD, glyphTrailing = true) {
                state.onboardingPage = page + 1
            }
            Spacer(Modifier.height(Space.sm))
            SecondaryButton("Explore with demo data", Modifier.fillMaxWidth(), onClick = onUseDemo)
        }
        Spacer(Modifier.height(Space.sm))
    }
}
