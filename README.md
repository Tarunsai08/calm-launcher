# Calm Launcher

A text-first, distraction-reducing Android home screen. Clock, date, a few favorite apps as
plain words, and everything else one swipe away. Optional mindful pauses, daily limits, focus
mode and a notification digest help you open apps on purpose.

**Everything is free.** No ads, no tracking, no analytics, no accounts, no paywalls, no
artificial limits, and **no internet permission**. All data stays on the phone.

> The app name lives in one place: `app_name` in `app/src/main/res/values/strings.xml`.

---

## Install on your phone (no Android Studio needed)

Every push to `main` runs GitHub Actions, which builds the app and refreshes a pre-release
called **latest**:

1. On your phone, open the repo on GitHub → **Releases** → **Latest build** →
   download `calm-launcher.apk`.
2. Allow your browser to install unknown apps when Android asks.
3. Open Calm Launcher, follow the short welcome, and tap **Set as home app**.

Builds are signed with a fixed test key (`app/debug.keystore`), so each new build installs
as an update over the previous one. `calm-launcher-debug.apk` is a separate debug copy
(`com.calmlauncher.debug`) that can sit next to it.

To go back to your old launcher: Settings → Apps → Default apps → Home app, or uninstall.

## Build locally

Requirements: JDK 17+, Android SDK (compileSdk 36). Android Studio Ladybug or newer works.

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # minified (R8) release APK
./gradlew :domain:test :app:testDebugUnitTest   # unit tests
./gradlew :app:connectedDebugAndroidTest        # UI + instrumented tests (device/emulator)
./gradlew detekt :app:lintDebug                 # static analysis
./gradlew :app:generateBaselineProfile          # regenerate the baseline profile (device, API 28+)
```

Real release signing: set `CALM_KEYSTORE_FILE`, `CALM_KEYSTORE_PASSWORD`, `CALM_KEY_ALIAS`
and `CALM_KEY_PASSWORD` (environment or a git-ignored `keystore.properties`). Nothing secret
is committed.

---

## Features

### Home
- Large clock (12/24 h or system, optional seconds, size, weight) and date; alignment left/center/right.
- Tapping the clock/date opens your clock/calendar app (or any app/action you pick).
- Unlimited text favorites (scrolls when needed), optional icons, two bottom shortcuts (Phone and Camera by default).
- Optional battery line, next calendar event, and a daily intention line — all off by default.
- One-tap focus switch at the bottom; dims or hides apps not allowed during focus.
- Wallpaper with adjustable dimming, or a solid background.

### Gestures (each configurable to any app or action)
| Gesture | Default |
|---|---|
| Swipe up | App list |
| Swipe down | Notification shade (falls back to search) |
| Swipe left / right | Camera / Phone |
| Double tap | Lock screen (accessibility on Android 9+, or device-admin fallback) |
| Long press | Launcher settings |

Touches starting in the system gesture zones are ignored, so Back/Home always win. Every
gesture has a non-gesture alternative (TalkBack custom actions on home, the drawer footer,
search, and settings), and typing on a hardware keyboard at home starts a search.

### App list and search
- Alphabetical list with an A–Z fast-scroll strip, optional group filter chips, optional icons.
- Instant search: exact → prefix → word/camel-case starts → initials (`ym` → YouTube Music)
  → substring → fuzzy (one typo allowed), case- and accent-insensitive, aliases included,
  package names optional. Ranked by match quality, then recent/frequent use.
- Search also finds Android settings pages, launcher screens and tools, contacts (opt-in),
  a calculator (`12*3+4`, `15% of 80`), and "search the web for …" (your browser does that).
- Open-the-only-match after a configurable delay, with a cancel tap.
- Hidden apps (still findable by exact name if you want), optional biometric/screen-lock or PIN.

### Long-press menu
Favorite/unfavorite and reorder, rename (local alias), hide, group, focus rules (pause,
limit, block, quiet hours), notification mode, app shortcuts, open the work-profile copy,
App info, Uninstall.

### Focus tools
1. **Mindful pause** – 3–15 s calm countdown, optional breathing circle, optional "why?" with quick reasons and a private note.
2. **Daily limits** per app and per group, from Android's usage stats. At the limit: close, +5 minutes, or "no limit today" with friction.
3. **Focus mode** – manual (timed or untimed), Quick Settings tile, multiple weekly schedules, allowlist, optional strict exit (wait + type a phrase).
4. **Quiet hours** – scheduled block windows per app or group (e.g. social apps 22:00–07:00).
5. **Black-and-white launcher** theme, plus a shortcut to Android's own grayscale setting.
6. **Notification rules** – allow, silence, or collect into a digest delivered at times you choose.
7. **Enforcement outside the launcher** – optional accessibility service (window-change events only) or a usage-stats fallback monitor.
8. **Insights** – screen time, unlocks, top apps, launches after a pause, "times you chose not to open"; day and week.
9. **Light tools** – notes, to-do list, timer shortcut (each can be turned off).

**Safety exit:** Settings → Focus → *Pause all rules for 1 hour* (press and hold 3 s, then a
short typing step). System Settings, the dialer and the keyboard are never blocked, so the
launcher can never trap you.

### Settings, backup, onboarding
Searchable settings (Home, Gestures, App list & search, Focus, Notifications, Appearance,
Tools, Backup & restore, Privacy, About). JSON export/import through the system file picker
with a versioned schema and migrations; reset to defaults; delete all data. Five-screen,
skippable onboarding.

### Appearance
Light, Dark, AMOLED black, Follow system; optional accent (default none), optional Material
You colors (Android 12+). Six bundled OFL fonts (Inter, IBM Plex Sans, Atkinson Hyperlegible
Next, Space Grotesk, Source Serif 4, JetBrains Mono) plus system font; size, weight, letter
spacing and line height. Works with system font scaling up to 200%.

---

## Permissions and why

| Permission | When it's asked | Why |
|---|---|---|
| `QUERY_ALL_PACKAGES` | Install | A launcher must list every app. |
| `EXPAND_STATUS_BAR` | Install (normal) | Swipe down to open notifications. |
| `REQUEST_DELETE_PACKAGES` | Install (normal) | "Uninstall" in the long-press menu opens the system dialog. |
| `SET_ALARM` | Install (normal) | Timer shortcut opens your clock app. |
| Usage access (`PACKAGE_USAGE_STATS`) | When you turn on limits/insights | Read today's per-app time from Android's own records. |
| Accessibility service | When you choose it | Double-tap lock; notice which app opened so your rules apply everywhere. App names only. |
| Device admin (`force-lock`) | When you choose it | Double-tap lock fallback. Nothing else. |
| Notification access | When you set notification rules | Silence or collect notifications you chose. |
| `POST_NOTIFICATIONS` | When you enable the digest | One reminder when the digest is ready. |
| `READ_CONTACTS` / `READ_CALENDAR` | When you turn those features on | Contacts in search / next event on home. |
| `USE_BIOMETRIC` | When you lock hidden apps | Fingerprint/face/screen-lock prompt. |
| `RECEIVE_BOOT_COMPLETED` | Install | Restore schedules after reboot. |
| `FOREGROUND_SERVICE(_SPECIAL_USE)` | Only with the fallback monitor | Android requires it for that optional service. |
| `SYSTEM_ALERT_WINDOW` | Only with the fallback monitor | Show the pause screen over another app; otherwise a notification is used. |

Deny any optional permission and the rest of the launcher keeps working.

---

## Architecture

```
app/       Android app (single activity, Compose, Hilt)
  core/        design system (tokens, theme, typography, components), utilities
  data/        Room DB, typed DataStore settings, repositories, policy evaluator, backup
  service/     launching, enforcement, accessibility, notification listener, tile, alarms
  ui/          root navigation, shared composables, activity view model
  feature/     home, drawer, appsheet, gate, focus, insights, tools, digest, settings, onboarding
