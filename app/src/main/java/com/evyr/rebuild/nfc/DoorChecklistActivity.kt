package com.evyr.rebuild.nfc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.NfcTag
import com.evyr.rebuild.data.RebuildDatabase
import com.evyr.rebuild.ui.theme.EvaGreen
import com.evyr.rebuild.ui.theme.NervDim
import com.evyr.rebuild.ui.theme.NervGreen
import com.evyr.rebuild.ui.theme.NervOrange
import com.evyr.rebuild.ui.theme.NervPanel
import com.evyr.rebuild.ui.theme.NervRed
import com.evyr.rebuild.ui.theme.TerminalFontFamily
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Opened by tapping a door tag (NfcTag.opensChecklist). Lists every other
 * registered tag and whether it's been tapped today, not-yet-tapped first.
 * Updates live from the database, so tapping your keys' tag while this is
 * open ticks them off in place.
 *
 * Skips the PIN, same as a normal tag tap: stopping at the door to unlock the
 * app defeats the point. It shows only tag names and tap times, no other
 * data and no navigation into the app.
 */
class DoorChecklistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = AppRepository(RebuildDatabase.getInstance(applicationContext))
        setContent {
            DoorChecklist(repo = repo, onClose = { finish() })
        }
    }
}

private data class ChecklistItem(
    val title: String,
    val spot: String?,
    val tappedAt: Long?
)

@Composable
private fun DoorChecklist(repo: AppRepository, onClose: () -> Unit) {
    val tags by repo.observeTags().collectAsState(initial = null)

    // Linked directive titles, looked up whenever the tag list changes.
    var titles by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(tags) {
        val ids = tags.orEmpty().mapNotNull { it.linkedDirectiveId }.distinct()
        titles = ids.mapNotNull { id -> repo.directiveById(id)?.let { id to it.title } }.toMap()
    }

    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }

    val items = tags.orEmpty()
        .filter { !it.opensChecklist }
        .map { tag -> tag.toItem(titles, today, zone) }
        .sortedWith(compareBy<ChecklistItem> { it.tappedAt != null }.thenBy { it.title.lowercase() })

    val done = items.count { it.tappedAt != null }

    // Dimmed backdrop; tapping outside the card closes it.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NervPanel)
                // Swallow taps on the card so they don't close it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .padding(20.dp)
        ) {
            Text(
                "HEADING OUT",
                fontFamily = TerminalFontFamily,
                color = NervOrange,
                fontSize = 18.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    tags == null -> "Loading..."
                    items.isEmpty() -> "No tagged items yet. Register your item tags in NFC Tags."
                    done == items.size -> "All set — everything tapped today."
                    else -> "$done of ${items.size} tapped today. Tap the rest as you grab them."
                },
                fontFamily = TerminalFontFamily,
                color = if (items.isNotEmpty() && done == items.size) EvaGreen else NervDim,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    val ok = item.tappedAt != null
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (ok) EvaGreen else NervRed.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (ok) "\u2713" else "",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title.uppercase(),
                                fontFamily = TerminalFontFamily,
                                color = if (ok) NervGreen else NervOrange,
                                fontSize = 14.sp
                            )
                            item.spot?.let {
                                Text(it, fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp)
                            }
                        }
                        Text(
                            item.tappedAt?.let {
                                Instant.ofEpochMilli(it).atZone(zone).format(timeFmt)
                            } ?: "NOT YET",
                            fontFamily = TerminalFontFamily,
                            color = if (ok) NervDim else NervRed,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "DONE",
                fontFamily = TerminalFontFamily,
                color = NervOrange,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable { onClose() }
                    .padding(8.dp)
            )
        }
    }
}

private fun NfcTag.toItem(titles: Map<Long, String>, today: LocalDate, zone: ZoneId): ChecklistItem {
    val directiveTitle = linkedDirectiveId?.let { titles[it] }
    val tappedToday = lastScanEpochMs?.takeIf {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today
    }
    return ChecklistItem(
        title = directiveTitle ?: label,
        // Show where the tag lives ("Key Hook") when the title is the directive.
        spot = label.takeIf { directiveTitle != null && !it.equals(directiveTitle, ignoreCase = true) },
        tappedAt = tappedToday
    )
}
