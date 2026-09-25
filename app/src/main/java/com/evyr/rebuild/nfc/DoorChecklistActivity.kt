package com.evyr.rebuild.nfc

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Opened by tapping a door tag (NfcTag.opensChecklist). Two lists:
 *
 *  - HEADING OUT: items on the leaving list, checked once grabbed since you
 *    last put them away (or since midnight, if you never did).
 *  - JUST GOT HOME: items on the arriving list, checked once put away since
 *    you walked in.
 *
 * DoorState picks the mode per tap (first tap of the day is heading out, then
 * it alternates) and tells grab taps from put-away taps. The switch at the top
 * fixes a wrong guess. Reads live from the database, so tapping an item's tag
 * with this open ticks it off in place.
 *
 * Skips the PIN, same as a normal tag tap: stopping at the door to unlock the
 * app defeats the point. Shows only tag names and tap times.
 */
class DoorChecklistActivity : ComponentActivity() {

    private val mode = mutableStateOf(DoorMode.LEAVING)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A fresh open is a door tap; a recreate (rotation) keeps its mode.
        mode.value = savedInstanceState?.getString(KEY_MODE)?.let { DoorMode.valueOf(it) }
            ?: DoorState.modeForNewTap(this)

        val repo = AppRepository(RebuildDatabase.getInstance(applicationContext))
        setContent {
            DoorChecklist(
                repo = repo,
                mode = mode.value,
                onSwitch = { m ->
                    if (m != mode.value) {
                        DoorState.setMode(this, m)
                        mode.value = m
                    }
                },
                onClose = { finish() }
            )
        }
    }

    /** Door tag tapped again while this is open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        mode.value = DoorState.modeForNewTap(this)
    }

    override fun onResume() {
        super.onResume()
        // Tapping an item briefly covers this card; coming back mustn't find
        // the put-away window closed underneath you.
        if (mode.value == DoorMode.ARRIVING) DoorState.keepPutAwayWindowOpen(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_MODE, mode.value.name)
    }

    private companion object {
        const val KEY_MODE = "door_mode"
    }
}

private data class ChecklistItem(
    val title: String,
    val spot: String?,
    /** When it was grabbed (heading out) or put away (just got home); null = not yet. */
    val doneAt: Long?
)

@Composable
private fun DoorChecklist(
    repo: AppRepository,
    mode: DoorMode,
    onSwitch: (DoorMode) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val tags by repo.observeTags().collectAsState(initial = null)

    // Linked directive titles, looked up whenever the tag list changes.
    var titles by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(tags) {
        val ids = tags.orEmpty().mapNotNull { it.linkedDirectiveId }.distinct()
        titles = ids.mapNotNull { id -> repo.directiveById(id)?.let { id to it.title } }.toMap()
    }

    val zone = ZoneId.systemDefault()
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val leaving = mode == DoorMode.LEAVING

    val items = tags.orEmpty()
        .filter { !it.opensChecklist && (if (leaving) it.onLeavingList else it.onArrivingList) }
        .map { tag ->
            val directiveTitle = tag.linkedDirectiveId?.let { titles[it] }
            ChecklistItem(
                title = directiveTitle ?: tag.label,
                // Where the tag lives ("Key Hook") when the title is the directive.
                spot = tag.label.takeIf { directiveTitle != null && !it.equals(directiveTitle, ignoreCase = true) },
                doneAt = if (leaving) grabbedSincePutAway(context, tag) else putAwaySinceArriving(context, tag)
            )
        }
        .sortedWith(compareBy<ChecklistItem> { it.doneAt != null }.thenBy { it.title.lowercase() })

    val done = items.count { it.doneAt != null }
    val verb = if (leaving) "grabbed" else "put away"

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
            // Mode switch: fixes a wrong alternating guess in one tap.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                ModeTab("HEADING OUT", leaving, Modifier.weight(1f)) { onSwitch(DoorMode.LEAVING) }
                ModeTab("JUST GOT HOME", !leaving, Modifier.weight(1f)) { onSwitch(DoorMode.ARRIVING) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    tags == null -> "Loading..."
                    items.isEmpty() -> "Nothing on this list. Pick items with the OUT / HOME toggles in NFC Tags."
                    done == items.size -> if (leaving) "All set. Everything's with you." else "All put away."
                    leaving -> "$done of ${items.size} grabbed. Tap each as you grab it."
                    else -> "$done of ${items.size} put away. Tap each as you put it back."
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
                    val ok = item.doneAt != null
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
                            item.doneAt?.let {
                                verb.uppercase() + " " + Instant.ofEpochMilli(it).atZone(zone).format(timeFmt)
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

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) NervOrange else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontFamily = TerminalFontFamily,
            color = if (selected) Color.Black else NervDim,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
    }
}

/**
 * Heading out: grabbed since it was last put away, and today. Before this
 * feature, taps weren't split into grab/put-away, so an item with no split
 * history falls back to its last scan time.
 */
private fun grabbedSincePutAway(context: android.content.Context, tag: NfcTag): Long? {
    val putAway = DoorState.putAwayAt(context, tag.tagUid)
    val grabbed = DoorState.grabbedAt(context, tag.tagUid)
        ?: tag.lastScanEpochMs.takeIf { putAway == null }
        ?: return null
    val cutoff = maxOf(DoorState.startOfToday(), putAway ?: 0L)
    return grabbed.takeIf { it >= cutoff }
}

/** Just got home: put away since the arrival door tap. */
private fun putAwaySinceArriving(context: android.content.Context, tag: NfcTag): Long? {
    val arrived = DoorState.arrivedAt(context).takeIf { it > 0L } ?: return null
    return DoorState.putAwayAt(context, tag.tagUid)?.takeIf { it >= arrived }
}
