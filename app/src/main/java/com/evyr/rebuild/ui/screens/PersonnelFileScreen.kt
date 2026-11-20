package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.ui.MainViewModel
import com.evyr.rebuild.ui.components.HazardStripe
import com.evyr.rebuild.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The pilot's file. Sealed until the first real milestone (RecordVault).
 * A service record built from everything already logged, plus
 * commendations -- some listed with progress, some classified until earned.
 */
@Composable
fun PersonnelFileScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    var record by remember { mutableStateOf<AppRepository.ServiceRecord?>(null) }
    LaunchedEffect(Unit) { record = viewModel.serviceRecord() }

    Column(modifier = Modifier.fillMaxSize().background(EvaBackground)) {
        HazardStripe(height = 6.dp, colorA = EvaRed, colorB = EvaOutline)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = EvaTextPrimary)
            }
            Column {
                Text("PERSONNEL FILE", fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 16.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                Text("NERV · CLASSIFIED · EYES ONLY", fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 11.sp, letterSpacing = 2.sp)
            }
        }
        HazardStripe(height = 6.dp, colorA = EvaRed, colorB = EvaOutline)

        val r = record
        if (r == null) {
            Text("RETRIEVING FILE…", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
        } else {
            val commendations = commendationsFor(r)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { IdCard(r) }
                item { StatGrid(r) }
                item {
                    FileSection("GRADUATES")
                    if (r.graduates.isEmpty()) {
                        Text("None yet.", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 12.sp)
                    }
                    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    r.graduates.forEach { (title, at) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text("★ ${title.uppercase()}", fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(fmt.format(Date(at)).uppercase(), fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    FileSection("COMMENDATIONS · ${commendations.count { it.earned }}/${commendations.size}")
                }
                items(commendations) { c -> CommendationRow(c) }
            }
        }
    }
}

@Composable
private fun IdCard(r: AppRepository.ServiceRecord) {
    val since = r.firstDate?.let { d ->
        runCatching {
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                .format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d)!!)
        }.getOrNull()
    }
    Column(modifier = Modifier.fillMaxWidth().border(2.dp, EvaRed).background(EvaPanelBg)) {
        Box(modifier = Modifier.fillMaxWidth().background(EvaRed).padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text("SPECIAL AGENCY NERV — PILOT IDENTIFICATION", fontFamily = TerminalFontFamily, color = Color.White, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(72.dp).border(2.dp, EvaYellow).background(EvaBackground),
                contentAlignment = Alignment.Center
            ) {
                Text("01", fontFamily = TerminalFontFamily, color = EvaYellow, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("EVYR", fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text("DESIGNATION: UNIT 01 PILOT", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp)
                Text("ACTIVE SINCE: ${since?.uppercase() ?: "—"}", fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp)
                Text("DAYS ON RECORD: ${r.daysActive}", fontFamily = TerminalFontFamily, color = EvaGreen, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StatGrid(r: AppRepository.ServiceRecord) {
    val cells = listOf(
        "ACTIONS LOGGED" to r.totalCompletions.toString(),
        "BEST SYNC" to "${r.bestSync}%",
        "LONGEST RUN" to "${r.longestRun} DAYS",
        "URGES RIDDEN OUT" to r.urgesRidden.toString(),
        "ANGELS DOWN" to r.anomaliesCleared.toString(),
        "🔥 CLEARED" to r.hardCleared.toString()
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f).background(EvaPanelBg).padding(10.dp)) {
                        Text(label, fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp, letterSpacing = 1.sp)
                        Text(value, fontFamily = TerminalFontFamily, color = EvaTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FileSection(title: String) {
    Text(title, fontFamily = TerminalFontFamily, color = EvaRed, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
}

private data class Commendation(
    val name: String,
    val detail: String,
    val earned: Boolean,
    val progress: String? = null,
    val classified: Boolean = false
)

private fun commendationsFor(r: AppRepository.ServiceRecord): List<Commendation> {
    fun prog(n: Int, of: Int) = "${n.coerceAtMost(of)}/$of"
    return listOf(
        Commendation("FIRST CONTACT", "Logged a first action", r.totalCompletions >= 1),
        Commendation("CENTURION", "100 actions logged", r.totalCompletions >= 100, prog(r.totalCompletions, 100)),
        Commendation("THOUSAND-FOLD", "1,000 actions logged", r.totalCompletions >= 1000, prog(r.totalCompletions, 1000)),
        Commendation("LONG HAUL", "100 days on record", r.daysActive >= 100, prog(r.daysActive, 100)),
        Commendation("FULL SYNC", "Closed a day at 100%", r.bestSync >= 100),
        Commendation("BERSERK", "Pushed a day past 100%", r.bestSync > 100, classified = true),
        Commendation("FORTNIGHT", "One habit done 14 days straight", r.perfectFortnight),
        Commendation("IRON WILL", "A 30-day run on a standing goal", r.longestRun >= 30, prog(r.longestRun, 30)),
        Commendation("UNBROKEN", "A 100-day run on a standing goal", r.longestRun >= 100, prog(r.longestRun, 100), classified = true),
        Commendation("GRADUATE", "Made a habit automatic", r.graduates.isNotEmpty()),
        Commendation("HONOR ROLL", "Three habits graduated", r.graduates.size >= 3, prog(r.graduates.size, 3)),
        Commendation("WAVE RIDER", "10 urges ridden out", r.urgesRidden >= 10, prog(r.urgesRidden, 10)),
        Commendation("ANGEL HUNTER", "10 Angels cleared", r.anomaliesCleared >= 10, prog(r.anomaliesCleared, 10), classified = true),
        Commendation("DEMOLITION", "10 🔥 objectives cleared", r.hardCleared >= 10, prog(r.hardCleared, 10), classified = true)
    )
}

@Composable
private fun CommendationRow(c: Commendation) {
    val hidden = c.classified && !c.earned
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (c.earned) EvaPanelBg else EvaBackground)
            .border(1.dp, if (c.earned) EvaYellow else EvaPanelBgLight)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (c.earned) "◆" else "◇",
            color = if (c.earned) EvaYellow else EvaTextDim, fontSize = 16.sp
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (hidden) "CLASSIFIED" else c.name,
                fontFamily = TerminalFontFamily,
                color = if (c.earned) EvaTextPrimary else EvaTextDim,
                fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
            )
            Text(
                if (hidden) "██████████████" else c.detail,
                fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp
            )
        }
        if (!c.earned && !hidden && c.progress != null) {
            Text(c.progress, fontFamily = TerminalFontFamily, color = EvaTextDim, fontSize = 11.sp)
        }
    }
}
