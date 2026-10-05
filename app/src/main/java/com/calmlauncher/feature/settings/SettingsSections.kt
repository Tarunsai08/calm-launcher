package com.calmlauncher.feature.settings

import android.Manifest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.calmlauncher.BuildConfig
import com.calmlauncher.R
import com.calmlauncher.data.settings.AccentColor
import com.calmlauncher.data.settings.BackgroundMode
import com.calmlauncher.data.settings.ClockFormat
import com.calmlauncher.data.settings.FontChoice
import com.calmlauncher.data.settings.HorizontalAlign
import com.calmlauncher.data.settings.LauncherSettings
import com.calmlauncher.data.settings.LockMethod
import com.calmlauncher.data.settings.SlotStyle
import com.calmlauncher.data.settings.TextScale
import com.calmlauncher.data.settings.TextWeight
import com.calmlauncher.data.settings.ThemeMode
import com.calmlauncher.domain.model.LauncherApp
import com.calmlauncher.service.AppLauncher
import com.calmlauncher.ui.ExplainKind
import com.calmlauncher.ui.Routes
import com.calmlauncher.ui.actionLabel
import kotlin.math.roundToInt

class SettingsActions(
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    val navigate: (String) -> Unit,
    val pickAction: (ActionSlot) -> Unit,
    val requestPermission: (String) -> Unit,
    val requestDefaultLauncher: () -> Unit,
    val requestDeviceAdmin: () -> Unit,
    val exportBackup: () -> Unit,
    val importBackup: () -> Unit,
    val confirmReset: () -> Unit,
    val confirmDelete: () -> Unit,
    val launcher: AppLauncher,
    val setLockMethod: (LockMethod) -> Unit,
)

@Composable
fun buildSettingsSections(
    s: LauncherSettings,
    apps: List<LauncherApp>,
    status: PermissionStatus,
    a: SettingsActions,
): List<SettingsSection> = listOf(
    homeSection(s, apps, status, a),
    gestureSection(s, apps, status, a),
    drawerSection(s, a),
    focusSection(a),
    notificationSection(s, status, a),
    appearanceSection(s, a),
    toolsSection(s, a),
    backupSection(a),
    privacySection(status, a),
    aboutSection(a),
)

private fun on(value: Boolean, onRes: String, offRes: String) = if (value) onRes else offRes

