package com.evyr.rebuild.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.AppRepository.DayMark
import com.evyr.rebuild.data.DayPlan
import com.evyr.rebuild.data.Directive
import com.evyr.rebuild.data.MissReasons
import com.evyr.rebuild.data.Streak
import com.evyr.rebuild.data.Zone
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.ConsistencyBar
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*
import java.util.Calendar

/**
 * Evening check-in: close out the day in one pass.
 *  - Open items get DONE, MIN, or a miss reason. Daily self-monitoring with a
 *    reason is one of the most reliable behavior-change techniques, and the
 *    reasons drive Most Missed and the coping-plan prompt.
 *  - Standing goals get HELD or SLIPPED, so they're recorded every day
 *    instead of only when you remember to tap HOLD.
 *  - The same reason winning 3+ times prompts an if-then plan for it.
 *  - Tomorrow gets its first move, shown in the morning summary.
 *  - On Sundays (or on demand), the week in review and the focus picker.
 */
@Composable
fun CheckInScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val daily by viewModel.dailyItems.collectAsState()
    val weekly by viewModel.weeklyItems.collectAsState()
    val standing by viewModel.standingItems.collectAsState()
    val all by viewModel.allDirectives.collectAsState()
    val completions by viewModel.todayCompletions.collectAsState()
    val streaks by viewModel.streaks.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val misses by viewModel.missesToday.collectAsState()
    val syncRatio by viewModel.syncRatio.collectAsState()
    val today = viewModel.todayKey()
    val tomorrow = remember(today) { viewModel.dayKeyOffset(1) }
    val doneIds = remember(completions) { completions.map { it.directiveId }.toSet() }

    val open = remember(daily, weekly, completions) {
        (daily + weekly)
            .filter { !it.locked && viewModel.isDueToday(it) && it.id !in doneIds }
            .sortedWith(compareBy<Directive>({ if (it.focus) 0 else 1 }, { it.zone.ordinal }, { it.sortOrder }))
    }
    val doneCount = remember(daily, weekly, completions) {
        (daily + weekly).count { !it.locked && viewModel.isDueToday(it) && it.id in doneIds }
    }

    var coping by remember { mutableStateOf<List<Pair<Directive, String>>>(emptyList()) }
    LaunchedEffect(misses, all) { coping = viewModel.copingCandidates() }

    var firstMove by remember(tomorrow) { mutableStateOf(DayPlan.firstMove(context, tomorrow) ?: "") }
    var showReview by remember {
        mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
    }

    Column(modifier = Modifier.fillMaxSize().background(EvaBackground)) {
        HazardStripe(height = 6.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = EvaTextPrimary)
            }
            Text(
                "EVENING CHECK-IN",
                fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 16.sp,
                letterSpacing = 2.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text("$syncRatio%", fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
        }
        HazardStripe(height = 6.dp)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SectionLabel(
                    if (open.isEmpty()) "ALL CLEAR · $doneCount DONE TODAY"
                    else "STILL OPEN · ${open.size}" + if (doneCount > 0) "  ($doneCount DONE)" else ""
                )
            }
            if (open.isEmpty()) {
                item {
                    Text(
                        "Everything due today is done.",
                        fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 12.sp
                    )
                }
            }
            items(open, key = { it.id }) { d ->
                OpenItem(
                    d = d,
                    reason = misses[d.id],
                    overdue = stats[d.id]?.overdueDays,
                    onDone = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleComplete(d, true)
                    },
                    onMin = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.completeMinimum(d)
                    },
                    onMiss = { r -> if (misses[d.id] == r) viewModel.clearMiss(d.id) else viewModel.recordMiss(d.id, r) }
                )
            }

            if (standing.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)); SectionLabel("STANDING") }
                items(standing, key = { it.id }) { d ->
                    StandingCheck(
                        d = d,
                        streak = streaks.find { it.directiveId == d.id },
                        today = today,
                        onHeld = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.confirmStreak(d.id)
                        },
                        onSlip = {
                            viewModel.slipStreak(d.id, null) { outcome ->
                                Toast.makeText(context, slipMessage(outcome), Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }

            if (coping.isNotEmpty()) {
                item { Spacer(Modifier.height(4.dp)); SectionLabel("PLAN FOR IT") }
                items(coping, key = { it.first.id }) { (d, reason) ->
                    CopingCard(
                        d = d,
                        reason = reason,
                        count = stats[d.id]?.topMissReasonCount ?: 0,
                        onSave = { plan ->
                            viewModel.setCopingPlan(d.id, reason, plan)
                            Toast.makeText(context, "Plan saved. It'll show in this one's reminders.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionLabel("FIRST THING TOMORROW")
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = firstMove,
                    onValueChange = {
                        firstMove = it
                        DayPlan.setFirstMove(context, tomorrow, it)
                    },
                    placeholder = { Text("One thing. It opens the morning summary.", fontFamily = TerminalFontFamily, fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = rebuildTextFieldColors()
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    (if (showReview) "▾ " else "▸ ") + "WEEK IN REVIEW",
                    fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 12.sp,
                    letterSpacing = 2.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable { showReview = !showReview }.padding(vertical = 6.dp)
                )
            }
            if (showReview) {
                item {
                    WeekReview(
                        directives = (daily + weekly + standing).filter { !it.locked },
                        stats = stats,
                        syncRatio = syncRatio,
                        onFocus = { d ->
                            viewModel.setFocus(d.id, !d.focus) {
                                Toast.makeText(context, "Focus is full (3). Take one off first.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShare = { text ->
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Share the week"))
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EvaYellow, contentColor = Color.Black)
                ) {
                    Text("CLOSE OUT THE DAY", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
        }
    }
}

private fun slipMessage(outcome: AppRepository.SlipOutcome): String = when (outcome) {
    AppRepository.SlipOutcome.KEPT -> "Slip logged. The run stands — hold tomorrow and it survives."
    AppRepository.SlipOutcome.RESET -> "Second day in a row, so the run resets. Your best and your clean days stay."
    AppRepository.SlipOutcome.ALREADY -> "Already logged for today."
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 12.sp,
        letterSpacing = 2.sp, fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CheckChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Text(
        label,
        fontFamily = TerminalFontFamily,
        color = if (selected) Color.Black else color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(if (selected) color else EvaPanelBgLight, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun OpenItem(
    d: Directive,
    reason: String?,
    overdue: Int?,
    onDone: () -> Unit,
    onMin: () -> Unit,
    onMiss: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EvaPanelBg)
            .padding(10.dp)
    ) {
        Text(
            (if (d.focus) "◆ " else "") + d.title.uppercase(),
            fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
        overdue?.let {
            Text("OVERDUE $it DAY${if (it == 1) "" else "S"}", fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CheckChip("DONE", false, EvaGreen, onDone)
            if (!d.minimumVersion.isNullOrBlank() && d.requiredScans <= 1 && d.targetOz == null) {
                CheckChip("MIN: ${(d.minimumVersion ?: "").uppercase()}", false, EvaYellow, onMin)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MISSED:", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp)
            MissReasons.ALL.forEach { (key, label) ->
                CheckChip(label, reason == key, EvaRed) { onMiss(key) }
            }
        }
    }
}

@Composable
private fun StandingCheck(
    d: Directive,
    streak: Streak?,
    today: String,
    onHeld: () -> Unit,
    onSlip: () -> Unit
) {
    val held = streak?.lastHeldDate == today
    val slipped = streak?.lastSlipDate == today
    Row(
        modifier = Modifier.fillMaxWidth().background(EvaPanelBg).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(d.title.uppercase(), fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                "${streak?.currentStreakDays ?: 0} DAY RUN",
                fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp
            )
        }
        when {
            held -> Text("HELD ✓", fontFamily = TerminalFontFamily, color = EvaGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            slipped -> Text("SLIPPED", fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CheckChip("HELD", false, EvaGreen, onHeld)
                CheckChip("SLIPPED", false, EvaRed, onSlip)
            }
        }
    }
}

@Composable
private fun CopingCard(d: Directive, reason: String, count: Int, onSave: (String) -> Unit) {
    var plan by remember(d.id, reason) { mutableStateOf(d.minimumVersion ?: "") }
    Column(modifier = Modifier.fillMaxWidth().background(EvaPanelBg).padding(10.dp)) {
        Text(
            "${d.title.uppercase()} — MISSED ${count}× · ${MissReasons.label(reason)}",
            fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "IF ${MissReasons.condition(reason).uppercase()}, THEN I'LL…",
            fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 11.sp
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = plan,
            onValueChange = { plan = it },
            placeholder = { Text("Something small enough to do anyway", fontFamily = TerminalFontFamily, fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = rebuildTextFieldColors()
        )
        Spacer(Modifier.height(6.dp))
        CheckChip("SAVE PLAN", false, EvaYellow) { if (plan.isNotBlank()) onSave(plan) }
    }
}

@Composable
private fun WeekReview(
    directives: List<Directive>,
    stats: Map<Long, AppRepository.DirectiveStats>,
    syncRatio: Int,
    onFocus: (Directive) -> Unit,
    onShare: (String) -> Unit
) {
    val focusCount = directives.count { it.focus }
    fun week(d: Directive): Pair<Int, Int> {
        val cells = stats[d.id]?.cells?.takeLast(7) ?: emptyList()
        val done = cells.count { it == DayMark.DONE || it == DayMark.MIN }
        val due = cells.count { it == DayMark.DONE || it == DayMark.MIN || it == DayMark.MISSED || it == DayMark.OPEN }
        return done to due
    }
    Column(modifier = Modifier.fillMaxWidth().background(EvaPanelBg).padding(10.dp)) {
        Text(
            "FOCUS $focusCount/${AppRepository.MAX_FOCUS} · Tap ◆ to pick what you're building this week. Focus goes to the top and counts double.",
            fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        directives.forEach { d ->
            val (done, due) = week(d)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        d.title.uppercase() + if (d.graduatedEpochMs != null) " ★" else "",
                        fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 12.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConsistencyBar(stats[d.id]?.cells?.takeLast(7) ?: emptyList())
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (due == 0) "—" else "$done/$due",
                            fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp
                        )
                    }
                }
                if (d.graduatedEpochMs == null && d.zone != Zone.HARD && d.zone != Zone.MINDSET) {
                    Text(
                        "◆",
                        color = if (d.focus) EvaYellow else EvaTextDim,
                        fontSize = 18.sp,
                        modifier = Modifier.clickable { onFocus(d) }.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        CheckChip("SHARE THE WEEK", false, EvaYellow) {
            // Daily and weekly items only: standing goals can be personal, and
            // sharing should never put one in a message by default.
            val lines = directives.filter { it.zone == Zone.DAILY || it.zone == Zone.WEEKLY }
                .mapNotNull { d -> val (done, due) = week(d); if (due == 0) null else "${d.title}: $done/$due" }
            onShare(
                buildString {
                    appendLine("REBUILD — my week")
                    lines.forEach { appendLine(it) }
                    append("Sync ratio today: $syncRatio%")
                }
            )
        }
    }
}
