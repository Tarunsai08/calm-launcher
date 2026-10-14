package com.calmlauncher.service.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.calmlauncher.data.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class SystemNavMode { BUTTONS, TWO_BUTTONS, GESTURES, UNKNOWN }

/** State of a "hide buttons" try that the user still has to confirm with a working swipe. */
sealed interface HideTrial {
    data object Idle : HideTrial
    data class Waiting(val secondsLeft: Int) : HideTrial
    data object Confirmed : HideTrial
    data object RolledBack : HideTrial
}

/**
 * Reads the system navigation mode and, on Xiaomi phones only, hides or restores the system
 * navigation buttons.
 *
 * Xiaomi's HyperOS/MIUI switches to the 3-button bar whenever a third-party launcher is the
 * home app. Its own "full screen gestures" key (`force_fsg_nav_bar`) still hides the bar, but
 * the system's home/recents gestures then stop responding, so this launcher's own edge
 * gestures take over. Writing that key needs WRITE_SECURE_SETTINGS, which Android only lets a
 * user grant once from a computer (adb). The launcher never asks for anything else.
 *
 * Safety: buttons are only hidden while the gesture overlay is running, the previous value is
 * remembered and restored, and every new "hide" must be confirmed by a working swipe within
 * a few seconds or the buttons come back on their own.
 */
@Singleton
class NavBarController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _trial = MutableStateFlow<HideTrial>(HideTrial.Idle)
    val trial: StateFlow<HideTrial> = _trial.asStateFlow()
    private var trialJob: Job? = null

    fun systemNavMode(): SystemNavMode = when (
        runCatching { Settings.Secure.getInt(context.contentResolver, KEY_NAV_MODE, -1) }.getOrDefault(-1)
    ) {
        0 -> SystemNavMode.BUTTONS
        1 -> SystemNavMode.TWO_BUTTONS
        2 -> SystemNavMode.GESTURES
        else -> SystemNavMode.UNKNOWN
    }

    fun isXiaomi(): Boolean {
        val brand = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return listOf("xiaomi", "redmi", "poco").any { brand.contains(it) }
    }

    fun hasSecureSettingsPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun canHideButtons(): Boolean = isXiaomi() && hasSecureSettingsPermission()

    /** True when buttons are hidden, by us or by the system's own gesture mode. */
    fun buttonsHidden(): Boolean = (isXiaomi() && readFsg() == 1) || systemNavMode() == SystemNavMode.GESTURES

    fun hiddenByUs(): Boolean = prefs.getBoolean(KEY_HIDDEN_BY_US, false)

    /** The one-time command the user runs on a computer to allow hiding the buttons. */
    fun grantCommand(): String =
        "adb shell pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"

    fun hideButtons(): Boolean {
        if (!canHideButtons()) return false
        if (!hiddenByUs()) {
            prefs.edit().putInt(KEY_PREVIOUS, readFsg()).putBoolean(KEY_HIDDEN_BY_US, true).apply()
        }
        return writeFsg(1)
    }

    /** Puts the buttons back exactly as they were before we hid them. Safe to call any time. */
    fun restoreButtons() {
        cancelTrial()
        if (!hiddenByUs()) return
        val previous = prefs.getInt(KEY_PREVIOUS, 0)
        if (writeFsg(previous) || !hasSecureSettingsPermission()) {
            prefs.edit().putBoolean(KEY_HIDDEN_BY_US, false).apply()
        }
    }

    /**
     * Called at app start: if the buttons were hidden by us but the focus helper (which draws
     * the gesture strips) is no longer switched on, bring the buttons back.
     */
    fun restoreIfHelperOff() {
        if (hiddenByUs() && !isHelperEnabled()) restoreButtons()
    }

    fun isHelperEnabled(): Boolean {
        val enabled = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        }.getOrNull().orEmpty()
        return enabled.split(':').any { it.startsWith(context.packageName + "/") }
    }

    /**
     * Hides the buttons for [seconds]; if [confirmTrial] isn't called in time (a working swipe
     * from the bottom edge), the buttons come back automatically.
     */
    fun startTrial(seconds: Int = TRIAL_SECONDS): Boolean {
        if (!hideButtons()) return false
        trialJob?.cancel()
        trialJob = scope.launch {
            for (left in seconds downTo 1) {
                _trial.value = HideTrial.Waiting(left)
                delay(1_000)
            }
            restoreButtons()
            _trial.value = HideTrial.RolledBack
        }
        return true
    }

    fun isTrialRunning(): Boolean = _trial.value is HideTrial.Waiting

    fun confirmTrial() {
        if (!isTrialRunning()) return
        trialJob?.cancel()
        trialJob = null
        _trial.value = HideTrial.Confirmed
    }

    fun resetTrialState() {
        if (!isTrialRunning()) _trial.value = HideTrial.Idle
    }

    private fun cancelTrial() {
        trialJob?.cancel()
        trialJob = null
        if (_trial.value is HideTrial.Waiting) _trial.value = HideTrial.Idle
    }

    private fun readFsg(): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, KEY_FSG, 0) }.getOrDefault(0)

    private fun writeFsg(value: Int): Boolean =
        runCatching { Settings.Global.putInt(context.contentResolver, KEY_FSG, value) }.getOrDefault(false)

    companion object {
        private const val PREFS = "calm_navigation"
        private const val KEY_HIDDEN_BY_US = "hidden_by_us"
        private const val KEY_PREVIOUS = "previous_fsg"
        private const val KEY_FSG = "force_fsg_nav_bar"
        private const val KEY_NAV_MODE = "navigation_mode"
        const val TRIAL_SECONDS = 20
    }
}