@Composable
private fun homeSection(s: LauncherSettings, apps: List<LauncherApp>, status: PermissionStatus, a: SettingsActions): SettingsSection {
    val calendarPermission = Manifest.permission.READ_CALENDAR
    return SettingsSection(
        id = "home",
        title = stringResource(R.string.section_home),
        items = listOf(
            SettingItem.Action(
                "default_launcher",
                stringResource(R.string.set_default_launcher),
                stringResource(if (status.isDefaultLauncher) R.string.set_default_launcher_done else R.string.set_default_launcher_desc),
                onClick = a.requestDefaultLauncher,
            ),
            SettingItem.Action(
                "favorites",
                stringResource(R.string.set_favorites),
                stringResource(R.string.set_favorites_desc),
                onClick = { a.navigate(Routes.FAVORITES) },
            ),
            SettingItem.Choice(
                "alignment",
                stringResource(R.string.set_alignment),
                stringResource(R.string.set_alignment_desc),
                listOf(stringResource(R.string.align_start), stringResource(R.string.align_center), stringResource(R.string.align_end)),
                s.homeAlignment.ordinal,
            ) { i -> a.update { it.copy(homeAlignment = HorizontalAlign.entries[i]) } },
            SettingItem.Toggle("show_clock", stringResource(R.string.set_show_clock), null, s.showClock) { v -> a.update { it.copy(showClock = v) } },
            SettingItem.Choice(
                "clock_format",
                stringResource(R.string.set_clock_format),
                null,
                listOf(stringResource(R.string.clock_system), stringResource(R.string.clock_12), stringResource(R.string.clock_24)),
                s.clockFormat.ordinal,
            ) { i -> a.update { it.copy(clockFormat = ClockFormat.entries[i]) } },
            SettingItem.Toggle("seconds", stringResource(R.string.set_seconds), stringResource(R.string.set_seconds_desc), s.showSeconds) { v ->
                a.update { it.copy(showSeconds = v) }
            },
            SettingItem.Slider(
                "clock_size",
                stringResource(R.string.set_clock_size),
                null,
                s.clockSizeSp.toFloat(),
                32f..120f,
                21,
                { "${it.roundToInt()} sp" },
            ) { v -> a.update { it.copy(clockSizeSp = v.roundToInt()) } },
            SettingItem.Choice(
                "clock_weight",
                stringResource(R.string.set_clock_weight),
                null,
                weightLabels(),
                s.clockWeight.ordinal,
            ) { i -> a.update { it.copy(clockWeight = TextWeight.entries[i]) } },
            SettingItem.Toggle("show_date", stringResource(R.string.set_show_date), null, s.showDate) { v -> a.update { it.copy(showDate = v) } },
            SettingItem.Action(
                "clock_tap",
                stringResource(R.string.set_clock_tap),
                null,
                actionLabel(s.clockAction, apps),
            ) { a.pickAction(ActionSlot.CLOCK) },
            SettingItem.Action(
                "date_tap",
                stringResource(R.string.set_date_tap),
                null,
                actionLabel(s.dateAction, apps),
            ) { a.pickAction(ActionSlot.DATE) },
            SettingItem.Toggle("battery", stringResource(R.string.set_battery), null, s.showBattery) { v -> a.update { it.copy(showBattery = v) } },
            SettingItem.Toggle(
                "next_event",
                stringResource(R.string.set_next_event),
                stringResource(R.string.set_next_event_desc),
                s.showNextEvent,
            ) { v ->
                a.update { it.copy(showNextEvent = v) }
                if (v) a.requestPermission(calendarPermission)
            },
            SettingItem.Toggle("intention", stringResource(R.string.set_intention), stringResource(R.string.set_intention_desc), s.showIntention) { v ->
                a.update { it.copy(showIntention = v) }
            },
            SettingItem.Slider(
                "fav_size",
                stringResource(R.string.set_favorite_size),
                null,
                s.favoriteTextSizeSp.toFloat(),
                16f..48f,
                15,
                { "${it.roundToInt()} sp" },
            ) { v -> a.update { it.copy(favoriteTextSizeSp = v.roundToInt()) } },
            SettingItem.Slider(
                "fav_spacing",
                stringResource(R.string.set_favorite_spacing),
                null,
                s.favoriteSpacingDp.toFloat(),
                0f..40f,
                9,
                { "${it.roundToInt()} dp" },
            ) { v -> a.update { it.copy(favoriteSpacingDp = v.roundToInt()) } },
            SettingItem.Toggle("home_icons", stringResource(R.string.set_home_icons), stringResource(R.string.set_home_icons_desc), s.showIconsOnHome) { v ->
                a.update { it.copy(showIconsOnHome = v) }
            },
            SettingItem.Toggle("slots", stringResource(R.string.set_bottom_slots), null, s.showBottomSlots) { v -> a.update { it.copy(showBottomSlots = v) } },
            SettingItem.Action("left_slot", stringResource(R.string.set_left_slot), null, actionLabel(s.leftSlot, apps)) { a.pickAction(ActionSlot.LEFT_SLOT) },
            SettingItem.Action("right_slot", stringResource(R.string.set_right_slot), null, actionLabel(s.rightSlot, apps)) {
                a.pickAction(ActionSlot.RIGHT_SLOT)
            },
            SettingItem.Choice(
                "slot_style",
                stringResource(R.string.set_slot_style),
                null,
                listOf(stringResource(R.string.slot_text), stringResource(R.string.slot_glyph)),
                s.bottomSlotStyle.ordinal,
            ) { i -> a.update { it.copy(bottomSlotStyle = SlotStyle.entries[i]) } },
            SettingItem.Toggle("focus_toggle", stringResource(R.string.set_focus_toggle), stringResource(R.string.set_focus_toggle_desc), s.showFocusToggle) { v ->
                a.update { it.copy(showFocusToggle = v) }
            },
            SettingItem.Toggle("all_apps_button", stringResource(R.string.set_all_apps_button), stringResource(R.string.set_all_apps_button_desc), s.showAllAppsButton) { v ->
                a.update { it.copy(showAllAppsButton = v) }
            },
        ),
    )
}

