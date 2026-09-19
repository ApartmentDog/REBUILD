package com.evyr.rebuild.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.ui.theme.EvaTextDim
import com.evyr.rebuild.ui.theme.EvaTextPrimary
import com.evyr.rebuild.ui.theme.TerminalFontFamily

private val SegmentFilled = Color(0xFF3D8AFF)
private val SegmentEmpty = Color(0xFF1A1F2A)

/**
 * Segmented bar for a directive with requiredScans > 1: one segment per required
 * tap, tapping the bar advances the count by one. Built for the water tracker
 * specifically -- the NFC tag on a vacuum-insulated steel bottle reads
 * unreliably because metal shields the tag's antenna, so a direct-tap UI
 * sidesteps that hardware problem entirely rather than fighting it.
 *
 * Uses the same AppRepository.incrementScanProgress path as an NFC scan, so both
 * obey identical rules (ignores taps once the target's met, marks the directive
 * complete at target). This is a second front door onto the same logic, not a
 * separate counting mechanism.
 */
@Composable
fun SegmentedTapBar(
    filled: Int,
    total: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
    compact: Boolean = false,
    onTap: () -> Unit
) {
    val safeTotal = total.coerceAtLeast(1)
    val safeFilled = filled.coerceIn(0, safeTotal)
    val done = safeFilled >= safeTotal

    if (compact) {
        // One line per tracker: name, count, and the bar sharing a row. The
        // roomy version stacks a card, a title, a label row and the bar at
        // roughly 100dp each, so five trackers push everything else off screen
        // before the actual checklist starts.
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clickable(enabled = !done, onClick = onTap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (label != null) {
                Text(
                    label.uppercase(),
                    fontFamily = TerminalFontFamily,
                    color = if (done) SegmentFilled else EvaTextPrimary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.42f)
                )
            }
            Row(
                modifier = Modifier.weight(0.42f).height(12.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                repeat(safeTotal) { i ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i < safeFilled) SegmentFilled else SegmentEmpty)
                    )
                }
            }
            Text(
                "$safeFilled/$safeTotal",
                fontFamily = TerminalFontFamily,
                color = if (done) SegmentFilled else EvaTextDim,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        return
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "$safeFilled / $safeTotal",
                fontFamily = TerminalFontFamily,
                color = if (done) SegmentFilled else EvaTextPrimary,
                fontSize = 13.sp
            )
            if (!done) {
                Text(
                    "TAP TO LOG",
                    fontFamily = TerminalFontFamily,
                    color = EvaTextDim,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clickable(enabled = !done, onClick = onTap),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(safeTotal) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i < safeFilled) SegmentFilled else SegmentEmpty)
                )
            }
        }
    }
}
