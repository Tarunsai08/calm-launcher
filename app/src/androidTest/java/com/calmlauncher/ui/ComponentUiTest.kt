package com.calmlauncher.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.calmlauncher.R
import com.calmlauncher.core.designsystem.CalmLauncherTheme
import com.calmlauncher.core.designsystem.ToggleRow
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.feature.gate.FrictionChallenge
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Component-level UI tests that don't need the full app graph. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ComponentUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun toggleRowIsAccessibleAndToggles() {
        var checked = false
        rule.setContent {
            CalmLauncherTheme(LauncherSettings.DEFAULT) {
                ToggleRow(title = "Show seconds", description = "Uses a little more battery.", checked = checked, onCheckedChange = { checked = it })
            }
        }
        rule.enableAccessibilityChecks()
        rule.onRoot().tryPerformAccessibilityChecks()
        rule.onNodeWithText("Show seconds").performClick()
        assertTrue(checked)
    }

    @Test
    fun frictionChallengeShowsPhrase() {
        rule.setContent {
            CalmLauncherTheme(LauncherSettings.DEFAULT) {
                FrictionChallenge(challenge = "quiet river", onCancel = {}, onPassed = {}, waitSeconds = 0)
            }
        }
        rule.onNodeWithText(context.getString(R.string.friction_type, "quiet river")).assertExists()
    }
}
