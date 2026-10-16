package com.evyr.rebuild.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

class AppRepository(private val db: RebuildDatabase) {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    fun todayKey(): String = dateFmt.format(Date())

    /**
     * Whether [directive] is due today, given its zone and recurrence settings.
     * The single source of truth for "is this due" -- previously this check was
     * inlined separately in computeSyncRatioBreakdown, openDailyDirectivesToday,
     * and the notification workers, which meant a recurrence change here would
     * need finding and updating all of them by hand. Now they all call this.
     *
     * recurrenceType == NONE falls back to the original zone-only behavior
     * (every day for DAILY, Directive.weekday for WEEKLY) so every directive
     * created before recurrence existed keeps behaving exactly as it did.
     */
    fun isDueToday(directive: Directive, now: Calendar = Calendar.getInstance()): Boolean {
        if (directive.zone != Zone.DAILY && directive.zone != Zone.WEEKLY) return false

        return when (directive.recurrenceType) {
            RecurrenceType.NONE -> {
                if (directive.zone == Zone.DAILY) true
                else directive.weekday == now.get(Calendar.DAY_OF_WEEK)
            }
            RecurrenceType.SPECIFIC_DAYS -> {
                val days = directive.recurrenceDays?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                now.get(Calendar.DAY_OF_WEEK) in days
            }
            RecurrenceType.INTERVAL -> {
                val interval = directive.recurrenceIntervalDays?.coerceAtLeast(1) ?: return true
                val last = directive.lastCompletedEpochMs
                    // Never completed yet -- due immediately rather than waiting a
                    // full interval from a start date the person never set.
                    ?: return true
                val daysSince = ((now.timeInMillis - last) / (1000L * 60 * 60 * 24)).toInt()
                daysSince >= interval
            }
            RecurrenceType.MONTHLY -> {
                val targetDay = directive.recurrenceDayOfMonth ?: return true
                val lastDayThisMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
                // Clamp rather than skip: a directive set for the 31st should
                // still fire on the 28th/29th/30th in a shorter month instead of
                // silently never triggering that month at all.
                val effectiveDay = targetDay.coerceAtMost(lastDayThisMonth)
                now.get(Calendar.DAY_OF_MONTH) == effectiveDay
            }
        }
    }

    // ---------- Seeding ----------

    suspend fun seedIfEmpty() {
        val existing = db.directiveDao().observeAll().first()
        if (existing.isEmpty()) {
            SeedData.buildInitialDirectives().forEach { db.directiveDao().upsert(it) }
        } else {
            // Data fixup for installs seeded before requiresNfcScan existed: this
            // directive should never have been checkbox-tappable to begin with.
            // Idempotent -- only touches the row if the flag isn't already set.
            existing.find { it.title == "Wallet + Keys In Place" && !it.requiresNfcScan }
                ?.let { db.directiveDao().upsert(it.copy(requiresNfcScan = true)) }
        }
        if (db.sessionStepDao().count() == 0) {
            SeedData.buildInitialSessionSteps().forEach { db.sessionStepDao().upsert(it) }
        }
        if (db.notificationPrefsDao().observe().first() == null) {
            db.notificationPrefsDao().upsert(NotificationPrefs())
        }
    }

    // ---------- Directives ----------

    fun observeDirectivesByZone(zone: Zone): Flow<List<Directive>> =
        db.directiveDao().observeByZone(zone)

    fun observeAllDirectives(): Flow<List<Directive>> = db.directiveDao().observeAll()

    suspend fun addOrUpdateDirective(directive: Directive) = db.directiveDao().upsert(directive)

    /**
     * Deletes a directive and its reminders together. There's no foreign key
     * on Reminder.directiveId, so without this the reminders survive and keep
     * firing forever with no directive left to name them -- which is how you
     * end up with notifications that just say "Reminder".
     *
     * Returns the reminders it removed so the caller can cancel their pending
     * alarms; the rows going away doesn't stop an alarm already sitting in
     * AlarmManager.
     */
    suspend fun deleteDirective(directive: Directive): List<Reminder> {
        val reminders = db.reminderDao().getForDirective(directive.id)
        reminders.forEach { db.reminderDao().delete(it) }
        db.directiveDao().delete(directive)
        return reminders
    }

