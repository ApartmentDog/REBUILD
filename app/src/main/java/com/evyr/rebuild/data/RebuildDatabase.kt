package com.evyr.rebuild.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Directive::class,
        Completion::class,
        Streak::class,
        NfcTag::class,
        NotebookEntry::class,
        GeofencePlace::class,
        SyncHistoryEntry::class,
        SessionStep::class,
        SessionLog::class,
        MediaAttachment::class,
        NotificationPrefs::class,
        StreakBreak::class,
        DailyScanProgress::class,
        HiddenMediaItem::class,
        GeofencePlaceDirective::class,
        Reminder::class,
        TarotPull::class,
        BookItem::class,
        ConfessionEntry::class,
        DailyVolumeProgress::class
    ],
    version = 19,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RebuildDatabase : RoomDatabase() {
    abstract fun directiveDao(): DirectiveDao
    abstract fun completionDao(): CompletionDao
    abstract fun streakDao(): StreakDao
    abstract fun nfcTagDao(): NfcTagDao
    abstract fun notebookDao(): NotebookDao
    abstract fun geofencePlaceDao(): GeofencePlaceDao
    abstract fun syncHistoryDao(): SyncHistoryDao
    abstract fun sessionStepDao(): SessionStepDao
    abstract fun sessionLogDao(): SessionLogDao
    abstract fun mediaAttachmentDao(): MediaAttachmentDao
    abstract fun notificationPrefsDao(): NotificationPrefsDao
    abstract fun streakBreakDao(): StreakBreakDao
    abstract fun dailyScanProgressDao(): DailyScanProgressDao
    abstract fun dailyVolumeProgressDao(): DailyVolumeProgressDao
    abstract fun hiddenMediaDao(): HiddenMediaDao
    abstract fun geofencePlaceDirectiveDao(): GeofencePlaceDirectiveDao
    abstract fun reminderDao(): ReminderDao
    abstract fun tarotPullDao(): TarotPullDao
    abstract fun bookDao(): BookDao
    abstract fun confessionEntryDao(): ConfessionEntryDao

    companion object {
        @Volatile private var INSTANCE: RebuildDatabase? = null

        /**
         * Adds streak_breaks. A real migration rather than a destructive one:
         * fallbackToDestructiveMigration wipes and recreates the database on any
         * version change, which was an acceptable trade early on and is not now
         * that there's real history in here. Creating a table touches nothing
         * existing, so this is a safe, additive migration.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS streak_breaks (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        directiveId INTEGER NOT NULL,
                        brokenAtEpochMs INTEGER NOT NULL,
                        note TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Adds multi-tap support: a per-day tap counter table, and a target on
         * directives. Both additive -- the column defaults to 1, so every existing
         * directive keeps behaving as a single-scan item.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE directives ADD COLUMN requiredScans INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_scan_progress (
                        directiveId INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        count INTEGER NOT NULL,
                        PRIMARY KEY(directiveId, date)
                    )
                    """.trimIndent()
                )
            }
        }

        /** Adds the hidden-section media library. Purely additive. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS hidden_media (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        label TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        addedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Adds the per-place directive checklist. Purely additive. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS geofence_place_directives (
                        placeId INTEGER NOT NULL,
                        directiveId INTEGER NOT NULL,
                        PRIMARY KEY(placeId, directiveId)
                    )
                    """.trimIndent()
                )
            }
        }

        /**
         * Adds saved_foods (quick re-log list) and a meal column on macro_entries
         * (breakfast/lunch/dinner/snack grouping). meal defaults to 'SNACK' so
         * every existing logged entry gets a valid value rather than null.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS saved_foods (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        label TEXT NOT NULL,
                        calories INTEGER NOT NULL,
                        proteinG REAL NOT NULL,
                        carbsG REAL NOT NULL,
                        fatG REAL NOT NULL,
                        lastUsedEpochMs INTEGER NOT NULL,
                        useCount INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE macro_entries ADD COLUMN meal TEXT NOT NULL DEFAULT 'SNACK'")
            }
        }

        /**
         * Adds custom recurrence to directives: specific weekdays, an N-day
         * interval, or a day-of-month. Purely additive columns, all nullable or
         * defaulted (recurrenceType defaults to NONE), so every existing directive
         * keeps its original DAILY/WEEKLY behavior unchanged.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE directives ADD COLUMN recurrenceType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE directives ADD COLUMN recurrenceDays TEXT")
                db.execSQL("ALTER TABLE directives ADD COLUMN recurrenceIntervalDays INTEGER")
                db.execSQL("ALTER TABLE directives ADD COLUMN lastCompletedEpochMs INTEGER")
                db.execSQL("ALTER TABLE directives ADD COLUMN recurrenceDayOfMonth INTEGER")
            }
        }

        /** Adds custom per-directive reminders. Purely additive: new table only. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminders (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        directiveId INTEGER NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        customMessage TEXT,
                        enabled INTEGER NOT NULL DEFAULT 1
                    )
                    """.trimIndent()
                )
            }
        }

        /** Reminders gain a delivery type (notification/alarm/calendar) plus
         *  calendar-only recurrence fields and a link to the CalendarContract
         *  event they own. Purely additive columns, all existing reminders
         *  default to NOTIFICATION and behave exactly as before. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN reminderType TEXT NOT NULL DEFAULT 'NOTIFICATION'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN calendarRecurrenceType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN calendarRecurrenceDays TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN calendarRecurrenceIntervalDays INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN calendarEventId INTEGER")
            }
        }

        /** Adds the tarot pull log (SWORD pulls / MIRROR views). Purely
         *  additive: new table only. */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS tarot_pulls (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        cardName TEXT NOT NULL,
                        arcana TEXT NOT NULL,
                        reversed INTEGER NOT NULL,
                        pulledAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Adds the ALEXANDRIA book library. Purely additive: new table only. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS books (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        format TEXT NOT NULL,
                        addedAtEpochMs INTEGER NOT NULL,
                        lastPosition INTEGER NOT NULL DEFAULT 0,
                        totalPages INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        /** Adds the Confession Booth entries table. Purely additive: new
         *  table only. The booth's password lives in its own
         *  EncryptedSharedPreferences (ConfessionPasswordManager), not here. */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS confession_entries (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        text TEXT NOT NULL,
                        writtenAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Adds the healthConnectMeals flag to directives. Purely additive:
         *  every existing directive defaults to 0 (false) and keeps behaving
         *  exactly as before. */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE directives ADD COLUMN healthConnectMeals INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Drops the meal-logging tables. The macro tracker, food search,
         *  barcode scanner and nutrition goals screens were removed -- meals
         *  are tracked in a dedicated app instead, and these half-built
         *  screens were only adding clutter. Destructive by request: the
         *  logged food data goes with them. */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS macro_entries")
                db.execSQL("DROP TABLE IF EXISTS saved_foods")
                db.execSQL("DROP TABLE IF EXISTS nutrition_goals")
                db.execSQL("DROP TABLE IF EXISTS weight_logs")

                // healthConnectMeals also has to go: Room validates the table
                // against the entity on open and rejects a column the entity no
                // longer declares, so leaving it would crash at startup rather
                // than being harmlessly ignored. SQLite has no portable DROP
                // COLUMN, hence the rebuild-and-copy.
                db.execSQL(
                    """
                    CREATE TABLE directives_new (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL,
                        zone TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        weight REAL NOT NULL,
                        weekday INTEGER,
                        expandableContent TEXT,
                        locked INTEGER NOT NULL,
                        requiresNfcScan INTEGER NOT NULL,
                        requiredScans INTEGER NOT NULL,
                        recurrenceType TEXT NOT NULL,
                        recurrenceDays TEXT,
                        recurrenceIntervalDays INTEGER,
                        lastCompletedEpochMs INTEGER,
                        recurrenceDayOfMonth INTEGER,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO directives_new (
                        id, title, zone, kind, weight, weekday, expandableContent, locked,
                        requiresNfcScan, requiredScans, recurrenceType, recurrenceDays,
                        recurrenceIntervalDays, lastCompletedEpochMs, recurrenceDayOfMonth, sortOrder
                    )
                    SELECT
                        id, title, zone, kind, weight, weekday, expandableContent, locked,
                        requiresNfcScan, requiredScans, recurrenceType, recurrenceDays,
                        recurrenceIntervalDays, lastCompletedEpochMs, recurrenceDayOfMonth, sortOrder
                    FROM directives
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE directives")
                db.execSQL("ALTER TABLE directives_new RENAME TO directives")
            }
        }

        /** Reminders gain a trigger, day-of-week filter, and place gating.
         *  Additive: every existing reminder defaults to TIME / every day /
         *  ANYWHERE, which is exactly how it behaved before. */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN triggerType TEXT NOT NULL DEFAULT 'TIME'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN days TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN placeId INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN placeCondition TEXT NOT NULL DEFAULT 'ANYWHERE'")
            }
        }

        /** Session steps gain an optional reference URL alongside the existing
         *  local video path. Additive; existing rows get null. */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE session_steps ADD COLUMN referenceUrl TEXT")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE directives ADD COLUMN targetOz INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_volume_progress (
                        directiveId INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        loggedOz INTEGER NOT NULL,
                        PRIMARY KEY(directiveId, date)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): RebuildDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RebuildDatabase::class.java,
                    "rebuild.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
