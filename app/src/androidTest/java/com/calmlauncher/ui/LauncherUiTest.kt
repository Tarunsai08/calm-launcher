package com.calmlauncher.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    @Test
    fun swipeUpOpensDrawerWithSearch() {
        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasSetTextAction()).performTextInput("launcher settings")
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText(str(R.string.sc_launcher_settings))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun searchCalculatorShowsResult() {
        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasSetTextAction()).performTextInput("12*3+4")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("= 40").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun settingsAreSearchable() {
        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.drawer_launcher_settings)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.drawer_launcher_settings)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.settings_search)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasSetTextAction()).performTextInput("font")
        rule.waitUntil(5_000) { rule.onAllNodesWithText(str(R.string.set_font)).fetchSemanticsNodes().isNotEmpty() }
        rule.enableAccessibilityChecks()
        rule.onRoot().tryPerformAccessibilityChecks()
    }
}
