package com.evyr.rebuild.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectiveDao {
    @Query("SELECT * FROM directives ORDER BY zone, sortOrder")
    fun observeAll(): Flow<List<Directive>>

    @Query("SELECT * FROM directives WHERE zone = :zone ORDER BY sortOrder")
    fun observeByZone(zone: Zone): Flow<List<Directive>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(directive: Directive): Long

    @Delete
    suspend fun delete(directive: Directive)

    @Query("UPDATE directives SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)
}

@Dao
interface CompletionDao {
    @Query("SELECT * FROM completions WHERE date = :date")
    fun observeForDate(date: String): Flow<List<Completion>>

    @Query("SELECT * FROM completions WHERE directiveId = :directiveId ORDER BY date DESC LIMIT :limit")
    suspend fun recentForDirective(directiveId: Long, limit: Int = 90): List<Completion>

    @Query("SELECT * FROM completions")
    suspend fun getAll(): List<Completion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(completion: Completion)

    @Query("DELETE FROM completions WHERE directiveId = :directiveId AND date = :date")
    suspend fun clear(directiveId: Long, date: String)
}

@Dao
interface StreakDao {
    @Query("SELECT * FROM streaks WHERE directiveId = :directiveId")
    suspend fun get(directiveId: Long): Streak?

    @Query("SELECT * FROM streaks")
    fun observeAll(): Flow<List<Streak>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(streak: Streak)
}

@Dao
interface NfcTagDao {
    @Query("SELECT * FROM nfc_tags")
    fun observeAll(): Flow<List<NfcTag>>

    @Query("SELECT * FROM nfc_tags WHERE tagUid = :uid LIMIT 1")
    suspend fun findByUid(uid: String): NfcTag?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tag: NfcTag)

    @Delete
    suspend fun delete(tag: NfcTag)
}

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebook_entries ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<NotebookEntry>>

    @Query(
        "SELECT * FROM notebook_entries " +
            "WHERE text LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' " +
            "ORDER BY createdAtEpochMs DESC"
    )
    fun search(query: String): Flow<List<NotebookEntry>>

    @Insert
    suspend fun insert(entry: NotebookEntry): Long

    @Delete
    suspend fun delete(entry: NotebookEntry)
}

@Dao
interface SessionStepDao {
    @Query("SELECT * FROM session_steps WHERE sessionType = :type ORDER BY sortOrder")
    fun observeForType(type: SessionType): Flow<List<SessionStep>>

    @Query("SELECT * FROM session_steps")
    suspend fun getAll(): List<SessionStep>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(step: SessionStep): Long

    @Query("SELECT COUNT(*) FROM session_steps")
    suspend fun count(): Int
}

@Dao
interface SessionLogDao {
    @Query("SELECT * FROM session_logs ORDER BY startedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<SessionLog>>

    @Query("SELECT * FROM session_logs WHERE sessionType = :type ORDER BY startedAtEpochMs DESC LIMIT :limit")
    fun observeRecentForType(type: SessionType, limit: Int = 20): Flow<List<SessionLog>>

    @Query("SELECT * FROM session_logs")
    suspend fun getAll(): List<SessionLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: SessionLog): Long
}


@Dao
interface MediaAttachmentDao {
    @Query("SELECT * FROM media_attachments WHERE linkedNotebookEntryId = :entryId")
    fun observeForNotebookEntry(entryId: Long): Flow<List<MediaAttachment>>

    @Query("SELECT * FROM media_attachments WHERE linkedSessionLogId = :logId")
    fun observeForSessionLog(logId: Long): Flow<List<MediaAttachment>>

    @Query("SELECT * FROM media_attachments")
    suspend fun getAll(): List<MediaAttachment>

    @Insert
    suspend fun insert(attachment: MediaAttachment): Long

    @Delete
    suspend fun delete(attachment: MediaAttachment)
}

@Dao
interface NotificationPrefsDao {
    @Query("SELECT * FROM notification_prefs WHERE id = 1 LIMIT 1")
    fun observe(): Flow<NotificationPrefs?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prefs: NotificationPrefs)
}



@Dao
interface GeofencePlaceDao {
    @Query("SELECT * FROM geofence_places")
    fun observeAll(): Flow<List<GeofencePlace>>

    @Query("SELECT * FROM geofence_places WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): GeofencePlace?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: GeofencePlace): Long

    @Delete
    suspend fun delete(place: GeofencePlace)
}

@Dao
interface GeofencePlaceDirectiveDao {
    @Query("SELECT directiveId FROM geofence_place_directives WHERE placeId = :placeId")
    fun observeForPlace(placeId: Long): Flow<List<Long>>

