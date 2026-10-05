package com.calmlauncher.domain.model

/**
 * Stable identity of a launchable activity for a specific user profile.
 *
 * The serialized form `package/class#userSerial` is used as a primary key in storage and
 * in backups. User serial numbers (from `UserManager.getSerialNumberForUser`) are stable
 * across reboots, unlike `UserHandle` ids.
 */
data class AppKey(
    val packageName: String,
    val className: String,
    val userSerial: Long,
) {
    val id: String get() = "$packageName/$className#$userSerial"

    companion object {
        fun parse(id: String): AppKey? {
            val hash = id.lastIndexOf('#')
            val slash = id.indexOf('/')
            if (hash <= 0 || slash <= 0 || slash > hash) return null
            val serial = id.substring(hash + 1).toLongOrNull() ?: return null
            val pkg = id.substring(0, slash)
            val cls = id.substring(slash + 1, hash)
            if (pkg.isBlank() || cls.isBlank()) return null
            return AppKey(pkg, cls, serial)
        }
    }
}

/** A launchable app as presented by the launcher, already merged with user metadata. */
data class LauncherApp(
    val key: AppKey,
    /** Label reported by the system (may be blank for badly-behaved apps). */
    val systemLabel: String,
    /** Local, user-defined display name. */
    val alias: String? = null,
    val isWorkProfile: Boolean = false,
    /** True for clone/dual-app profiles reported by some OEMs as secondary profiles. */
    val isClone: Boolean = false,
    val hidden: Boolean = false,
    val categoryId: Long? = null,
    val favoriteOrder: Int? = null,
    val lastLaunchedAt: Long = 0L,
    val launchCount: Int = 0,
) {
    /** The name the user sees. Never empty: falls back to the package name. */
    val label: String
        get() = alias?.takeIf { it.isNotBlank() }
            ?: systemLabel.takeIf { it.isNotBlank() }
            ?: key.packageName

    val isFavorite: Boolean get() = favoriteOrder != null
}
