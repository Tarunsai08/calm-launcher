package com.calmlauncher.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile for the critical user journeys:
 * cold start to home, opening the app list, scrolling it, and searching.
 *
 * Run with: ./gradlew :app:generateBaselineProfile (needs a connected device, API 28+).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE)), 5_000)
        // Swipe up to open the drawer.
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, (h * 0.7).toInt(), w / 2, (h * 0.3).toInt(), 10)
        device.waitForIdle()
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        device.waitForIdle()
        device.pressBack()
    }
}

const val TARGET_PACKAGE = "com.calmlauncher"
