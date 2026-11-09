# REBUILD — code map

Read this before touching code. It says where things live so you can open the
one or two files a change needs instead of sweeping the tree. Symbol names, not
line numbers (those drift). **Update this file in the same commit when you add,
move, or rename anything listed here.**

Package root: `app/src/main/java/com/evyr/rebuild/` (paths below are relative to it).

## Build & ship
- Kotlin + Jetpack Compose + Room, single module `app`. compileSdk 36, minSdk 26, targetSdk 34.
- Built only by GitHub Actions (`.github/workflows/build-apk.yml`, Gradle pinned to 8.13 there and must match the wrapper). Push to `main` → APK on the `latest` release as `rebuild-latest.apk`. No local Android SDK is assumed.
- Signing: committed `keystore/debug.keystore`, wired in `app/build.gradle.kts`. Never replace it (updates would stop installing).
- `VersionFooter` shows `BuildConfig.VERSION_NAME`, `GIT_COMMIT_HASH`, `BUILD_TIMESTAMP` (set in `app/build.gradle.kts`).
- Owner's rules: push straight to `main` without asking; targeted diffs, not rewrites; nothing is "done" until a CI build passes.

## Architecture in one line
`MainActivity` (screen state machine) → `ui/MainViewModel` (StateFlows) → `data/AppRepository` (all logic) → Room DAOs (`data/Daos.kt`) on `data/RebuildDatabase`. Background pieces (workers, receivers, widget) build their own `AppRepository(RebuildDatabase.getInstance(ctx))`.

## Core concepts
- **Directive** (`data/Models.kt`) = any tracked item. The UI calls them directives; the owner may say "objectives".
  - `zone`: `DAILY`, `WEEKLY`, `STANDING` (streaks), `MINDSET` (quote lines, display-only), `HARD` (🔥 one-time).
  - Recurrence (DAILY/WEEKLY only): `recurrenceType` NONE/SPECIFIC_DAYS/INTERVAL/MONTHLY + `weekday`, `recurrenceDays`, `recurrenceIntervalDays`, `recurrenceDayOfMonth`, `lastCompletedEpochMs`.
  - Inputs: checkbox; `requiresNfcScan` (tag only); `requiredScans` > 1 (segmented tap bar); `targetOz` (volume/water fill bar).
  - `placeId` = location objective (geofence place). `hardBoost` / `hardClearedEpochMs` = 🔥 fields.
- **"Is it due today?"** has exactly one source of truth: `AppRepository.isDueToday`. HARD/STANDING/MINDSET are never "due".
- **Completion** rows are keyed (directiveId, yyyy-MM-dd). `markComplete` / `markIncomplete` in the repo. Unticking also clears that day's tap/volume progress and rolls `lastCompletedEpochMs` back.
- **Streaks**: `Streak` table; `confirmStreakHeldToday` (once a day via `lastHeldDate`), `breakStreak` (also logs `StreakBreak` and an optional notebook entry).
- **Sync Ratio**: `AppRepository.computeSyncRatioBreakdown` = 70% weighted completion of due items + 30% standing streaks held → 0–100, plus 🔥 boosts (`hardBoostNow`: full on clear day, linear fade over `HARD_BOOST_DAYS` = 7), capped at `MAX_SYNC` = 400. Snapshotted daily to `sync_history`. VM recomputes on any change to completions, streaks or directives. Display: `ui/components/SyncRatioBar` (>100 = BERSERK).
- **Dates**: `AppRepository.todayKey()`, thread-local formatter. Live "today" flows go through `observeTodayKey()` so they roll over at midnight. Use that for any new per-day flow.
- **🔥 Hard objectives**: `clearHardObjective` / `unclearHardObjective` (same-day undo only). `data/HardModeVault` latches the unlock of a sealed cheat code at 5 cleared (SharedPreferences `rebuild_vault`, `REQUIRED`, `CODE`). The code opens `ui/screens/HoardScreen.kt`. The owner asked not to be told what that screen is: don't describe it to him.

