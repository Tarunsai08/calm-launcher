package com.calmlauncher.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import com.calmlauncher.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class LockResult { LOCKED, NEEDS_SETUP }

/**
 * Screen lock and notification shade. Both rely on opt-in system features:
 * - Lock: the accessibility service (Android 9+, preferred) or a device-admin receiver.
 * - Shade: a long-standing but non-public StatusBarManager method, with the accessibility
 *   action as a fallback. If neither works the caller opens search instead.
 */
@Singleton
class SystemActions @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    val adminComponent = ComponentName(context, LockAdminReceiver::class.java)

    fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val ours = ComponentName(context, CalmAccessibilityService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (item in splitter) {
            val cn = ComponentName.unflattenFromString(item) ?: continue
            if (cn == ours) return true
        }
        return false
    }

    fun isServiceConnected(): Boolean = CalmAccessibilityService.instance != null

    fun isDeviceAdminActive(): Boolean = runCatching { dpm.isAdminActive(adminComponent) }.getOrDefault(false)

    fun canLock(): Boolean =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isServiceConnected()) || isDeviceAdminActive()

    fun lockScreen(): LockResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val service = CalmAccessibilityService.instance
            if (service != null && service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)) {
                return LockResult.LOCKED
            }
        }
        if (isDeviceAdminActive()) {
            return try {
                dpm.lockNow()
                LockResult.LOCKED
            } catch (_: SecurityException) {
                LockResult.NEEDS_SETUP
            }
        }
        return LockResult.NEEDS_SETUP
    }

    /** Intent that asks the user to enable the device-admin lock fallback. */
    fun deviceAdminIntent(): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.device_admin_explanation))

    fun removeDeviceAdmin() {
        runCatching { dpm.removeActiveAdmin(adminComponent) }
    }

    /** Returns true if the shade was opened. */
    @SuppressLint("WrongConstant", "PrivateApi")
    fun expandNotifications(): Boolean {
        try {
            val service = context.getSystemService("statusbar")
            if (service != null) {
                val method = service.javaClass.getMethod("expandNotificationsPanel")
                method.invoke(service)
                return true
            }
        } catch (_: Exception) {
            // Not available on this build; try the accessibility action.
        }
        val a11y = CalmAccessibilityService.instance
        return a11y?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) == true
    }

    fun openQuickSettings(): Boolean =
        CalmAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) == true
}
