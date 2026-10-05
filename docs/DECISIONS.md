# Decisions

Where the brief left room, these are the choices made, always leaning toward the most
user-respecting default.

## Platform and build
- **compileSdk/targetSdk 36** (Android 16), the newest stable level the toolchain here was
  verified against; **minSdk 26** as required. Bump both in `gradle/libs.versions.toml`.
- **Modules:** `:app` (Android), `:domain` (pure Kotlin, JUnit 5) and `:benchmark`. The
  requested `core / data / domain / ui / service / feature/*` split is done as packages inside
  `:app` plus the separate `:domain` module, which keeps builds fast while making all business
  logic testable on the JVM.
- **Settings storage:** a typed Jetpack DataStore holding one `@Serializable` data class as
  JSON (instead of Proto). Same guarantees as Proto DataStore, no protobuf toolchain, and the
  same serializer powers backup import with "ignore unknown keys, default missing ones".
- **Encrypted secret:** the optional hidden-apps PIN is stored as a salted PBKDF2 hash,
  encrypted with an AES-GCM key in the Android Keystore. The deprecated
  `androidx.security:security-crypto` library is not used.
- **No WorkManager:** schedules are evaluated from the wall clock whenever needed; inexact
  `AlarmManager` alarms only refresh the QS tile/monitor and deliver the digest. This avoids
  an extra dependency and any exact-alarm permission.
- **Test signing key committed** (`app/debug.keystore`, public test key) so every CI build
  installs as an update on the user's phone. Real releases use `CALM_KEYSTORE_*` secrets.
- **versionCode = GitHub run number** for the same reason.
- **Instrumented tests use the real app graph** (plain `AndroidJUnitRunner`) rather than Hilt
  test components, because the launcher's startup work lives in `Application.onCreate`.
- **Room schema version 2** with a tested `1 → 2` migration (launch counts + digest table).
  The migration test builds a v1 database from SQL and lets Room validate the result.

## UX
- **Defaults:** text only, monochrome, clock + date visible, every optional widget off.
  Search keyboard opens automatically (a launcher's drawer is mostly used to search);
  auto-launch is off.
- **Focus toggle on home** is a single small word ("focus") at the bottom, because the brief
  asks for a one-tap toggle on home; it can be hidden in settings.
- **Swipe down** defaults to the notification shade using the long-standing
  `StatusBarManager.expandNotificationsPanel()` with an accessibility fallback; if neither is
  available, search opens so the gesture never does nothing.
- **Double tap** defaults to lock but does nothing until the user picks a lock method; the
  app then explains where to set it up. The accessibility method is recommended (no
  uninstall friction), device admin is offered for Android 8 and for users who prefer it.
- **Edge handling:** rather than claiming large exclusion zones (Android caps them at 200 dp
  and users rely on back gestures), home swipes simply ignore touches that start inside the
  system gesture insets. The A–Z strip, which must sit at the edge, uses
  `systemGestureExclusion()`.
- **Web search fallback** sends `ACTION_WEB_SEARCH` to the user's chosen or default app; if
  nothing handles it, it opens a DuckDuckGo results URL in the browser. The launcher itself
  never makes network requests.
- **Calculator** supports `+ - * / % ^`, parentheses, `×`/`÷`, decimal commas and
  "`15% of 80`".
- **Pause pass:** after continuing through a pause, the same app isn't paused again for
  5 minutes (configurable), so returning from a quick app switch isn't punished.
- **Overrides** for quiet hours / always-blocked apps exist, but take a 10-second wait plus
  typing a phrase, and are logged for insights. Limits offer "+5 min" freely and "no limit
  today" with the same friction. Nothing is ever impossible to undo.
- **Never gated:** this launcher, system Settings, the dialer/telecom/emergency apps, System
  UI, the current keyboard, the permission controller and the package installer.
- **Safety exit:** Focus screen → "Pause all rules for 1 hour" (hold 3 s + phrase). It is
  documented in the README and reachable from settings search.
- **Strict mode** only adds friction to ending focus early; it never prevents it.
- **Background enforcement** prefers the accessibility service (no polling, no foreground
  notification). The usage-stats fallback runs as a `specialUse` foreground service only while
  a rule could apply, polls every 3 s with no wakelock, and stops itself otherwise. Without
  the overlay permission it posts a notification instead of drawing over apps.
- **Notification digest** stores title + text locally (needed to show the digest). Ongoing,
  foreground-service, call, alarm and navigation notifications are never touched.
- **Dual/clone apps and private space** (Android 15+) get a "clone" badge; work profile apps a
  "work" badge. Uninstall of a work app goes through App info (the system uninstaller only
  targets the current profile).
- **Duplicate labels** in the same profile show a short package hint next to the name.
- **Grayscale:** the launcher can render itself (including icons) without color; a shortcut
  opens Android's accessibility color settings for system-wide grayscale, as requested.

## Things intentionally not done
- No icon-pack support (not required).
- No system-wide display color changes (not allowed/requested).
- No exact alarms, no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Play policy); an OEM help
  screen links to the right settings instead.
- Android's cloud backup is disabled; users export a local JSON file instead.