## Database
- `data/RebuildDatabase.kt`: entity list, `version`, every `MIGRATION_x_y`, `getInstance`. **Current schema: 23.**
- Adding/changing a column: (1) field with a default in `Models.kt`, (2) bump `version`, (3) write an additive `MIGRATION_n_n+1` and add it to `.addMigrations(...)`, (4) if it's a new table, add a DAO in `Daos.kt` and an abstract getter, and add it to `BackupEnvelope`, `buildEnvelope`, `applyEnvelope` and `countRows` in `data/BackupManager.kt` (defaulted list so old backups still load). Room rejects columns the entity doesn't declare, so a dropped column needs a table rebuild (see `MIGRATION_15_16`).
- No foreign keys anywhere: deletes clean up by hand (`AppRepository.deleteDirective`, `deleteGeofencePlace`).
- Tables: directives, completions, streaks, streak_breaks, nfc_tags, notebook_entries, session_steps, session_logs, media_attachments, notification_prefs (singleton id 1), geofence_places, geofence_place_directives, sync_history, daily_scan_progress, daily_volume_progress, hidden_media, reminders, tarot_pulls, books, confession_entries.
- Seed data (first run): `data/SeedData.kt` (`buildInitialDirectives`, `buildInitialSessionSteps`), applied by `AppRepository.seedIfEmpty`.
- Enum ↔ string converters: `data/Converters.kt` (add one for any new enum column).

## Backup
- `data/BackupManager.kt`: JSON envelope of every table, full replace on import.
- `data/BackupArchive.kt`: zip = JSON + media files (`writeTo`, `restoreFrom`). UI: `ui/screens/BackupSettingsSection.kt` (`BackupField`). After a restore, `MainViewModel.rearmAfterRestore` re-arms alarms and geofences.
- `data/WeeklyBackupWorker.kt` + `data/BackupFolderManager.kt`: automatic weekly zip into a user-picked SAF folder.
- Not in backups: hidden media and books (stored outside `filesDir/media` on purpose), SharedPreferences (PIN, vault, geofence presence, quote tracker).

## Screens & navigation
`MainActivity`: `private enum class Screen`, a `when (screen)` that renders each one, the cheat-code dialog (opened by tapping `VersionFooter` on the Dashboard), NFC foreground dispatch, and `onNewIntent` (tag write/read).
Flow: `LOCK` (`PinLockScreen`) → `PERMISSIONS` (`PermissionFlowScreen`) → `DAILY_QUOTE` (`DailyQuoteScreen`, once a day via `data/DailyQuoteTracker`) → `DASHBOARD`.

| Screen | File | Notes |
|---|---|---|
| Dashboard | `ui/screens/DashboardScreen.kt` | Tabs DAILY / WEEKLY / STANDING / 🔥 HARD / SESSIONS (`selectedZone` 0–4). Private pieces: `HardObjectivesPanel`, `CheckableRow` (📍 place line), `StreakRow`, `ZonePanel`, `VolumeEntryDialog`. Code-reveal dialog lives here. |
| Settings | `ui/screens/SettingsScreen.kt` | Directive list + add/edit/delete-confirm, links to NFC/Locations/Sessions, plus sections below |
| Directive form | `ui/screens/DirectiveEditComponents.kt` | `DirectiveEditRow`, `DirectiveEditDialog` (zone chips, 🔥 boost, LOCATION, recurrence, weekday, NFC, taps, volume), `AddMindsetLineDialog`, `ReminderManagementDialog` (time/arrival/departure, notification/alarm/calendar) |
| Settings sections | `NotificationSettingsSection.kt` (`NotificationPrefsPanel`), `BatteryOptimizationSection.kt`, `SyncRatioDebugSection.kt`, `GeofenceDebugSection.kt`, `MindsetSettingsSection.kt` (`MindsetSection`), `BackupSettingsSection.kt` | all in `ui/screens/` |
| Notebook | `ui/screens/NotebookScreen.kt` | search, tags, media attachments |
| NFC tags | `ui/screens/NfcRegistrationScreen.kt` | armed-label registration (`MainActivity.armPendingRegistration`), leaving/arriving lists |
| Locations | `ui/screens/GeofencePlacesScreen.kt` | map picker (`ui/components/GeofenceMapPicker`, OSMDroid), per-place checklist (`PlaceChecklistDialog`) |
| Guided sessions | `ui/screens/GuidedSessionScreen.kt` | posture/voice/fry scream runner; `GuidedSessionContent` is reused in the Dashboard tab |