@Composable
private fun gestureSection(s: LauncherSettings, apps: List<LauncherApp>, status: PermissionStatus, a: SettingsActions): SettingsSection {
    val lockOptions = listOf(
        stringResource(R.string.lock_none),
        stringResource(R.string.lock_accessibility),
        stringResource(R.string.lock_admin),
    )
    return SettingsSection(
        id = "gestures",
        title = stringResource(R.string.section_gestures),
        items = listOf(
            SettingItem.Action("g_up", stringResource(R.string.gesture_up), null, actionLabel(s.swipeUp, apps)) { a.pickAction(ActionSlot.SWIPE_UP) },
            SettingItem.Action("g_down", stringResource(R.string.gesture_down), stringResource(R.string.gesture_down_desc), actionLabel(s.swipeDown, apps)) {
                a.pickAction(ActionSlot.SWIPE_DOWN)
            },
            SettingItem.Action("g_left", stringResource(R.string.gesture_left), null, actionLabel(s.swipeLeft, apps)) { a.pickAction(ActionSlot.SWIPE_LEFT) },
            SettingItem.Action("g_right", stringResource(R.string.gesture_right), null, actionLabel(s.swipeRight, apps)) { a.pickAction(ActionSlot.SWIPE_RIGHT) },
            SettingItem.Action("g_double", stringResource(R.string.gesture_double_tap), null, actionLabel(s.doubleTap, apps)) {
                a.pickAction(ActionSlot.DOUBLE_TAP)
            },
            SettingItem.Action("g_long", stringResource(R.string.gesture_long_press), stringResource(R.string.gesture_long_press_desc), actionLabel(s.longPress, apps)) {
                a.pickAction(ActionSlot.LONG_PRESS)
            },
            SettingItem.Choice(
                "lock_method",
                stringResource(R.string.lock_method),
                stringResource(
                    when {
                        status.accessibility && s.lockMethod == LockMethod.ACCESSIBILITY -> R.string.lock_method_ready_a11y
                        status.deviceAdmin && s.lockMethod == LockMethod.DEVICE_ADMIN -> R.string.lock_method_ready_admin
                        else -> R.string.lock_method_desc
                    },
                ),
                lockOptions,
                s.lockMethod.ordinal,
            ) { i ->
                val method = LockMethod.entries[i]
                a.setLockMethod(method)
                when (method) {
                    LockMethod.ACCESSIBILITY -> if (!status.accessibility) a.navigate(Routes.explain(ExplainKind.ACCESSIBILITY))
                    LockMethod.DEVICE_ADMIN -> if (!status.deviceAdmin) a.requestDeviceAdmin()
                    LockMethod.NONE -> Unit
                }
            },
            SettingItem.Toggle("type_search", stringResource(R.string.set_type_to_search), stringResource(R.string.set_type_to_search_desc), s.typeToSearch) { v ->
                a.update { it.copy(typeToSearch = v) }
            },
        ),
    )
}