    @Query("SELECT directiveId FROM geofence_place_directives WHERE placeId = :placeId")
    suspend fun forPlace(placeId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(entry: GeofencePlaceDirective)

    @Query("DELETE FROM geofence_place_directives WHERE placeId = :placeId AND directiveId = :directiveId")
    suspend fun unlink(placeId: Long, directiveId: Long)

    @Query("SELECT * FROM geofence_place_directives")
    suspend fun getAll(): List<GeofencePlaceDirective>

    @Query("SELECT * FROM geofence_place_directives WHERE placeId = :placeId")
    fun observeLinksForPlace(placeId: Long): Flow<List<GeofencePlaceDirective>>

    @Query("SELECT * FROM geofence_place_directives WHERE placeId = :placeId")
    suspend fun linksForPlace(placeId: Long): List<GeofencePlaceDirective>

    /** link() ignores conflicts; this one overwrites, for changing OUT/IN. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLink(entry: GeofencePlaceDirective)

    @Query("DELETE FROM geofence_place_directives WHERE placeId = :placeId")
    suspend fun clearForPlace(placeId: Long)
}

@Dao
interface HiddenMediaDao {
    @Query("SELECT * FROM hidden_media ORDER BY addedAtEpochMs DESC")
    fun observeAll(): Flow<List<HiddenMediaItem>>

    @Insert
    suspend fun insert(item: HiddenMediaItem): Long

    @Delete
    suspend fun delete(item: HiddenMediaItem)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE directiveId = :directiveId ORDER BY hour, minute")
    fun observeForDirective(directiveId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getAllEnabled(): List<Reminder>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE directiveId = :directiveId")
    suspend fun getForDirective(directiveId: Long): List<Reminder>

    @Query("SELECT * FROM reminders WHERE placeId = :placeId AND triggerType = :trigger")
    suspend fun getForPlace(placeId: Long, trigger: ReminderTrigger): List<Reminder>

    /** Reminders whose directive no longer exists. There's no foreign key on
     *  directiveId, so deleting a directive leaves its reminders behind and
     *  they keep firing with nothing to name them. */
    @Query("SELECT * FROM reminders WHERE directiveId NOT IN (SELECT id FROM directives)")
    suspend fun getOrphaned(): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: Reminder): Long

    @Delete
    suspend fun delete(reminder: Reminder)
}


@Dao
interface DailyScanProgressDao {
    @Query("SELECT * FROM daily_scan_progress WHERE date = :date")
    fun observeForDate(date: String): Flow<List<DailyScanProgress>>

    @Query("SELECT * FROM daily_scan_progress WHERE directiveId = :directiveId AND date = :date LIMIT 1")
    suspend fun get(directiveId: Long, date: String): DailyScanProgress?

    @Query("SELECT * FROM daily_scan_progress")
    suspend fun getAll(): List<DailyScanProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: DailyScanProgress)

    @Query("DELETE FROM daily_scan_progress WHERE directiveId = :directiveId AND date = :date")
    suspend fun clear(directiveId: Long, date: String)
}

@Dao
interface DailyVolumeProgressDao {
    @Query("SELECT * FROM daily_volume_progress WHERE date = :date")
    fun observeForDate(date: String): Flow<List<DailyVolumeProgress>>

    @Query("SELECT * FROM daily_volume_progress WHERE directiveId = :directiveId AND date = :date LIMIT 1")
    suspend fun get(directiveId: Long, date: String): DailyVolumeProgress?

    @Query("SELECT * FROM daily_volume_progress")
    suspend fun getAll(): List<DailyVolumeProgress>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: DailyVolumeProgress)

    @Query("DELETE FROM daily_volume_progress WHERE directiveId = :directiveId AND date = :date")
    suspend fun clear(directiveId: Long, date: String)
}

@Dao
interface StreakBreakDao {
    @Query("SELECT * FROM streak_breaks WHERE directiveId = :directiveId ORDER BY brokenAtEpochMs DESC")
    suspend fun forDirective(directiveId: Long): List<StreakBreak>

    @Query("SELECT * FROM streak_breaks")
    suspend fun getAll(): List<StreakBreak>

    @Insert
    suspend fun insert(entry: StreakBreak)
}

@Dao
interface SyncHistoryDao {
    @Query("SELECT * FROM sync_history ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 90): Flow<List<SyncHistoryEntry>>

    @Query("SELECT * FROM sync_history")
    suspend fun getAll(): List<SyncHistoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SyncHistoryEntry)
}

@Dao
interface TarotPullDao {
    @Query("SELECT * FROM tarot_pulls ORDER BY pulledAtEpochMs DESC")
    fun observeAll(): Flow<List<TarotPull>>

    @Insert
    suspend fun insert(pull: TarotPull): Long
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAtEpochMs DESC")
    fun observeAll(): Flow<List<BookItem>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): BookItem?

    @Insert
    suspend fun insert(book: BookItem): Long

    @Update
    suspend fun update(book: BookItem)

    @Delete
    suspend fun delete(book: BookItem)
}

@Dao
interface ConfessionEntryDao {
    @Query("SELECT * FROM confession_entries ORDER BY writtenAtEpochMs DESC")
    fun observeAll(): Flow<List<ConfessionEntry>>

    @Insert
    suspend fun insert(entry: ConfessionEntry): Long

    @Delete
    suspend fun delete(entry: ConfessionEntry)
}
