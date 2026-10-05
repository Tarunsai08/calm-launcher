package com.calmlauncher.feature.drawer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.calmlauncher.R
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.ui.Routes

/** Things a search result can do besides launching an app. */
sealed interface ShortcutTarget {
    data class SystemSettings(val action: String) : ShortcutTarget
    data class InApp(val route: String) : ShortcutTarget
    data object Timer : ShortcutTarget
}

data class SearchShortcut(
    val id: String,
    val labelRes: Int,
    val keywords: List<String>,
    val target: ShortcutTarget,
)

sealed interface DrawerItem {
    val key: String

    data class App(val app: LauncherApp, val hint: String? = null) : DrawerItem {
        override val key: String get() = "app:" + app.key.id
    }

    data class Shortcut(val shortcut: SearchShortcut, val label: String) : DrawerItem {
        override val key: String get() = "sc:" + shortcut.id
    }

    data class Contact(val name: String, val uri: Uri) : DrawerItem {
        override val key: String get() = "contact:$uri"
    }

    data class Calculation(val expression: String, val result: String) : DrawerItem {
        override val key: String get() = "calc"
    }

    data class WebSearch(val query: String) : DrawerItem {
        override val key: String get() = "web"
    }

    data class Header(val title: String) : DrawerItem {
        override val key: String get() = "header:$title"
    }
}

object SearchShortcuts {
    /** Android Settings pages plus the launcher's own screens and tools. */
    fun all(notesEnabled: Boolean, todosEnabled: Boolean, timerEnabled: Boolean): List<SearchShortcut> = buildList {
        add(
            SearchShortcut(
                "wifi",
                R.string.sc_wifi,
                listOf("wifi", "wi-fi", "internet", "network"),
                ShortcutTarget.SystemSettings(Settings.ACTION_WIFI_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "bluetooth",
                R.string.sc_bluetooth,
                listOf("bluetooth", "headphones"),
                ShortcutTarget.SystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "display",
                R.string.sc_display,
                listOf("display", "brightness", "screen", "dark mode"),
                ShortcutTarget.SystemSettings(Settings.ACTION_DISPLAY_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "sound",
                R.string.sc_sound,
                listOf("sound", "volume", "ringtone", "vibration"),
                ShortcutTarget.SystemSettings(Settings.ACTION_SOUND_SETTINGS),
            ),
        )
        add(SearchShortcut("battery", R.string.sc_battery, listOf("battery", "power", "saver"), ShortcutTarget.SystemSettings(ACTION_BATTERY_SAVER)))
        add(
            SearchShortcut(
                "apps",
                R.string.sc_apps,
                listOf("apps", "applications", "manage"),
                ShortcutTarget.SystemSettings(Settings.ACTION_APPLICATION_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "notifications",
                R.string.sc_notifications,
                listOf("notifications", "alerts"),
                ShortcutTarget.SystemSettings(ACTION_NOTIFICATION_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "location",
                R.string.sc_location,
                listOf("location", "gps"),
                ShortcutTarget.SystemSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "security",
                R.string.sc_security,
                listOf("security", "lock", "password", "fingerprint"),
                ShortcutTarget.SystemSettings(Settings.ACTION_SECURITY_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "privacy",
                R.string.sc_privacy,
                listOf("privacy", "permissions"),
                ShortcutTarget.SystemSettings(Settings.ACTION_PRIVACY_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "accessibility",
                R.string.sc_accessibility,
                listOf("accessibility", "talkback", "grayscale", "color"),
                ShortcutTarget.SystemSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "datetime",
                R.string.sc_date_time,
                listOf("date", "time", "timezone"),
                ShortcutTarget.SystemSettings(Settings.ACTION_DATE_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "language",
                R.string.sc_language,
                listOf("language", "keyboard", "input"),
                ShortcutTarget.SystemSettings(Settings.ACTION_LOCALE_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "storage",
                R.string.sc_storage,
                listOf("storage", "space"),
                ShortcutTarget.SystemSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "mobile",
                R.string.sc_mobile,
                listOf("mobile", "data", "sim", "cellular"),
                ShortcutTarget.SystemSettings(Settings.ACTION_WIRELESS_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "airplane",
                R.string.sc_airplane,
                listOf("airplane", "flight"),
                ShortcutTarget.SystemSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "dnd",
                R.string.sc_dnd,
                listOf("do not disturb", "dnd", "silence", "zen"),
                ShortcutTarget.SystemSettings(ACTION_ZEN_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "about",
                R.string.sc_about_phone,
                listOf("about", "phone", "version"),
                ShortcutTarget.SystemSettings(Settings.ACTION_DEVICE_INFO_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "system",
                R.string.sc_system_settings,
                listOf("settings", "system"),
                ShortcutTarget.SystemSettings(Settings.ACTION_SETTINGS),
            ),
        )
        add(
            SearchShortcut(
                "launcher",
                R.string.sc_launcher_settings,
                listOf("launcher", "calm", "home", "settings"),
                ShortcutTarget.InApp(Routes.SETTINGS),
            ),
        )
        add(SearchShortcut("focus", R.string.sc_focus, listOf("focus", "block", "limit"), ShortcutTarget.InApp(Routes.FOCUS)))
        add(SearchShortcut("insights", R.string.sc_insights, listOf("insights", "screen time", "usage"), ShortcutTarget.InApp(Routes.INSIGHTS)))
        add(SearchShortcut("digest", R.string.sc_digest, listOf("digest", "notifications"), ShortcutTarget.InApp(Routes.DIGEST)))
        if (notesEnabled) add(SearchShortcut("notes", R.string.sc_notes, listOf("notes", "note", "memo"), ShortcutTarget.InApp(Routes.NOTES)))
        if (todosEnabled) add(SearchShortcut("todos", R.string.sc_todos, listOf("todo", "to-do", "tasks", "list"), ShortcutTarget.InApp(Routes.TODOS)))
        if (timerEnabled) add(SearchShortcut("timer", R.string.sc_timer, listOf("timer", "alarm", "countdown"), ShortcutTarget.Timer))
    }

    // Constants spelled out so lint/API-level differences never matter.
    private const val ACTION_BATTERY_SAVER = "android.settings.BATTERY_SAVER_SETTINGS"
    private const val ACTION_NOTIFICATION_SETTINGS = "android.settings.ALL_APPS_NOTIFICATION_SETTINGS"
    private const val ACTION_ZEN_SETTINGS = "android.settings.ZEN_MODE_SETTINGS"
}

data class ContactHit(val name: String, val uri: Uri)

object ContactSearch {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Local contact lookup by name. Never leaves the device. */
    fun search(context: Context, query: String, limit: Int = 5): List<ContactHit> {
        if (query.isBlank() || !hasPermission(context)) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                val result = ArrayList<ContactHit>()
                while (c.moveToNext() && result.size < limit) {
                    val id = c.getLong(0)
                    val lookup = c.getString(1) ?: continue
                    val name = c.getString(2) ?: continue
                    result += ContactHit(name, ContactsContract.Contacts.getLookupUri(id, lookup))
                }
                result
            }.orEmpty()
        }.getOrDefault(emptyList())
    }
}