@Composable
private fun drawerSection(s: LauncherSettings, a: SettingsActions): SettingsSection {
    val contactsPermission = Manifest.permission.READ_CONTACTS
    return SettingsSection(
        id = "drawer",
        title = stringResource(R.string.section_drawer),
        items = listOf(
            SettingItem.Toggle("autofocus", stringResource(R.string.set_autofocus), stringResource(R.string.set_autofocus_desc), s.autoFocusSearch) { v ->
                a.update { it.copy(autoFocusSearch = v) }
            },
            SettingItem.Toggle("autolaunch", stringResource(R.string.set_autolaunch), stringResource(R.string.set_autolaunch_desc), s.autoLaunchSingleMatch) { v ->
                a.update { it.copy(autoLaunchSingleMatch = v) }
            },
            SettingItem.Slider(
                "autolaunch_delay",
                stringResource(R.string.set_autolaunch_delay),
                null,
                s.autoLaunchDelayMs.toFloat(),
                0f..1500f,
                14,
                { "${it.roundToInt()} ms" },
            ) { v -> a.update { it.copy(autoLaunchDelayMs = v.roundToInt()) } },
            SettingItem.Toggle("search_pkg", stringResource(R.string.set_search_packages), stringResource(R.string.set_search_packages_desc), s.searchPackageNames) { v ->
                a.update { it.copy(searchPackageNames = v) }
            },
            SettingItem.Toggle("search_settings", stringResource(R.string.set_search_settings), null, s.searchSettingsShortcuts) { v ->
                a.update { it.copy(searchSettingsShortcuts = v) }
            },
            SettingItem.Toggle("search_calc", stringResource(R.string.set_search_calc), null, s.searchCalculator) { v ->
                a.update { it.copy(searchCalculator = v) }
            },
            SettingItem.Toggle("search_contacts", stringResource(R.string.set_search_contacts), stringResource(R.string.set_search_contacts_desc), s.searchContacts) { v ->
                a.update { it.copy(searchContacts = v) }
                if (v) a.requestPermission(contactsPermission)
            },
            SettingItem.Toggle("web_fallback", stringResource(R.string.set_web_fallback), stringResource(R.string.set_web_fallback_desc), s.webSearchFallback) { v ->
                a.update { it.copy(webSearchFallback = v) }
            },
            SettingItem.Toggle("drawer_icons", stringResource(R.string.set_drawer_icons), null, s.showIconsInDrawer) { v ->
                a.update { it.copy(showIconsInDrawer = v) }
            },
            SettingItem.Toggle("categories", stringResource(R.string.set_show_categories), null, s.showCategories) { v ->
                a.update { it.copy(showCategories = v) }
            },
            SettingItem.Action("manage_categories", stringResource(R.string.set_manage_categories), stringResource(R.string.set_manage_categories_desc)) {
                a.navigate(Routes.CATEGORIES)
            },
            SettingItem.Slider(
                "drawer_size",
                stringResource(R.string.set_drawer_size),
                null,
                s.drawerTextSizeSp.toFloat(),
                14f..32f,
                17,
                { "${it.roundToInt()} sp" },
            ) { v -> a.update { it.copy(drawerTextSizeSp = v.roundToInt()) } },
            SettingItem.Action("hidden", stringResource(R.string.set_hidden_apps), stringResource(R.string.set_hidden_apps_desc)) {
                a.navigate(Routes.HIDDEN_APPS)
            },
            SettingItem.Toggle("hidden_search", stringResource(R.string.set_hidden_searchable), stringResource(R.string.set_hidden_searchable_desc), s.hiddenSearchable) { v ->
                a.update { it.copy(hiddenSearchable = v) }
            },
        ),
    )
}

@Composable
private fun focusSection(a: SettingsActions): SettingsSection = SettingsSection(
    id = "focus",
    title = stringResource(R.string.section_focus),
    items = listOf(
        SettingItem.Action("focus_mode", stringResource(R.string.set_focus_mode), stringResource(R.string.set_focus_mode_desc)) { a.navigate(Routes.FOCUS) },
        SettingItem.Action("insights", stringResource(R.string.set_insights), stringResource(R.string.set_insights_desc)) { a.navigate(Routes.INSIGHTS) },
        SettingItem.Action("color_correction", stringResource(R.string.set_system_grayscale), stringResource(R.string.set_system_grayscale_desc)) {
            a.launcher.openColorCorrectionSettings()
        },
    ),
)

@Composable
private fun notificationSection(s: LauncherSettings, status: PermissionStatus, a: SettingsActions): SettingsSection {
    val postPermission = notificationPermission
    return SettingsSection(
        id = "notifications",
        title = stringResource(R.string.section_notifications),
        items = listOf(
            SettingItem.Action(
                "notif_access",
                stringResource(R.string.set_notification_access),
                stringResource(if (status.notificationAccess) R.string.state_granted else R.string.set_notification_access_desc),
            ) { a.navigate(Routes.explain(ExplainKind.NOTIFICATIONS)) },
            SettingItem.Action("notif_rules", stringResource(R.string.digest_rules), stringResource(R.string.digest_rules_desc)) {
                a.navigate(Routes.NOTIFICATION_RULES)
            },
            SettingItem.Toggle("digest", stringResource(R.string.digest_deliver), stringResource(R.string.digest_deliver_desc), s.digestEnabled) { v ->
                a.update { it.copy(digestEnabled = v) }
                if (v && postPermission != null) a.requestPermission(postPermission)
            },
            SettingItem.Action("digest_open", stringResource(R.string.digest_title), null) { a.navigate(Routes.DIGEST) },
        ),
    )
}

