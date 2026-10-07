package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.DailyQuoteTracker
import com.evyr.rebuild.data.Directive
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*

/**
 * Shown once on the first unlock of each day: a single mindset line, picked at
 * random from the Mindset directives, as a full-screen card. Tap through to
 * continue to the Dashboard.
 *
 * If there are no mindset entries, the caller should skip this screen entirely
 * rather than show an empty card -- see MainActivity's navigation.
 */
@Composable
fun DailyQuoteScreen(mindset: List<Directive>, quoteTracker: DailyQuoteTracker, onContinue: () -> Unit) {
    // Keyed on mindset.isNotEmpty(): the caller already guards against an
    // empty list at navigation time, but this screen's own `mindset` param
    // comes from a separately-collected StateFlow that can still be briefly
    // empty on the very first composition right after cold start -- the same
    // stale-empty-read problem MainActivity's mindsetItemsNow() exists to
    // avoid, just one level down. remember with NO key locked the random
    // pick onto whatever that first, possibly-empty frame saw, forever, so
    // a real list arriving a moment later changed nothing: the quote stayed
    // blank for the rest of this screen's lifetime. Keying on isNotEmpty()
    // re-picks exactly once, the first time real data shows up, and never
    // again after that -- so the line still doesn't shuffle underneath you
    // once it's actually picked.
    //
    // Avoids repeating the last couple of days' picks: with a small mindset
    // pool, a plain random pick repeats often enough to be noticeable. The
    // excluded set only applies when the pool is big enough to spare it --
    // with 1 or 2 lines total, excluding history would leave nothing to pick
    // from, so it falls back to the full list rather than show nothing.
    val directive = remember(mindset.isNotEmpty()) {
        val recent = quoteTracker.recentlyShownIds().toSet()
        val eligible = mindset.filterNot { it.id in recent }
        (eligible.ifEmpty { mindset }).randomOrNull()
            ?.also { quoteTracker.recordShown(it.id) }
    }
    val quote = directive?.title

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EvaBackground)
            .clickable { onContinue() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HazardStripe(height = 8.dp)

        Spacer(Modifier.weight(1f))

        Text(
            text = "MINDSET",
            fontFamily = TerminalFontFamily,
            color = EvaYellow,
            fontSize = 12.sp,
            letterSpacing = 4.sp
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = quote?.uppercase() ?: "",
            fontFamily = TerminalFontFamily,
            color = EvaTextPrimary,
            fontSize = 22.sp,
            lineHeight = 32.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(containerColor = EvaRed, contentColor = EvaTextPrimary)
        ) {
            Text("BEGIN", fontFamily = TerminalFontFamily, fontSize = 13.sp, letterSpacing = 2.sp)
        }

        Spacer(Modifier.weight(1f))

        HazardStripe(height = 8.dp)
    }
}
