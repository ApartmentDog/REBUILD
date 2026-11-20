package com.evyr.rebuild.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class AppRepository(private val db: RebuildDatabase) {

    // SimpleDateFormat isn't thread-safe, and this repository is hit from the
    // UI, workers, the widget and receivers concurrently. A shared instance can
    // hand back a corrupted date string under contention, which would file a
    // completion under the wrong day. One formatter per thread instead.
    private val dateFmt = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
    private fun dateKey(date: Date): String = dateFmt.get()!!.format(date)
    fun todayKey(): String = dateKey(Date())

    /**
     * Today's date key, re-emitted when the date changes. The "today" flows
     * used to bake in the date once, when first collected -- so with the app
     * left open past midnight the dashboard kept showing yesterday's ticks
     * and tap counts until it was restarted.
     */
    fun observeTodayKey(): Flow<String> = flow {
        while (true) {
            emit(todayKey())
            val next = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 1); set(Calendar.MILLISECOND, 0)
            }
            // Capped so a clock or timezone change is picked up within a minute.
            delay((next.timeInMillis - System.currentTimeMillis()).coerceIn(1_000L, 60_000L))
        }
    }.distinctUntilChanged()

    /** Whole calendar days between two instants (local time), ignoring the
     *  time of day -- 11pm yesterday to 8am today is 1 day, not 0. */
    private fun calendarDaysBetween(fromMs: Long, to: Calendar): Int {
        val a = Calendar.getInstance().apply {
            timeInMillis = fromMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val b = (to.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // Round rather than truncate: DST days are 23h or 25h long.
        return Math.round((b.timeInMillis - a.timeInMillis) / 86_400_000.0).toInt()
    }

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
                else weeklyOverdueDays(directive, now) != null
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
                // Calendar days, not 24h blocks: the old millisecond division
                // meant something done at 11pm wasn't due again on an every-1-day
                // cycle until 11pm the next day. And a completion today keeps it
                // listed (as done) instead of vanishing the moment it's ticked.
                val daysSince = calendarDaysBetween(last, now)
                daysSince == 0 || daysSince >= interval
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

    /**
     * For a single-day Weekly directive: days since this week's occurrence
     * came due (0 = due today), or null when it isn't open. A missed Weekly
     * item used to vanish until the same weekday came round again; now it
     * stays listed, overdue, until it's done. Done today keeps it listed (as
     * done) so it doesn't disappear the moment it's ticked.
     */
    fun weeklyOverdueDays(d: Directive, now: Calendar = Calendar.getInstance()): Int? {
        val wd = d.weekday ?: return null
        val back = (now.get(Calendar.DAY_OF_WEEK) - wd + 7) % 7
        if (back == 0) return 0
        val last = d.lastCompletedEpochMs ?: return back
        val sinceDone = calendarDaysBetween(last, now)
        if (sinceDone == 0) return back
        return if (sinceDone <= back) null else back
    }

    private fun isSingleDayWeekly(d: Directive) =
        d.zone == Zone.WEEKLY && d.recurrenceType == RecurrenceType.NONE && d.weekday != null

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
        // Nothing has a foreign key, so everything keyed on this id is cleaned
        // up by hand. Left behind, a reused autoincrement id would never be hit,
        // but the place checklist and streak rows would sit there forever.
        db.geofencePlaceDirectiveDao().clearForDirective(directive.id)
        db.streakDao().deleteFor(directive.id)
        db.missDao().clearAllFor(directive.id)
        db.urgeDao().clearAllFor(directive.id)
        db.directiveDao().delete(directive)
        return reminders
    }

    suspend fun unlockDirective(id: Long) = db.directiveDao().setLocked(id, locked = false)

    // ---------- Completions ----------

    fun observeCompletionsForToday(): Flow<List<Completion>> =
        observeTodayKey().flatMapLatest { db.completionDao().observeForDate(it) }

    suspend fun markComplete(directiveId: Long, source: String = "manual") {
        // Done after all: a reason logged earlier today no longer applies.
        db.missDao().clear(directiveId, todayKey())
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
        db.directiveDao().getById(directiveId)?.let { directive ->
            db.directiveDao().upsert(directive.copy(lastCompletedEpochMs = System.currentTimeMillis()))
        }
    }

    /**
     * Unticks today. Also resets today's tap/volume progress -- previously the
     * counter stayed at its target, so the next tap was ignored as "already
     * done" and a multi-tap directive could never be completed again that day.
     * lastCompletedEpochMs is rolled back to the previous real completion, so
     * an accidental tick doesn't push an every-N-days directive out N days.
     */
    suspend fun markIncomplete(directiveId: Long) {
        val today = todayKey()
        db.completionDao().clear(directiveId, today)
        db.dailyScanProgressDao().clear(directiveId, today)
        db.dailyVolumeProgressDao().clear(directiveId, today)
        db.directiveDao().getById(directiveId)?.let { directive ->
            val previous = db.completionDao().latestCompletedAt(directiveId)
            db.directiveDao().upsert(directive.copy(lastCompletedEpochMs = previous))
        }
    }

    // ---------- 🔥 Hard objectives ----------

    /** Clears a one-time hard objective for good. Logged as a completion too,
     *  so it counts toward the all-time tally like anything else. */
    suspend fun clearHardObjective(directiveId: Long) {
        val d = db.directiveDao().getById(directiveId) ?: return
        if (d.zone != Zone.HARD || d.hardClearedEpochMs != null) return
        val now = System.currentTimeMillis()
        db.directiveDao().upsert(d.copy(hardClearedEpochMs = now, lastCompletedEpochMs = now))
        db.completionDao().upsert(Completion(directiveId, todayKey(), now, source = "hard"))
    }

    /** Undo, only offered the same day it was cleared (a misfire, not a do-over). */
    suspend fun unclearHardObjective(directiveId: Long) {
        val d = db.directiveDao().getById(directiveId) ?: return
        if (d.zone != Zone.HARD) return
        db.directiveDao().upsert(d.copy(hardClearedEpochMs = null, lastCompletedEpochMs = null))
        db.completionDao().clearAllFor(directiveId)
    }

    fun isClearedToday(d: Directive): Boolean =
        d.hardClearedEpochMs?.let { dateKey(Date(it)) == todayKey() } ?: false

    /** Current boost from one cleared hard objective: full on the day it's
     *  cleared, fading linearly to nothing after 7 days. */
    fun hardBoostNow(d: Directive, now: Calendar = Calendar.getInstance()): Float {
        val cleared = d.hardClearedEpochMs ?: return 0f
        val days = calendarDaysBetween(cleared, now)
        if (days < 0 || days >= HARD_BOOST_DAYS) return 0f
        return d.hardBoost * (1f - days.toFloat() / HARD_BOOST_DAYS)
    }

    fun observeCompletionCount(): Flow<Int> = db.completionDao().observeCount()

    // ---------- Streaks (Standing directives) ----------

    /** Call once per day per standing directive to confirm it held. Breaking requires an explicit call to breakStreak. */
    suspend fun confirmStreakHeldToday(directiveId: Long) {
        val existing = db.streakDao().get(directiveId) ?: Streak(directiveId = directiveId)
        val today = todayKey()
        // Once a day. HOLD could be tapped repeatedly before, adding a day each time.
        if (existing.lastHeldDate == today) return
        // A day you've logged a slip on can't also be held.
        if (existing.lastSlipDate == today) return
        val updated = existing.copy(
            currentStreakDays = existing.currentStreakDays + 1,
            bestStreakDays = maxOf(existing.bestStreakDays, existing.currentStreakDays + 1),
            lastHeldDate = today
        )
        db.streakDao().upsert(updated)
        // Held days are also logged as completions (source "hold"), so clean
        // days over a window can be counted -- the streak row alone only
        // knows the current run.
        db.completionDao().upsert(Completion(directiveId, today, System.currentTimeMillis(), source = "hold"))
    }

    enum class SlipOutcome { KEPT, RESET, ALREADY }

    /**
     * Logs a slip on a standing goal. Never miss twice: a single slip is
     * recorded (and shows on the row) but the count stands; a slip on the day
     * right after another slip resets it. Resetting to zero after one lapse is
     * what feeds "already blew it, might as well" -- the abstinence violation
     * effect -- so one bad day no longer erases a long run.
     *
     * Slipping after holding the same day takes that day's hold back.
     */
    suspend fun breakStreak(directiveId: Long, note: String?): SlipOutcome {
        val existing = db.streakDao().get(directiveId) ?: Streak(directiveId = directiveId)
        val now = System.currentTimeMillis()
        val today = todayKey()
        // Every slip goes in the history, which the weekday-pattern line reads.
        db.streakBreakDao().insert(StreakBreak(directiveId = directiveId, brokenAtEpochMs = now, note = note))
        if (note != null) {
            db.notebookDao().insert(
                NotebookEntry(text = "Slip: $note", createdAtEpochMs = now, linkedDirectiveId = directiveId)
            )
        }
        if (existing.lastSlipDate == today) return SlipOutcome.ALREADY

        var days = existing.currentStreakDays
        if (existing.lastHeldDate == today) {
            db.completionDao().clear(directiveId, today)
            days = (days - 1).coerceAtLeast(0)
        }
        val reset = existing.lastSlipDate == dayKeyOffset(-1) || days == 0
        db.streakDao().upsert(
            existing.copy(
                currentStreakDays = if (reset) 0 else days,
                lastHeldDate = null,
                lastSlipDate = today,
                lastBrokenEpochMs = if (reset) now else existing.lastBrokenEpochMs,
                lastBrokenNote = if (reset) note else existing.lastBrokenNote
            )
        )
        return if (reset) SlipOutcome.RESET else SlipOutcome.KEPT
    }

    /** yyyy-MM-dd for today plus [offset] days. */
    fun dayKeyOffset(offset: Int): String =
        dateKey(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }.time)

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
        val directive = db.directiveDao().getById(directiveId) ?: return null
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
        val directive = db.directiveDao().getById(directiveId) ?: return null
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
        observeTodayKey().flatMapLatest { db.dailyVolumeProgressDao().observeForDate(it) }
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
        observeTodayKey().flatMapLatest { db.dailyScanProgressDao().observeForDate(it) }
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

        val directives = db.directiveDao().getAll()
            .filter { it.id in directiveIds }
        val todaysCompletions = db.completionDao().getForDate(todayKey())
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
        db.directiveDao().getAll().find { it.title == "Fry Scream Basics" }?.locked ?: true

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
        db.completionDao().getForDate(todayKey())
            .find { it.directiveId == directiveId }?.completedAtEpochMs

    /** Whether there's nothing left to remind about today: ticked, held, or
     *  (for a 🔥 objective) cleared. A reminder for it would just be noise. */
    suspend fun isSatisfiedToday(d: Directive): Boolean = when (d.zone) {
        Zone.HARD -> d.hardClearedEpochMs != null
        Zone.STANDING -> db.streakDao().get(d.id)?.lastHeldDate == todayKey()
        Zone.MINDSET -> false
        else -> completedAtToday(d.id) != null
    }

    /** What the DONE button on a reminder does, per kind of directive. Multi-tap
     *  ones advance by one, the same as a tap in the app or on the widget. */
    suspend fun completeFromNotification(directiveId: Long) {
        val d = db.directiveDao().getById(directiveId) ?: return
        when (d.zone) {
            Zone.HARD -> clearHardObjective(d.id)
            Zone.STANDING -> confirmStreakHeldToday(d.id)
            Zone.MINDSET -> {}
            else -> incrementScanProgress(d.id, source = "reminder")
        }
    }

    // ---------- Habit stats ----------

    /** One day's state for a directive, oldest to newest on the row's bar. */
    enum class DayMark { DONE, MIN, MISSED, OFF, OPEN }

    data class DirectiveStats(
        val cells: List<DayMark>,      // last STAT_DAYS days, oldest first, today last
        val done30: Int,               // past 30 days, today excluded (it isn't over)
        val due30: Int,
        val lifetime: Int,             // every completion ever, never goes down
        val daysTracked: Int,          // since the first completion; 0 if never done
        val missedYesterday: Boolean,  // daily/weekly missed, or standing slipped
        val topMissReason: String?,    // most common logged reason, last 30 days
        val topMissReasonCount: Int,
        val overdueDays: Int?          // single-day weekly: days past its due day
    ) {
        val consistency30: Float? get() = if (due30 == 0) null else done30.toFloat() / due30
    }

    /**
     * Per-directive history for the rows, Most Missed, graduation and the
     * earned unlock -- one pass, one set of rules. Days before a directive's
     * first completion aren't counted against it (it wasn't being tracked).
     * Single-day weekly items are judged per occurrence: done any time in the
     * 7 days from their due day counts, and the current week's occurrence is
     * OPEN, not MISSED, until the week is out.
     */
    suspend fun computeStats(): Map<Long, DirectiveStats> {
        val now = Calendar.getInstance()
        val window = 30
        val completions = db.completionDao().getSince(dayKeyOffset(-(window + 7)))
        val byDir = completions.groupBy { it.directiveId }.mapValues { e -> e.value.associateBy { it.date } }
        val lifetime = db.completionDao().countsByDirective().associate { it.directiveId to it.n }
        val firsts = db.completionDao().firstDates().associate { it.directiveId to it.first }
        val slips = db.streakBreakDao().getAll().groupBy({ it.directiveId }, { dateKey(Date(it.brokenAtEpochMs)) })
            .mapValues { it.value.toSet() }
        val misses = db.missDao().getSince(dayKeyOffset(-window)).groupBy { it.directiveId }

        val days = (0..window).map { i ->
            val c = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }
            c to dateKey(c.time)
        }
        val today = days[0].second

        return db.directiveDao().getAll().associate { d ->
            val done = byDir[d.id] ?: emptyMap()
            val first = firsts[d.id]
            val dates = done.keys.toSortedSet()
            val marks = days.mapIndexed { i, (cal, key) ->
                markFor(d, i, cal, key, today, done, first, dates, slips[d.id] ?: emptySet())
            }
            val past = marks.drop(1)
            val reasons = misses[d.id].orEmpty().groupingBy { it.reason }.eachCount()
            val top = reasons.maxByOrNull { it.value }
            d.id to DirectiveStats(
                cells = marks.take(STAT_DAYS).reversed(),
                done30 = past.count { it == DayMark.DONE || it == DayMark.MIN },
                due30 = past.count { it == DayMark.DONE || it == DayMark.MIN || it == DayMark.MISSED },
                lifetime = lifetime[d.id] ?: 0,
                daysTracked = first?.let { f ->
                    dateFmt.get()!!.parse(f)?.time?.let { calendarDaysBetween(it, now) + 1 }
                } ?: 0,
                missedYesterday = marks[1] == DayMark.MISSED,
                topMissReason = top?.key,
                topMissReasonCount = top?.value ?: 0,
                overdueDays = if (isSingleDayWeekly(d) && done[today] == null)
                    weeklyOverdueDays(d, now)?.takeIf { it > 0 } else null
            )
        }
    }

    private fun markFor(
        d: Directive, i: Int, cal: Calendar, key: String, today: String,
        done: Map<String, Completion>, first: String?, dates: java.util.SortedSet<String>,
        slipDays: Set<String>
    ): DayMark = when (d.zone) {
        Zone.STANDING -> when {
            done[key]?.source == "hold" -> DayMark.DONE
            key in slipDays -> DayMark.MISSED
            i == 0 -> DayMark.OPEN
            else -> DayMark.OFF
        }
        Zone.DAILY, Zone.WEEKLY -> when {
            d.locked -> DayMark.OFF
            isSingleDayWeekly(d) -> {
                if (cal.get(Calendar.DAY_OF_WEEK) != d.weekday) DayMark.OFF
                else {
                    // Done on the day, or late within its week.
                    val hit = (0..6).asSequence()
                        .map { k -> dateKey((cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, k) }.time) }
                        .takeWhile { it <= today }
                        .mapNotNull { done[it] }
                        .firstOrNull()
                    when {
                        hit != null -> if (hit.source == "min") DayMark.MIN else DayMark.DONE
                        first == null || key < first -> DayMark.OFF
                        i <= 6 -> DayMark.OPEN
                        else -> DayMark.MISSED
                    }
                }
            }
            else -> {
                val c = done[key]
                when {
                    c != null -> if (c.source == "min") DayMark.MIN else DayMark.DONE
                    first == null || key < first -> DayMark.OFF
                    !wasDueOn(d, cal, key, dates) -> DayMark.OFF
                    i == 0 -> DayMark.OPEN
                    else -> DayMark.MISSED
                }
            }
        }
        else -> DayMark.OFF
    }

    /** isDueToday for a past day. Every-N-days can't use lastCompletedEpochMs
     *  (that's "now"), so it's worked out from the completion before that day,
     *  and counts once per cycle -- the day it came due -- not every overdue day. */
    private fun wasDueOn(d: Directive, day: Calendar, key: String, dates: java.util.SortedSet<String>): Boolean {
        if (d.recurrenceType != RecurrenceType.INTERVAL) return isDueToday(d, day)
        val interval = d.recurrenceIntervalDays?.coerceAtLeast(1) ?: 1
        val prev = dates.headSet(key).lastOrNull() ?: return false
        val prevMs = dateFmt.get()!!.parse(prev)?.time ?: return false
        return calendarDaysBetween(prevMs, day) == interval
    }

    // ---------- Most missed ----------

    data class MissStat(val directive: Directive, val missed: Int, val due: Int, val topReason: String? = null)

    /** The directives missed most over the last 30 days (today excluded), from
     *  computeStats. Needs 2+ misses to be listed. */
    suspend fun mostMissed(limit: Int = 3): List<MissStat> {
        val stats = computeStats()
        return db.directiveDao().getAll()
            .filter { (it.zone == Zone.DAILY || it.zone == Zone.WEEKLY) && !it.locked }
            .mapNotNull { d ->
                val st = stats[d.id] ?: return@mapNotNull null
                val missed = st.due30 - st.done30
                if (missed < 2) null else MissStat(d, missed, st.due30, st.topMissReason)
            }
            .sortedWith(compareByDescending<MissStat> { it.missed }.thenBy { it.directive.sortOrder })
            .take(limit)
    }

    // ---------- Misses, coping plans, focus ----------

    suspend fun recordMiss(directiveId: Long, reason: String) {
        db.missDao().upsert(MissEntry(directiveId, todayKey(), reason, System.currentTimeMillis()))
    }

    suspend fun clearMissToday(directiveId: Long) = db.missDao().clear(directiveId, todayKey())

    fun observeMissesToday(): Flow<List<MissEntry>> =
        observeTodayKey().flatMapLatest { db.missDao().observeForDate(it) }

    /** Directives where the same reason has won COPING_TRIGGER+ times in 30 days
     *  and there's no plan for that reason yet. */
    suspend fun copingCandidates(): List<Pair<Directive, String>> {
        val stats = computeStats()
        return db.directiveDao().getAll().mapNotNull { d ->
            val st = stats[d.id] ?: return@mapNotNull null
            val reason = st.topMissReason ?: return@mapNotNull null
            if (st.topMissReasonCount < COPING_TRIGGER) return@mapNotNull null
            if (d.copingReason == reason && !d.copingPlan.isNullOrBlank()) return@mapNotNull null
            d to reason
        }
    }

    suspend fun setCopingPlan(directiveId: Long, reason: String, plan: String) {
        val d = db.directiveDao().getById(directiveId) ?: return
        db.directiveDao().upsert(d.copy(copingReason = reason, copingPlan = plan.trim().ifBlank { null }))
    }

    /** Turns focus on or off. Returns false (and changes nothing) when turning
     *  it on would go past MAX_FOCUS. */
    suspend fun setFocus(directiveId: Long, on: Boolean): Boolean {
        val d = db.directiveDao().getById(directiveId) ?: return false
        if (on && !d.focus && db.directiveDao().getAll().count { it.focus } >= MAX_FOCUS) return false
        db.directiveDao().upsert(
            d.copy(focus = on, focusSinceEpochMs = if (on) (d.focusSinceEpochMs ?: System.currentTimeMillis()) else null)
        )
        return true
    }

    /** Median time of day this directive gets done, as minutes after
     *  midnight, from its last 30 timed completions. Null under 5. */
    suspend fun typicalCompletionMinute(directiveId: Long): Int? {
        val times = db.completionDao().recentTimed(directiveId, 30)
            .filter { it.source != "reminder" }
            .map { c -> Calendar.getInstance().apply { timeInMillis = c.completedAtEpochMs }
                .let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) } }
            .sorted()
        if (times.size < 5) return null
        return times[times.size / 2]
    }

    // ---------- Milestones ----------

    data class Milestone(val kind: String, val title: String, val detail: String) {
        companion object {
            const val GRADUATED = "GRADUATED"
            const val UNLOCKED = "UNLOCKED"
        }
    }

    /**
     * Graduations and earned unlocks, applied and returned so the UI can mark
     * the moment. Graduation: a habit counts as automatic after
     * GRADUATE_DAYS (Lally et al.'s median time to automaticity was 66 days)
     * at GRADUATE_RATE or better over the last 30. Standing goals graduate at
     * a GRADUATE_DAYS run. Fry Scream unlocks itself once Voice Protocol has
     * been done in full on UNLOCK_NEEDED of the last STAT_DAYS days.
     */
    suspend fun checkMilestones(): List<Milestone> {
        val stats = computeStats()
        val all = db.directiveDao().getAll()
        val streaks = db.streakDao().observeAll().first().associateBy { it.directiveId }
        val now = System.currentTimeMillis()
        val out = mutableListOf<Milestone>()
        for (d in all) {
            if (d.graduatedEpochMs != null || d.locked) continue
            val st = stats[d.id] ?: continue
            val detail: String? = when (d.zone) {
                Zone.DAILY, Zone.WEEKLY -> {
                    val minDue = if (d.zone == Zone.WEEKLY) 4 else 15
                    if (st.daysTracked >= GRADUATE_DAYS && st.due30 >= minDue &&
                        st.done30 >= GRADUATE_RATE * st.due30
                    ) "${st.daysTracked} DAYS · ${(st.done30 * 100) / st.due30}% OF THE LAST 30" else null
                }
                Zone.STANDING -> {
                    val run = streaks[d.id]?.currentStreakDays ?: 0
                    if (run >= GRADUATE_DAYS) "$run DAYS HELD" else null
                }
                else -> null
            }
            if (detail != null) {
                db.directiveDao().upsert(d.copy(graduatedEpochMs = now, focus = false, focusSinceEpochMs = null))
                out += Milestone(Milestone.GRADUATED, d.title, detail)
            }
        }
        val fry = all.find { it.title == FRY_SCREAM_TITLE && it.locked }
        if (fry != null && (unlockProgress(stats, all) ?: 0) >= UNLOCK_NEEDED) {
            db.directiveDao().setLocked(fry.id, locked = false)
            out += Milestone(Milestone.UNLOCKED, fry.title, "VOICE PROTOCOL $UNLOCK_NEEDED OF $STAT_DAYS DAYS")
        }
        return out
    }

    /** Full Voice Protocol days in the last STAT_DAYS, toward the Fry Scream
     *  unlock. Null when there's no Voice Protocol directive to earn it from. */
    fun unlockProgress(stats: Map<Long, DirectiveStats>, all: List<Directive>): Int? {
        val voice = all.find { it.title == VOICE_TITLE } ?: return null
        return stats[voice.id]?.cells?.count { it == DayMark.DONE } ?: 0
    }

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
            val dateKey = dateKey(cal.time)
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

    /** Also detaches anything pointing at the place, so a location objective
     *  falls back to "anywhere" instead of naming a place that's gone. */
    suspend fun deleteGeofencePlace(place: GeofencePlace) {
        db.geofencePlaceDirectiveDao().clearForPlace(place.id)
        db.directiveDao().clearPlace(place.id)
        db.geofencePlaceDao().delete(place)
    }

    /** Location objectives tied to [placeId] that still need doing: due today
     *  and not done yet, or an uncleared 🔥 objective. */
    suspend fun pendingObjectivesForPlace(placeId: Long): List<Directive> {
        val done = db.completionDao().getForDate(todayKey()).map { it.directiveId }.toSet()
        val now = Calendar.getInstance()
        return db.directiveDao().getAll().filter { d ->
            d.placeId == placeId && !d.locked && when (d.zone) {
                Zone.HARD -> d.hardClearedEpochMs == null
                else -> isDueToday(d, now) && d.id !in done
            }
        }
    }

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
        val hardBoost: Float,
        val finalPercent: Int,
        val anomalyBoost: Int = 0
    )

    suspend fun computeSyncRatioBreakdown(): SyncRatioBreakdown {
        val directives = db.directiveDao().getAll()
        val completions = db.completionDao().getForDate(todayKey())
        val streaks = db.streakDao().observeAll().first()
        val byId = completions.associateBy { it.directiveId }
        val today = todayKey()

        val now = Calendar.getInstance()
        val dueToday = directives.filter { isDueToday(it, now) && !it.locked }

        // Focus directives count double; a minimum-version completion counts half.
        fun w(d: Directive) = d.weight * (if (d.focus) 2f else 1f)
        val totalWeight = dueToday.sumOf { w(it).toDouble() }.toFloat()
        val doneWeight = dueToday.sumOf { d ->
            when (byId[d.id]?.source) {
                null -> 0.0
                "min" -> w(d) * 0.5
                else -> w(d).toDouble()
            }
        }.toFloat()
        val completionScore = if (dueToday.isEmpty() || totalWeight == 0f) 1f else doneWeight / totalWeight

        val standingDirectives = directives.filter { it.zone == Zone.STANDING }
        // A streak that survived a slip today still isn't "held" today.
        val heldCount = standingDirectives.count { d ->
            streaks.find { it.directiveId == d.id }?.let { it.currentStreakDays > 0 && it.lastSlipDate != today } ?: false
        }
        val streakScore = if (standingDirectives.isEmpty()) 1f else heldCount.toFloat() / standingDirectives.size

        val combo = (completionScore * 0.7f) + (streakScore * 0.3f)
        val base = (combo * 100).toInt().coerceIn(0, 100)
        // 🔥 boosts sit on top of the normal 0-100 score, so stacking cleared
        // hard objectives is the only way past 100. Capped at 400.
        val boost = directives.filter { it.zone == Zone.HARD }.sumOf { hardBoostNow(it, now).toDouble() }.toFloat()
        val anomalyBoost = db.anomalyDao().getForDate(today).filter { it.clearedAtEpochMs != null }.sumOf { it.boost }
        return SyncRatioBreakdown(
            dueCount = dueToday.size,
            totalWeight = totalWeight,
            doneWeight = doneWeight,
            completionScore = completionScore,
            standingCount = standingDirectives.size,
            heldCount = heldCount,
            streakScore = streakScore,
            hardBoost = boost,
            finalPercent = (base + boost.toInt() + anomalyBoost).coerceIn(0, MAX_SYNC),
            anomalyBoost = anomalyBoost
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

    suspend fun directiveById(id: Long): Directive? = db.directiveDao().getById(id)

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
    /** Standing goals with neither a hold nor a slip logged today. */
    suspend fun standingUncheckedToday(): Int {
        val today = todayKey()
        val streaks = db.streakDao().observeAll().first().associateBy { it.directiveId }
        return db.directiveDao().getAll().count { d ->
            d.zone == Zone.STANDING && streaks[d.id].let { it?.lastHeldDate != today && it?.lastSlipDate != today }
        }
    }

    suspend fun openDailyDirectivesToday(): List<Directive> {
        val doneIds = db.completionDao().getForDate(todayKey()).map { it.directiveId }.toSet()
        val now = Calendar.getInstance()
        return db.directiveDao().getAll()
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

    companion object {
        const val HARD_BOOST_DAYS = 7
        const val MAX_SYNC = 400
        const val STAT_DAYS = 14
        const val MAX_FOCUS = 3
        const val GRADUATE_DAYS = 66
        const val GRADUATE_RATE = 0.8f
        const val UNLOCK_NEEDED = 10
        const val COPING_TRIGGER = 3
        const val VOICE_TITLE = "Voice Protocol"
        const val FRY_SCREAM_TITLE = "Fry Scream Basics"
    }
}
