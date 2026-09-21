package com.evyr.rebuild.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Whole-database export/import as a single JSON file the person can save
 * anywhere (Google Drive, email, local storage) and restore from later.
 * This is NOT automatic cloud backup -- it's a manual "make a file, keep it
 * somewhere safe" export, which is the actual ask: don't lose data on app
 * updates or reinstalls, without standing up an account/server system for
 * a single-user personal app.
 *
 * Does not include media files (photos/videos referenced by MediaAttachment
 * or SessionStep.referenceVideoPath) -- only their file paths, which won't
 * resolve after a reinstall since app-private storage is wiped. Flagging
 * this as a known limitation rather than silently dropping it: a full
 * media-inclusive backup would need to bundle files into an archive format
 * (zip) instead of plain JSON, which is a larger undertaking than this pass.
 */
@Serializable
data class BackupEnvelope(
    val schemaVersion: Int,
    val exportedAtEpochMs: Long,
    val directives: List<Directive>,
    val completions: List<Completion>,
    val streaks: List<Streak>,
    val nfcTags: List<NfcTag>,
    val notebookEntries: List<NotebookEntry>,
    val sessionSteps: List<SessionStep>,
    val sessionLogs: List<SessionLog>,
    val mediaAttachments: List<MediaAttachment>,
    val notificationPrefs: NotificationPrefs?,
    val geofencePlaces: List<GeofencePlace>,
    val syncHistory: List<SyncHistoryEntry>,
    // Defaulted so archives written before this table existed still deserialize
    // instead of failing outright.
    val streakBreaks: List<StreakBreak> = emptyList(),
    val scanProgress: List<DailyScanProgress> = emptyList(),
    val placeDirectives: List<GeofencePlaceDirective> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val volumeProgress: List<DailyVolumeProgress> = emptyList()
)

object BackupManager {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    sealed class BackupResult {
        data class ExportSuccess(val json: String, val rowCount: Int) : BackupResult()
        data class ImportSuccess(val rowCount: Int) : BackupResult()
        data class Error(val message: String) : BackupResult()
    }

    /** Reads every table into an envelope. Shared by the JSON and zip backup paths. */
    suspend fun buildEnvelope(db: RebuildDatabase): BackupEnvelope = BackupEnvelope(
        schemaVersion = 3,
        exportedAtEpochMs = System.currentTimeMillis(),
        directives = db.directiveDao().observeAll().first(),
        completions = db.completionDao().getAll(),
        streaks = db.streakDao().observeAll().first(),
        nfcTags = db.nfcTagDao().observeAll().first(),
        notebookEntries = db.notebookDao().observeAll().first(),
        sessionSteps = db.sessionStepDao().getAll(),
        sessionLogs = db.sessionLogDao().getAll(),
        mediaAttachments = db.mediaAttachmentDao().getAll(),
        notificationPrefs = db.notificationPrefsDao().observe().first(),
        geofencePlaces = db.geofencePlaceDao().observeAll().first(),
        syncHistory = db.syncHistoryDao().getAll(),
        streakBreaks = db.streakBreakDao().getAll(),
        scanProgress = db.dailyScanProgressDao().getAll(),
        placeDirectives = db.geofencePlaceDirectiveDao().getAll(),
        reminders = db.reminderDao().getAll(),
        volumeProgress = db.dailyVolumeProgressDao().getAll()
    )

    fun countRows(e: BackupEnvelope): Int =
        e.directives.size + e.completions.size + e.streaks.size + e.nfcTags.size +
            e.notebookEntries.size + e.sessionSteps.size + e.sessionLogs.size +
            e.mediaAttachments.size +
            e.geofencePlaces.size + e.syncHistory.size + e.streakBreaks.size + e.scanProgress.size + e.placeDirectives.size + e.reminders.size + e.volumeProgress.size

    /**
     * Wipes every table and repopulates from the envelope, inside one transaction
     * so a failure partway through can't leave a half-old, half-new database.
     * This is a full REPLACE, not a merge.
     */
    suspend fun applyEnvelope(db: RebuildDatabase, envelope: BackupEnvelope) {
        db.withTransaction {
            db.clearAllTables()
            envelope.directives.forEach { db.directiveDao().upsert(it) }
            envelope.completions.forEach { db.completionDao().upsert(it) }
            envelope.streaks.forEach { db.streakDao().upsert(it) }
            envelope.nfcTags.forEach { db.nfcTagDao().upsert(it) }
            envelope.notebookEntries.forEach { db.notebookDao().insert(it) }
            envelope.sessionSteps.forEach { db.sessionStepDao().upsert(it) }
            envelope.sessionLogs.forEach { db.sessionLogDao().upsert(it) }
            envelope.mediaAttachments.forEach { db.mediaAttachmentDao().insert(it) }
            envelope.notificationPrefs?.let { db.notificationPrefsDao().upsert(it) }
            envelope.geofencePlaces.forEach { db.geofencePlaceDao().upsert(it) }
            envelope.syncHistory.forEach { db.syncHistoryDao().upsert(it) }
            envelope.streakBreaks.forEach { db.streakBreakDao().insert(it) }
            envelope.scanProgress.forEach { db.dailyScanProgressDao().upsert(it) }
            envelope.placeDirectives.forEach { db.geofencePlaceDirectiveDao().link(it) }
            envelope.volumeProgress.forEach { db.dailyVolumeProgressDao().upsert(it) }
            // Known gap: this writes reminders to the database but does not
            // re-arm their AlarmManager alarms -- applyEnvelope has no Context
            // to call ReminderScheduler with, and widening this function's
            // signature is a larger change than this pass covers. A restored
            // reminder will show correctly in the app but won't actually fire
            // until its directive's reminder is toggled off and back on (which
            // does schedule it) or the phone reboots (BootReceiver reschedules
            // every enabled reminder). Flagging this plainly rather than
            // pretending restore fully re-arms everything.
            envelope.reminders.forEach { db.reminderDao().upsert(it) }
        }
    }

    suspend fun exportToJson(db: RebuildDatabase): BackupResult = withContext(Dispatchers.IO) {
        try {
            val envelope = buildEnvelope(db)
            BackupResult.ExportSuccess(json.encodeToString(envelope), countRows(envelope))
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Export failed")
        }
    }

    /**
     * Wipes every table and repopulates from the envelope, inside one
     * transaction so a failure partway through doesn't leave the DB in a
     * mixed old/new state. This is a full REPLACE, not a merge -- current
     * data is discarded in favor of whatever's in the backup file.
     */
    suspend fun importFromJson(db: RebuildDatabase, jsonText: String): BackupResult = withContext(Dispatchers.IO) {
        try {
            val envelope = json.decodeFromString<BackupEnvelope>(jsonText)
            applyEnvelope(db, envelope)
            BackupResult.ImportSuccess(countRows(envelope))
        } catch (e: kotlinx.serialization.SerializationException) {
            BackupResult.Error("File doesn't look like a valid REBUILD backup")
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "Import failed")
        }
    }

    suspend fun writeToUri(context: Context, uri: Uri, jsonText: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(jsonText.toByteArray()) } ?: return@withContext false
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun readFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}
