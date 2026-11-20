package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*

/**
 * Runs a routine one step at a time. Each step cues the next, so the whole
 * chain is one decision instead of five. Steps already done today show as
 * done; the list is fixed when the run starts, so ticking one doesn't
 * reshuffle it.
 */
@Composable
fun RoutineRunnerScreen(viewModel: MainViewModel, routineId: Long, onBack: () -> Unit) {
    val routines by viewModel.routines.collectAsState()
    val all by viewModel.allDirectives.collectAsState()
    val completions by viewModel.todayCompletions.collectAsState()
    val scan by viewModel.scanProgressToday.collectAsState()
    val haptic = LocalHapticFeedback.current
    val routine = routines.find { it.id == routineId }
    val doneIds = remember(completions) { completions.map { it.directiveId }.toSet() }
    val steps = remember(routine, all.isNotEmpty()) {
        routine?.ids()?.mapNotNull { id -> all.find { it.id == id } }
            ?.filter { !it.locked && viewModel.isDueToday(it) } ?: emptyList()
    }
    var index by remember(steps) {
        mutableStateOf(steps.indexOfFirst { it.id !in doneIds }.let { if (it < 0) steps.size else it })
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
                (routine?.name ?: "ROUTINE").uppercase(),
                fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 16.sp,
                letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            if (steps.isNotEmpty()) {
                Text(
                    "${steps.count { it.id in doneIds }}/${steps.size}",
                    fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        HazardStripe(height = 6.dp)

        if (steps.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text(
                    if (routine == null) "This routine doesn't exist any more." else "Nothing in this routine is due today.",
                    fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) { Text("BACK", fontFamily = TerminalFontFamily, color = EvaYellow) }
            }
        } else if (index >= steps.size) {
            val done = steps.count { it.id in doneIds }
            LaunchedEffect(Unit) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ROUTINE COMPLETE", fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text("$done OF ${steps.size} DONE", fontFamily = TerminalFontFamily, color = if (done == steps.size) EvaGreen else EvaTextDim, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = EvaYellow, contentColor = Color.Black)
                ) { Text("BACK TO THE DASHBOARD", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold) }
            }
        } else {
            val d = steps[index]
            val isDone = d.id in doneIds
            val simple = !d.requiresNfcScan && d.targetOz == null && d.requiredScans <= 1
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)
            ) {
                Text("STEP ${index + 1} OF ${steps.size}", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 12.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                    steps.forEachIndexed { i, s ->
                        Box(
                            Modifier.weight(1f).height(4.dp).background(
                                when {
                                    s.id in doneIds -> EvaYellow
                                    i == index -> EvaTextPrimary
                                    else -> EvaPanelBgLight
                                }
                            )
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text(d.title.uppercase(), fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                d.anchor?.takeIf { it.isNotBlank() }?.let {
                    Text("AFTER ${it.uppercase()}", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 12.sp)
                }
                d.expandableContent?.let { content ->
                    Spacer(Modifier.height(12.dp))
                    content.split("\n").forEach { line ->
                        Text("> $line", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
                d.minimumVersion?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("BAD DAY? MINIMUM: ${it.uppercase()}", fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 12.sp)
                }
                if (isDone) {
                    Spacer(Modifier.height(12.dp))
                    Text("DONE ✓", fontFamily = TerminalFontFamily, color = EvaGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val next = { index += 1 }
                when {
                    isDone -> BigButton("NEXT", EvaYellow) { next() }
                    d.requiresNfcScan -> Text("NFC TAG ONLY — TAP IT, OR SKIP", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 12.sp)
                    d.targetOz != null -> Text("LOG THE AMOUNT ON THE DASHBOARD, OR SKIP", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 12.sp)
                    d.requiredScans > 1 -> BigButton("+1  (${scan[d.id] ?: 0}/${d.requiredScans})", EvaYellow) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.tapProgress(d.id)
                    }
                    else -> BigButton("DONE", EvaYellow) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleComplete(d, true)
                        next()
                    }
                }
                if (!isDone && simple && !d.minimumVersion.isNullOrBlank()) {
                    OutlinedButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.completeMinimum(d)
                        next()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("DID THE MINIMUM", fontFamily = TerminalFontFamily, color = EvaYellow)
                    }
                }
                if (!isDone) {
                    TextButton(onClick = { next() }, modifier = Modifier.fillMaxWidth()) {
                        Text("SKIP", fontFamily = TerminalFontFamily, color = EvaTextDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black)
    ) {
        Text(label, fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 2.sp)
    }
}
