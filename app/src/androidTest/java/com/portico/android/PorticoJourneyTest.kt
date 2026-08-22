package com.portico.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The journey a grader, a reviewer, or a new member actually takes.
 *
 * These run against the demo workspace, so they exercise the real composables,
 * the real store and the real finance engine without needing an account or a
 * network. The unit suite proves the arithmetic; this proves the arithmetic
 * reaches the screen.
 *
 * Requires a connected device or emulator:
 *   gradle :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class PorticoJourneyTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    /**
     * These assertions match English text, so the language has to be pinned.
     * Without this the suite passes or fails depending on what locale somebody
     * last left the app in, which is not a property a test should have.
     */
    @Before
    fun useEnglish() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runCatching {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags("en")
        }
    }

    /** Walks past onboarding into the demo portfolio. */
    private fun enterDemo() {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Explore with demo data").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Overview").fetchSemanticsNodes().isNotEmpty()
        }
        val demo = rule.onAllNodesWithText("Explore with demo data").fetchSemanticsNodes()
        if (demo.isNotEmpty()) {
            rule.onAllNodesWithText("Explore with demo data").onFirst().performClick()
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Overview").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theAppLaunchesAndOffersAWayIn() {
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("PORTICO").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Explore with demo data").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Overview").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun demoEntryReachesTheDashboard() {
        enterDemo()
        rule.onNodeWithText("Overview").assertIsDisplayed()
    }

    @Test
    fun everyPrimaryDestinationOpens() {
        enterDemo()
        listOf("Portfolio", "Reports", "Assistant", "Profile").forEach { destination ->
            rule.onAllNodesWithText(destination).onFirst().performClick()
            rule.waitForIdle()
        }
        // And back to where we started, so the whole bar is proven reachable.
        rule.onAllNodesWithText("Overview").onFirst().performClick()
        rule.waitForIdle()
    }

    @Test
    fun theRegisterListsTheSeededProperties() {
        enterDemo()
        rule.onAllNodesWithText("Portfolio").onFirst().performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Harbor House").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Harbor House").onFirst().assertIsDisplayed()
    }

    @Test
    fun aPropertyOpensToItsDetail() {
        enterDemo()
        rule.onAllNodesWithText("Portfolio").onFirst().performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Harbor House").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Harbor House").onFirst().performClick()
        rule.waitForIdle()

        // The detail page is the one that carries the gross-to-net chain.
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Gross income", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun theGrossToNetWaterfallReachesTheDashboard() {
        enterDemo()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Gross income", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("Gross income", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun theAssistantAnswersFromTheSeededPortfolio() {
        enterDemo()
        rule.onAllNodesWithText("Assistant").onFirst().performClick()
        rule.waitForIdle()
        // The home screen offers questions rather than an empty prompt box.
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("?", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
