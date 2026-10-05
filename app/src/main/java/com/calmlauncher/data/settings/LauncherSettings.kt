package com.calmlauncher.data.settings

import com.calmlauncher.domain.model.LauncherAction
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Stores a [LauncherAction] as its compact string form. Unknown values decode to None. */
object LauncherActionSerializer : KSerializer<LauncherAction> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.calmlauncher.LauncherAction", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LauncherAction) = encoder.encodeString(value.storageValue)

    override fun deserialize(decoder: Decoder): LauncherAction = LauncherAction.parse(decoder.decodeString())
}

typealias StoredAction = @Serializable(with = LauncherActionSerializer::class) LauncherAction

enum class ClockFormat { SYSTEM, H12, H24 }
enum class HorizontalAlign { START, CENTER, END }
enum class TextWeight { LIGHT, REGULAR, MEDIUM, BOLD }
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class BackgroundMode { WALLPAPER, SOLID }
enum class SlotStyle { TEXT, GLYPH }
enum class FocusHideMode { DIM, HIDE }
enum class LockMethod { NONE, ACCESSIBILITY, DEVICE_ADMIN }

enum class FontChoice(val displayName: String) {
    SYSTEM("System default"),
    INTER("Inter"),
    IBM_PLEX_SANS("IBM Plex Sans"),
    ATKINSON("Atkinson Hyperlegible"),
    SPACE_GROTESK("Space Grotesk"),
    SOURCE_SERIF("Source Serif"),
    JETBRAINS_MONO("JetBrains Mono"),
}

enum class TextScale(val factor: Float) {
    SMALL(0.85f),
    MEDIUM(1.0f),
    LARGE(1.15f),
    EXTRA_LARGE(1.3f),
    HUGE(1.5f),
}

/** Optional accent colours. NONE keeps the launcher purely monochrome. */
enum class AccentColor(val argb: Long?) {
    NONE(null),
    SAGE(0xFF7A9E7E),
    SKY(0xFF6A8EAE),
    SAND(0xFFC2A878),
    ROSE(0xFFB57F8A),
    LAVENDER(0xFF9488B8),
}

/**
 * Every user-facing preference with its default. Defaults follow the "calm by default"
 * principle: text only, monochrome, nothing optional switched on.
 */
@Serializable
data class LauncherSettings(
    // Onboarding
    val onboardingDone: Boolean = false,

    // Home
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val showSeconds: Boolean = false,
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val clockSizeSp: Int = 64,
    val clockWeight: TextWeight = TextWeight.LIGHT,
    val homeAlignment: HorizontalAlign = HorizontalAlign.START,
    val clockAction: StoredAction = LauncherAction.DefaultClock,
    val dateAction: StoredAction = LauncherAction.DefaultCalendar,
    val showBattery: Boolean = false,
    val showNextEvent: Boolean = false,
    val showIntention: Boolean = false,
    val intentionText: String = "",
    val favoriteTextSizeSp: Int = 28,
    val favoriteSpacingDp: Int = 12,
    val showBottomSlots: Boolean = true,
    val bottomSlotStyle: SlotStyle = SlotStyle.TEXT,
    val leftSlot: StoredAction = LauncherAction.DefaultDialer,
    val rightSlot: StoredAction = LauncherAction.DefaultCamera,
    val showIconsOnHome: Boolean = false,
    val showAllAppsButton: Boolean = false,
    val showFocusToggle: Boolean = true,

    // Gestures
    val swipeUp: StoredAction = LauncherAction.OpenDrawer,
    val swipeDown: StoredAction = LauncherAction.ExpandNotifications,
    val swipeLeft: StoredAction = LauncherAction.DefaultCamera,
    val swipeRight: StoredAction = LauncherAction.DefaultDialer,
    val doubleTap: StoredAction = LauncherAction.LockScreen,
    val longPress: StoredAction = LauncherAction.OpenLauncherSettings,
    val lockMethod: LockMethod = LockMethod.NONE,
    val typeToSearch: Boolean = true,

    // Drawer & search
    val autoFocusSearch: Boolean = true,
    val autoLaunchSingleMatch: Boolean = false,
    val autoLaunchDelayMs: Int = 300,
    val searchPackageNames: Boolean = false,
    val showIconsInDrawer: Boolean = false,
    val showCategories: Boolean = true,
    val searchContacts: Boolean = false,
    val searchSettingsShortcuts: Boolean = true,
    val searchCalculator: Boolean = true,
    val webSearchFallback: Boolean = true,
    val webSearchPackage: String? = null,
    val hiddenSearchable: Boolean = true,
    val hiddenLockEnabled: Boolean = false,
    val drawerTextSizeSp: Int = 20,

    // Focus
    val defaultPauseSeconds: Int = 5,
    val breathingAnimation: Boolean = true,
    val askReasonByDefault: Boolean = true,
    val focusManualActive: Boolean = false,
    val focusManualUntil: Long = 0L,
    val focusAllowlist: Set<String> = emptySet(),
    val focusStrict: Boolean = false,
    val focusHideMode: FocusHideMode = FocusHideMode.DIM,
    val passMinutes: Int = 5,
    val enforcementEnabled: Boolean = false,
    val fallbackMonitorEnabled: Boolean = false,
    val grayscaleUi: Boolean = false,
    /** Emergency escape: every rule and focus mode is suspended until this time (epoch ms). */
    val rulesPausedUntil: Long = 0L,

    // Notifications
    val digestEnabled: Boolean = false,
    /** Minutes of day at which the digest is delivered. */
    val digestTimes: Set<Int> = setOf(12 * 60, 18 * 60),

    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.NONE,
    val dynamicColor: Boolean = false,
    val font: FontChoice = FontChoice.INTER,
    val textScale: TextScale = TextScale.MEDIUM,
    val textWeight: TextWeight = TextWeight.REGULAR,
    val letterSpacingEm: Float = 0f,
    val lineHeightMultiplier: Float = 1.2f,
    val backgroundMode: BackgroundMode = BackgroundMode.SOLID,
    val wallpaperScrim: Float = 0.35f,

    // Tools
    val notesEnabled: Boolean = true,
    val todosEnabled: Boolean = true,
    val timerShortcutEnabled: Boolean = true,
) {
    companion object {
        val DEFAULT = LauncherSettings()
    }
}