@Composable
private fun weightLabels() = listOf(
    stringResource(R.string.weight_light),
    stringResource(R.string.weight_regular),
    stringResource(R.string.weight_medium),
    stringResource(R.string.weight_bold),
)

@Composable
private fun appearanceSection(s: LauncherSettings, a: SettingsActions): SettingsSection {
    val items = mutableListOf<SettingItem>(
        SettingItem.Choice(
            "theme",
            stringResource(R.string.set_theme),
            null,
            listOf(
                stringResource(R.string.theme_system),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark),
                stringResource(R.string.theme_amoled),
            ),
            s.themeMode.ordinal,
        ) { i -> a.update { it.copy(themeMode = ThemeMode.entries[i]) } },
        SettingItem.Choice(
            "accent",
            stringResource(R.string.set_accent),
            stringResource(R.string.set_accent_desc),
            listOf(
                stringResource(R.string.accent_none),
                stringResource(R.string.accent_sage),
                stringResource(R.string.accent_sky),
                stringResource(R.string.accent_sand),
                stringResource(R.string.accent_rose),
                stringResource(R.string.accent_lavender),
            ),
            s.accent.ordinal,
        ) { i -> a.update { it.copy(accent = AccentColor.entries[i]) } },
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        items += SettingItem.Toggle("dynamic", stringResource(R.string.set_dynamic_color), stringResource(R.string.set_dynamic_color_desc), s.dynamicColor) { v ->
            a.update { it.copy(dynamicColor = v) }
        }
    }
    items += listOf(
        SettingItem.Toggle("grayscale", stringResource(R.string.set_grayscale), stringResource(R.string.set_grayscale_desc), s.grayscaleUi) { v ->
            a.update { it.copy(grayscaleUi = v) }
        },
        SettingItem.Choice(
            "font",
            stringResource(R.string.set_font),
            stringResource(R.string.set_font_desc),
            FontChoice.entries.map { if (it == FontChoice.SYSTEM) "System" else it.displayName },
            s.font.ordinal,
        ) { i -> a.update { it.copy(font = FontChoice.entries[i]) } },
        SettingItem.Choice(
            "text_scale",
            stringResource(R.string.set_text_size),
            stringResource(R.string.set_text_size_desc),
            listOf(
                stringResource(R.string.size_small),
                stringResource(R.string.size_medium),
                stringResource(R.string.size_large),
                stringResource(R.string.size_xl),
                stringResource(R.string.size_huge),
            ),
            s.textScale.ordinal,
        ) { i -> a.update { it.copy(textScale = TextScale.entries[i]) } },
        SettingItem.Choice("text_weight", stringResource(R.string.set_text_weight), null, weightLabels(), s.textWeight.ordinal) { i ->
            a.update { it.copy(textWeight = TextWeight.entries[i]) }
        },
        SettingItem.Slider(
            "letter_spacing",
            stringResource(R.string.set_letter_spacing),
            null,
            s.letterSpacingEm,
            -0.05f..0.2f,
            24,
            { "%.2f em".format(it) },
        ) { v -> a.update { it.copy(letterSpacingEm = v) } },
        SettingItem.Slider(
            "line_height",
            stringResource(R.string.set_line_height),
            null,
            s.lineHeightMultiplier,
            1.0f..2.0f,
            9,
            { "%.1f×".format(it) },
        ) { v -> a.update { it.copy(lineHeightMultiplier = v) } },
        SettingItem.Choice(
            "background",
            stringResource(R.string.set_background),
            stringResource(R.string.set_background_desc),
            listOf(stringResource(R.string.background_wallpaper), stringResource(R.string.background_solid)),
            s.backgroundMode.ordinal,
        ) { i -> a.update { it.copy(backgroundMode = BackgroundMode.entries[i]) } },
        SettingItem.Slider(
            "scrim",
            stringResource(R.string.set_scrim),
            stringResource(R.string.set_scrim_desc),
            s.wallpaperScrim,
            0f..0.9f,
            8,
            { "${(it * 100).roundToInt()}%" },
        ) { v -> a.update { it.copy(wallpaperScrim = v) } },
    )
    return SettingsSection("appearance", stringResource(R.string.section_appearance), items)
}

