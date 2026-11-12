package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Extracted from SettingsScreen.kt during the cleanup pass. No behavior change,
 * purely moved.
 */
@Composable
fun SyncRatioDebugPanel(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    var breakdown by remember { mutableStateOf<com.evyr.rebuild.data.AppRepository.SyncRatioBreakdown?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text(
            "REFRESH",
            fontFamily = TerminalFontFamily,
            color = NervOrange,
            fontSize = 11.sp,
            modifier = Modifier
                .clickable { scope.launch { breakdown = viewModel.syncRatioBreakdown() } }
                .padding(vertical = 4.dp)
        )
        breakdown?.let { b ->
            Spacer(Modifier.height(6.dp))
            val lines = listOf(
                "DUE TODAY: ${b.dueCount}",
                "TOTAL WEIGHT: ${b.totalWeight}",
                "DONE WEIGHT: ${b.doneWeight}",
                "COMPLETION SCORE: ${"%.3f".format(b.completionScore)}",
                "STANDING COUNT: ${b.standingCount}",
                "STREAKS HELD: ${b.heldCount}",
                "STREAK SCORE: ${"%.3f".format(b.streakScore)}",
                "🔥 BOOST: +${"%.1f".format(b.hardBoost)}",
                "FINAL: ${b.finalPercent}%"
            )
            lines.forEach {
                Text(it, fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 11.sp)
            }
        }
    }
}