    suspend fun unlockDirective(id: Long) = db.directiveDao().setLocked(id, locked = false)

    // ---------- Completions ----------

    fun observeCompletionsForToday(): Flow<List<Completion>> =
        db.completionDao().observeForDate(todayKey())

    suspend fun markComplete(directiveId: Long, source: String = "manual") {
        db.completionDao().upsert(
            Completion(
                directiveId = directiveId,
                date = todayKey(),
                completedAtEpochMs = System.currentTimeMillis(),
                source = source
            )
        )
        // Needed for RecurrenceType.INTERVAL, which counts "N days since last
        // completed" rather than from a fixed calendar anchor.
        db.directiveDao().observeAll().first().find { it.id == directiveId }?.let { directive ->
            db.directiveDao().upsert(directive.copy(lastCompletedEpochMs = System.currentTimeMillis()))
        }
    }

    suspend fun markIncomplete(directiveId: Long) {
        db.completionDao().clear(directiveId, todayKey())
    }

    // ---------- Streaks (Standing directives) ----------

    /** Call once per day per standing directive to confirm it held. Breaking requires an explicit call to breakStreak. */
    suspend fun confirmStreakHeldToday(directiveId: Long) {
        val existing = db.streakDao().get(directiveId) ?: Streak(directiveId = directiveId)
        val updated = existing.copy(
            currentStreakDays = existing.currentStreakDays + 1,
            bestStreakDays = maxOf(existing.bestStreakDays, existing.currentStreakDays + 1)
        )
        db.streakDao().upsert(updated)
    }

    suspend fun breakStreak(directiveId: Long, note: String?) {
        val existing = db.streakDao().get(directiveId) ?: Streak(directiveId = directiveId)
        // Also append to streak_breaks: Streak only keeps the most recent break
        // (it's overwritten each time), so without this there's no history to
        // find a pattern in.
        db.streakBreakDao().insert(
            StreakBreak(
                directiveId = directiveId,
                brokenAtEpochMs = System.currentTimeMillis(),
                note = note
            )
        )
        db.streakDao().upsert(
            existing.copy(
                currentStreakDays = 0,
                lastBrokenEpochMs = System.currentTimeMillis(),
                lastBrokenNote = note
            )
        )
        if (note != null) {
            db.notebookDao().insert(
                NotebookEntry(
                    text = "Streak broken: $note",
                    createdAtEpochMs = System.currentTimeMillis(),
                    linkedDirectiveId = directiveId
                )
            )
        }
    }

    fun observeStreaks(): Flow<List<Streak>> = db.streakDao().observeAll()

    // ---------- NFC ----------

    suspend fun registerTag(
        uid: String,
        label: String,
        linkedDirectiveId: Long?,
        opensChecklist: Boolean = false
    ) {
        db.nfcTagDao().upsert(
            NfcTag(
                tagUid = uid,
                label = label,
                // A checklist tag reports on the other tags; it doesn't tick anything itself.
                linkedDirectiveId = if (opensChecklist) null else linkedDirectiveId,
                opensChecklist = opensChecklist
            )
        )
    }

    suspend fun findTag(uid: String): NfcTag? = db.nfcTagDao().findByUid(uid)

    /** Which door checklists a tagged item appears on. */
    suspend fun setTagLists(uid: String, leaving: Boolean, arriving: Boolean) {
        val tag = db.nfcTagDao().findByUid(uid) ?: return
        db.nfcTagDao().upsert(tag.copy(onLeavingList = leaving, onArrivingList = arriving))
    }

    /**
     * Result of a scan, so callers can tell the difference between "done" and
     * "2 of 3" without re-querying.
     */
    data class ScanOutcome(
        val directiveTitle: String?,
        val count: Int,
        val required: Int,
        val completed: Boolean
    )

    suspend fun handleTagScan(uid: String, locationLabel: String?): ScanOutcome? {
        val tag = db.nfcTagDao().findByUid(uid) ?: return null
        db.nfcTagDao().upsert(
            tag.copy(
                lastScanEpochMs = System.currentTimeMillis(),
                lastScanLocationLabel = locationLabel
            )
        )

        val directiveId = tag.linkedDirectiveId
            ?: return ScanOutcome(tag.label, 0, 0, false)

        return incrementScanProgress(directiveId, source = "nfc") ?: ScanOutcome(tag.label, 0, 0, false)
    }