@Composable
private fun toolsSection(s: LauncherSettings, a: SettingsActions): SettingsSection = SettingsSection(
    id = "tools",
    title = stringResource(R.string.section_tools),
    items = listOf(
        SettingItem.Toggle("notes", stringResource(R.string.set_notes), stringResource(R.string.set_notes_desc), s.notesEnabled) { v ->
            a.update { it.copy(notesEnabled = v) }
        },
        SettingItem.Toggle("todos", stringResource(R.string.set_todos), stringResource(R.string.set_todos_desc), s.todosEnabled) { v ->
            a.update { it.copy(todosEnabled = v) }
        },
        SettingItem.Toggle("timer", stringResource(R.string.set_timer), stringResource(R.string.set_timer_desc), s.timerShortcutEnabled) { v ->
            a.update { it.copy(timerShortcutEnabled = v) }
        },
    ),
)

@Composable
private fun backupSection(a: SettingsActions): SettingsSection = SettingsSection(
    id = "backup",
    title = stringResource(R.string.section_backup),
    items = listOf(
        SettingItem.Action("export", stringResource(R.string.backup_export), stringResource(R.string.backup_export_desc), onClick = a.exportBackup),
        SettingItem.Action("import", stringResource(R.string.backup_import), stringResource(R.string.backup_import_desc), onClick = a.importBackup),
        SettingItem.Action("reset", stringResource(R.string.reset_title), stringResource(R.string.reset_desc), onClick = a.confirmReset),
        SettingItem.Action("delete_all", stringResource(R.string.delete_all_title), stringResource(R.string.delete_all_desc), onClick = a.confirmDelete),
    ),
)

@Composable
private fun privacySection(status: PermissionStatus, a: SettingsActions): SettingsSection {
    val granted = stringResource(R.string.state_granted)
    val notGranted = stringResource(R.string.state_not_granted)
    return SettingsSection(
        id = "privacy",
        title = stringResource(R.string.section_privacy),
        items = listOf(
            SettingItem.Action("privacy_summary", stringResource(R.string.privacy_summary_title), stringResource(R.string.privacy_summary)) {
                a.navigate(Routes.ABOUT)
            },
            SettingItem.Action("p_usage", stringResource(R.string.perm_usage), on(status.usageAccess, granted, notGranted)) {
                a.navigate(Routes.explain(ExplainKind.USAGE))
            },
            SettingItem.Action("p_a11y", stringResource(R.string.perm_accessibility), on(status.accessibility, granted, notGranted)) {
                a.navigate(Routes.explain(ExplainKind.ACCESSIBILITY))
            },
            SettingItem.Action("p_notif", stringResource(R.string.perm_notifications), on(status.notificationAccess, granted, notGranted)) {
                a.navigate(Routes.explain(ExplainKind.NOTIFICATIONS))
            },
            SettingItem.Action("p_overlay", stringResource(R.string.perm_overlay), on(status.overlay, granted, notGranted)) {
                a.navigate(Routes.explain(ExplainKind.OVERLAY))
            },
            SettingItem.Action("p_admin", stringResource(R.string.perm_admin), on(status.deviceAdmin, granted, notGranted)) {
                if (status.deviceAdmin) a.launcher.openOwnAppDetails() else a.requestDeviceAdmin()
            },
            SettingItem.Action("p_app", stringResource(R.string.perm_all), stringResource(R.string.perm_all_desc)) { a.launcher.openOwnAppDetails() },
        ),
    )
}

@Composable
private fun aboutSection(a: SettingsActions): SettingsSection = SettingsSection(
    id = "about",
    title = stringResource(R.string.section_about),
    items = listOf(
        SettingItem.Action(
            "about",
            stringResource(R.string.app_name),
            stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
        ) { a.navigate(Routes.ABOUT) },
        SettingItem.Action("oem", stringResource(R.string.focus_battery_help), stringResource(R.string.focus_battery_help_desc)) {
            a.navigate(Routes.OEM_HELP)
        },
        SettingItem.Action("onboarding_again", stringResource(R.string.set_onboarding_again), null) {
            a.update { it.copy(onboardingDone = false) }
        },
    ),
)
