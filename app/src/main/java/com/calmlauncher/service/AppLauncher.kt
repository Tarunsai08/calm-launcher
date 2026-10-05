package com.calmlauncher.service

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import com.calmlauncher.R
import com.calmlauncher.data.apps.AppSource
import com.calmlauncher.domain.model.AppKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class AppShortcut(
    val id: String,
    val label: String,
    val packageName: String,
    val info: ShortcutInfo,
)

/** Every way the launcher starts something. All calls are failure-tolerant and never throw. */
@Singleton
class AppLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: AppSource,
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    fun launchApp(key: AppKey, bounds: Rect? = null): Boolean {
        val user = source.userFor(key.userSerial) ?: return fallbackLaunch(key.packageName)
        return try {
            launcherApps.startMainActivity(ComponentName(key.packageName, key.className), user, bounds, null)
            true
        } catch (_: SecurityException) {
            fallbackLaunch(key.packageName)
        } catch (_: ActivityNotFoundException) {
            fallbackLaunch(key.packageName)
        } catch (_: IllegalStateException) {
            // Work profile paused or user locked.
            toast(R.string.launch_failed_profile)
            false
        }
    }

    private fun fallbackLaunch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            toast(R.string.launch_failed)
            return false
        }
        return start(intent)
    }

    /** Starts [intent]; shows [failureMessage] on failure unless it is null (silent attempt). */
    fun start(intent: Intent, failureMessage: Int? = R.string.launch_failed): Boolean {
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            failureMessage?.let { toast(it) }
            false
        } catch (_: SecurityException) {
            failureMessage?.let { toast(it) }
            false
        }
    }

    fun openAppInfo(key: AppKey) {
        val user = source.userFor(key.userSerial)
        try {
            if (user != null) {
                launcherApps.startAppDetailsActivity(ComponentName(key.packageName, key.className), user, null, null)
                return
            }
        } catch (_: Exception) {
            // Fall through to the settings intent.
        }
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", key.packageName, null)))
    }

    fun uninstall(key: AppKey) {
        val user = source.userFor(key.userSerial)
        if (user != null && user != Process.myUserHandle()) {
            // The system uninstaller only acts on the current profile; app info works for all.
            openAppInfo(key)
            return
        }
        start(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null)))
    }

    // ---- Shortcuts (static/dynamic/pinned). Requires being the default launcher. ----

    fun hasShortcutAccess(): Boolean = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    fun shortcutsFor(key: AppKey): List<AppShortcut> {
        if (!hasShortcutAccess()) return emptyList()
        val user = source.userFor(key.userSerial) ?: return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(key.packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        return try {
            launcherApps.getShortcuts(query, user).orEmpty()
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .take(MAX_SHORTCUTS)
                .map { AppShortcut(it.id, (it.shortLabel ?: it.longLabel ?: it.id).toString(), it.`package`, it) }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: IllegalStateException) {
            emptyList()
        }
    }

    fun startShortcut(shortcut: AppShortcut) {
        try {
            launcherApps.startShortcut(shortcut.info, null, null)
        } catch (_: Exception) {
            toast(R.string.launch_failed)
        }
    }

    // ---- System defaults ----

    fun openClock() = start(Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun openCalendar(): Boolean {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .appendPath(System.currentTimeMillis().toString()).build()
        return start(Intent(Intent.ACTION_VIEW, uri), null) ||
            start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
    }

    fun openDialer() = start(Intent(Intent.ACTION_DIAL))

    fun openCamera(): Boolean =
        start(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), null) || start(Intent(MediaStore.ACTION_IMAGE_CAPTURE))

    fun setTimer() = start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_SKIP_UI, false))

    fun webSearch(query: String, packageName: String?): Boolean {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (packageName != null) intent.setPackage(packageName)
        if (start(intent, null)) return true
        // Fall back to a browser view of a search URL; the browser, not the launcher, makes the request.
        val url = Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query))
        return start(Intent(Intent.ACTION_VIEW, url), R.string.no_search_app)
    }

    fun openContact(lookupUri: Uri) = start(Intent(Intent.ACTION_VIEW, lookupUri))

    fun openSettingsAction(action: String): Boolean = start(Intent(action), null) ||
        start(Intent(Settings.ACTION_SETTINGS))

    fun openHomeSettings(): Boolean = start(Intent(Settings.ACTION_HOME_SETTINGS), null) || start(Intent(Settings.ACTION_SETTINGS))

    fun openAccessibilitySettings() = start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openUsageAccessSettings(): Boolean {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) intent.data = Uri.fromParts("package", context.packageName, null)
        return start(intent, null) || start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun openNotificationListenerSettings() = start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openOverlaySettings() = start(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", context.packageName, null)),
    )

    fun openBatteryOptimizationSettings() = start(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun openOwnAppDetails() = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    /** System color correction / grayscale lives in accessibility settings; there's no public deep link. */
    fun openColorCorrectionSettings(): Boolean =
        start(Intent("com.android.settings.ACCESSIBILITY_COLOR_SPACE_SETTINGS"), null) ||
            openAccessibilitySettings()

    fun openNotificationSettingsForApp() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        start(intent)
    }

    private fun toast(message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val MAX_SHORTCUTS = 6
    }
}
