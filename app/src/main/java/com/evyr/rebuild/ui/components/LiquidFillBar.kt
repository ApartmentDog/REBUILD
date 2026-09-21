package com.evyr.rebuild.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.ui.theme.EvaTextDim
import com.evyr.rebuild.ui.theme.EvaTextPrimary
import com.evyr.rebuild.ui.theme.TerminalFontFamily
import kotlin.math.sin

private val LiquidColor = Color(0xFF3D8AFF)
private val LiquidColorDone = Color(0xFF3DFFB4)
private val LiquidTrack = Color(0xFF1A1F2A)
private val BubbleColor = Color(0x66FFFFFF)

/**
 * A single continuous bar for a volume-tracker directive (targetOz != null):
 * fills by percentage (loggedOz / targetOz) rather than discrete segments, with
 * a wobbling liquid surface and a few drifting bubbles while it's filling.
 * Purely visual -- separate from SegmentedTapBar, which stays untouched and
 * keeps its own tap-count trackers exactly as they are.
 */
@Composable
fun LiquidFillBar(
    loggedOz: Int,
    targetOz: Int,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val safeTarget = targetOz.coerceAtLeast(1)
    val fraction = (loggedOz.toFloat() / safeTarget).coerceIn(0f, 1f)
    val done = loggedOz >= safeTarget

    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 500),
        label = "liquidFraction"
    )

    val infinite = rememberInfiniteTransition(label = "liquidWobble")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )
    val bubblePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubblePhase"
    )

    // Fixed per-bubble seeds (horizontal position, phase offset, size) so bubbles
    // don't jump around between recompositions -- computed once, not re-randomized.
    val bubbleSeeds = remember { List(4) { i -> Triple(0.15f + i * 0.22f, i * 0.27f, 3f + (i % 2) * 2f) } }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (label != null) {
                Text(
                    label.uppercase(),
                    fontFamily = TerminalFontFamily,
                    color = if (done) LiquidColorDone else EvaTextPrimary,
                    fontSize = 13.sp
                )
            }
            Text(
                "$loggedOz / $safeTarget OZ",
                fontFamily = TerminalFontFamily,
                color = if (done) LiquidColorDone else EvaTextPrimary,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(LiquidTrack)
                .border(1.dp, EvaTextDim.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
        ) {
            if (animatedFraction <= 0f) return@Canvas

            val fillColor = if (done) LiquidColorDone else LiquidColor
            val waveAmplitude = if (done) 1.5f else 3f // near-still once the target's met
            val waveHeightPx = waveAmplitude.dp.toPx()
            val fillTopY = size.height * (1f - animatedFraction)

            val surfacePath = Path().apply {
                moveTo(0f, size.height)
                lineTo(0f, fillTopY)
                val step = size.width / 24f
                var x = 0f
                while (x <= size.width) {
                    val y = fillTopY + sin((x / size.width) * 4 * Math.PI + wavePhase).toFloat() * waveHeightPx
                    lineTo(x, y)
                    x += step
                }
                lineTo(size.width, fillTopY)
                lineTo(size.width, size.height)
                close()
            }

            clipPath(surfacePath) {
                drawRect(color = fillColor, size = size)

                if (!done) {
                    bubbleSeeds.forEach { (xFrac, phaseOffset, radiusDp) ->
                        val t = (bubblePhase + phaseOffset) % 1f
                        val bubbleY = size.height - (t * (size.height - fillTopY).coerceAtLeast(0f))
                        drawCircle(
                            color = BubbleColor,
                            radius = radiusDp.dp.toPx(),
                            center = Offset(size.width * xFrac, bubbleY)
                        )
                    }
                }
            }
        }
    }
}