Cheat codes (case-insensitive, in `MainActivity`'s dialog):
`FREEPLAY` → `HiddenMediaPlayerScreen` · `SECFOLDER` → launches Samsung Secure Folder · `SWORD` → `TarotPullScreen` (deck in `tarot/TarotDeck.kt`) · `MIRROR` → `TarotLogScreen` · `ALEXANDRIA` → `BookLibraryScreen` / `BookReaderScreen` (`pdf/PdfHelper`, `epub/EpubParser`) · `APPLE` → `ConfessionBoothScreen` (password: `security/ConfessionPasswordManager`) · `HardModeVault.CODE` → `HoardScreen` (sealed until 5 🔥).
**Adding a code**: add a `Screen` value, an `else if` branch in the dialog's ENTER handler, and a `when (screen)` case.

## Theme & components (`ui/theme`, `ui/components`)
- `ui/theme/Theme.kt`: `Eva*` colors (EvaRed, EvaYellow, EvaPanelBg, EvaTextPrimary/Dim, EvaGreen), `Nerv*` aliases used by settings-style screens, `TerminalFontFamily` (Share Tech Mono), `RebuildTheme`.
- Components: `EvaPanel` (angled shadow panel), `HazardStripe(height, colorA, colorB)`, `CelOutlineText`, `ConsoleFrame` (beige console housing around the whole app), `HexGridBackground`, `SyncRatioBar`, `MiniLineChart`, `SegmentedTapBar`, `LiquidFillBar`, `VersionFooter` (secret tap), `GeofenceMapPicker`.
- Redemption alternate identity: `IconSwitcher.kt` (`LauncherTheme` NERV/REDEMPTION) toggles the two `activity-alias` launchers in `AndroidManifest.xml`. In-app retitling isn't built yet.

## Location / geofencing (`location/`)
- `GeofenceManager`: Play Services register/remove; `reconcileGeofences` = clear all, then register current (removal has no callback).
- `GeofenceSync`: `syncIfNeeded` (app open, stale > 1 day or flagged) / `reregister`.
- `GeofenceBroadcastReceiver`: accuracy gate (200m), sync-pending position reports, phantom-EXIT guard, 10-minute per-place debounce. Then ENTER → `ArrivalNudgeWorker`, EXIT → `DepartureCheckWorker`, plus place-triggered reminders fired under one `goAsync()`.
- `GeofencePresence`: SharedPreferences inside/outside per place, last departure time. The Dashboard reads it for "YOU'RE HERE".
- `GeofenceDebugLog`: on-device log shown in Settings.
- Location objectives: `Directive.placeId` → `AppRepository.pendingObjectivesForPlace` → `ArrivalNudgeWorker` → `NotificationHelper.showPlaceObjectives` (notification id 4000 + placeId).

## Notifications & reminders (`notification/`)
- `NotificationHelper`: channels (geofence / daily quote / reminders) and every `show*` method. Fixed ids: 1001 departure, 1002 arrival, 2001 morning summary, 3000+ reminders, 4000+ place objectives.
- `ReminderScheduler`: `schedule` / `cancel` / `rescheduleAll` (AlarmManager; ALARM type uses `setAlarmClock`), `isDueToday` for reminder day filters. `ReminderAlarmReceiver` fires them; `CalendarReminderWriter` handles CALENDAR type.
- `BootReceiver`: re-arms on boot or app update. `MainActivity.onCreate` also runs `rescheduleAll` (which clears orphaned reminders) and `GeofenceSync.syncIfNeeded`.
- `ArrivalNudgeWorker` / `DepartureCheckWorker`: door checklists (built by `nfc/DoorChecklistItems.build`). `NotificationActionReceiver`: DONE / REMIND ME LATER. `QuietHours.kt`: `isWithinQuietHours` (geofence-driven paths only; timed reminders ignore it on purpose).

## NFC (`nfc/`)
- `NfcHelper`: foreground dispatch, write/read label, UID. `NfcScanActivity`: transparent activity for tag taps while the app is closed; marks the item done and finishes, skipping the PIN on purpose.
- Door system: `DoorState` (LEAVING/ARRIVING mode, put-away window), `DoorChecklistItems`, `DoorChecklistActivity` (the checklist card a door tag opens).
- Tag scan → `AppRepository.handleTagScan` → `incrementScanProgress` (shared with UI taps and the widget).

## Widget (`widget/RebuildWidgetProvider.kt`)
RemoteViews with fixed rows (`ROW_IDS` / `ROW_CONTAINER_IDS`, `MAX_ROWS`), layout `res/layout/widget_rebuild.xml`, info `res/xml/rebuild_widget_info.xml`. Tapping a row sends `ACTION_COMPLETE` → `incrementScanProgress`. `refresh(context)` runs from `MainActivity.onPause`. RemoteViews can't use a bare `<View>`.

## Security
`security/PinManager` (EncryptedSharedPreferences PIN), `BiometricUnlock`, `ConfessionPasswordManager`. `MainActivity` is a `FragmentActivity` because BiometricPrompt needs one. An unknown NFC tag must never navigate away from `LOCK`/`PERMISSIONS`.

## Known gaps / open items
- Geofence nudges haven't been confirmed in a real-world crossing; check `GeofenceDebugLog`.
- Redemption in-app retitling (Pass 2) isn't built.
- `DEBUGGING_HANDOFF.md` is an old dashboard-render investigation, kept for history.
