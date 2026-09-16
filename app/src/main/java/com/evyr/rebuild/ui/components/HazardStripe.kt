package com.evyr.rebuild.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evyr.rebuild.ui.theme.EvaOutline
import com.evyr.rebuild.ui.theme.EvaYellow

/**
 * Two-tone diagonal hazard bar (yellow/black), matching real hazard-stripe
 * tape and the "NERV ONLY" / "CAUTION" banding reference sheets.
 *
 * Two modes:
 *  - color = null (default): proper two-tone yellow/black hazard stripe.
 *  - color = <a color>: legacy single-tone diagonal lines on transparent,
 *    kept so the ~17 existing call sites across every screen (all written
 *    against the old single-color API) keep compiling without a risky
 *    mass find-and-replace. New call sites should prefer leaving color null.
 */
@Composable
fun HazardStripe(
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    color: Color? = null,
    colorA: Color = EvaYellow,
    colorB: Color = EvaOutline
) {
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        // stripeW must never be able to produce a non-advancing loop. If this
        // Canvas is ever measured at zero size (e.g. during an intermediate
        // layout pass before its parent has finished sizing), size.width/height
        // can be 0, and a naive while loop keyed off screen-space math can spin
        // forever with no exception -- no crash, no ANR dialog, just the render
        // thread stuck and everything after this composable in the tree never
        // getting a chance to draw. This was a real bug: HazardStripe used
        // unconditionally inside EvaPanel's content was the actual cause of
        // 'Dashboard renders the header and Sync Ratio panel, then nothing at
        // all below it, no crash, no tap response' -- confirmed by isolating
        // ConsoleFrame (not the cause), then a hardcoded diagnostic marker
        // placed after the tab row (never rendered, meaning composition
        // never got past this point at all).
        val stripeW = (16.dp.toPx()).coerceAtLeast(1f)
        val skew = size.height
        if (color != null) {
            var x = -stripeW
            var guard = 0
            while (x < size.width && guard < 2000) {
                drawLine(color, Offset(x, size.height), Offset(x + stripeW, 0f), strokeWidth = stripeW / 2)
                x += stripeW * 1.6f
                guard++
            }
        } else {
            clipRect {
                var x = -stripeW * 2
                var toggle = true
                var guard = 0
                while (x < size.width + stripeW * 2 && guard < 2000) {
                    val path = Path().apply {
                        moveTo(x, size.height)
                        lineTo(x + skew, 0f)
                        lineTo(x + skew + stripeW, 0f)
                        lineTo(x + stripeW, size.height)
                        close()
                    }
                    drawPath(path, if (toggle) colorA else colorB)
                    toggle = !toggle
                    x += stripeW
                    guard++
                }
            }
        }
    }
}
