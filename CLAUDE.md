# REBUILD — code map

Read this before touching code. It says where things live so you can open the
one or two files a change needs instead of sweeping the tree. Symbol names, not
line numbers (those drift). **Update this file in the same commit when you add,
move, or rename anything listed here.**

Package root: `app/src/main/java/com/evyr/rebuild/` (paths below are relative to it).

## Build & ship
- Kotlin + Jetpack Compose + Room, single module `app`. compileSdk 36, minSdk 26, targetSdk 34.
- Built only by GitHub Actions (`.github/workflows/build-apk.yml`, Gradle pinned to 8.13 there and must match the wrapper). Push to `main` → APK on the `latest` release as `rebuild-latest.apk`. No local Android SDK is assumed.
- Pushes made from a Claude session do NOT trigger the workflow. After pushing, start it by hand: `gh api -X POST repos/ApartmentDog/REBUILD/actions/workflows/build-apk.yml/dispatches -f ref=main` with the owner's token in `GH_TOKEN`, then watch `actions/runs`. The session's own credentials can't reach Actions.
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
  - Habit design: `anchor` (the "after I ___" cue), `minimumVersion` (bad-day version), `focus` / `focusSinceEpochMs` (max `MAX_FOCUS` = 3), `graduatedEpochMs`, `copingReason` + `copingPlan` (if-then plan).
