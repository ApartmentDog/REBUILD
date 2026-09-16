package com.evyr.rebuild.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.evyr.rebuild.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Real countdown timer, tied to a single protocol step. Does NOT auto-advance
 * when it hits zero — per design decision, it just sits at 0:00 and waits for
 * the person to hit NEXT themselves. Start/Pause/Resume/Reset only.
 */
@Composable
fun StepTimer(
    totalSeconds: Int,
    modifier: Modifier = Modifier
) {
    var remainingSeconds by remember(totalSeconds) { mutableIntStateOf(totalSeconds) }
    var isRunning by remember(totalSeconds) { mutableStateOf(false) }

    LaunchedEffect(isRunning, remainingSeconds) {
        if (isRunning && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
            if (remainingSeconds <= 0) isRunning = false
        }
    }

    val minutes = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val progress = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds.toFloat() else 0f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "%d:%02d".format(minutes, secs),
            fontFamily = TerminalFontFamily,
            color = if (remainingSeconds == 0) NervRed else Color(0xFFFFB000),
            fontSize = 48.sp,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(12.dp))

        // Reuse the segmented bar visual language for the countdown itself
        SyncRatioBar(
            percent = (progress * 100).toInt(),
            modifier = Modifier.fillMaxWidth(0.7f)
        )

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(
                onClick = { isRunning = !isRunning },
                enabled = remainingSeconds > 0
            ) {
                Icon(
                    if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isRunning) "Pause" else "Start",
                    tint = if (remainingSeconds > 0) NervOrange else NervDim
                )
            }
            IconButton(onClick = {
                remainingSeconds = totalSeconds
                isRunning = false
            }) {
                Icon(Icons.Filled.Replay, contentDescription = "Reset", tint = NervDim)
            }
        }
    }
}
