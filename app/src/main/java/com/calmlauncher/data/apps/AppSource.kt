package com.calmlauncher.data.apps

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.calmlauncher.domain.model.AppKey

/** A launchable activity exactly as the system reports it. */
data class RawApp(
    val key: AppKey,
    val label: String,
    val isWork: Boolean,
    val isClone: Boolean,
)

/**
 * Thin wrapper over [LauncherApps] / [UserManager]. Covers every profile the launcher may show
 * (personal, work, clone/dual apps and, on Android 15+, private space when unlocked).
 */
class AppSource(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val myUser: UserHandle = Process.myUserHandle()

    fun profiles(): List<UserHandle> = try {
        launcherApps.profiles
    } catch (_: SecurityException) {
        listOf(myUser)
    }

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    fun userFor(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    fun isMainUser(user: UserHandle): Boolean = user == myUser

    fun isQuietMode(user: UserHandle): Boolean =
        user != myUser && runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false)

    /** Full scan of all profiles. Runs on a background thread. */
    fun scanAll(): List<RawApp> = profiles().flatMap { scanUser(it, null) }

    /** Incremental scan of one package in one profile. */
    fun scanPackage(packageName: String, user: UserHandle): List<RawApp> = scanUser(user, packageName)

    private fun scanUser(user: UserHandle, packageName: String?): List<RawApp> {
        val activities: List<LauncherActivityInfo> = try {
            launcherApps.getActivityList(packageName, user)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        val serial = serialOf(user)
        val (isWork, isClone) = profileKind(user)
        return activities
            .filter { it.componentName.packageName != context.packageName || !isMainUser(user) }
            .map { info ->
                RawApp(
                    key = AppKey(info.componentName.packageName, info.componentName.className, serial),
                    label = runCatching { info.label?.toString().orEmpty() }.getOrDefault("").trim(),
                    isWork = isWork,
                    isClone = isClone,
                )
            }
    }

    /** (isWork, isClone) for a profile. */
    @SuppressLint("MissingPermission", "NewApi")
    private fun profileKind(user: UserHandle): Pair<Boolean, Boolean> {
        if (user == myUser) return false to false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val type = runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrNull()
            if (type != null) {
                return when {
                    type.contains("CLONE", ignoreCase = true) -> false to true
                    type.contains("PRIVATE", ignoreCase = true) -> false to true
                    else -> true to false
                }
            }
        }
        return true to false
    }

    fun resolve(key: AppKey): LauncherActivityInfo? {
        val user = userFor(key.userSerial) ?: return null
        return try {
            launcherApps.getActivityList(key.packageName, user)
                .firstOrNull { it.componentName.className == key.className }
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun componentOf(key: AppKey) = ComponentName(key.packageName, key.className)
}
