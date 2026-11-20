package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.Directive
import com.evyr.rebuild.data.Routine
import com.evyr.rebuild.data.Zone
import com.evyr.rebuild.notification.AnomalyScheduler
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.theme.*

/** Settings: routines (ordered chains of directives run step by step). */
@Composable
fun RoutinesSection(viewModel: MainViewModel, directives: List<Directive>) {
    val routines by viewModel.routines.collectAsState()
    var editing by remember { mutableStateOf<Routine?>(null) }
    var creating by remember { mutableStateOf(false) }
    val chainable = directives.filter { (it.zone == Zone.DAILY || it.zone == Zone.WEEKLY) && !it.locked }

    Column {
        Spacer(Modifier.height(16.dp))
        Text("ROUTINES", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp, letterSpacing = 2.sp)
        Text(
            "A chain of directives run one at a time, so one decision starts the lot. Run them from the DAILY tab.",
            fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp
        )
        Spacer(Modifier.height(4.dp))
        routines.forEach { r ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(r.name.uppercase(), fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 13.sp)
                    Text(
                        r.ids().mapNotNull { id -> directives.find { it.id == id }?.title }.joinToString(" → "),
                        fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp, maxLines = 2
                    )
                }
                TextButton(onClick = { editing = r }) {
                    Text("EDIT", fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 11.sp)
                }
                TextButton(onClick = { viewModel.deleteRoutine(r) }) {
                    Text("DELETE", fontFamily = TerminalFontFamily, color = NervRed, fontSize = 11.sp)
                }
            }
        }
        Text(
            "+ NEW ROUTINE",
            fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 12.sp,
            modifier = Modifier.clickable { creating = true }.padding(vertical = 8.dp)
        )
    }

    if (creating || editing != null) {
        RoutineEditDialog(
            existing = editing,
            directives = chainable,
            onDismiss = { creating = false; editing = null },
            onSave = {
                viewModel.saveRoutine(it)
                creating = false
                editing = null
            }
        )
    }
}

@Composable
private fun RoutineEditDialog(
    existing: Routine?,
    directives: List<Directive>,
    onDismiss: () -> Unit,
    onSave: (Routine) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var order by remember {
        mutableStateOf(existing?.ids()?.filter { id -> directives.any { it.id == id } } ?: emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NervPanel,
        title = {
            Text(
                if (existing == null) "NEW ROUTINE" else "EDIT ROUTINE",
                fontFamily = TerminalFontFamily, color = NervOrange
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Morning run", fontFamily = TerminalFontFamily) },
                    singleLine = true,
                    colors = rebuildTextFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                Text("TAP THEM IN THE ORDER YOU DO THEM", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                directives.forEach { d ->
                    val pos = order.indexOf(d.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { order = if (pos >= 0) order - d.id else order + d.id }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(if (pos >= 0) NervOrange else NervPanelBorder, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (pos >= 0) {
                                Text("${pos + 1}", fontFamily = TerminalFontFamily, color = NervBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(d.title.uppercase(), fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && order.isNotEmpty(),
                onClick = {
                    onSave(
                        (existing ?: Routine(name = name.trim(), directiveIds = ""))
                            .copy(name = name.trim(), directiveIds = order.joinToString(","))
                    )
                }
            ) { Text("SAVE", fontFamily = TerminalFontFamily, color = NervGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL", fontFamily = TerminalFontFamily, color = NervDim) }
        }
    )
}

/** Settings: on/off for surprise challenges. */
@Composable
fun SurpriseChallengeToggle() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(AnomalyScheduler.isEnabled(context)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("SURPRISE CHALLENGES", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp)
            Text(
                "Some days, between 10:00 and 20:00, a one-hour challenge shows up. Clear it for +${AnomalyScheduler.BOOST}% that day.",
                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp
            )
        }
        Switch(
            checked = on,
            onCheckedChange = {
                on = it
                AnomalyScheduler.setEnabled(context, it)
            },
            colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
        )
    }
}
