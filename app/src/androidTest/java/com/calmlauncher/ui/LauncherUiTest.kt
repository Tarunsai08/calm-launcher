package com.calmlauncher.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.calmlauncher.MainActivity
import com.calmlauncher.R
import com.calmlauncher.feature.drawer.DrawerTestTags
import com.calmlauncher.feature.settings.SETTINGS_SEARCH_TAG
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests over the real app graph. They tolerate either first-run state (onboarding)
 * or an already-configured launcher.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class LauncherUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    @Before
    fun skipOnboardingIfShown() {
        rule.waitForIdle()
        val skip = rule.onAllNodesWithText(str(R.string.onboarding_skip)).fetchSemanticsNodes()
        if (skip.isNotEmpty()) {
            rule.onNodeWithText(str(R.string.onboarding_skip)).performClick()
            rule.waitForIdle()
        }
    }

    @Test
    fun homeShowsFocusToggleAndPassesAccessibilityChecks() {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText(str(R.string.home_focus_off)).fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText(str(R.string.home_focus_on)).fetchSemanticsNodes().isNotEmpty()
        }
        rule.enableAccessibilityChecks()
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    /**
     * Opens the app list with a swipe that starts well above the system gesture area, the
     * same way a person swipes. (A swipe starting on the very bottom edge belongs to the
     * system home gesture and is ignored by the launcher on purpose.)
     */
    private fun openDrawer() {
        rule.onRoot().performTouchInput {
            swipeUp(startY = height * 0.7f, endY = height * 0.2f)
        }
        rule.waitUntil(TIMEOUT) { rule.onAllNodesWithTag(DrawerTestTags.SEARCH).fetchSemanticsNodes().isNotEmpty() }
        // Let the keyboard open and the list settle before interacting.
        rule.waitForIdle()
    }

    @Test
    fun swipeUpOpensDrawerWithSearch() {
        openDrawer()
        rule.onNodeWithTag(DrawerTestTags.SEARCH).performTextInput("launcher settings")
        rule.waitUntil(TIMEOUT) {
            rule.onAllNodes(hasText(str(R.string.sc_launcher_settings))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun searchCalculatorShowsResult() {
        openDrawer()
        rule.onNodeWithTag(DrawerTestTags.SEARCH).performTextInput("12*3+4")
        rule.waitUntil(TIMEOUT) { rule.onAllNodesWithText("= 40").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun settingsAreSearchable() {
        openDrawer()
        // The settings entry is pinned in the drawer header, so it is reachable no matter how
        // many apps are installed or whether the keyboard is covering the list.
        rule.onNodeWithTag(DrawerTestTags.SETTINGS)
            .assertContentDescriptionEquals(str(R.string.drawer_launcher_settings))
            .performClick()
        rule.waitUntil(TIMEOUT) { rule.onAllNodesWithTag(SETTINGS_SEARCH_TAG).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithTag(SETTINGS_SEARCH_TAG).performTextInput("font")
        rule.waitUntil(TIMEOUT) { rule.onAllNodesWithText(str(R.string.set_font)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.set_font)).assertIsDisplayed()
        rule.enableAccessibilityChecks()
        rule.onRoot().tryPerformAccessibilityChecks()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
