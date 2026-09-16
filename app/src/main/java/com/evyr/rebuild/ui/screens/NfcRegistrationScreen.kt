package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.Directive
import com.evyr.rebuild.data.NfcTag
import com.evyr.rebuild.nfc.NfcHelper
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.components.HexGridBackground
import com.evyr.rebuild.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Registration flow: tap "REGISTER NEW TAG", choose a label + optional linked
 * directive UP FRONT, then tap the blank tag to physically write it. The write
 * fires the instant the tap is detected (MainActivity.onNewIntent, armed via
 * armPendingRegistration) — no gap between "finish typing" and "write attempted"
 * where the tag could leave the NFC field. That gap was the original bug.
 *
 * Once a tag is registered, future taps to that same physical tag call
 * repo.handleTagScan directly and mark the linked directive complete — this screen
 * is only needed the first time a given tag is set up.
 */
@Composable
fun NfcRegistrationScreen(
    viewModel: MainViewModel,
    pendingScanUid: String?,
    onClearPendingScan: () -> Unit,
    onBack: () -> Unit
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as? com.evyr.rebuild.MainActivity
    val tags by viewModel.tags.collectAsState()
    val daily by viewModel.dailyItems.collectAsState()
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.US) }

    var showSetupDialog by remember { mutableStateOf(false) }
    var labelDraft by remember { mutableStateOf("") }
    var linkedDirective by remember { mutableStateOf<Directive?>(null) }
    var awaitingTap by remember { mutableStateOf(false) }

    val writeResult = activity?.lastWriteResult

    // Once a write result comes back while we're waiting on a tap, resolve it:
    // success registers the tag in the DB using the label we already chose;
    // failure surfaces the reason and lets the user retry the tap without
    // re-typing anything.
    LaunchedEffect(writeResult, awaitingTap) {
        if (!awaitingTap) return@LaunchedEffect
        val result = writeResult ?: return@LaunchedEffect
        val uid = pendingScanUid ?: return@LaunchedEffect
        if (result is NfcHelper.WriteResult.Success) {
            viewModel.registerTag(uid, labelDraft, linkedDirective?.id)
            awaitingTap = false
            showSetupDialog = false
            activity?.disarmPendingRegistration()
            onClearPendingScan()
        }
        // Failure: stay in awaitingTap state, error shows below, tag is still armed
        // so the very next tap retries the same write with the same label.
    }

    HexGridBackground(modifier = Modifier.fillMaxSize().background(NervBackground)) {
    Column(modifier = Modifier.fillMaxSize()) {
        HazardStripe(height = 4.dp, color = NervRed.copy(alpha = 0.6f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = NervGreen)
            }
            Text("NFC TAGS", fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 16.sp, letterSpacing = 2.sp)
        }
        HazardStripe(height = 4.dp, color = NervRed.copy(alpha = 0.6f))
        Spacer(Modifier.height(4.dp))

        Text(
            text = "Set a label first, then tap the blank tag to write it.",
            fontFamily = TerminalFontFamily,
            color = NervDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
        ) {
            items(tags) { tag ->
                TagRow(tag, daily, fmt, viewModel) { viewModel.deleteTagPublic(tag) }
            }
        }

        Button(
            onClick = {
                labelDraft = ""
                linkedDirective = null
                awaitingTap = false
                activity?.disarmPendingRegistration()
                showSetupDialog = true
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
        ) {
            Icon(Icons.Filled.Nfc, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("REGISTER NEW TAG", fontFamily = TerminalFontFamily, fontSize = 12.sp)
        }
    }
    }

    if (showSetupDialog) {
        AlertDialog(
            onDismissRequest = {
                showSetupDialog = false
                awaitingTap = false
                activity?.disarmPendingRegistration()
                onClearPendingScan()
            },
            containerColor = NervPanel,
            title = {
                Text(
                    if (awaitingTap) "HOLD TAG TO PHONE" else "NEW TAG",
                    fontFamily = TerminalFontFamily, color = NervOrange
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (!awaitingTap) {
                        OutlinedTextField(
                            value = labelDraft,
                            onValueChange = { labelDraft = it },
                            placeholder = { Text("Label, e.g. Wallet Tray", fontFamily = TerminalFontFamily) },
                            colors = rebuildTextFieldColors()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("LINK TO DIRECTIVE (OPTIONAL)", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp)
                        daily.forEach { d ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                RadioButton(
                                    selected = linkedDirective?.id == d.id,
                                    onClick = { linkedDirective = d },
                                    colors = RadioButtonDefaults.colors(selectedColor = NervGreen)
                                )
                                Text(d.title, fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Text(
                            "Labeling as \"$labelDraft\" — hold the phone to the blank tag now.",
                            fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 13.sp
                        )
                        val result = writeResult
                        if (result != null && result !is NfcHelper.WriteResult.Success) {
                            Spacer(Modifier.height(12.dp))
                            val message = when (result) {
                                is NfcHelper.WriteResult.NotNdefCapable -> "tag is not NDEF-capable"
                                is NfcHelper.WriteResult.NotWritable -> "tag is read-only or locked"
                                is NfcHelper.WriteResult.TooSmall -> "label too long (${result.needed}B, tag holds ${result.available}B)"
                                is NfcHelper.WriteResult.Error -> result.message
                                is NfcHelper.WriteResult.Success -> ""
                            }
                            Text(
                                "WRITE FAILED — $message. Try tapping again.",
                                fontFamily = TerminalFontFamily, color = NervRed, fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!awaitingTap) {
                    TextButton(onClick = {
                        if (labelDraft.isNotBlank()) {
                            activity?.armPendingRegistration(labelDraft)
                            awaitingTap = true
                        }
                    }) {
                        Text("READY — NOW SCAN", fontFamily = TerminalFontFamily, color = NervGreen)
                    }
                } else {
                    TextButton(onClick = {
                        showSetupDialog = false
                        awaitingTap = false
                        activity?.disarmPendingRegistration()
                        onClearPendingScan()
                    }) {
                        Text("CANCEL", fontFamily = TerminalFontFamily, color = NervDim)
                    }
                }
            },
            dismissButton = {
                if (!awaitingTap) {
                    TextButton(onClick = {
                        showSetupDialog = false
                        activity?.disarmPendingRegistration()
                        onClearPendingScan()
                    }) {
                        Text("CANCEL", fontFamily = TerminalFontFamily, color = NervDim)
                    }
                }
            }
        )
    }
}

@Composable
private fun TagRow(tag: NfcTag, daily: List<Directive>, fmt: SimpleDateFormat, viewModel: MainViewModel, onDelete: () -> Unit) {
    val linkedTitle = daily.find { it.id == tag.linkedDirectiveId }?.title
    var missPattern by remember(tag.tagUid) { mutableStateOf<Pair<Int, Int>?>(null) }

    LaunchedEffect(tag.linkedDirectiveId) {
        missPattern = tag.linkedDirectiveId?.let { viewModel.mostMissedWeekdayFor(it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tag.label.uppercase(), fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 13.sp)
            Text(
                linkedTitle?.let { "LINKED — $it" } ?: "UNLINKED",
                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp
            )
            tag.lastScanEpochMs?.let {
                Text(
                    "LAST SCAN — ${fmt.format(Date(it))}",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp
                )
            }
            missPattern?.let { (weekday, misses) ->
                Text(
                    "MOST MISSED ON ${WEEKDAY_NAMES.getOrElse(weekday) { "?" }} ($misses in last 60d)",
                    fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 10.sp
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete tag", tint = NervRed, modifier = Modifier.size(18.dp))
        }
    }
}

private val WEEKDAY_NAMES = listOf("", "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