- **"Is it due today?"** has exactly one source of truth: `AppRepository.isDueToday`. HARD/STANDING/MINDSET are never "due". Single-day weekly items carry over: `weeklyOverdueDays` keeps them listed from their day until done that week.
- **Completion** rows are keyed (directiveId, yyyy-MM-dd). `markComplete` / `markIncomplete` in the repo. Unticking also clears that day's tap/volume progress and rolls `lastCompletedEpochMs` back.
- **Streaks**: `Streak` table; `confirmStreakHeldToday` (once a day via `lastHeldDate`; also writes a Completion with source `"hold"` so clean days can be counted). `breakStreak` = **slip** with never-miss-twice: logs a `StreakBreak`, keeps the run unless `lastSlipDate` was yesterday (then resets); returns `SlipOutcome` KEPT / RESET / ALREADY. UI calls it SLIP.
- **Completion sources**: `manual`, `nfc`, `volume`, `reminder`, `hard`, `health`, `hold` (standing), `min` (minimum version: half weight in Sync, counts for consistency).
- **Habit stats**: `AppRepository.computeStats()` → `DirectiveStats` per directive (14 `DayMark` cells, 30-day done/due, lifetime count, days tracked, missed yesterday, top miss reason, weekly overdue). One rule set shared by the row bars, `mostMissed`, `checkMilestones` and `copingCandidates`. VM: `stats` (recomputed on `habitTrigger`).
- **Milestones**: `checkMilestones()` graduates DAILY/WEEKLY at `GRADUATE_DAYS` = 66 tracked days and ≥ `GRADUATE_RATE` 80% over 30 (STANDING at a 66-day run), and unlocks Fry Scream at `UNLOCK_NEEDED` = 10 full Voice Protocol days of 14 (by title: `VOICE_TITLE`, `FRY_SCREAM_TITLE`). VM queues them in `milestones`; Dashboard shows one at a time.
- **Misses / coping**: `miss_log` (one reason per directive per day, keys in `MissReasons`), `recordMiss`, cleared by `markComplete`. `copingCandidates` = same reason ≥ `COPING_TRIGGER` (3) in 30 days with no plan for it.
- **Sync Ratio**: `AppRepository.computeSyncRatioBreakdown` = 70% weighted completion of due items (focus ×2, minimum ×0.5) + 30% standing streaks held (not if slipped today) → 0–100, plus cleared anomalies today, plus 🔥 boosts (`hardBoostNow`: full on clear day, linear fade over `HARD_BOOST_DAYS` = 7), capped at `MAX_SYNC` = 400. Snapshotted daily to `sync_history`. VM recomputes on any change to completions, streaks or directives. Display: `ui/components/SyncRatioBar` (>100 = BERSERK).
- **Dates**: `AppRepository.todayKey()`, thread-local formatter. Live "today" flows go through `observeTodayKey()` so they roll over at midnight. Use that for any new per-day flow.
- **Routines**: `Routine` (ordered `directiveIds` CSV). Edited in Settings (`RoutinesSection` in `ui/screens/HabitSettingsSections.kt`), launched from chips at the top of the Dashboard DAILY tab, run in `ui/screens/RoutineRunnerScreen.kt` (step list fixed at start; DONE / +1 / MINIMUM / SKIP; NFC-only and volume steps can only be skipped there).
- **Urges**: StreakRow "URGE? RIDE IT OUT" → `UrgeDialog` (10-minute count with prompts) → `logUrge` (`urge_log`, outcome RODE_OUT / SLIPPED; SLIPPED opens the slip dialog). Count shown as "N BEATEN".
- **Anomalies (surprise challenges)**: `notification/AnomalyScheduler.kt` (`AnomalyScheduler`, `AnomalyPool`, `AnomalyReceiver`). One roll a day at a random minute 10:00–20:00 (time stored in prefs `rebuild_anomaly` so re-arming doesn't reshuffle it), `CHANCE` 0.4, skipped in quiet hours, max one a day (`spawnAnomaly`). One-hour window, `BOOST` +5 to that day's Sync Ratio when cleared (from the notification's CLEARED button or the Dashboard `AnomalyBanner`). Toggle: `SurpriseChallengeToggle` in Settings. Armed from `MainActivity.onCreate` and `BootReceiver`. Notification id 2003.
- **Health Connect**: `data/HealthSync` ticks ONE linked directive (prefs `rebuild_health`) when an `ExerciseSessionRecord` of `MIN_MINUTES` (10)+ exists today; completion source `"health"`. Runs in `MainActivity.onResume` and `DigestReceiver`. Settings: `HealthConnectSection`. Needs `connect-client` 1.1.0, `READ_EXERCISE`, the `<queries>` entry, and `HealthRationaleActivity` + the `ViewPermissionUsageActivity` alias in the manifest (HC won't show its permission screen without them).
- **Second sealed code**: `data/RecordVault` (prefs `rebuild_record`, `CODE`), earned by any graduation or a `RUN_DAYS` (30) standing run; revealed once on the Dashboard; opens `ui/screens/PersonnelFileScreen.kt` (data from `AppRepository.serviceRecord`). Same rule as HOARD: don't describe it to the owner.
- **🔥 Hard objectives**: `clearHardObjective` / `unclearHardObjective` (same-day undo only). `data/HardModeVault` latches the unlock of a sealed cheat code at 5 cleared (SharedPreferences `rebuild_vault`, `REQUIRED`, `CODE`). The code opens `ui/screens/HoardScreen.kt`. The owner asked not to be told what that screen is: don't describe it to him.

## Database
- `data/RebuildDatabase.kt`: entity list, `version`, every `MIGRATION_x_y`, `getInstance`. **Current schema: 26.** Never reuse a version number the phone may already have: v23 = tarot spreads, v24 = place/🔥/HOLD columns, v25 = reminder tone/vibrate/re-nag + digest hours, v26 = habit design (directive cue/minimum/focus/graduation/coping, streak `lastSlipDate`, tables miss_log, routines, urge_log, anomalies).
- Adding/changing a column: (1) field with a default in `Models.kt`, (2) bump `version`, (3) write an additive `MIGRATION_n_n+1` and add it to `.addMigrations(...)`, (4) if it's a new table, add a DAO in `Daos.kt` and an abstract getter, and add it to `BackupEnvelope`, `buildEnvelope`, `applyEnvelope` and `countRows` in `data/BackupManager.kt` (defaulted list so old backups still load). Room rejects columns the entity doesn't declare, so a dropped column needs a table rebuild (see `MIGRATION_15_16`).
- No foreign keys anywhere: deletes clean up by hand (`AppRepository.deleteDirective`, `deleteGeofencePlace`).
- Tables: directives, completions, streaks, streak_breaks, miss_log, routines, urge_log, anomalies, nfc_tags, notebook_entries, session_steps, session_logs, media_attachments, notification_prefs (singleton id 1), geofence_places, geofence_place_directives, sync_history, daily_scan_progress, daily_volume_progress, hidden_media, reminders, tarot_pulls, books, confession_entries.
- Seed data (first run): `data/SeedData.kt` (`buildInitialDirectives`, `buildInitialSessionSteps`), applied by `AppRepository.seedIfEmpty`.
- Enum ↔ string converters: `data/Converters.kt` (add one for any new enum column).

## Backup
- `data/BackupManager.kt`: JSON envelope of every table, full replace on import.
- `data/BackupArchive.kt`: zip = JSON + media files (`writeTo`, `restoreFrom`). UI: `ui/screens/BackupSettingsSection.kt` (`BackupField`). After a restore, `MainViewModel.rearmAfterRestore` re-arms alarms and geofences.
- `data/WeeklyBackupWorker.kt` + `data/BackupFolderManager.kt`: automatic weekly zip into a user-picked SAF folder.
- Not in backups: tarot pulls, confession entries, hidden media and books (stored outside `filesDir/media` on purpose), SharedPreferences (PIN, vault, geofence presence, quote tracker).

## Screens & navigation
`MainActivity`: `private enum class Screen`, a `when (screen)` that renders each one, the cheat-code dialog (opened by tapping `VersionFooter` on the Dashboard), NFC foreground dispatch, and `onNewIntent` (tag write/read).
Flow: `LOCK` (`PinLockScreen`) → `PERMISSIONS` (`PermissionFlowScreen`) → `DAILY_QUOTE` (`DailyQuoteScreen`, once a day via `data/DailyQuoteTracker`) → `DASHBOARD`. A notification can ask to land somewhere via `MainActivity.EXTRA_OPEN` (`OPEN_CHECKIN`); `pendingOpen` is acted on once the Dashboard is reached, so the PIN still applies.

| Screen | File | Notes |
|---|---|---|
| Dashboard | `ui/screens/DashboardScreen.kt` | Tabs DAILY / WEEKLY / STANDING / 🔥 HARD / SESSIONS (`selectedZone` 0–4). Private pieces: `HardObjectivesPanel`, `CheckableRow` (📍 place line), `StreakRow`, `ZonePanel`, `VolumeEntryDialog`, `MostMissedPanel`. `+ ADD` in the footer opens the directive form on the current tab's zone; long-pressing a row (`combinedClickable`) opens DID THE MINIMUM / DID THE FULL THING / FOCUS / EDIT / REMINDERS / DELETE. Rows are ordered by `habitOrder` (focus, missed yesterday, here) and show ◆ focus, ★ graduated, the cue, DON'T MISS TWICE / OVERDUE, and `HabitLine` (14-day `ConsistencyBar`, 30-day %, lifetime ×N). Tap and volume trackers have no long-press. Completion feedback: `acted()` (haptic + arms `syncFlash`, the +N% shown on the sync panel). The trend chart is behind a tap on the sync panel (`showTrend`). Code-reveal dialog lives here. |
| Evening check-in | `ui/screens/CheckInScreen.kt` | Open items → DONE / MIN / miss reason; standing → HELD / SLIPPED; coping-plan prompts; "first thing tomorrow" (`data/DayPlan`); WEEK IN REVIEW (open on Sundays) with focus picker and share (daily/weekly only, never standing). Opened from the Dashboard footer CHECK-IN and the evening nudge. |
| Settings | `ui/screens/SettingsScreen.kt` | Order: NFC/Locations/Sessions links, directive list (add/edit/delete-confirm), then notifications, identity, backup, mindset, and a collapsed DIAGNOSTICS block (sync + geofence debug) |
| Directive form | `ui/screens/DirectiveEditComponents.kt` | `DirectiveEditRow`, `DirectiveEditDialog` (zone chips, 🔥 boost, LOCATION, recurrence, weekday, NFC, taps, volume), `AddMindsetLineDialog`, `ReminderManagementDialog` (time/arrival/departure, notification/alarm/calendar) |
| Settings sections | `NotificationSettingsSection.kt` (`NotificationPrefsPanel`), `BatteryOptimizationSection.kt`, `SyncRatioDebugSection.kt`, `GeofenceDebugSection.kt`, `MindsetSettingsSection.kt` (`MindsetSection`), `BackupSettingsSection.kt` | all in `ui/screens/` |
| Notebook | `ui/screens/NotebookScreen.kt` | search, tags, media attachments |
| NFC tags | `ui/screens/NfcRegistrationScreen.kt` | armed-label registration (`MainActivity.armPendingRegistration`), leaving/arriving lists |
| Locations | `ui/screens/GeofencePlacesScreen.kt` | map picker (`ui/components/GeofenceMapPicker`, OSMDroid), per-place checklist (`PlaceChecklistDialog`) |
| Guided sessions | `ui/screens/GuidedSessionScreen.kt` | posture/voice/fry scream runner; `GuidedSessionContent` is reused in the Dashboard tab |

Cheat codes (case-insensitive, in `MainActivity`'s dialog):
`FREEPLAY` → `HiddenMediaPlayerScreen` · `SECFOLDER` → launches Samsung Secure Folder · `SWORD` → `TarotPullScreen` (question field + spreads; deck and spreads in `tarot/TarotDeck.kt`) · `MIRROR` → `TarotLogScreen` · `ALEXANDRIA` → `BookLibraryScreen` / `BookReaderScreen` (`pdf/PdfHelper`, `epub/EpubParser`) · `APPLE` → `ConfessionBoothScreen` (password: `security/ConfessionPasswordManager`) · `HardModeVault.CODE` → `HoardScreen` (sealed until 5 🔥) · `RecordVault.CODE` → `PersonnelFileScreen` (sealed until the first milestone).
**Adding a code**: add a `Screen` value, an `else if` branch in the dialog's ENTER handler, and a `when (screen)` case.

## Theme & components (`ui/theme`, `ui/components`)
- `ui/theme/Theme.kt`: `Eva*` colors (EvaRed, EvaYellow, EvaPanelBg, EvaTextPrimary/Dim, EvaGreen), `Nerv*` aliases used by settings-style screens, `TerminalFontFamily` (Share Tech Mono), `RebuildTheme`.
- Components: `EvaPanel` (angled shadow panel), `HazardStripe(height, colorA, colorB)`, `CelOutlineText`, `ConsoleFrame` (beige console housing around the whole app), `HexGridBackground`, `SyncRatioBar`, `MiniLineChart`, `SegmentedTapBar`, `LiquidFillBar`, `VersionFooter` (secret tap), `GeofenceMapPicker`, `ConsistencyBar` (14-day cells).
- Text floor is 11sp on every screen except the tarot screens (card art is size-dependent) and `HoardScreen`. Don't add smaller text.
- Redemption alternate identity: `IconSwitcher.kt` (`LauncherTheme` NERV/REDEMPTION) toggles the two `activity-alias` launchers in `AndroidManifest.xml`. In-app retitling isn't built yet.

## Location / geofencing (`location/`)
- `GeofenceManager`: Play Services register/remove; `reconcileGeofences` = clear all, then register current (removal has no callback).
- `GeofenceSync`: `syncIfNeeded` (app open, stale > 1 day or flagged) / `reregister`.
- `GeofenceBroadcastReceiver`: accuracy gate (200m), sync-pending position reports, phantom-EXIT guard, 10-minute per-place debounce. Then ENTER → `ArrivalNudgeWorker`, EXIT → `DepartureCheckWorker`, plus place-triggered reminders fired under one `goAsync()`.
- `GeofencePresence`: SharedPreferences inside/outside per place, last departure time. The Dashboard reads it for "YOU'RE HERE".
- `GeofenceDebugLog`: on-device log shown in Settings.
- Location objectives: `Directive.placeId` → `AppRepository.pendingObjectivesForPlace` → `ArrivalNudgeWorker` → `NotificationHelper.showPlaceObjectives` (notification id 4000 + placeId).

## Notifications & reminders (`notification/`)
- **Reminder** (`data/Models.kt`): belongs to a directive. `triggerType` TIME/ARRIVAL/DEPARTURE, `reminderType` NOTIFICATION/ALARM/CALENDAR, `days` filter, `placeId` + `placeCondition` gate, `soundUri` (own tone), `vibrate`, `renagMinutes` / `renagMax` (repeat until done).
- `ReminderScheduler`: `schedule` (daily exact alarm; ALARM type uses `setAlarmClock`) / `cancel` / `rescheduleAll`, `scheduleFollowUp` / `cancelFollowUp` (one pending snooze-or-re-nag per reminder), `actionIntent` (PendingIntents told apart by intent action), `canScheduleExact`, `isDueToday` for the day filter.
- `ReminderAlarmReceiver`: the whole lifecycle. No action = daily firing (re-arms tomorrow first, then day/place gates, then skip if `AppRepository.isSatisfiedToday`, show, queue re-nag). `ACTION_FOLLOW_UP` = snooze/re-nag due (skips the gates). `ACTION_DONE` → `AppRepository.completeFromNotification`. `ACTION_SNOOZE_SHORT` / `_LONG` = 10 min / 1 hr.
- `NotificationHelper`: fixed channels (geofence / daily quote) and every `show*` method. `showReminder(reminder, directive, nagCount)` (body: message, "After <cue>.", re-nag line, if-then plan on re-nags, "Minimum counts") uses **one channel per reminder** (`rebuild_rem_<id>_<hash of tone settings>`), because Android fixes sound per channel; a tone change makes a new channel and `deleteReminderChannels` removes the old. ALARM type is `FLAG_INSISTENT` (loops). Buttons: DONE (or +1 for multi-tap; none for mindset/volume), 10 MIN, 1 HR. Fixed ids: 1001 departure, 1002 arrival, 2001 morning, 2002 evening, 2003 anomaly, 3000+ reminders, 4000+ place objectives.
- `DigestScheduler.kt` (`DigestScheduler` + `DigestReceiver`): morning list (`DayPlan` first move, then focus first) and evening nudge (fires when anything's open or a standing goal is unchecked; opens the check-in), hours in `NotificationPrefs`. Armed from `MainActivity.onCreate`, `BootReceiver`, after restore, and on any change in `NotificationPrefsPanel`.
- Reminder UI: `ReminderManagementDialog` in `ui/screens/DirectiveEditComponents.kt` (tap a row to edit; `ReminderTimeField` = system time picker + presets; shows the usual completion time from `typicalCompletionMinute` with a REMIND 30-min-before chip; tone via the system ringtone picker). New directives can get a daily reminder straight from `DirectiveEditDialog` (`onSaveWithReminder` → `MainViewModel.addDirectiveWithReminder`).
- `CalendarReminderWriter` handles CALENDAR type. `BootReceiver` re-arms on boot or app update; `MainActivity.onCreate` also runs `rescheduleAll` (which clears orphaned reminders) and `GeofenceSync.syncIfNeeded`.
- Settings shows `ExactAlarmWarning` (in `NotificationSettingsSection.kt`) when exact alarms aren't permitted.
- `ArrivalNudgeWorker` / `DepartureCheckWorker`: door checklists (built by `nfc/DoorChecklistItems.build`). `NotificationActionReceiver`: DONE / REMIND ME LATER on those. `QuietHours.kt`: `isWithinQuietHours` (geofence-driven paths only; timed reminders and digests ignore it on purpose).
- Most missed: `AppRepository.mostMissed` (derived from the completions table, last 30 days, excludes today) → `MainViewModel.mostMissed` → `MostMissedPanel` at the bottom of the Dashboard DAILY tab.

## NFC (`nfc/`)
- `NfcHelper`: foreground dispatch, write/read label, UID. `NfcScanActivity`: transparent activity for tag taps while the app is closed; marks the item done and finishes, skipping the PIN on purpose.
- Door system: `DoorState` (LEAVING/ARRIVING mode, put-away window), `DoorChecklistItems`, `DoorChecklistActivity` (the checklist card a door tag opens).
- Tag scan → `AppRepository.handleTagScan` → `incrementScanProgress` (shared with UI taps and the widget).

## Widget (`widget/RebuildWidgetProvider.kt`)
RemoteViews with 10 fixed rows (`ROW_IDS` / `ROW_CONTAINER_IDS`); `rowsFor` shows 4–10 of them depending on the widget's resized height (`onAppWidgetOptionsChanged` redraws). Layout `res/layout/widget_rebuild.xml`, info `res/xml/rebuild_widget_info.xml`. Tapping a row sends `ACTION_COMPLETE` → `incrementScanProgress`. `refresh(context)` runs from `MainActivity.onPause`. RemoteViews can't use a bare `<View>`.

## Security
`security/PinManager` (EncryptedSharedPreferences PIN), `BiometricUnlock`, `ConfessionPasswordManager`. `MainActivity` is a `FragmentActivity` because BiometricPrompt needs one. An unknown NFC tag must never navigate away from `LOCK`/`PERMISSIONS`.

## Known gaps / open items
- Geofence nudges haven't been confirmed in a real-world crossing; check `GeofenceDebugLog`.
- Redemption in-app retitling (Pass 2) isn't built.
- `DEBUGGING_HANDOFF.md` is an old dashboard-render investigation, kept for history.