domain/    pure Kotlin: search engine, calculator, schedules, launch policy, usage math, backup schema
benchmark/ baseline-profile generator + startup/scroll macrobenchmarks
```

- **MVVM + unidirectional data flow:** repositories expose `StateFlow`s; view models combine
  them; Compose renders state and sends intents back.
- **App discovery:** `LauncherApps` across all profiles (`UserManager` serials as stable IDs),
  cached in Room for an instant first frame, refreshed incrementally via
  `LauncherApps.Callback` plus a package broadcast fallback.
- **Launch policy:** one pure function (`LaunchPolicy.decide`) used by the launcher, the
  accessibility service and the fallback monitor, so behavior is identical everywhere.
- **Settings:** a single `@Serializable` data class in a typed DataStore (JSON file). Unknown
  keys are ignored and missing keys take defaults, which also powers backup import.
- **Schedules:** evaluated from wall-clock time every time, so reboots, time-zone and DST
  changes can't leave focus mode stuck; inexact alarms only refresh the tile/monitor/digest.

## Docs
- [docs/PRIVACY.md](docs/PRIVACY.md) – plain-language privacy notes
- [docs/QA.md](docs/QA.md) – manual test checklist across devices and OEMs
- [docs/DECISIONS.md](docs/DECISIONS.md) – choices made where the spec left room

## License of bundled assets
Fonts are under the SIL Open Font License 1.1 (texts in `app/src/main/assets/licenses`).
The icon and all code are original to this project.
