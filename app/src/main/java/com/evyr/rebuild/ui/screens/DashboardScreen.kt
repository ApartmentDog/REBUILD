@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.Directive
import com.evyr.rebuild.data.Streak
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.CelOutlineText
import com.evyr.rebuild.ui.components.EvaPanel
import com.evyr.rebuild.ui.components.EvaPanelShadowCorner
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.components.LiquidFillBar
import com.evyr.rebuild.ui.components.MiniLineChart
import com.evyr.rebuild.ui.components.VersionFooter
import com.evyr.rebuild.ui.theme.*

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    nfcDisabled: Boolean = false,
    nfcUnavailableReason: String = "",
    onOpenSecretPrompt: () -> Unit = {},
    onOpenNotebook: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val syncRatio by viewModel.syncRatio.collectAsState()
    val syncHistory by viewModel.syncHistory.collectAsState()
    val scanProgress by viewModel.scanProgressToday.collectAsState()
    val volumeProgress by viewModel.volumeProgressToday.collectAsState()
    val daily by viewModel.dailyItems.collectAsState()
    val weekly by viewModel.weeklyItems.collectAsState()
    val standing by viewModel.standingItems.collectAsState()
    val completions by viewModel.todayCompletions.collectAsState()
    val streaks by viewModel.streaks.collectAsState()
    val hard by viewModel.hardItems.collectAsState()
    val mostMissed by viewModel.mostMissed.collectAsState()
    // Add / edit / reminders / delete without a trip to Settings. Long-press a
    // row for the menu; + ADD is in the footer.
    // Completion feedback: a short buzz when you act, and the Sync Ratio
    // change flashed on the sync panel. The flash only shows for a change that
    // follows something you just did here -- not the number loading on open,
    // or moving because of a widget tap or the date rolling over.
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var actedAt by remember { mutableStateOf(0L) }
    var lastSync by remember { mutableStateOf<Int?>(null) }
    var syncFlash by remember { mutableStateOf<Int?>(null) }
    val acted: () -> Unit = {
        actedAt = System.currentTimeMillis()
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }
    LaunchedEffect(syncRatio) {
        val prev = lastSync
        lastSync = syncRatio
        if (prev != null && prev != syncRatio && System.currentTimeMillis() - actedAt < 4000) {
            syncFlash = syncRatio - prev
            kotlinx.coroutines.delay(1500)
            syncFlash = null
        }
    }
    // The 10-day trend chart sits behind a tap so the list starts higher.
    var showTrend by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var addZone by remember { mutableStateOf(com.evyr.rebuild.data.Zone.DAILY) }
    var actionTarget by remember { mutableStateOf<Directive?>(null) }
    var editTarget by remember { mutableStateOf<Directive?>(null) }
    var remindersTarget by remember { mutableStateOf<Directive?>(null) }
    var deleteTarget by remember { mutableStateOf<Directive?>(null) }
    val places by viewModel.geofencePlaces.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val completedIds = remember(completions) { completions.map { it.directiveId }.toSet() }
    val placeNames = remember(places) { places.associate { it.id to it.label } }
    // Places the phone is currently inside, from the geofence ENTER/EXIT state.
    // Re-read when places or completions change -- cheap prefs lookups.
    val herePlaceIds = remember(places, completions) {
        places.filter { com.evyr.rebuild.location.GeofencePresence.isInside(context, it.id) == true }
            .map { it.id }.toSet()
    }
    val today = viewModel.todayKey()

    // The sealed code: revealed once, the first time the 5th 🔥 clear lands.
    val vault = remember { com.evyr.rebuild.data.HardModeVault(context) }
    val clearedHard = hard.count { it.hardClearedEpochMs != null }
    var showReveal by remember { mutableStateOf(false) }
    LaunchedEffect(clearedHard) {
        if (vault.isUnlocked(clearedHard) && vault.consumeReveal()) showReveal = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EvaBackground)
    ) {
        // Header — hazard-stripe framed, EVA-red banner with cel-outlined title
        HazardStripe(height = 6.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(EvaRed)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    CelOutlineText(
                        text = "PERSONAL OPS",
                        fontSize = 20.sp,
                        color = Color.White,
                        strokeWidthDp = 2.5f,
                        letterSpacingSp = 0.5f,
                        modifier = Modifier.width(220.dp).height(28.dp)
                    )
                    Text(
                        "EVYR · UNIT 01",
                        fontFamily = TerminalFontFamily,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF141416))
                            .border(2.dp, EvaYellow)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("$syncRatio%", fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        HazardStripe(height = 6.dp)

        // Sync Ratio: full interwoven oscilloscope readout, matching the actual
        // EVA sync graph rather than a flat number-in-a-box panel.
        EvaPanel(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shadowCorner = EvaPanelShadowCorner.BOTTOM_RIGHT
        ) {
            Column(modifier = Modifier.clickable { showTrend = !showTrend }) {
                Box {
                    com.evyr.rebuild.ui.components.SyncRatioBar(percent = syncRatio)
                    // What that last action was worth, shown for a moment
                    // under the SYNC RATIO label.
                    syncFlash?.let { d ->
                        Text(
                            (if (d > 0) "+" else "") + "$d%",
                            fontFamily = TerminalFontFamily,
                            color = if (d > 0) EvaGreen else EvaRedLight,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 24.dp)
                        )
                    }
                }
                // Trend: sync_history has been recorded daily all along but nothing
                // ever read it back, so the number had no context. Oldest-to-newest
                // left to right; observeRecent returns newest first, hence reversed.
                val history = syncHistory.map { it.syncRatioPercent.toFloat() }.reversed()
                if (history.size >= 2 && !showTrend) {
                    Text(
                        "TAP FOR TREND",
                        fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp, letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                    )
                }
                if (history.size >= 2 && showTrend) {
                    MiniLineChart(
                        values = history,
                        lineColor = EvaYellow,
                        height = 44.dp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    // Was comparing today to the OLDEST point in the window (up to
                    // 9 days back), labeled as if it meant the trend as a whole --
                    // but the chart's shape reads as day-over-day change, so a dip
                    // from yesterday to today could still say "UP" as long as today
                    // beat the value from over a week ago. That's a real mismatch
                    // between what the label says and what the line visually shows.
                    // Comparing the last two points instead, which is what "am I
                    // trending the direction the chart looks like it's going" means.
                    val delta = if (history.size >= 2) history.last() - history[history.size - 2] else 0f
                    Text(
                        text = when {
                            delta > 0 -> "UP ${delta.toInt()} PTS SINCE YESTERDAY"
                            delta < 0 -> "DOWN ${-delta.toInt()} PTS SINCE YESTERDAY"
                            else -> "FLAT SINCE YESTERDAY"
                        },
                        fontFamily = TerminalFontFamily,
                        color = if (delta >= 0) EvaGreen else EvaRed,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
                HazardStripe(height = 7.dp)
            }
        }

        if (nfcDisabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(EvaRed.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .border(1.dp, EvaRed.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "⚠ $nfcUnavailableReason",
                    fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        val zoneTitles = listOf("DAILY", "WEEKLY", "STANDING", "🔥 HARD", "SESSIONS")
        var selectedZone by remember { mutableStateOf(0) }
        var selectedSessionType by remember { mutableStateOf<com.evyr.rebuild.data.SessionType?>(null) }

        // Explicit height: Material3's Tab reserves a 48dp minimum regardless of
        // how small its content is, which left a large dead gap between the Sync
        // Ratio panel and the tab labels.
        ScrollableTabRow(
            selectedTabIndex = selectedZone,
            modifier = Modifier.height(40.dp),
            containerColor = EvaBackground,
            contentColor = EvaYellow,
            edgePadding = 16.dp,
            divider = {}
        ) {
            zoneTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedZone == index,
                    onClick = { selectedZone = index },
                    text = {
                        Box(
                            modifier = if (selectedZone == index)
                                Modifier.background(EvaYellow).padding(horizontal = 10.dp, vertical = 4.dp)
                            else Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                title,
                                fontFamily = TerminalFontFamily,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedZone == index) Color.Black else EvaTextDim
                            )
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Sessions brings its own internal scrolling container, so it renders
        // directly rather than nested inside the LazyColumn below -- a
        // LazyColumn inside another LazyColumn's item{} doesn't size correctly.
        when (selectedZone) {
            4 -> GuidedSessionContent(
                viewModel = viewModel,
                selectedType = selectedSessionType,
                onTypeSelected = { selectedSessionType = it },
                modifier = Modifier.weight(1f)
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (selectedZone) {
                    0 -> item {
                        // Location objectives for wherever you are right now go first.
                        val dueDaily = daily.filter { viewModel.isDueToday(it) }
                            .sortedBy { if (it.placeId != null && it.placeId in herePlaceIds) 0 else 1 }
                        val tapTrackers = dueDaily.filter { it.requiredScans > 1 && !it.locked }
                        val volumeTrackers = dueDaily.filter { it.targetOz != null && !it.locked }
                        if (tapTrackers.isNotEmpty()) {
                            // All trackers share one panel as compact rows rather
                            // than each getting its own card. The old layout cost
                            // roughly 100dp apiece, so a handful of them filled the
                            // screen before the checklist below had a chance.
                            com.evyr.rebuild.ui.components.EvaPanel(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    tapTrackers.forEachIndexed { i, d ->
                                        if (i > 0) Spacer(Modifier.height(6.dp))
                                        com.evyr.rebuild.ui.components.SegmentedTapBar(
                                            filled = scanProgress[d.id] ?: 0,
                                            total = d.requiredScans,
                                            label = d.title,
                                            compact = true,
                                            onTap = { acted(); viewModel.tapProgress(d.id) }
                                        )
                                    }
                                }
                            }
                        }
                        if (volumeTrackers.isNotEmpty()) {
                            // Separate panel from tapTrackers above -- different input
                            // model (typed amount vs. tap-to-increment) and different
                            // visual (liquid fill vs. segments), own card so neither
                            // changes the other's layout or behavior.
                            com.evyr.rebuild.ui.components.EvaPanel(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    volumeTrackers.forEachIndexed { i, d ->
                                        if (i > 0) Spacer(Modifier.height(10.dp))
                                        var showEntry by remember(d.id) { mutableStateOf(false) }
                                        LiquidFillBar(
                                            loggedOz = volumeProgress[d.id] ?: 0,
                                            targetOz = d.targetOz ?: 1,
                                            label = d.title,
                                            modifier = Modifier.fillMaxWidth().clickable { showEntry = true }
                                        )
                                        if (showEntry) {
                                            VolumeEntryDialog(
                                                directiveTitle = d.title,
                                                onDismiss = { showEntry = false },
                                                onConfirm = { amount ->
                                                    viewModel.logVolume(d.id, amount)
                                                    showEntry = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        ZonePanel(title = "DAILY") {
                            dueDaily.filter { (it.requiredScans <= 1 || it.locked) && it.targetOz == null }.forEach { d ->
                                CheckableRow(
                                    directive = d,
                                    isDone = completedIds.contains(d.id),
                                    scansSoFar = scanProgress[d.id] ?: 0,
                                    placeLabel = d.placeId?.let { placeNames[it] },
                                    isHere = d.placeId != null && d.placeId in herePlaceIds,
                                    onLongPress = { actionTarget = d },
                                    onToggle = { acted(); viewModel.toggleComplete(d, it) },
                                    onUnlock = { viewModel.unlockDirective(d.id) },
                                    onTapProgress = { acted(); viewModel.tapProgress(it) }
                                )
                            }
                        }
                    }
                    1 -> item {
                        val dueWeekly = weekly.filter { viewModel.isDueToday(it) }
                            .sortedBy { if (it.placeId != null && it.placeId in herePlaceIds) 0 else 1 }
                        ZonePanel(title = "WEEKLY") {
                            if (weekly.isEmpty()) {
                                Text(
                                    "No Weekly directives yet.",
                                    fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp
                                )
                            } else if (dueWeekly.isEmpty()) {
                                // Real state, not a bug: WEEKLY only shows items
                                // due specifically today. Listing what exists and
                                // when it's actually scheduled, since "empty tab,
                                // no explanation" was indistinguishable from a
                                // genuine bug -- confirmed by a real report of
                                // exactly this confusion.
                                Text(
                                    "Nothing due today. Scheduled:",
                                    fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                weekly.forEach { d ->
                                    Text(
                                        "${d.title} — ${describeWeeklySchedule(d)}",
                                        fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                            dueWeekly.forEach { d ->
                                CheckableRow(
                                    directive = d,
                                    isDone = completedIds.contains(d.id),
                                    scansSoFar = scanProgress[d.id] ?: 0,
                                    placeLabel = d.placeId?.let { placeNames[it] },
                                    isHere = d.placeId != null && d.placeId in herePlaceIds,
                                    onLongPress = { actionTarget = d },
                                    onToggle = { acted(); viewModel.toggleComplete(d, it) },
                                    onUnlock = {},
                                    onTapProgress = { acted(); viewModel.tapProgress(it) }
                                )
                            }
                        }
                    }
                    2 -> item {
                        ZonePanel(title = "STANDING") {
                            standing.forEach { d ->
                                val streak = streaks.find { it.directiveId == d.id }
                                StreakRow(
                                    directive = d,
                                    streak = streak,
                                    heldToday = streak?.lastHeldDate == today,
                                    onLongPress = { actionTarget = d },
                                    viewModel = viewModel,
                                    onConfirmToday = { acted(); viewModel.confirmStreak(d.id) },
                                    onBreak = { note -> viewModel.breakStreak(d.id, note) }
                                )
                            }
                        }
                    }
                    3 -> item {
                        HardObjectivesPanel(
                            items = hard,
                            placeNames = placeNames,
                            herePlaceIds = herePlaceIds,
                            unlocked = vault.isUnlocked(clearedHard),
                            onLongPress = { actionTarget = it },
                            onActed = acted,
                            viewModel = viewModel
                        )
                    }
                }
                if (selectedZone == 0 && mostMissed.isNotEmpty()) {
                    item { MostMissedPanel(mostMissed) }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (selectedZone != 4) {
                Text(
                    "+ ADD",
                    fontFamily = TerminalFontFamily,
                    color = EvaYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            // Opens on the zone of the tab you're looking at.
                            addZone = when (selectedZone) {
                                1 -> com.evyr.rebuild.data.Zone.WEEKLY
                                2 -> com.evyr.rebuild.data.Zone.STANDING
                                3 -> com.evyr.rebuild.data.Zone.HARD
                                else -> com.evyr.rebuild.data.Zone.DAILY
                            }
                            showAdd = true
                        }
                        .padding(vertical = 6.dp)
                )
            }
            Text(
                "NOTEBOOK",
                fontFamily = TerminalFontFamily,
                color = EvaTextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onOpenNotebook() }
                    .padding(vertical = 6.dp)
            )
            Text(
                "SETTINGS",
                fontFamily = TerminalFontFamily,
                color = EvaTextDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { onOpenSettings() }
                    .padding(vertical = 6.dp)
            )
        }
        VersionFooter(compact = true, onSecretTap = onOpenSecretPrompt, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
    }

    if (showAdd) {
        DirectiveEditDialog(
            existing = null,
            defaultZone = addZone,
            places = places,
            onDismiss = { showAdd = false },
            onSaveWithReminder = { d, h, m ->
                viewModel.addDirectiveWithReminder(d, h, m, context)
                showAdd = false
            },
            onSave = { viewModel.addDirective(it); showAdd = false }
        )
    }

    actionTarget?.let { d ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            containerColor = EvaPanelBg,
            title = {
                Text(d.title.uppercase(), fontFamily = TerminalFontFamily, color = EvaYellow, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    listOf<Pair<String, () -> Unit>>(
                        "EDIT" to { editTarget = d },
                        "REMINDERS" to { remindersTarget = d },
                        "DELETE" to { deleteTarget = d }
                    ).forEach { (label, action) ->
                        Text(
                            label,
                            fontFamily = TerminalFontFamily,
                            color = if (label == "DELETE") EvaRed else EvaTextPrimary,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { actionTarget = null; action() }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionTarget = null }) {
                    Text("CLOSE", fontFamily = TerminalFontFamily, color = EvaTextDim)
                }
            }
        )
    }

    editTarget?.let { d ->
        DirectiveEditDialog(
            existing = d,
            places = places,
            onDismiss = { editTarget = null },
            onSave = { viewModel.addDirective(it); editTarget = null }
        )
    }

    remindersTarget?.let { d ->
        ReminderManagementDialog(directive = d, viewModel = viewModel, onDismiss = { remindersTarget = null })
    }

    deleteTarget?.let { d ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = EvaPanelBg,
            title = { Text("DELETE DIRECTIVE?", fontFamily = TerminalFontFamily, color = EvaRed, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"${d.title}\" and its reminders and streak go with it. Can't be undone.",
                    fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDirective(d, context)
                    deleteTarget = null
                }) { Text("DELETE", fontFamily = TerminalFontFamily, color = EvaRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("CANCEL", fontFamily = TerminalFontFamily, color = EvaTextDim)
                }
            }
        )
    }

    if (showReveal) {
        AlertDialog(
            onDismissRequest = { showReveal = false },
            containerColor = EvaPanelBg,
            title = {
                Text("🔥 5 / 5 — SEAL BROKEN", fontFamily = TerminalFontFamily, color = HardOrange, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("NEW CODE ACQUIRED", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp, letterSpacing = 2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        com.evyr.rebuild.data.HardModeVault.CODE,
                        fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 28.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 6.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Enter it where the other codes go.", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showReveal = false }) {
                    Text("ACKNOWLEDGED", fontFamily = TerminalFontFamily, color = EvaGreen, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

private val HardOrange = Color(0xFFFF6A00)

/** The directives skipped most over the last 30 days, worst first. */
@Composable
private fun MostMissedPanel(stats: List<com.evyr.rebuild.data.AppRepository.MissStat>) {
    EvaPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                "MOST MISSED · LAST 30 DAYS",
                fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp,
                letterSpacing = 2.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            stats.forEach { s ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        s.directive.title.uppercase(),
                        fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${s.missed} OF ${s.due}",
                        fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 🔥 one-time objectives. Active ones on top with a CLEAR button (confirmed --
 * it's permanent), cleared ones below as a record with their remaining boost.
 * A cleared objective can be undone the same day only, for misfires.
 */
@Composable
private fun HardObjectivesPanel(
    items: List<Directive>,
    placeNames: Map<Long, String>,
    herePlaceIds: Set<Long>,
    unlocked: Boolean,
    onLongPress: (Directive) -> Unit,
    onActed: () -> Unit,
    viewModel: MainViewModel
) {
    val active = items.filter { it.hardClearedEpochMs == null && !it.locked }
        .sortedBy { if (it.placeId != null && it.placeId in herePlaceIds) 0 else 1 }
    val cleared = items.filter { it.hardClearedEpochMs != null }
    var confirming by remember { mutableStateOf<Directive?>(null) }
    val required = com.evyr.rebuild.data.HardModeVault.REQUIRED

    EvaPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Progress toward the sealed code. Never says what it is.
            val n = cleared.size.coerceAtMost(required)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (1..required).joinToString("") { if (it <= n || unlocked) "🔥" else "·" },
                    fontSize = 14.sp, letterSpacing = 2.sp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (unlocked) "SEAL BROKEN — CODE ACQUIRED" else "SEALED · $n / $required",
                    fontFamily = TerminalFontFamily,
                    color = if (unlocked) EvaYellow else EvaTextDim,
                    fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))

            if (active.isEmpty()) {
                Text(
                    "No active hard objectives. Add one in Settings — pick the 🔥 zone.",
                    fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp
                )
            }
            active.forEach { d ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = {}, onLongClick = { onLongPress(d) })
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔥", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            d.title.uppercase(),
                            fontFamily = TerminalFontFamily, color = EvaTextPrimary,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "+${d.hardBoost}% SYNC" + (d.placeId?.let { placeNames[it] }?.let { " · 📍 ${it.uppercase()}" } ?: "") +
                                if (d.placeId != null && d.placeId in herePlaceIds) " · YOU'RE HERE" else "",
                            fontFamily = TerminalFontFamily, color = HardOrange, fontSize = 11.sp
                        )
                        d.expandableContent?.let {
                            Text(it, fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp)
                        }
                    }
                    TextButton(onClick = { confirming = d }) {
                        Text("CLEAR", fontFamily = TerminalFontFamily, color = HardOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (cleared.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HazardStripe(height = 4.dp)
                Spacer(Modifier.height(6.dp))
                Text("CLEARED", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp, letterSpacing = 2.sp)
                cleared.forEach { d ->
                    val boost = viewModel.hardBoostNow(d)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                d.title.uppercase(),
                                fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                            )
                            Text(
                                if (boost > 0f) "BOOST ACTIVE +${"%.1f".format(boost)}%" else "BOOST SPENT",
                                fontFamily = TerminalFontFamily,
                                color = if (boost > 0f) HardOrange else EvaTextDim, fontSize = 11.sp
                            )
                        }
                        if (viewModel.isClearedToday(d)) {
                            TextButton(onClick = { viewModel.unclearHardObjective(d.id) }) {
                                Text("UNDO", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    confirming?.let { d ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            containerColor = EvaPanelBg,
            title = { Text("🔥 CLEAR OBJECTIVE?", fontFamily = TerminalFontFamily, color = HardOrange, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${d.title.uppercase()}\n\nOne-time. +${d.hardBoost}% Sync Ratio, fading over a week.",
                    fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 12.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onActed()
                    viewModel.clearHardObjective(d.id)
                    confirming = null
                }) { Text("CLEARED", fontFamily = TerminalFontFamily, color = HardOrange, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) {
                    Text("NOT YET", fontFamily = TerminalFontFamily, color = EvaTextDim)
                }
            }
        )
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun ZonePanel(title: String, content: @Composable ColumnScope.() -> Unit) {
    // No title text here on purpose: the selected tab directly above already
    // reads DAILY / WEEKLY / STANDING, so repeating it inside the panel cost
    // roughly 35dp of vertical space for zero additional information -- space
    // the directive list needs. The `title` param is kept so call sites don't
    // change and it can be reinstated if the tabs ever move elsewhere.
    EvaPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            content()
        }
    }
}

@Composable
private fun CheckableRow(
    directive: Directive,
    isDone: Boolean,
    scansSoFar: Int = 0,
    placeLabel: String? = null,
    isHere: Boolean = false,
    onLongPress: () -> Unit = {},
    onToggle: (Boolean) -> Unit,
    onUnlock: () -> Unit,
    onTapProgress: (Long) -> Unit = {}
) {
    var expanded by remember(directive.id) { mutableStateOf(false) }
    val manualToggleBlocked = directive.requiresNfcScan && !directive.locked

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (!directive.locked && directive.expandableContent != null) expanded = !expanded
                    },
                    onLongClick = onLongPress
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val checkEnabled = !directive.locked && !manualToggleBlocked
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(if (isDone) EvaRed else Color(0xFF2A2A2E))
                    .border(2.dp, if (isDone) EvaRed else Color(0xFF3A3A3E))
                    .clickable(enabled = checkEnabled) { onToggle(!isDone) },
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = directive.title.uppercase(),
                fontFamily = TerminalFontFamily,
                color = if (directive.locked) EvaTextDim else EvaTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textDecoration = if (isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
            )
            if (directive.locked) {
                Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = EvaTextDim, modifier = Modifier.size(16.dp))
            } else if (directive.expandableContent != null) {
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = EvaTextDim
                )
            }
        }
        if (placeLabel != null) {
            Text(
                text = "📍 ${placeLabel.uppercase()}" + if (isHere) " · YOU'RE HERE" else "",
                fontFamily = TerminalFontFamily,
                color = if (isHere && !isDone) EvaYellow else EvaTextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 36.dp)
            )
        }
        if (directive.locked) {
            Text(
                text = "LOCKED — BUILD VOICE PROTOCOL CONSISTENCY TO UNLOCK",
                fontFamily = TerminalFontFamily,
                color = EvaTextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 36.dp, bottom = 4.dp)
            )
        } else if (manualToggleBlocked && directive.requiredScans > 1) {
            Text(
                text = "NFC TAPS: $scansSoFar / ${directive.requiredScans}",
                fontFamily = TerminalFontFamily,
                color = if (isDone) EvaGreen else EvaYellow,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 36.dp, bottom = 4.dp)
            )
        } else if (manualToggleBlocked) {
            Text(
                text = "NFC TAP ONLY — cannot be checked manually",
                fontFamily = TerminalFontFamily,
                color = EvaTextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 36.dp, bottom = 4.dp)
            )
        }
        if (expanded && directive.expandableContent != null) {
            Column(modifier = Modifier.padding(start = 36.dp, top = 4.dp, bottom = 8.dp)) {
                directive.expandableContent.split("\n").forEach { step ->
                    Text(
                        text = "> $step",
                        fontFamily = TerminalFontFamily,
                        color = EvaTextDim,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StreakRow(
    directive: Directive,
    streak: Streak?,
    heldToday: Boolean,
    onLongPress: () -> Unit = {},
    viewModel: MainViewModel,
    onConfirmToday: () -> Unit,
    onBreak: (String?) -> Unit
) {
    var showBreakDialog by remember { mutableStateOf(false) }
    val days = streak?.currentStreakDays ?: 0

    // Needs at least 3 recorded breaks before it says anything -- see
    // AppRepository.mostBrokenWeekdayFor. Recomputed when the streak count
    // changes, which is when a break would have just been logged.
    var breakPattern by remember(directive.id, streak?.currentStreakDays) {
        mutableStateOf<Pair<Int, Int>?>(null)
    }
    LaunchedEffect(directive.id, streak?.currentStreakDays) {
        breakPattern = viewModel.mostBrokenWeekdayFor(directive.id)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = directive.title.uppercase(),
                fontFamily = TerminalFontFamily,
                color = EvaTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$days DAY${if (days == 1) "" else "S"} UNBROKEN" +
                    (streak?.bestStreakDays?.takeIf { it > 0 }?.let { " · BEST $it" } ?: ""),
                fontFamily = TerminalFontFamily,
                color = EvaTextDim,
                fontSize = 11.sp
            )
            breakPattern?.let { (weekday, count) ->
                Text(
                    text = "BREAKS MOST ON ${WEEKDAY_NAMES.getOrElse(weekday) { "?" }} ($count TIMES)",
                    fontFamily = TerminalFontFamily,
                    color = EvaYellow,
                    fontSize = 11.sp
                )
            }
        }
        // Once a day: the repository ignores a second HOLD, so the button says so.
        TextButton(onClick = onConfirmToday, enabled = !heldToday) {
            Text(
                if (heldToday) "HELD" else "HOLD",
                fontFamily = TerminalFontFamily,
                color = if (heldToday) EvaTextDim else EvaGreen,
                fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
        }
        TextButton(onClick = { showBreakDialog = true }) {
            Text("BREAK", fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }

    if (showBreakDialog) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBreakDialog = false },
            containerColor = EvaPanelBg,
            title = { Text("STREAK BROKEN", fontFamily = TerminalFontFamily, color = EvaRed, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text("Optional note — why? (for your own data, not guilt)", fontFamily = TerminalFontFamily) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = EvaTextPrimary,
                        unfocusedTextColor = EvaTextPrimary,
                        focusedBorderColor = EvaYellow,
                        unfocusedBorderColor = EvaTextDim
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onBreak(note.ifBlank { null })
                    showBreakDialog = false
                }) {
                    Text("CONFIRM", fontFamily = TerminalFontFamily, color = EvaRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBreakDialog = false }) {
                    Text("CANCEL", fontFamily = TerminalFontFamily, color = EvaTextDim)
                }
            }
        )
    }
}

private val WEEKDAY_NAMES = listOf("", "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

/** Human-readable description of when a Weekly directive is actually due, for
 *  the "nothing due today, here's what's scheduled" list. */
private fun describeWeeklySchedule(d: Directive): String = when (d.recurrenceType) {
    com.evyr.rebuild.data.RecurrenceType.NONE ->
        d.weekday?.let { WEEKDAY_NAMES.getOrElse(it) { "?" } } ?: "no day set"
    com.evyr.rebuild.data.RecurrenceType.SPECIFIC_DAYS ->
        d.recurrenceDays?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.joinToString(", ") { WEEKDAY_NAMES.getOrElse(it) { "?" } }
            ?.ifBlank { "no days set" } ?: "no days set"
    com.evyr.rebuild.data.RecurrenceType.INTERVAL ->
        "every ${d.recurrenceIntervalDays ?: "?"} days"
    com.evyr.rebuild.data.RecurrenceType.MONTHLY ->
        "day ${d.recurrenceDayOfMonth ?: "?"} of the month"
}

/** Typed-amount entry for a volume-tracker directive (e.g. "16" for 16oz just
 *  drunk). Additive -- each confirmed entry adds to today's running total. */
@Composable
private fun VolumeEntryDialog(
    directiveTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EvaPanelBg,
        title = { Text(directiveTitle.uppercase(), fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                placeholder = { Text("Amount (oz)", fontFamily = TerminalFontFamily) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = EvaTextPrimary,
                    unfocusedTextColor = EvaTextPrimary,
                    focusedBorderColor = EvaYellow,
                    unfocusedBorderColor = EvaTextDim
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let { onConfirm(it) } },
                enabled = amount != null && amount > 0
            ) {
                Text("LOG", fontFamily = TerminalFontFamily, color = EvaGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", fontFamily = TerminalFontFamily, color = EvaTextDim)
            }
        }
    )
}
