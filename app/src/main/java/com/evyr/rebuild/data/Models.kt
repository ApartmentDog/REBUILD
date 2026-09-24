package com.evyr.rebuild.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class Zone { DAILY, WEEKLY, STANDING, MINDSET }

/**
 * How a directive recurs, beyond the coarse DAILY/WEEKLY zone split. NONE means
 * "just use the zone as before" (every day for DAILY, the single Directive.weekday
 * for WEEKLY) -- existing directives default here so nothing changes for them.
 */
@Serializable
enum class RecurrenceType { NONE, SPECIFIC_DAYS, INTERVAL, MONTHLY }

@Serializable
enum class ItemKind { CHECKBOX, STREAK, DISPLAY_ONLY }

@Serializable
enum class SessionType { POSTURE, VOICE, FRY_SCREAM }

@Serializable
enum class AttachmentKind { PHOTO, VIDEO }

/**
 * A single directive/task tracked by the app.
 * Daily items reset each day. Weekly items reset on their configured weekday.
 * Standing items are streak-tracked (days unbroken) rather than checked off.
 * Mindset items are display-only, no completion state.
 */
@Serializable
@Entity(tableName = "directives")
data class Directive(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val zone: Zone,
    val kind: ItemKind,
    val weight: Float = 1.0f,          // relative weight in Sync Ratio calc
    val weekday: Int? = null,           // 1-7 (Sunday=1) for WEEKLY items; null otherwise
    val expandableContent: String? = null, // sub-steps, stored as newline-delimited text
    val locked: Boolean = false,        // e.g. Fry Scream sub-module starts locked
    val requiresNfcScan: Boolean = false, // e.g. Wallet+Keys -- checkbox tap is disabled, only an NFC tag scan can complete it
    // Taps needed to complete this for the day. 1 = a single scan finishes it.
    // Higher values are for repeat targets, e.g. tap the water bottle 3x a day.
    val requiredScans: Int = 1,
    // Recurrence beyond the zone default. type=NONE (default) means unchanged
    // behavior -- every existing directive keeps working exactly as before.
    val recurrenceType: RecurrenceType = RecurrenceType.NONE,
    // SPECIFIC_DAYS: which weekdays, Calendar.DAY_OF_WEEK values (1=Sun..7=Sat),
    // comma-joined since Room doesn't store List<Int> natively without a
    // converter, and a handful of ints doesn't need a join table.
    val recurrenceDays: String? = null,
    // INTERVAL: "every N days", counted from lastCompletedEpochMs -- not from a
    // fixed calendar anchor, so skipping a day doesn't silently shift the cycle
    // to fixed calendar dates the person never chose.
    val recurrenceIntervalDays: Int? = null,
    val lastCompletedEpochMs: Long? = null,
    // MONTHLY: day of month, 1-31. Clamped to the actual last day for shorter
    // months (31 on Feb means the 28th/29th) rather than silently never firing
    // in months that don't have a 31st.
    val recurrenceDayOfMonth: Int? = null,
    val sortOrder: Int = 0,
    // Non-null marks this directive as a volume tracker (e.g. water): instead of
    // a checkbox or tap count, the user types in an amount and the bar fills as
    // a percentage of this target. Null means "not a volume tracker" -- every
    // existing directive keeps its current checkbox/tap behavior untouched.
    val targetOz: Int? = null
)

/**
 * A file added to the hidden section. Stored under filesDir/hidden rather than
 * filesDir/media on purpose: the backup archive walks the media folder, so keeping
 * these separate means they are not swept into a zip that gets written to a
 * cloud-synced folder. The trade is that they do not survive a reinstall via
 * restore -- they'd need re-adding.
 */
@Serializable
@Entity(tableName = "hidden_media")
data class HiddenMediaItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val filePath: String,
    val kind: AttachmentKind,
    val addedAtEpochMs: Long
)

/**
 * Running tap count for a multi-scan directive on a given day. Separate from
 * Completion because a Completion row means "done" -- this tracks the partial
 * progress that precedes it, and resets naturally each day by being keyed on date.
 */
@Serializable
@Entity(tableName = "daily_scan_progress", primaryKeys = ["directiveId", "date"])
data class DailyScanProgress(
    val directiveId: Long,
    val date: String,
    val count: Int
)

/**
 * Running oz total for a volume-tracker directive (targetOz != null) on a given
 * day. Mirrors DailyScanProgress's shape/reset-by-date pattern but stores a
 * logged amount instead of a tap count, since volume tracking is a distinct
 * input model (typed amount vs. tap-to-increment) from the NFC scan system.
 */
@Serializable
@Entity(tableName = "daily_volume_progress", primaryKeys = ["directiveId", "date"])
data class DailyVolumeProgress(
    val directiveId: Long,
    val date: String,
    val loggedOz: Int
)

/**
 * A single day's completion record for a Daily/Weekly item.
 */
@Serializable
@Entity(tableName = "completions", primaryKeys = ["directiveId", "date"])
data class Completion(
    val directiveId: Long,
    val date: String,          // ISO yyyy-MM-dd
    val completedAtEpochMs: Long,
    val source: String = "manual" // "manual" | "nfc" | "location"
)

