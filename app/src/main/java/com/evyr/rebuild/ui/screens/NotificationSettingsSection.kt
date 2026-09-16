package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.ui.theme.*

/**
 * Extracted from SettingsScreen.kt during the cleanup pass. No behavior change,
 * purely moved. NotificationPrefsPanel and HourStepper kept together since
 * HourStepper only exists to serve the quiet-hours fields in the panel above it.
 */
@Composable
fun NotificationPrefsPanel(prefs: com.evyr.rebuild.data.NotificationPrefs, onUpdate: (com.evyr.rebuild.data.NotificationPrefs) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LOCATION NUDGES", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = prefs.geofenceNudgesEnabled,
                onCheckedChange = { onUpdate(prefs.copy(geofenceNudgesEnabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MORNING SUMMARY", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = prefs.morningSummaryEnabled,
                onCheckedChange = { onUpdate(prefs.copy(morningSummaryEnabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("QUIET HOURS", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = prefs.quietHoursStart != null,
                onCheckedChange = { enabled ->
                    onUpdate(if (enabled) prefs.copy(quietHoursStart = 22, quietHoursEnd = 7) else prefs.copy(quietHoursStart = null, quietHoursEnd = null))
                },
                colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
            )
        }
        if (prefs.quietHoursStart != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HourStepper(
                    label = "FROM",
                    hour = prefs.quietHoursStart,
                    onChange = { onUpdate(prefs.copy(quietHoursStart = it)) }
                )
                HourStepper(
                    label = "TO",
                    hour = prefs.quietHoursEnd,
                    onChange = { onUpdate(prefs.copy(quietHoursEnd = it)) }
                )
            }
        }
    }
}

@Composable
private fun HourStepper(label: String, hour: Int?, onChange: (Int) -> Unit) {
    val h = hour ?: 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp)
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = { onChange((h + 23) % 24) }, modifier = Modifier.size(24.dp)) {
            Text("−", fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 16.sp)
        }
        Text("%02d:00".format(h), fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp)
        IconButton(onClick = { onChange((h + 1) % 24) }, modifier = Modifier.size(24.dp)) {
            Text("+", fontFamily = TerminalFontFamily, color = NervOrange, fontSize = 16.sp)
        }
    }
}

