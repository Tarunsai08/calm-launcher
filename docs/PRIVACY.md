# Privacy

Short version: **Calm Launcher collects nothing and sends nothing.** It does not have the
Internet permission, so it technically cannot.

## What is stored, and where
Only in the app's private storage on your phone:

- Your settings (one small JSON file).
- App metadata you created: favorites and their order, names you gave apps, hidden apps,
  groups, and a launch counter used to rank search results.
- Your rules: pauses, limits, quiet hours, focus schedules, and today's limit extensions.
- A simple log of gate events (for example "paused, then opened" or "chose not to open"),
  with the optional reason and note you typed. Kept for 90 days, for the Insights screen.
- Notes and to-dos, if you use them.
- Notification digest items (title and text) — only for apps you chose to send to the digest.
- If you set one, a PIN for hidden apps. Only a salted hash is kept, encrypted with a key in
  the Android Keystore.

Android's automatic cloud backup and device-to-device transfer are **turned off** for this
app. If you want a backup, use *Settings → Backup and restore → Export*; you pick where the
file goes.

## Optional access
Each is requested only when you switch on the feature that needs it, with an explanation
first. Saying no never breaks the rest of the launcher.

- **Usage access** — reads how long apps were used today, from Android's own records, on the
  phone, when a limit or the Insights screen needs it.
- **Accessibility service ("focus helper")** — receives only "a window changed in app X"
  events. It cannot read screen content (`canRetrieveWindowContent=false`) and does not see
  what you type. Used for double-tap lock and for applying your own rules everywhere.
- **Notification access** — applies your allow/silence/digest choice per app. Calls, alarms,
  navigation and ongoing notifications are never touched.
- **Device admin** — `force-lock` only, for double-tap lock on phones where the accessibility
  method isn't used.
- **Contacts / Calendar** — read locally for search results / the next-event line.
- **Display over other apps** — only for the optional fallback monitor.

## Your controls
- Remove any access in Android Settings at any time.
- *Settings → Backup and restore → Delete all data* erases everything listed above.
- Uninstalling removes all of it too (turn off device admin first if you enabled it).

## Third parties
None. No SDKs for ads, analytics, crash reporting or attribution are included.