    /**
     * One tap toward a directive's daily target, regardless of where the tap came
     * from -- an NFC scan or a direct UI tap (e.g. the water tracker bar, for tags
     * that don't read reliably on metal bottles). Shared so both paths obey
     * identical rules: ignores taps once the target's already met for the day,
     * marks the directive complete the moment the target is reached.
     */
    suspend fun incrementScanProgress(directiveId: Long, source: String): ScanOutcome? {
        val directive = db.directiveDao().observeAll().first().find { it.id == directiveId } ?: return null
        val required = directive.requiredScans.coerceAtLeast(1)

        if (required <= 1) {
            markComplete(directiveId, source = source)
            return ScanOutcome(directive.title, 1, 1, true)
        }

        val today = todayKey()
        val already = db.dailyScanProgressDao().get(directiveId, today)?.count ?: 0

        if (already >= required) {
            return ScanOutcome(directive.title, already, required, true)
        }

        val next = already + 1
        db.dailyScanProgressDao().upsert(DailyScanProgress(directiveId, today, next))

        val done = next >= required
        if (done) markComplete(directiveId, source = source)

        return ScanOutcome(directive.title, next, required, done)
    }

    /**
     * Logs a typed amount (oz) toward a volume-tracker directive's daily target,
     * additively -- each entry adds to the day's running total rather than
     * replacing it. Marks the directive complete once the total meets or exceeds
     * targetOz, same completion semantics as incrementScanProgress.
     */
    suspend fun logVolumeProgress(directiveId: Long, amountOz: Int): ScanOutcome? {
        if (amountOz <= 0) return null
        val directive = db.directiveDao().observeAll().first().find { it.id == directiveId } ?: return null
        val target = directive.targetOz?.coerceAtLeast(1) ?: return null

        val today = todayKey()
        val already = db.dailyVolumeProgressDao().get(directiveId, today)?.loggedOz ?: 0
        val next = already + amountOz
        db.dailyVolumeProgressDao().upsert(DailyVolumeProgress(directiveId, today, next))

        val done = next >= target
        if (done) markComplete(directiveId, source = "volume")

        return ScanOutcome(directive.title, next, target, done)
    }

    fun observeVolumeProgressToday(): Flow<Map<Long, Int>> =
        db.dailyVolumeProgressDao().observeForDate(todayKey())
            .map { list -> list.associate { it.directiveId to it.loggedOz } }

    // ---------- Hidden section media ----------

    fun observeHiddenMedia(): Flow<List<HiddenMediaItem>> = db.hiddenMediaDao().observeAll()

