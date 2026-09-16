package com.evyr.rebuild.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cel-animation-style outlined text: a black stroke drawn behind a solid
 * fill, same idea as CSS's `paint-order: stroke fill` + `-webkit-text-stroke`.
 * Compose's Text composable has no native stroke+fill support, so this drops
 * to a Canvas and uses two android.graphics.Paint passes (STROKE then FILL)
 * on the same text -- the stroke paints only at the letterform edge this way,
 * unlike a naive 4-direction drop-shadow trick which can leak visible seams
 * through letter counters (that was tried first in the HTML mockup and
 * produced exactly that bug -- visible lines inside the "O"s and "8").
 *
 * Sizing note: since this bypasses Compose's Text layout, width/height must
 * be supplied via the modifier (e.g. Modifier.fillMaxWidth().height(60.dp))
 * rather than the text intrinsically sizing its container.
 */
@Composable
fun CelOutlineText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    outlineColor: Color = Color.Black,
    strokeWidthDp: Float = 3f,
    letterSpacingSp: Float = 0f
) {
    val fillArgb = color.toArgb()
    val outlineArgb = outlineColor.toArgb()

    Canvas(modifier = modifier) {
        val strokePx = strokeWidthDp.dp.toPx()
        val fontSizePx = fontSize.toPx()
        val letterSpacingPx = letterSpacingSp.sp.toPx()

        val strokePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            this.strokeWidth = strokePx
            strokeJoin = android.graphics.Paint.Join.ROUND
            textSize = fontSizePx
            this.color = outlineArgb
            isFakeBoldText = true
            if (letterSpacingPx != 0f) this.letterSpacing = letterSpacingPx / fontSizePx
        }
        val fillPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
            textSize = fontSizePx
            this.color = fillArgb
            isFakeBoldText = true
            if (letterSpacingPx != 0f) this.letterSpacing = letterSpacingPx / fontSizePx
        }

        val baseline = size.height / 2f - (strokePaint.ascent() + strokePaint.descent()) / 2f
        drawContext.canvas.nativeCanvas.drawText(text, 0f, baseline, strokePaint)
        drawContext.canvas.nativeCanvas.drawText(text, 0f, baseline, fillPaint)
    }
}
