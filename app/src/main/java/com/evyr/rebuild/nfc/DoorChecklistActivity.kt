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
 * The leaving / arriving checklist card. Opened from the geofence departure
 * and arrival notifications (which pass the mode and place), or by tapping a
 * door tag (which uses the mode from the last geofence event).
 *
 *  - HEADING OUT: items to take with you.
 *  - ARRIVING: items to put back.
 *
 * What's on each list comes from DoorChecklistItems. The switch at the top
 * overrides the mode if the geofence got it wrong. Updates live: tapping an
 * item's tag with this open ticks it off in place.
 *
 * Skips the PIN, same as a normal tag tap. Shows only item names and times.
 */
class DoorChecklistActivity : ComponentActivity() {

    private val mode = mutableStateOf(DoorMode.LEAVING)
    private val placeId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyIntent(intent, savedInstanceState)

        val repo = AppRepository(RebuildDatabase.getInstance(applicationContext))
        setContent {
            DoorChecklist(
                repo = repo,
                mode = mode.value,
                placeId = placeId.value,
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyIntent(intent, null)
    }

    private fun applyIntent(intent: Intent?, saved: Bundle?) {
        mode.value = saved?.getString(EXTRA_MODE)?.let { DoorMode.valueOf(it) }
            ?: intent?.getStringExtra(EXTRA_MODE)?.let { runCatching { DoorMode.valueOf(it) }.getOrNull() }
            ?: DoorState.currentMode(this)
        placeId.value = intent?.getLongExtra(EXTRA_PLACE_ID, -1L)?.takeIf { it >= 0L }
            ?: DoorState.lastPlaceId(this)
    }

    override fun onResume() {
        super.onResume()
        // Tapping an item briefly covers this card; coming back mustn't find
        // the put-away window closed underneath you.
        if (mode.value == DoorMode.ARRIVING) DoorState.keepPutAwayWindowOpen(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_MODE, mode.value.name)
    }

    companion object {
        const val EXTRA_MODE = "door_mode"
        const val EXTRA_PLACE_ID = "place_id"
    }
}

@Composable
private fun DoorChecklist(
    repo: AppRepository,
    mode: DoorMode,
    placeId: Long?,
    onSwitch: (DoorMode) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    // Observed only to know when to rebuild: any tag tap updates this.
    val tags by repo.observeTags().collectAsState(initial = null)

    var items by remember { mutableStateOf<List<DoorItem>?>(null) }
    LaunchedEffect(tags, mode, placeId) {
        items = DoorChecklistItems.build(context, repo, mode, placeId)
    }

    val zone = ZoneId.systemDefault()
    val timeFmt = remember { DateTimeFormatter.ofPattern("h:mm a") }
    val leaving = mode == DoorMode.LEAVING
    val list = items.orEmpty()

    val done = list.count { it.doneAt != null }
    val verb = if (leaving) "done" else "put away"

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
                ModeTab("ARRIVING", !leaving, Modifier.weight(1f)) { onSwitch(DoorMode.ARRIVING) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    items == null -> "Loading..."
                    list.isEmpty() -> "Nothing on this list. Add items with the OUT / IN toggles in NFC Tags or a place's checklist in Locations."
                    done == list.size -> if (leaving) "All set. Everything's with you." else "All put away."
                    leaving -> "$done of ${list.size} done. Tap each as you grab it."
                    else -> "$done of ${list.size} put away. Tap each as you put it back."
                },
                fontFamily = TerminalFontFamily,
                color = if (list.isNotEmpty() && done == list.size) EvaGreen else NervDim,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(14.dp))

            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                list.forEach { item ->
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
