package com.calmlauncher.feature.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calmlauncher.R
import com.calmlauncher.data.apps.AppsRepository
import com.calmlauncher.data.backup.BackupRepository
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.LockMethod
import com.calmlauncher.data.settings.SettingsRepository
import com.calmlauncher.data.usage.UsageRepository
import com.calmlauncher.domain.backup.BackupFormatException
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.service.AppLauncher
import com.calmlauncher.service.CalmNotificationListener
import com.calmlauncher.service.Scheduler
import com.calmlauncher.service.SystemActions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Status of each optional system integration, shown in Privacy and onboarding. */
data class PermissionStatus(
    val isDefaultLauncher: Boolean,
    val usageAccess: Boolean,
    val accessibility: Boolean,
    val notificationAccess: Boolean,
    val deviceAdmin: Boolean,
    val overlay: Boolean,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SettingsRepository,
    private val backup: BackupRepository,
    private val scheduler: Scheduler,
    private val usage: UsageRepository,
    val systemActions: SystemActions,
    val launcher: AppLauncher,
    apps: AppsRepository,
) : ViewModel() {
    val settings: StateFlow<LauncherSettings> = repo.settings
    val apps: StateFlow<List<LauncherApp>> = apps.apps

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch {
            repo.update(transform)
            scheduler.rescheduleAll()
        }
    }

    fun permissionStatus(): PermissionStatus = PermissionStatus(
        isDefaultLauncher = isDefaultLauncher(context),
        usageAccess = usage.hasPermission(),
        accessibility = systemActions.isAccessibilityEnabled(),
        notificationAccess = CalmNotificationListener.connected || isNotificationListenerEnabled(),
        deviceAdmin = systemActions.isDeviceAdminActive(),
        overlay = android.provider.Settings.canDrawOverlays(context),
    )

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return flat.contains(context.packageName)
    }

    fun setLockMethod(method: LockMethod) = update { it.copy(lockMethod = method) }

    fun exportBackup(uri: Uri, includeTools: Boolean) {
        viewModelScope.launch {
            val message = try {
                backup.exportTo(uri, includeTools)
                context.getString(R.string.backup_export_done)
            } catch (e: Exception) {
                context.getString(R.string.backup_export_failed, e.message.orEmpty())
            }
            _messages.tryEmit(message)
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val summary = backup.importFrom(uri)
                scheduler.rescheduleAll()
                context.getString(R.string.backup_import_done, summary.apps, summary.rules, summary.schedules)
            } catch (e: BackupFormatException) {
                context.getString(R.string.backup_import_failed, e.message.orEmpty())
            } catch (e: Exception) {
                context.getString(R.string.backup_import_failed, e.message.orEmpty())
            }
            _messages.tryEmit(message)
        }
    }

    fun resetDefaults() {
        viewModelScope.launch {
            repo.resetToDefaults(keepOnboarding = true)
            scheduler.rescheduleAll()
            _messages.tryEmit(context.getString(R.string.reset_done))
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            backup.deleteEverything()
            systemActions.removeDeviceAdmin()
            scheduler.rescheduleAll()
            _messages.tryEmit(context.getString(R.string.delete_all_done))
        }
    }

    companion object {
        fun isDefaultLauncher(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = context.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
            }
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            return info?.activityInfo?.packageName == context.packageName
        }

        /** Intent that asks to become the default home app, or null to fall back to system settings. */
        fun requestDefaultIntent(context: Context): Intent? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rm = context.getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
                }
            }
            return null
        }
    }
}
