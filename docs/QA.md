# Manual QA checklist

Run on at least: Pixel (stock, latest Android), Samsung (One UI), Xiaomi/POCO (HyperOS/MIUI),
OnePlus, Oppo/Realme or Vivo, Motorola, Nothing, one Android 8.0/8.1 device or emulator, one
tablet, and one foldable (or the resizable emulator).

## Install and default launcher
- [ ] Fresh install shows onboarding; "Skip" works on every page.
- [ ] "Set as home app" uses the system role dialog (Android 10+) or Home settings (8–9).
- [ ] Press Home from another app → Calm Launcher home. Press Home again on home → nothing breaks, drawer closes.
- [ ] Rapidly press Home 10× → no crash, no duplicate screens.
- [ ] Switch back to the previous launcher in system settings; uninstall works (after disabling device admin, if enabled).
- [ ] Clear app data → app restarts into onboarding, no crash.

## Home
- [ ] Default home shows only clock, date, favorites (text), bottom shortcuts, "focus".
- [ ] 12/24 h, seconds, size, weight, alignment (left/center/right) apply immediately.
- [ ] Clock tap opens clock app; date tap opens calendar; both re-assignable.
- [ ] Battery, next event (grant + deny calendar), intention line toggle on/off.
- [ ] 0 favorites → hint row; 20 favorites → list scrolls; swipe up outside the list still opens the drawer.
- [ ] Wallpaper mode with scrim 0–90 %, solid mode, all four themes; text readable (AA) in each.
- [ ] Rotate, fold/unfold, split-screen, change font size to 200 %: nothing clipped, state kept.
- [ ] RTL language (Arabic/Hebrew): layout mirrors; A–Z strip still usable.

## Gestures
- [ ] Each of the six gestures performs its configured action; reassign each to an app and to "Do nothing".
- [ ] Back-gesture from screen edges never triggers a home swipe; 3-button nav works.
- [ ] Swipe down opens the shade (Pixel, Samsung, Xiaomi); where not possible, search opens.
- [ ] Double tap locks via accessibility (Android 9+) and via device admin (Android 8); with neither, a hint explains setup.
- [ ] TalkBack: home exposes custom actions (open app list, search, settings, lock, notifications, focus).

## Drawer and search
- [ ] 300+ apps: scrolling is smooth; A–Z strip jumps; drag along the strip.
- [ ] "ym" finds YouTube Music; "calender" finds Calendar; accents ignored.
- [ ] Search results under 100 ms per keystroke (no visible lag) on a mid-range phone.
- [ ] Auto-launch single match (on): opens after delay; tapping the notice cancels; typing cancels.
- [ ] Settings shortcuts, calculator (`12*3+4`), contacts (granted/denied), web search fallback.
- [ ] Back: clears query first, then closes the drawer.
- [ ] Hardware keyboard: typing on home opens search with the letter; Enter opens first result; Esc closes.
- [ ] Hidden apps: hidden from list; exact name finds them only when enabled; lock with biometrics; PIN fallback on a phone with no screen lock.
- [ ] Groups: create, rename, delete, filter chips, assign apps.

## Context sheet
- [ ] Favorite/unfavorite, move up/down, rename (and reset to original), hide, group, notification mode cycle.
- [ ] App shortcuts appear when Calm Launcher is the default launcher (e.g., Chrome, Maps).
- [ ] Work profile app shows "work" badge, launches in the work profile; "Open work version" appears when both exist; paused work profile shows a message.
- [ ] Clone/dual apps (Xiaomi Dual apps, Samsung Dual Messenger) show "clone" badge and launch the correct copy.
- [ ] Uninstall a favorite → it disappears from home without restart; app update keeps alias/favorite.

## Focus tools
- [ ] Pause: countdown 3–15 s, breathing animation (disabled when system animations are off), reasons + note saved; "Not now" returns home.
- [ ] Daily limit: with usage access, block appears at limit; +5 min works; "no limit today" requires wait + phrase.
- [ ] Group limit counts all apps in the group.
- [ ] Limit reached while the app is open (helper on) → block screen appears within ~2 s.
- [ ] Focus mode manual (25/45/90/untimed), QS tile, schedules (overnight 22–07), allowlist, dim vs hide; strict exit challenge.
- [ ] Quiet hours per app block in the window and release after.
- [ ] Apps opened from a notification/link are paused/blocked when the helper is on; fallback monitor works with helper off (notification or overlay).
- [ ] Settings, Phone, keyboard and System UI are never blocked.
- [ ] Safety exit (hold 3 s + phrase) pauses everything for 1 hour.
- [ ] Reboot during a schedule → state correct after boot; change time zone / DST → schedules follow wall-clock time.
- [ ] Revoke usage access / disconnect accessibility while active → no crash; features show a gentle prompt.

## Notifications
- [ ] Without access: rules screen explains and links to settings.
- [ ] Silence removes new notifications for that app; digest collects them; digest reminder at chosen time; calls/alarms untouched.
- [ ] POST_NOTIFICATIONS denied (Android 13+) → digest still collects; no crash.

## Settings, backup, privacy
- [ ] Settings search finds rows by title and description.
- [ ] Export JSON → Delete all data → Import → favorites, rules, schedules, settings restored.
- [ ] Import a v1 backup (see `BackupCodecTest`) → migrated.
- [ ] Import a corrupt file → friendly error.
- [ ] Reset to defaults keeps favorites and rules.
- [ ] No network traffic at any time (check with a firewall app / `adb shell dumpsys netstats`).

## Performance and battery
- [ ] Cold start to interactive home < 500 ms on a mid-range device (`StartupBenchmark`).
- [ ] Memory steady state < 100 MB (Android Studio profiler) with icons off and on.
- [ ] No wakelocks held (`adb shell dumpsys power`); fallback monitor stops when no rule applies.
- [ ] OEM help screen shows the right tips on Xiaomi, Samsung, Oppo, Vivo, OnePlus, Huawei.

## Accessibility
- [ ] TalkBack reads every control meaningfully; headings are announced; focus order is logical.
- [ ] Every touch target ≥ 48 dp; switch access and keyboard navigation reach all controls.
- [ ] Reduced motion: no animations when "Remove animations" is on.
