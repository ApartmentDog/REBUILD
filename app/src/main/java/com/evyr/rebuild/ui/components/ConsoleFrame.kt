package com.evyr.rebuild.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.ui.theme.*

/**
 * Beige 90s-hardware console housing wrapping the whole app -- the phone
 * screen itself becomes a chunky plastic bezel with a recessed dark screen,
 * status LEDs, and vents, with the actual app content rendered as what's
 * "displayed" inside it. Applied once around the whole navigation tree
 * (see MainActivity) rather than per-screen.
 *
 * This intentionally eats a few dp of edge padding on every screen for the
 * bezel border -- screens should not assume they own the full device width.
 */
@Composable
fun ConsoleFrame(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(ConsoleBeigeLight, ConsoleBeige, ConsoleBeigeDark)
                )
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "REBUILD OS",
                fontFamily = TerminalFontFamily,
                color = ConsoleLabel,
                fontSize = 11.sp,
                letterSpacing = 3.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusLight(color = EvaGreen)
                StatusLight(color = ConsoleVent)
            }
        }

        // Single Box instead of the previous nested Box-inside-Box (one for
        // background/padding, an inner one for clip+background+content) --
        // collapsing that into one modifier chain removes a layer of constraint
        // propagation between weight(1f) and content(). Unconfirmed whether this
        // was the actual cause of the "everything below Sync Ratio silently
        // doesn't render, no crash, no tap response" bug -- reasoned toward
        // rather than reproduced, since there's no device/emulator available
        // here to verify. If this doesn't fix it, the next thing to try is
        // removing weight(1f) entirely in favor of an explicit Modifier.height()
        // computed from the header/footer's measured height, or removing
        // ConsoleFrame's wrapping altogether to isolate it from screen content.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .background(ConsoleBezel, RoundedCornerShape(14.dp))
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EvaBackground)
        ) {
            content()
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(5) { i ->
                if (i > 0) Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .width(26.dp)
                        .height(4.dp)
                        .background(ConsoleVent, RoundedCornerShape(2.dp))
                )
            }
        }

        Text(
            "MODEL RBLD-01 · NERV COMPATIBLE",
            fontFamily = TerminalFontFamily,
            color = ConsoleLabel,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusLight(color: Color) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .background(color, CircleShape)
    )
}
