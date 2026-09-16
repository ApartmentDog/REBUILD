package com.evyr.rebuild.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evyr.rebuild.ui.theme.EvaTextDim

/**
 * Small line chart for trends. Values are plotted oldest-to-newest, left to right.
 *
 * targetValue, when supplied, draws a dashed horizontal reference line -- used for
 * the goal weight on the weight chart. The y-range always includes it so the line
 * can't fall outside the visible area.
 *
 * Fewer than two points draws nothing: a single point isn't a trend, and drawing
 * one dot floating in an empty box reads like a bug.
 */
@Composable
fun MiniLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    lineColor: Color,
    targetValue: Float? = null,
    targetColor: Color = EvaTextDim
) {
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Box

        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val pad = 6.dp.toPx()
            val w = size.width - pad * 2
            val h = size.height - pad * 2

            val candidates = values + listOfNotNull(targetValue)
            val rawMin = candidates.minOrNull() ?: 0f
            val rawMax = candidates.maxOrNull() ?: 0f
            // Guard a flat series: a zero range would divide by zero and collapse
            // every point onto one line.
            val range = (rawMax - rawMin).takeIf { it > 0.0001f } ?: 1f
            val minV = rawMin - range * 0.1f
            val span = range * 1.2f

            fun xAt(i: Int) = pad + w * (i.toFloat() / (values.size - 1))
            fun yAt(v: Float) = pad + h - ((v - minV) / span) * h

            targetValue?.let { t ->
                val y = yAt(t)
                drawLine(
                    color = targetColor.copy(alpha = 0.6f),
                    start = Offset(pad, y),
                    end = Offset(pad + w, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                )
            }

            val path = Path().apply {
                values.forEachIndexed { i, v ->
                    if (i == 0) moveTo(xAt(i), yAt(v)) else lineTo(xAt(i), yAt(v))
                }
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))

            // Emphasise the latest reading so "where am I now" is readable at a glance.
            drawCircle(lineColor, radius = 3.dp.toPx(), center = Offset(xAt(values.lastIndex), yAt(values.last())))
        }
    }
}
