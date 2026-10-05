package com.calmlauncher.domain.model

/**
 * Anything a gesture, quick-launch slot or clock/date tap can do. Serialised to a compact
 * string so it can live in preferences and backups.
 */
sealed interface LauncherAction {
    val storageValue: String

    data object None : LauncherAction { override val storageValue = "none" }
    data object OpenDrawer : LauncherAction { override val storageValue = "drawer" }
    data object OpenSearch : LauncherAction { override val storageValue = "search" }
    data object ExpandNotifications : LauncherAction { override val storageValue = "notifications" }
    data object LockScreen : LauncherAction { override val storageValue = "lock" }
    data object OpenLauncherSettings : LauncherAction { override val storageValue = "settings" }
    data object ToggleFocus : LauncherAction { override val storageValue = "focus" }
    data object OpenNotes : LauncherAction { override val storageValue = "notes" }
    data object OpenTodos : LauncherAction { override val storageValue = "todos" }
    data object OpenInsights : LauncherAction { override val storageValue = "insights" }
    data object OpenDigest : LauncherAction { override val storageValue = "digest" }
    data object DefaultClock : LauncherAction { override val storageValue = "clock" }
    data object DefaultCalendar : LauncherAction { override val storageValue = "calendar" }
    data object DefaultDialer : LauncherAction { override val storageValue = "dialer" }
    data object DefaultCamera : LauncherAction { override val storageValue = "camera" }

    data class LaunchApp(val key: AppKey) : LauncherAction {
        override val storageValue: String get() = "app:${key.id}"
    }

    companion object {
        private val simple: List<LauncherAction> = listOf(
            None, OpenDrawer, OpenSearch, ExpandNotifications, LockScreen, OpenLauncherSettings,
            ToggleFocus, OpenNotes, OpenTodos, OpenInsights, OpenDigest, DefaultClock,
            DefaultCalendar, DefaultDialer, DefaultCamera,
        )

        /** Built-in (non-app) actions in a sensible order for pickers. */
        val builtIns: List<LauncherAction> get() = simple

        fun parse(value: String?, fallback: LauncherAction = None): LauncherAction {
            if (value.isNullOrBlank()) return fallback
            simple.firstOrNull { it.storageValue == value }?.let { return it }
            if (value.startsWith("app:")) {
                AppKey.parse(value.removePrefix("app:"))?.let { return LaunchApp(it) }
            }
            return fallback
        }
    }
}