    suspend fun addHiddenMedia(label: String, filePath: String, kind: AttachmentKind) {
        db.hiddenMediaDao().insert(
            HiddenMediaItem(
                label = label,
                filePath = filePath,
                kind = kind,
                addedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    /** Deletes the row and the underlying file, so removing an item really removes it. */
    suspend fun deleteHiddenMedia(item: HiddenMediaItem) {
        runCatching { java.io.File(item.filePath).delete() }
        db.hiddenMediaDao().delete(item)
    }

    fun observeScanProgressToday(): Flow<Map<Long, Int>> =
        db.dailyScanProgressDao().observeForDate(todayKey())
            .map { list -> list.associate { it.directiveId to it.count } }

    /** One-shot snapshot for callers outside a composition (the widget), same
     *  reasoning as openDailyDirectivesToday/observeTagsOnce. */
    suspend fun scanProgressTodayOnce(): Map<Long, Int> =
        db.dailyScanProgressDao().observeForDate(todayKey()).first()
            .associate { it.directiveId to it.count }

    fun observeTags(): Flow<List<NfcTag>> = db.nfcTagDao().observeAll()

    suspend fun deleteTag(tag: NfcTag) = db.nfcTagDao().delete(tag)

    /**
     * Labels of directives that have NOT been completed since [sinceEpochMs] --
     * the NFC-linked set (unchanged behaviour, checked everywhere) plus, when
     * [placeId] is given, that place's own extra checklist layered on top.
     * Directives appearing in both are only listed once.
     */
    suspend fun nfcLinkedDirectivesMissingSince(sinceEpochMs: Long, placeId: Long? = null): List<String> {
        val tags = db.nfcTagDao().observeAll().first()
        val nfcLinkedIds = tags.mapNotNull { it.linkedDirectiveId }.toSet()
        val placeIds = placeId?.let { db.geofencePlaceDirectiveDao().forPlace(it).toSet() } ?: emptySet()
        val directiveIds = nfcLinkedIds + placeIds
        if (directiveIds.isEmpty()) return emptyList()

        val directives = db.directiveDao().observeAll().first()
            .filter { it.id in directiveIds }
        val todaysCompletions = db.completionDao().observeForDate(todayKey()).first()
        val completedAfter = todaysCompletions
            .filter { it.completedAtEpochMs >= sinceEpochMs }
            .map { it.directiveId }
            .toSet()

        return directives.filter { it.id !in completedAfter }.map { it.title }
    }

    // ---------- Geofence per-place checklist ----------

    fun observePlaceDirectiveIds(placeId: Long): Flow<List<Long>> =
        db.geofencePlaceDirectiveDao().observeForPlace(placeId)

    fun observePlaceLinks(placeId: Long): Flow<List<GeofencePlaceDirective>> =
        db.geofencePlaceDirectiveDao().observeLinksForPlace(placeId)

    suspend fun placeLinks(placeId: Long): List<GeofencePlaceDirective> =
        db.geofencePlaceDirectiveDao().linksForPlace(placeId)

    /** Put a directive on a place's leaving and/or arriving checklist; neither removes it. */
    suspend fun setPlaceLink(placeId: Long, directiveId: Long, onExit: Boolean, onEnter: Boolean) {
        val dao = db.geofencePlaceDirectiveDao()
        if (!onExit && !onEnter) dao.unlink(placeId, directiveId)
        else dao.upsertLink(GeofencePlaceDirective(placeId, directiveId, onExit, onEnter))
    }

    suspend fun allTags(): List<NfcTag> = db.nfcTagDao().observeAll().first()

    suspend fun setPlaceDirective(placeId: Long, directiveId: Long, enabled: Boolean) {
        if (enabled) db.geofencePlaceDirectiveDao().link(GeofencePlaceDirective(placeId, directiveId))
        else db.geofencePlaceDirectiveDao().unlink(placeId, directiveId)
    }

    // ---------- Notebook ----------

    fun observeNotebook(): Flow<List<NotebookEntry>> = db.notebookDao().observeAll()

    fun searchNotebook(query: String): Flow<List<NotebookEntry>> = db.notebookDao().search(query)

    suspend fun addNotebookEntry(text: String, tags: List<String> = emptyList(), linkedDirectiveId: Long? = null) {
        db.notebookDao().insert(
            NotebookEntry(
                text = text,
                createdAtEpochMs = System.currentTimeMillis(),
                tags = tags,
                linkedDirectiveId = linkedDirectiveId
            )
        )
    }

    suspend fun deleteNotebookEntry(entry: NotebookEntry) = db.notebookDao().delete(entry)

    // ---------- Guided sessions ----------

    fun observeSessionSteps(type: SessionType): Flow<List<SessionStep>> = db.sessionStepDao().observeForType(type)

    suspend fun setSessionStepReferenceUrl(step: SessionStep, url: String?) {
        db.sessionStepDao().upsert(step.copy(referenceUrl = url?.takeIf { it.isNotBlank() }))
    }

    suspend fun setSessionStepReferenceVideo(step: SessionStep, path: String?) {
        db.sessionStepDao().upsert(step.copy(referenceVideoPath = path))
    }

    fun observeRecentSessionLogs(limit: Int = 50): Flow<List<SessionLog>> = db.sessionLogDao().observeRecent(limit)

    fun observeRecentSessionLogsForType(type: SessionType, limit: Int = 20): Flow<List<SessionLog>> =
        db.sessionLogDao().observeRecentForType(type, limit)

    /** True if the Fry Scream directive is still locked (gated behind daily consistency). */
    suspend fun isFryScreamLocked(): Boolean =
        db.directiveDao().observeAll().first().find { it.title == "Fry Scream Basics" }?.locked ?: true

    suspend fun logSession(type: SessionType, startedAtEpochMs: Long, stepsCompleted: Int, stepsTotal: Int, notes: String? = null) {
        db.sessionLogDao().upsert(
            SessionLog(
                sessionType = type,
                startedAtEpochMs = startedAtEpochMs,
                completedAtEpochMs = if (stepsCompleted >= stepsTotal) System.currentTimeMillis() else null,
                stepsCompleted = stepsCompleted,
                stepsTotal = stepsTotal,
                notes = notes
            )
        )
    }

    // ---------- Reminders ----------

    fun observeRemindersForDirective(directiveId: Long): Flow<List<Reminder>> =
        db.reminderDao().observeForDirective(directiveId)

    suspend fun getAllEnabledReminders(): List<Reminder> = db.reminderDao().getAllEnabled()

    suspend fun getReminderById(id: Long): Reminder? = db.reminderDao().getById(id)

    suspend fun upsertReminder(reminder: Reminder): Long = db.reminderDao().upsert(reminder)

    suspend fun deleteReminder(reminder: Reminder) = db.reminderDao().delete(reminder)

    /** When this directive was last completed today, or null if it hasn't been. */
    suspend fun completedAtToday(directiveId: Long): Long? =
        db.completionDao().observeForDate(todayKey()).first()
            .find { it.directiveId == directiveId }?.completedAtEpochMs

    /** Reminders that fire on entering or leaving a given place. */
    suspend fun remindersForPlace(placeId: Long, trigger: ReminderTrigger): List<Reminder> =
        db.reminderDao().getForPlace(placeId, trigger)

    /** Reminders left pointing at a directive that no longer exists. */
    suspend fun orphanedReminders(): List<Reminder> = db.reminderDao().getOrphaned()

    // ---------- Tarot ----------

    fun observeTarotPulls(): Flow<List<TarotPull>> = db.tarotPullDao().observeAll()

    suspend fun logTarotPull(pull: TarotPull): Long = db.tarotPullDao().insert(pull)

    suspend fun logTarotReading(pulls: List<TarotPull>) = db.tarotPullDao().insertAll(pulls)

    // ---------- Books (ALEXANDRIA) ----------

    fun observeBooks(): Flow<List<BookItem>> = db.bookDao().observeAll()

    suspend fun getBookById(id: Long): BookItem? = db.bookDao().getById(id)

    suspend fun addBook(book: BookItem): Long = db.bookDao().insert(book)

    suspend fun updateBookPosition(book: BookItem, position: Int) =
        db.bookDao().update(book.copy(lastPosition = position))

    suspend fun deleteBook(book: BookItem) = db.bookDao().delete(book)

    // ---------- Confession Booth ----------

    fun observeConfessionEntries(): Flow<List<ConfessionEntry>> = db.confessionEntryDao().observeAll()

    suspend fun addConfessionEntry(entry: ConfessionEntry): Long = db.confessionEntryDao().insert(entry)

    suspend fun deleteConfessionEntry(entry: ConfessionEntry) = db.confessionEntryDao().delete(entry)

    // ---------- Media attachments ----------

    fun observeMediaForNotebookEntry(entryId: Long): Flow<List<MediaAttachment>> =
        db.mediaAttachmentDao().observeForNotebookEntry(entryId)

    fun observeMediaForSessionLog(logId: Long): Flow<List<MediaAttachment>> =
        db.mediaAttachmentDao().observeForSessionLog(logId)

    suspend fun addMediaAttachment(kind: AttachmentKind, filePath: String, notebookEntryId: Long? = null, sessionLogId: Long? = null) {
        db.mediaAttachmentDao().insert(
            MediaAttachment(
                kind = kind,
                filePath = filePath,
                createdAtEpochMs = System.currentTimeMillis(),
                linkedNotebookEntryId = notebookEntryId,
                linkedSessionLogId = sessionLogId
            )
        )
    }

    suspend fun deleteMediaAttachment(attachment: MediaAttachment) = db.mediaAttachmentDao().delete(attachment)

    // ---------- Notification prefs ----------

    fun observeNotificationPrefs(): Flow<NotificationPrefs?> = db.notificationPrefsDao().observe()

    suspend fun getNotificationPrefs(): NotificationPrefs = db.notificationPrefsDao().observe().first() ?: NotificationPrefs()

    suspend fun updateNotificationPrefs(prefs: NotificationPrefs) = db.notificationPrefsDao().upsert(prefs)

    // ---------- Item-miss pattern (day-of-week) ----------

    /**
     * For a given NFC-linked directive, which weekday it's most often NOT completed on,
     * over the last [lookbackDays]. Returns null if there's not enough history yet (<2
     * misses) to say anything meaningful. Weekday values match Calendar.DAY_OF_WEEK (1=Sun).
     */
    /**
     * For a given NFC-linked directive, which weekday it's most often NOT completed on,
     * over the last [lookbackDays]. Returns null if there's not enough history yet (<2
     * misses) to say anything meaningful. Weekday values match Calendar.DAY_OF_WEEK (1=Sun).
     *
     * Bounded at the directive's earliest real completion: without this, every day
     * before the directive (or the app) existed had no completion row and counted
     * as a miss, which meant a brand-new directive looked like it had been failed
     * every day since before install. If there's no completion at all yet, there's
     * no real history to report a pattern from, so this returns null rather than
     * treating the whole lookback window as failures.
     */
    suspend fun mostMissedWeekdayFor(directiveId: Long, lookbackDays: Int = 60): Pair<Int, Int>? {
        val completions = db.completionDao().recentForDirective(directiveId, limit = lookbackDays)
        if (completions.isEmpty()) return null

        val completionDates = completions.map { it.date }.toSet()
        val earliestDate = completions.minOf { it.date } // ISO yyyy-MM-dd sorts lexicographically

        val cal = Calendar.getInstance()
        val missesByWeekday = IntArray(8) // index 1-7 used

        for (i in 0 until lookbackDays) {
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -i)
            val dateKey = dateFmt.format(cal.time)
            if (dateKey < earliestDate) break // walking backward past the directive's start
            val weekday = cal.get(Calendar.DAY_OF_WEEK)
            if (dateKey !in completionDates) {
                missesByWeekday[weekday]++
            }
        }

        val maxMisses = missesByWeekday.maxOrNull() ?: 0
        if (maxMisses < 2) return null
        val worstWeekday = missesByWeekday.indexOf(maxMisses)
        return worstWeekday to maxMisses
    }

    // ---------- Geofence places ----------

    fun observeGeofencePlaces(): Flow<List<GeofencePlace>> = db.geofencePlaceDao().observeAll()

    suspend fun getGeofencePlace(id: Long): GeofencePlace? = db.geofencePlaceDao().getById(id)

    suspend fun addGeofencePlace(place: GeofencePlace) = db.geofencePlaceDao().upsert(place)

    suspend fun deleteGeofencePlace(place: GeofencePlace) = db.geofencePlaceDao().delete(place)

    // ---------- Sync Ratio ----------

    /**
     * Combo score: weighted % of today's Daily+Weekly-due items completed,
     * plus a small bonus per Standing directive currently on a streak >0.
     * Weighted heavier toward completion (70%) than streak bonus (30%),
     * so one bad day doesn't tank the number entirely if standing rules are intact.
     */
    /** Same inputs as computeSyncRatio, returned for display rather than
     *  collapsed into one number -- added specifically to debug a report of
     *  a nonzero score with nothing completed anywhere, which shouldn't be
     *  reachable given the formula as written. */
    data class SyncRatioBreakdown(
        val dueCount: Int,
        val totalWeight: Float,
        val doneWeight: Float,
        val completionScore: Float,
        val standingCount: Int,
        val heldCount: Int,
        val streakScore: Float,
        val finalPercent: Int
    )

    suspend fun computeSyncRatioBreakdown(): SyncRatioBreakdown {
        val directives = db.directiveDao().observeAll().first()
        val completions = db.completionDao().observeForDate(todayKey()).first()
        val streaks = db.streakDao().observeAll().first()
        val completedIds = completions.map { it.directiveId }.toSet()

        val now = Calendar.getInstance()
        val dueToday = directives.filter { isDueToday(it, now) && !it.locked }

        val totalWeight = dueToday.sumOf { it.weight.toDouble() }.toFloat()
        val doneWeight = dueToday.filter { completedIds.contains(it.id) }
            .sumOf { it.weight.toDouble() }.toFloat()
        val completionScore = if (dueToday.isEmpty() || totalWeight == 0f) 1f else doneWeight / totalWeight

        val standingDirectives = directives.filter { it.zone == Zone.STANDING }
        val heldCount = standingDirectives.count { d ->
            streaks.find { it.directiveId == d.id }?.currentStreakDays?.let { it > 0 } ?: false
        }
        val streakScore = if (standingDirectives.isEmpty()) 1f else heldCount.toFloat() / standingDirectives.size

        val combo = (completionScore * 0.7f) + (streakScore * 0.3f)
        return SyncRatioBreakdown(
            dueCount = dueToday.size,
            totalWeight = totalWeight,
            doneWeight = doneWeight,
            completionScore = completionScore,
            standingCount = standingDirectives.size,
            heldCount = heldCount,
            streakScore = streakScore,
            finalPercent = (combo * 100).toInt().coerceIn(0, 100)
        )
    }

    suspend fun computeSyncRatio(): Int = computeSyncRatioBreakdown().finalPercent

    suspend fun snapshotSyncRatioForHistory() {
        db.syncHistoryDao().upsert(SyncHistoryEntry(date = todayKey(), syncRatioPercent = computeSyncRatio()))
    }

    /**
     * Which weekday a standing directive's streak breaks on most often, as
     * (Calendar.DAY_OF_WEEK, count). Null until there are at least 3 breaks --
     * below that it's noise, and presenting "you always break this on Tuesdays"
     * off a single data point would be actively misleading.
     */
    suspend fun mostBrokenWeekdayFor(directiveId: Long): Pair<Int, Int>? {
        val breaks = db.streakBreakDao().forDirective(directiveId)
        if (breaks.size < 3) return null

        val counts = IntArray(8)
        val cal = Calendar.getInstance()
        breaks.forEach {
            cal.timeInMillis = it.brokenAtEpochMs
            counts[cal.get(Calendar.DAY_OF_WEEK)]++
        }
        val max = counts.maxOrNull() ?: 0
        if (max < 2) return null
        return counts.indexOf(max) to max
    }

    fun observeSyncHistory(): Flow<List<SyncHistoryEntry>> = db.syncHistoryDao().observeRecent()

    // ---------- Backup / restore ----------

    /** One-shot snapshots for use outside a composition (widget, NFC quick-scan). */
    suspend fun observeTagsOnce(): List<NfcTag> = db.nfcTagDao().observeAll().first()

    suspend fun directiveById(id: Long): Directive? =
        db.directiveDao().observeAll().first().find { it.id == id }

    /**
     * Daily directives not yet completed today, in sort order. One-shot rather than
     * a Flow because the widget and the NFC quick-scan activity both need a single
     * snapshot outside any composition.
     */
    /**
     * Directives due today and not yet completed, in sort order. Was DAILY-zone
     * only; widened to isDueToday since a WEEKLY-zone item due today (whether via
     * the original single Directive.weekday or the newer recurrence fields) is
     * exactly the same kind of "still needs doing today" as a Daily one, and the
     * widget/quick-scan callers want everything actually due, not one zone.
     */
    suspend fun openDailyDirectivesToday(): List<Directive> {
        val doneIds = db.completionDao().observeForDate(todayKey()).first().map { it.directiveId }.toSet()
        val now = Calendar.getInstance()
        return db.directiveDao().observeAll().first()
            .filter { isDueToday(it, now) && !it.locked && it.id !in doneIds }
            .sortedBy { it.sortOrder }
    }

    suspend fun exportBackup(): BackupManager.BackupResult = BackupManager.exportToJson(db)

    suspend fun importBackup(jsonText: String): BackupManager.BackupResult = BackupManager.importFromJson(db, jsonText)

    /** Full zip backup including media files. */
    suspend fun writeArchive(context: android.content.Context, target: android.net.Uri) =
        BackupArchive.writeTo(context, db, target)

    /** Restores a full zip backup, unpacking media and rewriting stored paths. */
    suspend fun restoreArchive(context: android.content.Context, source: android.net.Uri) =
        BackupArchive.restoreFrom(context, db, source)
}
