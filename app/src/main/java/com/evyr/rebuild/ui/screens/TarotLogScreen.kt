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
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reached via the MIRROR cheat code (or the LOG button inside the tarot pull
 * screen itself). Read-only history of every SWORD pull, most recent first.
 */
@Composable
fun TarotLogScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val pulls by viewModel.tarotPulls.collectAsState()
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
                items(pulls) { pull ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(EvaPanelBg, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                pull.cardName + if (pull.reversed) " (Reversed)" else "",
                                fontFamily = TerminalFontFamily,
                                color = if (pull.reversed) NervRed else NervGreen,
                                fontSize = 13.sp
                            )
                            Text(
                                dateFormat.format(Date(pull.pulledAtEpochMs)),
                                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp
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
