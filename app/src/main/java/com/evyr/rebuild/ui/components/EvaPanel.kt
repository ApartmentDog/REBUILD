package com.evyr.rebuild.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.evyr.rebuild.ui.theme.EvaOutline
import com.evyr.rebuild.ui.theme.EvaPanelBg
import com.evyr.rebuild.ui.theme.EvaPanelShadow

/**
 * Flat-color card with a thick black border and one hard-edged diagonal
 * shadow wedge -- the cel-shading trick from the plugsuit reference (a flat
 * darker shape, not a gradient or blur).
 *
 * IMPORTANT -- why the wedge is drawn with drawBehind and not a Canvas child:
 *
 * The original version put a `Canvas(Modifier.fillMaxSize())` inside this Box
 * as a sibling of the content. In a Box, fillMaxSize means "expand to the
 * maximum the incoming constraints allow" -- and the incoming maxHeight here
 * is however much vertical space is left in the parent Column. So the Canvas
 * expanded to the full remaining screen height, which forced the Box to that
 * height too. In a Column, siblings measured after a child that eats all the
 * remaining space get zero height: invisible AND untouchable, with no crash
 * and nothing in logcat.
 *
 * That is exactly what broke the Dashboard -- the tab row, every directive
 * card, the Mindset panel, the nav buttons and the version footer were all
 * still being composed, just allocated zero height beneath a panel that had
 * swallowed the screen.
 *
 * drawBehind is a draw-only modifier: it runs during the draw phase and can
 * never influence measurement. With it, this Box has exactly one child
 * (content) and can only ever wrap that content's height, which is the whole
 * intent. matchParentSize() would also work, but it relies on a BoxScope
 * rule that is easy to break again by accident; drawBehind makes it
 * structurally impossible for the decoration to affect layout.
 */
@Composable
fun EvaPanel(
    modifier: Modifier = Modifier,
    borderWidth: Dp = 3.dp,
    backgroundColor: Color = EvaPanelBg,
    shadowColor: Color = EvaPanelShadow,
    shadowCorner: EvaPanelShadowCorner = EvaPanelShadowCorner.TOP_RIGHT,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .background(backgroundColor)
            .drawBehind {
                val w = size.width * 0.32f
                val path = Path().apply {
                    when (shadowCorner) {
                        EvaPanelShadowCorner.TOP_RIGHT -> {
                            moveTo(size.width - w, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height)
                            lineTo(size.width * 0.55f, size.height)
                            close()
                        }
                        EvaPanelShadowCorner.BOTTOM_RIGHT -> {
                            moveTo(size.width, size.height * 0.4f)
                            lineTo(size.width, size.height)
                            lineTo(size.width * 0.55f, size.height)
                            close()
                        }
                    }
                }
                drawPath(path, shadowColor)
            }
            .border(borderWidth, EvaOutline)
    ) {
        content()
    }
}

enum class EvaPanelShadowCorner { TOP_RIGHT, BOTTOM_RIGHT }
