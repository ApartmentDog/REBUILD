package com.evyr.rebuild.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.evyr.rebuild.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(private val repo: AppRepository) : ViewModel() {

    val allDirectives: StateFlow<List<Directive>> = repo.observeAllDirectives()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 🔥 one-time objectives, uncleared first, then cleared newest first. */
    val hardItems: StateFlow<List<Directive>> = repo.observeDirectivesByZone(Zone.HARD)
        .map { list ->
            list.sortedWith(
                compareBy<Directive> { it.hardClearedEpochMs != null }
                    .thenByDescending { it.hardClearedEpochMs ?: 0L }
                    .thenBy { it.sortOrder }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Every completion ever logged -- the coin count in the hoard. */
    val lifetimeCompletions: StateFlow<Int> = repo.observeCompletionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun clearHardObjective(id: Long) {
        viewModelScope.launch { repo.clearHardObjective(id) }
    }

    fun unclearHardObjective(id: Long) {
        viewModelScope.launch { repo.unclearHardObjective(id) }
    }

    fun isClearedToday(d: Directive): Boolean = repo.isClearedToday(d)

    fun hardBoostNow(d: Directive): Float = repo.hardBoostNow(d)

    fun todayKey(): String = repo.todayKey()

    val dailyItems: StateFlow<List<Directive>> = repo.observeDirectivesByZone(Zone.DAILY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyItems: StateFlow<List<Directive>> = repo.observeDirectivesByZone(Zone.WEEKLY)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val standingItems: StateFlow<List<Directive>> = repo.observeDirectivesByZone(Zone.STANDING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mindsetItems: StateFlow<List<Directive>> = repo.observeDirectivesByZone(Zone.MINDSET)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * One-shot read of the actual current Mindset list, awaiting the real value
     * from the database rather than trusting mindsetItems.value -- that StateFlow
     * starts at emptyList() and only becomes accurate once something has been
     * collecting it for a moment. Read synchronously right at cold-start (the
     * daily-quote gate in MainActivity), .value was reliably still the empty
     * default even when Mindset had real entries, which is why the quote never
     * fired.
     */
    suspend fun mindsetItemsNow(): List<Directive> = repo.observeDirectivesByZone(Zone.MINDSET).first()

    val todayCompletions: StateFlow<List<Completion>> = repo.observeCompletionsForToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streaks: StateFlow<List<Streak>> = repo.observeStreaks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notebook: StateFlow<List<NotebookEntry>> = repo.observeNotebook()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<NfcTag>> = repo.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncHistory: StateFlow<List<SyncHistoryEntry>> = repo.observeSyncHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _syncRatio = MutableStateFlow(0)
    val syncRatio: StateFlow<Int> = _syncRatio

    suspend fun syncRatioBreakdown() = repo.computeSyncRatioBreakdown()

    fun isDueToday(directive: Directive): Boolean = repo.isDueToday(directive)

    init {
        viewModelScope.launch {
            repo.seedIfEmpty()
            refreshSyncRatio()
        }
        // Recompute whenever completions, streaks, or the directives themselves
        // change. Directives were missing: adding, editing, deleting or
        // unlocking one changes what's due (and 🔥 boosts), but the number
        // didn't move until something was ticked.
        viewModelScope.launch {
            combine(repo.observeCompletionsForToday(), repo.observeStreaks(), repo.observeAllDirectives()) { _, _, _ -> Unit }
                .collect { refreshSyncRatio() }
        }
    }

    private suspend fun refreshSyncRatio() {
        _syncRatio.value = repo.computeSyncRatio()
        repo.snapshotSyncRatioForHistory()
    }

    fun toggleComplete(directive: Directive, isDone: Boolean) {
        viewModelScope.launch {
            if (isDone) repo.markComplete(directive.id) else repo.markIncomplete(directive.id)
        }
    }

    fun confirmStreak(directiveId: Long) {
        viewModelScope.launch { repo.confirmStreakHeldToday(directiveId) }
    }

    fun breakStreak(directiveId: Long, note: String?) {
        viewModelScope.launch { repo.breakStreak(directiveId, note) }
    }

    fun deleteNotebookEntry(entry: NotebookEntry) {
        viewModelScope.launch { repo.deleteNotebookEntry(entry) }
    }

    fun addDirective(directive: Directive) {
        viewModelScope.launch { repo.addOrUpdateDirective(directive) }
    }

    /** Deletes, then cancels the alarms its reminders had armed -- removing
     *  the rows alone left them in AlarmManager until the next app open. */
    fun deleteDirective(directive: Directive, context: android.content.Context? = null) {
        val appContext = context?.applicationContext
        viewModelScope.launch {
            val removed = repo.deleteDirective(directive)
            if (appContext != null) {
                removed.forEach { com.evyr.rebuild.notification.ReminderScheduler.cancel(appContext, it) }
            }
        }
    }

    fun unlockDirective(id: Long) {
        viewModelScope.launch { repo.unlockDirective(id) }
    }

    fun setTagLists(uid: String, leaving: Boolean, arriving: Boolean) {
        viewModelScope.launch { repo.setTagLists(uid, leaving, arriving) }
    }

    fun registerTag(uid: String, label: String, linkedDirectiveId: Long?, opensChecklist: Boolean = false) {
        viewModelScope.launch { repo.registerTag(uid, label, linkedDirectiveId, opensChecklist) }
    }

    fun handleTagScan(uid: String, locationLabel: String?) {
        viewModelScope.launch { repo.handleTagScan(uid, locationLabel) }
    }

    /** Direct UI tap toward a multi-tap directive's daily target -- the water
     *  tracker bar, or anything else where the tag route isn't practical. */
    fun tapProgress(directiveId: Long) {
        viewModelScope.launch { repo.incrementScanProgress(directiveId, source = "manual") }
    }

    val hiddenMedia: StateFlow<List<HiddenMediaItem>> = repo.observeHiddenMedia()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addHiddenMedia(label: String, filePath: String, kind: AttachmentKind) {
        viewModelScope.launch { repo.addHiddenMedia(label, filePath, kind) }
    }

    fun deleteHiddenMedia(item: HiddenMediaItem) {
        viewModelScope.launch { repo.deleteHiddenMedia(item) }
    }

    fun observePlaceDirectiveIds(placeId: Long): StateFlow<List<Long>> =
        repo.observePlaceDirectiveIds(placeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPlaceDirective(placeId: Long, directiveId: Long, enabled: Boolean) {
        viewModelScope.launch { repo.setPlaceDirective(placeId, directiveId, enabled) }
    }

    fun observePlaceLinks(placeId: Long): StateFlow<List<com.evyr.rebuild.data.GeofencePlaceDirective>> =
        repo.observePlaceLinks(placeId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPlaceLink(placeId: Long, directiveId: Long, onExit: Boolean, onEnter: Boolean) {
        viewModelScope.launch { repo.setPlaceLink(placeId, directiveId, onExit, onEnter) }
    }

    /** directiveId -> taps so far today, for multi-scan directives. */
    val scanProgressToday: StateFlow<Map<Long, Int>> = repo.observeScanProgressToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** directiveId -> oz logged so far today, for volume-tracker directives. */
    val volumeProgressToday: StateFlow<Map<Long, Int>> = repo.observeVolumeProgressToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Adds a typed amount (oz) to a volume-tracker directive's running daily total. */
    fun logVolume(directiveId: Long, amountOz: Int) {
        viewModelScope.launch { repo.logVolumeProgress(directiveId, amountOz) }
    }

    fun deleteTagPublic(tag: NfcTag) {
        viewModelScope.launch { repo.deleteTag(tag) }
    }

    val geofencePlaces: StateFlow<List<GeofencePlace>> = repo.observeGeofencePlaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Returns the real Room-assigned ID -- callers that need to register a
     *  geofence for this place must use this, not the id on the object passed
     *  in, which is 0 until Room assigns the real one on insert. */
    suspend fun addGeofencePlace(place: GeofencePlace): Long = repo.addGeofencePlace(place)

    fun deleteGeofencePlace(place: GeofencePlace) {
        viewModelScope.launch { repo.deleteGeofencePlace(place) }
    }

    // ---------- Notebook search ----------

    private val _notebookQuery = MutableStateFlow("")
    val notebookQuery: StateFlow<String> = _notebookQuery

    val notebookResults: StateFlow<List<NotebookEntry>> = _notebookQuery
        .flatMapLatest { q -> if (q.isBlank()) repo.observeNotebook() else repo.searchNotebook(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setNotebookQuery(query: String) { _notebookQuery.value = query }

    fun addNotebookEntry(text: String, tags: List<String>, linkedDirectiveId: Long? = null) {
        if (text.isBlank()) return
        viewModelScope.launch { repo.addNotebookEntry(text, tags, linkedDirectiveId) }
    }

    // ---------- Guided sessions ----------

    fun observeSessionSteps(type: SessionType): StateFlow<List<SessionStep>> =
        repo.observeSessionSteps(type).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSessionLogs: StateFlow<List<SessionLog>> = repo.observeRecentSessionLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived from the live directive list: was read once at startup, so
    // unlocking Fry Scream didn't register until the app restarted.
    val fryScreamLocked: StateFlow<Boolean> = repo.observeAllDirectives()
        .map { list -> list.find { it.title == "Fry Scream Basics" }?.locked ?: true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun logSession(type: SessionType, startedAtEpochMs: Long, stepsCompleted: Int, stepsTotal: Int, notes: String? = null) {
        viewModelScope.launch { repo.logSession(type, startedAtEpochMs, stepsCompleted, stepsTotal, notes) }
    }

    fun setSessionStepReferenceUrl(step: SessionStep, url: String?) {
        viewModelScope.launch { repo.setSessionStepReferenceUrl(step, url) }
    }

    fun setSessionStepReferenceVideo(step: SessionStep, path: String?) {
        viewModelScope.launch { repo.setSessionStepReferenceVideo(step, path) }
    }

    fun observeRemindersForDirective(directiveId: Long): Flow<List<Reminder>> =
        repo.observeRemindersForDirective(directiveId)

    /** Persists the reminder and returns it with its real id -- the caller
     *  (which has a Context, unlike this view model) is responsible for
     *  calling ReminderScheduler.schedule/cancel with the result. */
    suspend fun saveReminder(reminder: Reminder): Reminder {
        val id = repo.upsertReminder(reminder)
        return reminder.copy(id = id)
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch { repo.deleteReminder(reminder) }
    }

    val tarotPulls: StateFlow<List<com.evyr.rebuild.data.TarotPull>> = repo.observeTarotPulls()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logTarotPull(cardName: String, arcana: String, reversed: Boolean) {
        viewModelScope.launch {
            repo.logTarotPull(
                com.evyr.rebuild.data.TarotPull(
                    cardName = cardName,
                    arcana = arcana,
                    reversed = reversed,
                    pulledAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun logTarotReading(
        spread: com.evyr.rebuild.tarot.TarotSpread,
        question: String,
        drawn: List<Pair<com.evyr.rebuild.tarot.TarotCard, Boolean>>
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repo.logTarotReading(
                drawn.mapIndexed { i, (card, reversed) ->
                    com.evyr.rebuild.data.TarotPull(
                        cardName = card.name,
                        arcana = card.arcana.name,
                        reversed = reversed,
                        pulledAtEpochMs = now,
                        readingId = now,
                        spreadId = spread.id,
                        positionIndex = i,
                        positionLabel = spread.positions.getOrElse(i) { "" },
                        question = question.trim()
                    )
                }
            )
        }
    }

    val books: StateFlow<List<com.evyr.rebuild.data.BookItem>> = repo.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addBook(title: String, filePath: String, format: com.evyr.rebuild.data.BookFormat, totalPages: Int) {
        viewModelScope.launch {
            repo.addBook(
                com.evyr.rebuild.data.BookItem(
                    title = title,
                    filePath = filePath,
                    format = format,
                    addedAtEpochMs = System.currentTimeMillis(),
                    totalPages = totalPages
                )
            )
        }
    }

    fun updateBookPosition(book: com.evyr.rebuild.data.BookItem, position: Int) {
        viewModelScope.launch { repo.updateBookPosition(book, position) }
    }

    fun deleteBook(book: com.evyr.rebuild.data.BookItem) {
        viewModelScope.launch { repo.deleteBook(book) }
    }

    val confessionEntries: StateFlow<List<com.evyr.rebuild.data.ConfessionEntry>> = repo.observeConfessionEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addConfessionEntry(text: String) {
        viewModelScope.launch {
            repo.addConfessionEntry(
                com.evyr.rebuild.data.ConfessionEntry(
                    text = text,
                    writtenAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteConfessionEntry(entry: com.evyr.rebuild.data.ConfessionEntry) {
        viewModelScope.launch { repo.deleteConfessionEntry(entry) }
    }

    // ---------- Media attachments ----------

    fun observeMediaForNotebookEntry(entryId: Long): StateFlow<List<MediaAttachment>> =
        repo.observeMediaForNotebookEntry(entryId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addMediaAttachment(kind: AttachmentKind, filePath: String, notebookEntryId: Long? = null, sessionLogId: Long? = null) {
        viewModelScope.launch { repo.addMediaAttachment(kind, filePath, notebookEntryId, sessionLogId) }
    }

    fun deleteMediaAttachment(attachment: MediaAttachment) {
        viewModelScope.launch { repo.deleteMediaAttachment(attachment) }
    }

    // ---------- Notification prefs ----------

    val notificationPrefs: StateFlow<NotificationPrefs> = repo.observeNotificationPrefs()
        .map { it ?: NotificationPrefs() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationPrefs())

    fun updateNotificationPrefs(prefs: NotificationPrefs) {
        viewModelScope.launch { repo.updateNotificationPrefs(prefs) }
    }

    // ---------- Day-of-week miss pattern ----------

    suspend fun mostMissedWeekdayFor(directiveId: Long): Pair<Int, Int>? = repo.mostMissedWeekdayFor(directiveId)

    suspend fun mostBrokenWeekdayFor(directiveId: Long): Pair<Int, Int>? = repo.mostBrokenWeekdayFor(directiveId)

    // ---------- Backup / restore ----------

    suspend fun exportBackup() = repo.exportBackup()

    suspend fun importBackup(jsonText: String) = repo.importBackup(jsonText)

    suspend fun writeArchive(context: android.content.Context, target: android.net.Uri) =
        repo.writeArchive(context, target)

    suspend fun restoreArchive(context: android.content.Context, source: android.net.Uri) =
        repo.restoreArchive(context, source)

    /** After a restore: re-arm every reminder alarm and re-register geofences.
     *  Restore only writes rows, so restored reminders never fired before. */
    suspend fun rearmAfterRestore(context: android.content.Context) {
        val app = context.applicationContext
        com.evyr.rebuild.notification.ReminderScheduler.rescheduleAll(app, repo)
        com.evyr.rebuild.location.GeofenceSync.reregister(app, "after restore")
    }
}

class MainViewModelFactory(private val repo: AppRepository) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
