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
fun DailyQuoteScreen(mindset: List<Directive>, onContinue: () -> Unit) {
    // remember with no key: the pick is made once when the screen appears and
    // survives recomposition, so the line doesn't shuffle underneath you.
    val quote = remember { mindset.randomOrNull()?.title }

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
