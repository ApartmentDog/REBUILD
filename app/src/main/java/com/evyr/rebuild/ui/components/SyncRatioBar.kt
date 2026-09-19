package com.evyr.rebuild.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.ui.theme.EvaBackground
import com.evyr.rebuild.ui.theme.EvaGreen
import com.evyr.rebuild.ui.theme.EvaOutline
import com.evyr.rebuild.ui.theme.EvaRed
import com.evyr.rebuild.ui.theme.EvaYellow
import com.evyr.rebuild.ui.theme.TerminalFontFamily
import kotlin.math.cos
import kotlin.math.sin

private val WaveColors = listOf(
    Color(0xFFFF3B5C), // red
    Color(0xFFFF9F1C), // orange
    Color(0xFF3DD6C8), // teal
    Color(0xFF3D8AFF), // blue
    Color(0xFFB53DFF)  // violet
)

/**
 * Full-panel oscilloscope-style sync readout, replacing the flat "0% / CRITICAL"
 * box. Multiple colored sine traces at staggered phase interweave the way the real
 * EVA sync graph does, continuously animating rather than sitting static, with a
 * tick-mark ruler across the bottom and the percentage overlaid on top.
 */
@Composable
fun SyncRatioBar(
    percent: Int,
    modifier: Modifier = Modifier
) {
    val animatedPercent by animateFloatAsState(
        targetValue = percent.toFloat(),
        animationSpec = tween(durationMillis = 600),
        label = "syncRatioAnim"
    )

    val statusColor = when {
        percent >= 60 -> EvaGreen
        percent >= 30 -> EvaYellow
        else -> EvaRed
    }
    val statusText = when {
        percent >= 60 -> "NOMINAL"
        percent >= 30 -> "CAUTION"
        else -> "CRITICAL"
    }

    // Continuous phase drift -- runs forever regardless of percent, so the panel
    // reads as alive even sitting at a fixed value.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        phase.animateTo(
            1f,
            animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing))
        )
    }

    // Higher sync pulls the traces into tighter alignment (near-unison, like a
    // healthy sync graph); lower sync spreads them apart and desyncs their phase.
    val spread = (100f - animatedPercent) / 100f

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .background(EvaBackground)
            .border(3.dp, EvaOutline)
    ) {
        // 150dp was most of a phone screen for a readout that's one number and
        // a trace. 104 keeps the oscilloscope legible without pushing the
        // checklist below the fold.
        Canvas(modifier = Modifier.fillMaxWidth().height(104.dp)) {
            val w = size.width
            val h = size.height
            val midY = h * 0.42f
            val amp = h * 0.3f

            // Crosshair ticks along a center horizontal axis, matching the "+"
            // markers in the reference readout.
            val tick = 4.dp.toPx()
            for (i in 0..10) {
                val x = w * i / 10
                drawLine(EvaOutline.copy(alpha = 0.5f), Offset(x - tick, midY), Offset(x + tick, midY), 1f)
            }
            drawLine(EvaOutline.copy(alpha = 0.25f), Offset(0f, midY), Offset(w, midY), 1f)

            fun wave(color: Color, freq: Float, phaseOffset: Float, ampScale: Float, strokeWidth: Float, alpha: Float) {
                val path = Path()
                var x = 0f
                val step = 3f
                while (x <= w) {
                    val t = (x / w) * freq * 2 * Math.PI + (phase.value + phaseOffset) * 2 * Math.PI
                    val y = midY + sin(t).toFloat() * amp * ampScale
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += step
                }
                drawPath(path, color.copy(alpha = alpha), style = Stroke(width = strokeWidth))
            }

            // Each color gets a cluster of 3 traces at slightly different phase and
            // amplitude, the way the reference image layers many strands per color
            // band rather than one clean line per color.
            //
            // spread now scales the base offset directly rather than adding a small
            // nudge on top of a dominant fixed stagger -- previously the fixed
            // stagger (i * 0.15f) dwarfed spread's contribution, so the traces
            // barely visibly converged even at 100% sync. Now full sync (spread=0)
            // collapses every cluster toward the same phase -- reading as five
            // bands moving in near-unison -- and low sync spreads them across a
            // full stagger, matching what the percent is supposed to communicate.
            WaveColors.forEachIndexed { i, color ->
                val basePhase = i * 0.15f * spread
                val baseFreq = 2.2f + i * 0.35f
                for (j in 0 until 3) {
                    val jitter = j * 0.02f * spread
                    wave(
                        color = color,
                        freq = baseFreq,
                        phaseOffset = basePhase + jitter,
                        ampScale = 0.85f + j * 0.08f,
                        strokeWidth = 1.dp.toPx(),
                        alpha = 0.55f - j * 0.12f
                    )
                }
            }

            // Tick-mark ruler across the bottom, numbered like the reference.
            val rulerY = h - 14.dp.toPx()
            drawLine(EvaOutline.copy(alpha = 0.6f), Offset(0f, rulerY), Offset(w, rulerY), 1.dp.toPx())
            for (i in -5..5) {
                val x = w / 2 + (w / 2) * (i / 5f)
                val tickH = if (i == 0) 8.dp.toPx() else 5.dp.toPx()
                drawLine(EvaOutline.copy(alpha = 0.6f), Offset(x, rulerY - tickH), Offset(x, rulerY + tickH), 1.dp.toPx())
            }
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = "SYNC RATIO",
                fontFamily = TerminalFontFamily,
                color = EvaYellow,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
        }

        Column(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopEnd)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.End
        ) {
            Text(
                text = "$percent%",
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                fontSize = 26.sp
            )
            Text(
                text = statusText,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
        }
    }
}
