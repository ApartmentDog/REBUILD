package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.tarot.TarotDeck
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reached via the MIRROR cheat code (or the LOG button inside the tarot pull
 * screen itself). Read-only history of every SWORD reading, most recent first.
 * Cards drawn together in a spread are grouped under one entry with the
 * question asked; pre-spread pulls (readingId 0) show as single-card entries.
 */
@Composable
fun TarotLogScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val pulls by viewModel.tarotPulls.collectAsState()
    val readings = remember(pulls) {
        pulls
            .groupBy { if (it.readingId != 0L) "r${it.readingId}" else "p${it.id}" }
            .values
            .map { group -> group.sortedBy { it.positionIndex } }
    }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().background(EvaBackground)) {
        HazardStripe(height = 6.dp, colorA = NervMagenta, colorB = EvaOutline)
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = EvaTextPrimary)
            }
            Text(
                "PULL LOG",
                fontFamily = TerminalFontFamily, color = NervMagenta, fontSize = 14.sp, letterSpacing = 2.sp
            )
        }
        HazardStripe(height = 6.dp, colorA = NervMagenta, colorB = EvaOutline)

        if (pulls.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "NO PULLS YET",
                    fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 12.sp, letterSpacing = 1.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                items(readings) { reading ->
                    val first = reading.first()
                    val multi = reading.size > 1
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(EvaPanelBg, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            dateFormat.format(Date(first.pulledAtEpochMs)) +
                                if (multi) "  ·  " + TarotDeck.spreadById(first.spreadId).name else "",
                            fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp
                        )
                        if (first.question.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Q: " + first.question,
                                fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        reading.forEach { pull ->
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (multi && pull.positionLabel.isNotBlank()) {
                                        Text(
                                            pull.positionLabel.uppercase(),
                                            fontFamily = TerminalFontFamily, color = NervOrange,
                                            fontSize = 9.sp, letterSpacing = 1.sp
                                        )
                                    }
                                    Text(
                                        pull.cardName + if (pull.reversed) " (Reversed)" else "",
                                        fontFamily = TerminalFontFamily,
                                        color = if (pull.reversed) NervRed else NervGreen,
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                    pull.arcana,
                                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp, letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
