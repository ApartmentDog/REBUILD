package com.evyr.rebuild.ui.screens

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.evyr.rebuild.data.SessionStep
import com.evyr.rebuild.data.SessionType
import com.evyr.rebuild.media.MediaCapture
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Guided session mode: pick Posture / Voice / Fry Scream, then step through each
 * move one at a time with a countdown for timed steps. Fry Scream stays locked
 * until the Fry Scream Basics directive itself unlocks (consistency-gated).
 *
 * Body is GuidedSessionContent (no header) so it can also render inline in the
 * Dashboard's Guided Sessions tab; this wrapper just adds the standalone
 * screen's own header for when it's opened from Settings.
 */
@Composable
fun GuidedSessionScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var selectedType by remember { mutableStateOf<SessionType?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(NervBackground)) {
        HazardStripe(height = 4.dp, color = NervRed.copy(alpha = 0.6f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (selectedType == null) onBack() else selectedType = null }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = NervGreen)
            }
            Text("GUIDED SESSION", fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 16.sp, letterSpacing = 2.sp)
        }
        HazardStripe(height = 4.dp, color = NervRed.copy(alpha = 0.6f))

        GuidedSessionContent(
            viewModel = viewModel,
            selectedType = selectedType,
            onTypeSelected = { selectedType = it },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * The actual guided-session UI (type picker or active runner), with no header
 * of its own -- used both by the standalone GuidedSessionScreen and inline in
 * the Dashboard Guided Sessions tab. selectedType/onTypeSelected are hoisted
 * so the caller's back-button behavior (picker vs runner) still works.
 */
@Composable
fun GuidedSessionContent(
    viewModel: MainViewModel,
    selectedType: SessionType?,
    onTypeSelected: (SessionType?) -> Unit,
    modifier: Modifier = Modifier
) {
    val fryLocked by viewModel.fryScreamLocked.collectAsState()

    Box(modifier = modifier.fillMaxWidth()) {
        if (selectedType == null) {
            SessionTypePicker(fryLocked = fryLocked, onSelect = onTypeSelected)
        } else {
            SessionRunner(
                type = selectedType,
                viewModel = viewModel,
                onFinished = { onTypeSelected(null) }
            )
        }
    }
}

@Composable
private fun SessionTypePicker(fryLocked: Boolean, onSelect: (SessionType) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SessionTypeCard("POSTURE", "Stretch + strengthen protocol", onClick = { onSelect(SessionType.POSTURE) })
        Spacer(Modifier.height(12.dp))
        SessionTypeCard("VOICE", "Warm-up, breath, resonance, range", onClick = { onSelect(SessionType.VOICE) })
        Spacer(Modifier.height(12.dp))
        SessionTypeCard(
            "FRY SCREAM BASICS",
            if (fryLocked) "LOCKED — unlock via daily consistency" else "Metal vocal technique fundamentals",
            enabled = !fryLocked,
            onClick = { onSelect(SessionType.FRY_SCREAM) }
        )
        // Bottom padding so the last card clears the tab-area edge rather than
        // sitting flush against it once scrolled all the way down.
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SessionTypeCard(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.4f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NervPanel, RoundedCornerShape(6.dp))
            .border(1.dp, NervPanelBorder, RoundedCornerShape(6.dp))
            .padding(16.dp)
    ) {
        Text(title, fontFamily = TerminalFontFamily, color = NervOrange.copy(alpha = alpha), fontSize = 15.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontFamily = TerminalFontFamily, color = NervDim.copy(alpha = alpha), fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("START", fontFamily = TerminalFontFamily)
        }
    }
}

@Composable
private fun SessionRunner(type: SessionType, viewModel: MainViewModel, onFinished: () -> Unit) {
    val steps by viewModel.observeSessionSteps(type).collectAsState(initial = emptyList())
    var currentIndex by remember(type) { mutableStateOf(0) }
    val startedAt = remember(type) { System.currentTimeMillis() }

    if (steps.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No steps configured.", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 13.sp)
        }
        return
    }

    if (currentIndex >= steps.size) {
        LaunchedEffect(Unit) {
            viewModel.logSession(type, startedAt, stepsCompleted = steps.size, stepsTotal = steps.size)
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SESSION COMPLETE", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 18.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onFinished,
                    colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
                ) { Text("DONE", fontFamily = TerminalFontFamily) }
            }
        }
        return
    }

    val step = steps[currentIndex]
    StepView(
        step = step,
        stepNumber = currentIndex + 1,
        totalSteps = steps.size,
        viewModel = viewModel,
        onAdvance = { currentIndex++ },
        onAbandon = {
            viewModel.logSession(type, startedAt, stepsCompleted = currentIndex, stepsTotal = steps.size, notes = "Abandoned mid-session")
            onFinished()
        }
    )
}

@Composable
private fun StepView(step: SessionStep, stepNumber: Int, totalSteps: Int, viewModel: MainViewModel, onAdvance: () -> Unit, onAbandon: () -> Unit) {
    var secondsLeft by remember(step.id) { mutableStateOf(step.durationSeconds) }
    val context = LocalContext.current

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val copiedPath = MediaCapture.copyPickedVideoIntoStorage(context, uri)
            if (copiedPath != null) {
                viewModel.setSessionStepReferenceVideo(step, copiedPath)
            }
        }
    }

    LaunchedEffect(step.id) {
        secondsLeft = step.durationSeconds
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft -= 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "STEP $stepNumber / $totalSteps",
            fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp, letterSpacing = 1.sp
        )
        Spacer(Modifier.height(16.dp))
        Text(
            step.title.uppercase(),
            fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 20.sp,
            letterSpacing = 1.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            step.instructions,
            fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))

        if (step.referenceVideoPath != null) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoPath(step.referenceVideoPath)
                        setOnPreparedListener { it.isLooping = true; start() }
                    }
                },
                modifier = Modifier.fillMaxWidth(0.8f).height(180.dp)
                    .background(NervPanel, RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { videoPickerLauncher.launch("video/*") }) {
                Text("REPLACE REFERENCE VIDEO", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp)
            }
        } else {
            OutlinedButton(
                onClick = { videoPickerLauncher.launch("video/*") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NervDim)
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("ADD REFERENCE VIDEO", fontFamily = TerminalFontFamily, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        if (step.durationSeconds > 0) {
            Text(
                "%d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                fontFamily = TerminalFontFamily, color = if (secondsLeft == 0) NervGreen else NervOrange, fontSize = 36.sp
            )
            Spacer(Modifier.height(24.dp))
        }
        Button(
            onClick = onAdvance,
            colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground),
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(Icons.Filled.SkipNext, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(if (stepNumber == totalSteps) "FINISH" else "NEXT STEP", fontFamily = TerminalFontFamily)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onAbandon) {
            Text("END SESSION EARLY", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp)
        }
    }
}