/**
 * Streak state for a Standing directive.
 */
@Serializable
@Entity(tableName = "streaks")
data class Streak(
    @PrimaryKey val directiveId: Long,
    val currentStreakDays: Int = 0,
    val bestStreakDays: Int = 0,
    val lastBrokenEpochMs: Long? = null,
    val lastBrokenNote: String? = null
)

/**
 * A registered NFC tag mapped to a resting spot (wallet tray, key hook, gym bag).
 */
@Serializable
@Entity(tableName = "nfc_tags")
data class NfcTag(
    @PrimaryKey val tagUid: String,
    val label: String,           // "Wallet Tray", "Key Hook", etc.
    val linkedDirectiveId: Long?,
    val lastScanEpochMs: Long? = null,
    val lastScanLocationLabel: String? = null,
    // A door tag: tapping it opens a checklist of every other tagged item
    // and when it was last tapped, instead of ticking off one directive.
    val opensChecklist: Boolean = false
)

/**
 * Freeform notebook entry. tags is a comma-joined string (via Converters) for simple
 * filtering without a join table — fine at personal-journal scale.
 */
@Serializable
@Entity(tableName = "notebook_entries")
data class NotebookEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAtEpochMs: Long,
    val linkedDirectiveId: Long? = null, // e.g. attached as a "why streak broke" note
    val tags: List<String> = emptyList()
)

/**
 * A single step within a guided session type (e.g. Posture: "Chin tucks", 30s).
 * Seeded per SessionType; order via sortOrder.
 */
@Serializable
@Entity(tableName = "session_steps")
data class SessionStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionType: SessionType,
    val title: String,
    val instructions: String,
    val durationSeconds: Int,   // 0 = untimed, user advances manually
    val sortOrder: Int = 0,
    val referenceVideoPath: String? = null, // uploaded "what this move looks like" clip, app-private storage
    // A link to demo it instead of storing a file. Saving a video off YouTube
    // to local storage is friction that mostly stops the clip from ever being
    // attached, so a URL you tap to open covers the common case. Independent of
    // referenceVideoPath -- a step can have either, both, or neither.
    val referenceUrl: String? = null
)

/**
 * A completed (or partially completed) guided session run.
 */
@Serializable
@Entity(tableName = "session_logs")
data class SessionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionType: SessionType,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long?,   // null if abandoned mid-session
    val stepsCompleted: Int,
    val stepsTotal: Int,
    val notes: String? = null
)

/**
 * How a reminder actually surfaces. NOTIFICATION is the original behavior
 * (silent default, existing reminders keep working unchanged). ALARM rings
 * like a real alarm clock via AlarmManager.setAlarmClock -- shows the status
 * bar alarm icon and uses the system alarm UI, though Android does not let
 * third-party apps add entries to the Clock app's own alarm list. CALENDAR
 * writes an event into the phone's calendar via CalendarContract instead of
 * (or alongside) notifying.
 */
@Serializable
enum class ReminderType { NOTIFICATION, ALARM, CALENDAR }

/**
 * What sets a reminder off. TIME is the clock-based original. ARRIVAL and
 * DEPARTURE hang off a geofence place instead and ignore hour/minute entirely
 * -- "when I get home", not "at 6pm and hope I'm home".
 */
@Serializable
enum class ReminderTrigger { TIME, ARRIVAL, DEPARTURE }

/**
 * For TIME reminders, an optional location gate. AT_PLACE fires only when
 * you're inside the place, AWAY_FROM_PLACE only when you're not -- so a
 * "put the keys away" nudge can stay quiet while you're at work. Presence
 * comes from the geofence ENTER/EXIT events the app already receives
 * (GeofencePresence), not a fresh location fix at fire time.
 */
@Serializable
enum class PlaceCondition { ANYWHERE, AT_PLACE, AWAY_FROM_PLACE }

/**
 * A custom scheduled reminder attached to a directive -- a specific time of day
 * plus an optional custom message, separate from the directive's own due-today
 * logic. A directive can have zero or more reminders; nothing about Sync Ratio
 * or completion tracking depends on these, they're purely "notify me at this
 * time" on top of whatever the directive already does.
 */
@Serializable
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val directiveId: Long,
    val hour: Int,        // 0-23, local time
    val minute: Int,      // 0-59
    val customMessage: String? = null,
    val enabled: Boolean = true,
    val reminderType: ReminderType = ReminderType.NOTIFICATION,
    // Only meaningful when reminderType == CALENDAR. NONE means a single
    // one-shot event on the next occurrence of hour:minute rather than a
    // repeating series -- reuses Directive's recurrence vocabulary instead
    // of inventing a second one.
    val calendarRecurrenceType: RecurrenceType = RecurrenceType.NONE,
    val calendarRecurrenceDays: String? = null,       // SPECIFIC_DAYS, comma-joined Calendar.DAY_OF_WEEK
    val calendarRecurrenceIntervalDays: Int? = null,  // INTERVAL, every N days
    // CalendarContract event/calendar IDs for the event this reminder owns,
    // so edits/deletes update the same event instead of creating duplicates.
    val calendarEventId: Long? = null,

    val triggerType: ReminderTrigger = ReminderTrigger.TIME,
    // Comma-joined Calendar.DAY_OF_WEEK values (1=Sun..7=Sat). Null or blank
    // means every day, which is how every pre-existing reminder behaved.
    val days: String? = null,
    // The geofence place this reminder cares about. Required for ARRIVAL and
    // DEPARTURE; for TIME it's only read when placeCondition isn't ANYWHERE.
    val placeId: Long? = null,
    val placeCondition: PlaceCondition = PlaceCondition.ANYWHERE
)

