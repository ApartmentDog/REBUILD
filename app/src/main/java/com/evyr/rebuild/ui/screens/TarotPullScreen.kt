package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.tarot.TarotCard
import com.evyr.rebuild.tarot.TarotDeck
import com.evyr.rebuild.tarot.TarotSpread
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*

/**
 * Reached via the SWORD cheat code. Optionally takes a question, then draws
 * a spread (single card, three-card, Celtic Cross, ...) from the full 78-card
 * deck without repeats, shows each card's ASCII glyph and upright/reversed
 * meaning under its position label, and logs the reading (the log itself is
 * viewed separately via MIRROR). Unlimited pulls by design --
 * this is a reflection prompt, not a fortune-telling mechanic gated by a
 * cooldown.
 */
@Composable
fun TarotPullScreen(viewModel: MainViewModel, onBack: () -> Unit, onOpenLog: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var spreadId by remember { mutableStateOf(TarotDeck.spreads.first().id) }
    var drawn by remember { mutableStateOf<List<Pair<TarotCard, Boolean>>>(emptyList()) }
    // The spread/question the shown cards were drawn with -- kept separate so
    // changing the picker after a draw doesn't relabel the cards on screen.
    var drawnSpread by remember { mutableStateOf<TarotSpread?>(null) }
    var drawnQuestion by remember { mutableStateOf("") }
    val spread = TarotDeck.spreadById(spreadId)
    var showExplainer by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EvaBackground)
    ) {
        HazardStripe(height = 6.dp, colorA = NervMagenta, colorB = EvaOutline)
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = EvaTextPrimary)
            }
            Text(
                "///",
                fontFamily = TerminalFontFamily, color = NervMagenta, fontSize = 16.sp, letterSpacing = 4.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showExplainer = true }) {
                Text("?", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 14.sp)
            }
            TextButton(onClick = onOpenLog) {
                Text("LOG", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 11.sp)
            }
        }
        HazardStripe(height = 6.dp, colorA = NervMagenta, colorB = EvaOutline)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Ask a question (optional)",
                        fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp
                    )
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = TerminalFontFamily, fontSize = 13.sp
                ),
                minLines = 2,
                maxLines = 4,
                colors = rebuildTextFieldColors()
            )

            Spacer(Modifier.height(12.dp))
            TarotDeck.spreads.forEach { option ->
                val selected = option.id == spreadId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .background(EvaPanelBg, RoundedCornerShape(4.dp))
                        .border(
                            1.dp,
                            if (selected) NervOrange else EvaPanelBgLight,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { spreadId = option.id }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        option.name,
                        fontFamily = TerminalFontFamily,
                        color = if (selected) NervOrange else EvaTextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${option.positions.size}",
                        fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            val shownSpread = drawnSpread
            if (drawn.isEmpty() || shownSpread == null) {
                Spacer(Modifier.height(20.dp))
                Text(
                    if (spread.positions.size == 1) "78 CARDS. DRAW ONE."
                    else "78 CARDS. DRAW ${spread.positions.size}.",
                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp, letterSpacing = 2.sp
                )
            } else {
                if (drawnQuestion.isNotBlank()) {
                    Text(
                        "Q: " + drawnQuestion,
                        fontFamily = TerminalFontFamily, color = NervDim, fontSize = 12.sp,
                        lineHeight = 17.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                }
                drawn.forEachIndexed { i, (card, reversed) ->
                    if (i > 0) Spacer(Modifier.height(28.dp))
                    if (shownSpread.positions.size > 1) {
                        Text(
                            shownSpread.positions.getOrElse(i) { "" }.uppercase(),
                            fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 11.sp,
                            letterSpacing = 2.sp
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        card.arcana.name,
                        fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp, letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        card.name.uppercase(),
                        fontFamily = TerminalFontFamily, color = NervMagenta, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                    )
                    if (reversed) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "REVERSED",
                            fontFamily = TerminalFontFamily, color = NervRed, fontSize = 10.sp, letterSpacing = 2.sp
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(EvaPanelBg, RoundedCornerShape(6.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            card.glyph,
                            fontFamily = TerminalFontFamily,
                            color = NervGreen,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            modifier = if (reversed) Modifier.rotate(180f) else Modifier
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        if (reversed) card.reversedMeaning else card.uprightMeaning,
                        fontFamily = TerminalFontFamily,
                        color = EvaTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(
                onClick = {
                    val cards = TarotDeck.pull(spread.positions.size)
                    drawn = cards
                    drawnSpread = spread
                    drawnQuestion = question.trim()
                    viewModel.logTarotReading(spread, question, cards)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
            ) {
                Text(
                    when {
                        drawn.isNotEmpty() -> "DRAW AGAIN"
                        spread.positions.size == 1 -> "DRAW A CARD"
                        else -> "DRAW ${spread.positions.size} CARDS"
                    },
                    fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showExplainer) {
        AlertDialog(
            onDismissRequest = { showExplainer = false },
            containerColor = NervPanel,
            title = { Text("WHAT THIS IS", fontFamily = TerminalFontFamily, color = NervOrange) },
            text = {
                Text(
                    "This isn't fortune-telling. A tarot pull hands you a random prompt and a bit of open-ended language, and your job is to see what it makes you think of.\n\n" +
                        "The card doesn't know anything about your life. Whatever meaning it has is the meaning you bring to it -- which is exactly why it can be a useful nudge: it's a low-stakes way to sit with a question for a minute instead of scrolling past it.\n\n" +
                        "Reversed just means the same card's theme showing up in a blocked, delayed, or inward-turned way rather than its usual direction. Neither orientation is \"bad.\"\n\n" +
                        "The question box is optional. Writing one down just gives the pull something to push against. Spreads give each card a role (past, present, obstacle...) so the same random draw reads as a small story instead of isolated prompts.",
                    fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 12.sp, lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showExplainer = false }) {
                    Text("GOT IT", fontFamily = TerminalFontFamily, color = NervGreen)
                }
            }
        )
    }
}
