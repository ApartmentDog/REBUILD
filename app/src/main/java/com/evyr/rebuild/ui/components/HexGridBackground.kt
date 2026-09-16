package com.evyr.rebuild.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.evyr.rebuild.ui.theme.NervHexLine
import kotlin.math.sqrt

/**
 * Subtle hexagon grid texture, matching the NERV logo background reference sheets.
 * Drawn low-alpha so it reads as texture, not a competing focal element -- content
 * placed inside this Box sits on top of it normally.
 *
 * Usage: wrap a screen's outer Box/Column background with this instead of a flat
 * Modifier.background(NervBackground) call, e.g.:
 *   HexGridBackground(modifier = Modifier.fillMaxSize()) { /* screen content */ }
 */
@Composable
fun HexGridBackground(
    modifier: Modifier = Modifier,
    hexRadiusDp: Int = 28,
    lineAlpha: Float = 0.08f,
    content: @Composable () -> Unit
) {
    // Drawn with drawBehind rather than a Canvas child: a Canvas(fillMaxSize())
    // sibling participates in measurement and expands the Box to the maximum the
    // incoming constraints allow, which silently starves later siblings of height
    // if this is ever given a wrap-height modifier. (That exact pattern in
    // EvaPanel is what broke the Dashboard.) drawBehind runs in the draw phase
    // only and cannot affect layout, so this is safe with any modifier.
    Box(
        modifier = modifier.drawBehind {
            val r = (hexRadiusDp.dp.toPx()).coerceAtLeast(1f)
            val hexHeight = r * 2
            val hexWidth = (sqrt(3f) * r).coerceAtLeast(1f)
            val vertSpacing = (hexHeight * 0.75f).coerceAtLeast(1f)

            var row = 0
            var y = -hexHeight
            while (y < size.height + hexHeight) {
                val xOffset = if (row % 2 == 0) 0f else hexWidth / 2
                var x = -hexWidth + xOffset
                while (x < size.width + hexWidth) {
                    drawHexagon(x, y, r, NervHexLine.copy(alpha = lineAlpha))
                    x += hexWidth
                }
                y += vertSpacing
                row++
            }
        }
    ) {
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHexagon(
    cx: Float, cy: Float, radius: Float, color: androidx.compose.ui.graphics.Color
) {
    val path = Path()
    for (i in 0..5) {
        val angle = Math.PI / 180 * (60 * i - 30)
        val x = cx + radius * kotlin.math.cos(angle).toFloat()
        val y = cy + radius * kotlin.math.sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(width = 1.dp.toPx()))
}