/**
 * A photo or video attached to a notebook entry or session log.
 * Stored as a file path under app-private storage (not scoped storage/MediaStore),
 * since these are personal logs, not shareable media.
 */
@Serializable
@Entity(tableName = "media_attachments")
data class MediaAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: AttachmentKind,
    val filePath: String,
    val createdAtEpochMs: Long,
    val linkedNotebookEntryId: Long? = null,
    val linkedSessionLogId: Long? = null
)

/**
 * Per-notification-type user preferences: on/off and quiet hours.
 * Singleton row (id always 1).
 */
@Serializable
@Entity(tableName = "notification_prefs")
data class NotificationPrefs(
    @PrimaryKey val id: Int = 1,
    val geofenceNudgesEnabled: Boolean = true,
    val morningSummaryEnabled: Boolean = true,
    // On by default. Every geofence path already checked quiet hours, but the
    // feature shipped off, so the guard never ran and a phantom transition could
    // notify at any hour. Still toggleable in Settings.
    val quietHoursStart: Int? = 22,  // 0-23, null = no quiet hours
    val quietHoursEnd: Int? = 7
)

/**
 * One row per streak break, so patterns ("you break this most on Fridays") can be
 * derived. Streak.lastBrokenEpochMs only holds the most recent break -- it's
 * overwritten each time, so it can't support any history-based analysis.
 */
@Serializable
@Entity(tableName = "streak_breaks")
data class StreakBreak(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val directiveId: Long,
    val brokenAtEpochMs: Long,
    val note: String? = null
)

/**
 * A geofenced location (e.g. Home) used for location-based reminders.
 */
@Serializable
@Entity(tableName = "geofence_places")
data class GeofencePlace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 150f,
    val notifyOnArrival: Boolean = true,
    val notifyOnDeparture: Boolean = true
)

/**
 * Links a directive to a specific geofence place, for the "check this directive
 * when I leave/arrive at this place" list. Separate from
 * Directive.requiresNfcScan/NfcTag.linkedDirectiveId on purpose: those still
 * apply everywhere as before (the wallet/keys style check), this is an
 * additional, opt-in, per-place list layered on top.
 */
@Serializable
@Entity(
    tableName = "geofence_place_directives",
    primaryKeys = ["placeId", "directiveId"]
)
data class GeofencePlaceDirective(
    val placeId: Long,
    val directiveId: Long
)

/**
 * A daily Sync Ratio snapshot, stored once per day for history/trend views.
 */
@Serializable
@Entity(tableName = "sync_history", primaryKeys = ["date"])
data class SyncHistoryEntry(
    val date: String,
    val syncRatioPercent: Int
)

/**
 * A single tarot pull, logged every time SWORD draws a card -- viewable
 * separately via MIRROR. Stores the card name/arcana/orientation as plain
 * fields rather than a foreign key into the deck, since TarotDeck is static
 * Kotlin data, not a DB table -- this keeps the log readable even if deck
 * content changes later.
 */
@Serializable
@Entity(tableName = "tarot_pulls")
data class TarotPull(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardName: String,
    val arcana: String,       // TarotDeck.Arcana enum name, stored as text
    val reversed: Boolean,
    val pulledAtEpochMs: Long
)

@Serializable
enum class BookFormat { PDF, EPUB }

/**
 * A book in the ALEXANDRIA library. File is copied into app-private storage
 * under filesDir/books (see MediaCapture.copyPickedFileIntoStorage), same
 * pattern as Hidden Media -- kept outside filesDir/media so it isn't swept
 * into the backup archive.
 *
 * lastPosition means different things per format: for PDF, the page index
 * (0-based). For EPUB, the chapter index within the parsed spine order --
 * scroll position within a chapter isn't tracked, only which chapter.
 */
@Serializable
@Entity(tableName = "books")
data class BookItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val format: BookFormat,
    val addedAtEpochMs: Long,
    val lastPosition: Int = 0,
    val totalPages: Int = 0    // PDF: real page count. EPUB: chapter count.
)

/**
 * A single Confession Booth entry (APPLE cheat code). Stored as plain text --
 * the booth's password gates entry to the screen itself (see
 * ConfessionPasswordManager), it does not encrypt each entry individually.
 * Entries are never edited after writing, only read back or deleted --
 * "sealed" is the framing, so there's deliberately no update path.
 */
@Serializable
@Entity(tableName = "confession_entries")
data class ConfessionEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val writtenAtEpochMs: Long
)



