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
    val context = androidx.compose.ui.platform.LocalContext.current
    // Every change also re-arms the digests, so a new time takes effect now
    // instead of at the next app start.
    val update: (com.evyr.rebuild.data.NotificationPrefs) -> Unit = { p ->
        onUpdate(p)
        com.evyr.rebuild.notification.DigestScheduler.scheduleAll(context.applicationContext, p)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        ExactAlarmWarning()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LOCATION NUDGES", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = prefs.geofenceNudgesEnabled,
                onCheckedChange = { update(prefs.copy(geofenceNudgesEnabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MORNING SUMMARY", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = prefs.morningSummaryEnabled,
                onCheckedChange = { update(prefs.copy(morningSummaryEnabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
            )
        }
        if (prefs.morningSummaryEnabled) {
            HourStepper(
                label = "AT",
                hour = prefs.morningSummaryHour,
                onChange = { update(prefs.copy(morningSummaryHour = it)) }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("EVENING NUDGE", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp)
                Text("Only if something is still open.", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp)
            }
            Switch(
                checked = prefs.eveningNudgeEnabled,
                onCheckedChange = { update(prefs.copy(eveningNudgeEnabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
            )
        }
        if (prefs.eveningNudgeEnabled) {
            HourStepper(
                label = "AT",
                hour = prefs.eveningNudgeHour,
                onChange = { update(prefs.copy(eveningNudgeHour = it)) }
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("QUIET HOURS", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = prefs.quietHoursStart != null,
                onCheckedChange = { enabled ->
                    update(if (enabled) prefs.copy(quietHoursStart = 22, quietHoursEnd = 7) else prefs.copy(quietHoursStart = null, quietHoursEnd = null))
                },
                colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
            )
        }
        if (prefs.quietHoursStart != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HourStepper(
                    label = "FROM",
                    hour = prefs.quietHoursStart,
                    onChange = { update(prefs.copy(quietHoursStart = it)) }
                )
                HourStepper(
                    label = "TO",
                    hour = prefs.quietHoursEnd,
                    onChange = { update(prefs.copy(quietHoursEnd = it)) }
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

/**
 * Shown only when Android won't let the app schedule exact alarms (API 31+,
 * "Alarms & reminders" switched off for REBUILD). Reminders still fire then,
 * but the system may hold them back by minutes, or far longer while the phone
 * is idle -- and nothing in the app said so.
 */
@Composable
private fun ExactAlarmWarning() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var exactOk by remember {
        mutableStateOf(com.evyr.rebuild.notification.ReminderScheduler.canScheduleExact(context))
    }
    // Re-check on return from the system settings screen.
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                exactOk = com.evyr.rebuild.notification.ReminderScheduler.canScheduleExact(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (exactOk) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NervRed.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .clickable {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:${context.packageName}")
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
            .padding(10.dp)
    ) {
        Text("⚠ REMINDERS MAY RUN LATE", fontFamily = TerminalFontFamily, color = NervRed, fontSize = 12.sp)
        Text(
            "Android is allowed to delay them because \"Alarms & reminders\" is off for this app. Tap to turn it on.",
            fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp
        )
    }
    Spacer(Modifier.height(8.dp))
}
