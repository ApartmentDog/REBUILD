package com.evyr.rebuild.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evyr.rebuild.data.Directive
import com.evyr.rebuild.data.ItemKind
import com.evyr.rebuild.data.RecurrenceType
import com.evyr.rebuild.data.Reminder
import com.evyr.rebuild.data.Zone
import com.evyr.rebuild.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Extracted from SettingsScreen.kt during the cleanup pass. No behavior change,
 * purely moved. DirectiveEditRow (the list row) and DirectiveEditDialog (the
 * add/edit form, including recurrence and NFC-only settings) kept together since
 * they're two views of the same editing flow. DirectiveEditDialog is called from
 * multiple other extracted files (MindsetSettingsSection, plus SettingsScreen
 * itself) via same-package visibility -- all files here share
 * com.evyr.rebuild.ui.screens, so no cross-file import is needed for that call.
 */
@Composable
fun DirectiveEditRow(directive: Directive, onEdit: () -> Unit, onReminders: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(NervPanel, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit)) {
            Text(directive.title, fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 13.sp)
            Text(
                "${directive.zone.name} · ${directive.kind.name}",
                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp
            )
        }
        IconButton(onClick = onReminders) {
            Icon(Icons.Filled.Notifications, contentDescription = "Reminders", tint = NervOrange, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = NervRed, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun DirectiveEditDialog(
    existing: Directive?,
    defaultZone: Zone = Zone.DAILY,
    onDismiss: () -> Unit,
    onSave: (Directive) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var zone by remember { mutableStateOf(existing?.zone ?: defaultZone) }
    var kind by remember { mutableStateOf(existing?.kind ?: (if (defaultZone == Zone.MINDSET) ItemKind.DISPLAY_ONLY else ItemKind.CHECKBOX)) }
    var nfcOnly by remember { mutableStateOf(existing?.requiresNfcScan ?: false) }
    var requiredScans by remember { mutableStateOf((existing?.requiredScans ?: 1).toString()) }
    var recurrenceType by remember { mutableStateOf(existing?.recurrenceType ?: RecurrenceType.NONE) }
    var selectedDays by remember {
        mutableStateOf(
            (existing?.recurrenceDays?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet()) ?: emptySet()
        )
    }
    var intervalDaysText by remember { mutableStateOf((existing?.recurrenceIntervalDays ?: 3).toString()) }
    var dayOfMonthText by remember { mutableStateOf((existing?.recurrenceDayOfMonth ?: 1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NervPanel,
        title = {
            Text(
                if (existing == null) "ADD DIRECTIVE" else "EDIT DIRECTIVE",
                fontFamily = TerminalFontFamily, color = NervOrange
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title", fontFamily = TerminalFontFamily) },
                    colors = rebuildTextFieldColors()
                )
                Spacer(Modifier.height(12.dp))
                Text("ZONE", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp)
                Row {
                    Zone.values().forEach { z ->
                        FilterChip(
                            selected = zone == z,
                            onClick = { zone = z; kind = if (z == Zone.STANDING) ItemKind.STREAK else if (z == Zone.MINDSET) ItemKind.DISPLAY_ONLY else ItemKind.CHECKBOX },
                            label = { Text(z.name.take(3), fontFamily = TerminalFontFamily, fontSize = 10.sp) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                // Only meaningful for zones that can be checked off. Streak and
                // display-only items have no checkbox to disable.
                if (zone == Zone.DAILY || zone == Zone.WEEKLY) {
                    Spacer(Modifier.height(12.dp))
                    Text("RECURRENCE", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            RecurrenceType.NONE to "DEFAULT",
                            RecurrenceType.SPECIFIC_DAYS to "DAYS",
                            RecurrenceType.INTERVAL to "EVERY N DAYS",
                            RecurrenceType.MONTHLY to "MONTHLY"
                        ).forEach { (type, label) ->
                            val selected = recurrenceType == type
                            Text(
                                label,
                                fontFamily = TerminalFontFamily,
                                color = if (selected) NervBackground else NervGreen,
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .background(if (selected) NervOrange else NervPanelBorder, RoundedCornerShape(6.dp))
                                    .clickable { recurrenceType = type }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                    when (recurrenceType) {
                        RecurrenceType.SPECIFIC_DAYS -> {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val dayLabels = listOf(1 to "SUN", 2 to "MON", 3 to "TUE", 4 to "WED", 5 to "THU", 6 to "FRI", 7 to "SAT")
                                dayLabels.forEach { (dayNum, label) ->
                                    val on = dayNum in selectedDays
                                    Text(
                                        label,
                                        fontFamily = TerminalFontFamily,
                                        color = if (on) NervBackground else NervGreen,
                                        fontSize = 9.sp,
                                        modifier = Modifier
                                            .background(if (on) NervOrange else NervPanelBorder, RoundedCornerShape(4.dp))
                                            .clickable {
                                                selectedDays = if (on) selectedDays - dayNum else selectedDays + dayNum
                                            }
                                            .padding(horizontal = 6.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        RecurrenceType.INTERVAL -> {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = intervalDaysText,
                                onValueChange = { intervalDaysText = it.filter(Char::isDigit).take(3) },
                                label = { Text("Every N days", fontFamily = TerminalFontFamily) },
                                colors = rebuildTextFieldColors()
                            )
                            Text(
                                "Counted from when you last completed it, not a fixed calendar date.",
                                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp
                            )
                        }
                        RecurrenceType.MONTHLY -> {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = dayOfMonthText,
                                onValueChange = { dayOfMonthText = it.filter(Char::isDigit).take(2) },
                                label = { Text("Day of month (1-31)", fontFamily = TerminalFontFamily) },
                                colors = rebuildTextFieldColors()
                            )
                            Text(
                                "Shorter months clamp to their last day instead of skipping — set to 31 and it fires on the 28th/29th/30th when there's no 31st.",
                                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp
                            )
                        }
                        RecurrenceType.NONE -> {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (zone == Zone.DAILY) "Every day (default for Daily)." else "Uses this directive's single Weekly day.",
                                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = nfcOnly,
                            onCheckedChange = { nfcOnly = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("NFC TAP ONLY", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 12.sp)
                            Text(
                                "Checkbox is disabled — only a tag scan completes it",
                                fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp
                            )
                        }
                    }

                    // No longer gated on nfcOnly: the tap bar works from a direct
                    // UI tap as much as from a tag scan (added specifically because
                    // NFC tags read unreliably on metal bottles), so requiring NFC
                    // to even set a target was blocking the exact case it exists
                    // for. Shown for any checkable directive regardless of the
                    // switch above.
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = requiredScans,
                        onValueChange = { requiredScans = it.filter(Char::isDigit).take(2) },
                        label = { Text("Taps needed per day", fontFamily = TerminalFontFamily) },
                        colors = rebuildTextFieldColors()
                    )
                    Text(
                        "1 completes on a single tap/scan. Higher counts show as a segmented bar above the checklist " +
                            "-- tap it directly, or scan an NFC tag if one's linked. Doesn't need NFC on.",
                        fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp
                    )

                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    onSave(
                        (existing ?: Directive(title = title, zone = zone, kind = kind))
                            .copy(
                                title = title,
                                zone = zone,
                                kind = kind,
                                // Forced off outside checkable zones so a stale flag
                                // can't linger after moving a directive to STANDING
                                // or MINDSET, where there's no checkbox to gate.
                                requiresNfcScan = nfcOnly && (zone == Zone.DAILY || zone == Zone.WEEKLY),
                                // Floor of 1: a target of 0 would make the directive
                                // impossible to complete at all. No longer tied to
                                // nfcOnly -- that was the actual bug: it silently
                                // forced this back to 1 the moment NFC was off,
                                // which blocked setting a tap target for anything
                                // meant to be tapped directly instead of scanned.
                                requiredScans = if (zone == Zone.DAILY || zone == Zone.WEEKLY) {
                                    (requiredScans.toIntOrNull() ?: 1).coerceAtLeast(1)
                                } else 1,
                                recurrenceType = if (zone == Zone.DAILY || zone == Zone.WEEKLY) recurrenceType else RecurrenceType.NONE,
                                recurrenceDays = if (recurrenceType == RecurrenceType.SPECIFIC_DAYS && selectedDays.isNotEmpty())
                                    selectedDays.sorted().joinToString(",") else null,
                                recurrenceIntervalDays = if (recurrenceType == RecurrenceType.INTERVAL)
                                    intervalDaysText.toIntOrNull()?.coerceAtLeast(1) else null,
                                recurrenceDayOfMonth = if (recurrenceType == RecurrenceType.MONTHLY)
                                    dayOfMonthText.toIntOrNull()?.coerceIn(1, 31) else null
                            )
                    )
                }
            }) {
                Text("SAVE", fontFamily = TerminalFontFamily, color = NervGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", fontFamily = TerminalFontFamily, color = NervDim)
            }
        }
    )
}

/**
 * Separate from DirectiveEditDialog on purpose: that dialog builds a Directive
 * object and hands it to onSave without persisting anything itself, so a
 * brand-new directive has no real id yet while the dialog is open. Reminders
 * need a real directiveId to attach to, so this only makes sense for a
 * directive that already exists -- reached from the REMINDERS bell icon on
 * DirectiveEditRow, not from inside the add/edit form.
 */
@Composable
fun ReminderManagementDialog(
    directive: Directive,
    viewModel: com.evyr.rebuild.ui.MainViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val reminders by viewModel.observeRemindersForDirective(directive.id).collectAsState(initial = emptyList())

    var showAddForm by remember { mutableStateOf(false) }
    var hour by remember { mutableStateOf(9) }
    var minute by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var reminderType by remember { mutableStateOf(com.evyr.rebuild.data.ReminderType.NOTIFICATION) }
    var calRecurrence by remember { mutableStateOf(RecurrenceType.NONE) }
    var calSelectedDays by remember { mutableStateOf(setOf<Int>()) }
    var calIntervalText by remember { mutableStateOf("") }

    // Pending save, held until calendar permission is granted (or denied).
    var pendingCalendarSave by remember { mutableStateOf<Reminder?>(null) }
    val calendarPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        val pending = pendingCalendarSave
        pendingCalendarSave = null
        if (granted && pending != null) {
            scope.launch {
                val eventId = com.evyr.rebuild.notification.CalendarReminderWriter.upsertEvent(context, pending)
                viewModel.saveReminder(pending.copy(calendarEventId = eventId))
            }
        }
    }

    fun hasCalendarPermission(): Boolean {
        val read = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR)
        val write = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR)
        return read == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            write == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** Saves a reminder, routing CALENDAR-type ones through the calendar
     *  writer (requesting permission first if needed) instead of AlarmManager. */
    fun saveReminderRouted(reminder: Reminder) {
        if (reminder.reminderType == com.evyr.rebuild.data.ReminderType.CALENDAR) {
            if (hasCalendarPermission()) {
                scope.launch {
                    val eventId = com.evyr.rebuild.notification.CalendarReminderWriter.upsertEvent(context, reminder)
                    viewModel.saveReminder(reminder.copy(calendarEventId = eventId))
                }
            } else {
                pendingCalendarSave = reminder
                calendarPermissionLauncher.launch(
                    arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR)
                )
            }
        } else {
            scope.launch {
                val saved = viewModel.saveReminder(reminder)
                com.evyr.rebuild.notification.ReminderScheduler.schedule(context, saved)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NervPanel,
        title = { Text("${directive.title.uppercase()} REMINDERS", fontFamily = TerminalFontFamily, color = NervOrange) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (reminders.isEmpty() && !showAddForm) {
                    Text("No reminders set.", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 11.sp)
                }
                reminders.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(NervPanelBorder, RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "%02d:%02d".format(r.hour, r.minute),
                                    fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 13.sp
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    when (r.reminderType) {
                                        com.evyr.rebuild.data.ReminderType.NOTIFICATION -> "NOTIFY"
                                        com.evyr.rebuild.data.ReminderType.ALARM -> "ALARM"
                                        com.evyr.rebuild.data.ReminderType.CALENDAR -> "CALENDAR"
                                    },
                                    fontFamily = TerminalFontFamily, color = NervDim, fontSize = 8.sp, letterSpacing = 1.sp
                                )
                            }
                            if (!r.customMessage.isNullOrBlank()) {
                                Text(r.customMessage, fontFamily = TerminalFontFamily, color = NervDim, fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = r.enabled,
                            onCheckedChange = { enabled ->
                                val updated = r.copy(enabled = enabled)
                                if (enabled) {
                                    saveReminderRouted(updated)
                                } else {
                                    scope.launch { viewModel.saveReminder(updated) }
                                    com.evyr.rebuild.notification.ReminderScheduler.cancel(context, r)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = NervOrange)
                        )
                        IconButton(onClick = {
                            com.evyr.rebuild.notification.ReminderScheduler.cancel(context, r)
                            viewModel.deleteReminder(r)
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete reminder", tint = NervRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (showAddForm) {
                    Text("TIME", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReminderTimeStepper("HOUR", hour, 0, 23) { hour = it }
                        Text(":", fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 16.sp)
                        ReminderTimeStepper("MIN", minute, 0, 59) { minute = it }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("Custom message (optional)", fontFamily = TerminalFontFamily) },
                        colors = rebuildTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("TYPE", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            com.evyr.rebuild.data.ReminderType.NOTIFICATION to "NOTIFY",
                            com.evyr.rebuild.data.ReminderType.ALARM to "ALARM",
                            com.evyr.rebuild.data.ReminderType.CALENDAR to "CALENDAR"
                        ).forEach { (type, label) ->
                            val selected = reminderType == type
                            Text(
                                label,
                                fontFamily = TerminalFontFamily,
                                color = if (selected) NervBackground else NervGreen,
                                fontSize = 9.sp,
                                modifier = Modifier
                                    .background(if (selected) NervOrange else NervPanelBorder, RoundedCornerShape(6.dp))
                                    .clickable { reminderType = type }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (reminderType == com.evyr.rebuild.data.ReminderType.CALENDAR) {
                        Spacer(Modifier.height(10.dp))
                        Text("CALENDAR REPEAT", fontFamily = TerminalFontFamily, color = NervDim, fontSize = 9.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                RecurrenceType.NONE to "ONE-TIME",
                                RecurrenceType.SPECIFIC_DAYS to "DAYS",
                                RecurrenceType.INTERVAL to "EVERY N DAYS"
                            ).forEach { (type, label) ->
                                val selected = calRecurrence == type
                                Text(
                                    label,
                                    fontFamily = TerminalFontFamily,
                                    color = if (selected) NervBackground else NervGreen,
                                    fontSize = 9.sp,
                                    modifier = Modifier
                                        .background(if (selected) NervOrange else NervPanelBorder, RoundedCornerShape(6.dp))
                                        .clickable { calRecurrence = type }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                        when (calRecurrence) {
                            RecurrenceType.SPECIFIC_DAYS -> {
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val dayLabels = listOf(1 to "SUN", 2 to "MON", 3 to "TUE", 4 to "WED", 5 to "THU", 6 to "FRI", 7 to "SAT")
                                    dayLabels.forEach { (dayNum, label) ->
                                        val on = dayNum in calSelectedDays
                                        Text(
                                            label,
                                            fontFamily = TerminalFontFamily,
                                            color = if (on) NervBackground else NervGreen,
                                            fontSize = 9.sp,
                                            modifier = Modifier
                                                .background(if (on) NervOrange else NervPanelBorder, RoundedCornerShape(4.dp))
                                                .clickable {
                                                    calSelectedDays = if (on) calSelectedDays - dayNum else calSelectedDays + dayNum
                                                }
                                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            RecurrenceType.INTERVAL -> {
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = calIntervalText,
                                    onValueChange = { calIntervalText = it.filter(Char::isDigit).take(3) },
                                    label = { Text("Every N days", fontFamily = TerminalFontFamily) },
                                    colors = rebuildTextFieldColors()
                                )
                            }
                            else -> {}
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showAddForm = false }) {
                            Text("CANCEL", fontFamily = TerminalFontFamily, color = NervDim)
                        }
                        Button(
                            onClick = {
                                val newReminder = Reminder(
                                    directiveId = directive.id,
                                    hour = hour,
                                    minute = minute,
                                    customMessage = message.ifBlank { null },
                                    reminderType = reminderType,
                                    calendarRecurrenceType = if (reminderType == com.evyr.rebuild.data.ReminderType.CALENDAR) calRecurrence else RecurrenceType.NONE,
                                    calendarRecurrenceDays = if (reminderType == com.evyr.rebuild.data.ReminderType.CALENDAR && calRecurrence == RecurrenceType.SPECIFIC_DAYS && calSelectedDays.isNotEmpty())
                                        calSelectedDays.sorted().joinToString(",") else null,
                                    calendarRecurrenceIntervalDays = if (reminderType == com.evyr.rebuild.data.ReminderType.CALENDAR && calRecurrence == RecurrenceType.INTERVAL)
                                        calIntervalText.toIntOrNull() else null
                                )
                                saveReminderRouted(newReminder)
                                message = ""
                                reminderType = com.evyr.rebuild.data.ReminderType.NOTIFICATION
                                calRecurrence = RecurrenceType.NONE
                                calSelectedDays = setOf()
                                calIntervalText = ""
                                showAddForm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NervOrange, contentColor = NervBackground)
                        ) {
                            Text("ADD", fontFamily = TerminalFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Text(
                        "+ ADD REMINDER",
                        fontFamily = TerminalFontFamily,
                        color = NervOrange,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { showAddForm = true }
                            .padding(vertical = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("DONE", fontFamily = TerminalFontFamily, color = NervGreen)
            }
        }
    )
}

// Named distinctly from the HourStepper in NotificationSettingsSection.kt (a
// different file, quiet-hours-specific) to avoid any ambiguity between two
// same-purpose-but-different-signature composables living in the same package.
@Composable
private fun ReminderTimeStepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontFamily = TerminalFontFamily, color = NervDim, fontSize = 8.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "-",
                fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 16.sp,
                modifier = Modifier
                    .clickable { onChange(if (value <= min) max else value - 1) }
                    .padding(8.dp)
            )
            Text(
                "%02d".format(value),
                fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Text(
                "+",
                fontFamily = TerminalFontFamily, color = NervGreen, fontSize = 16.sp,
                modifier = Modifier
                    .clickable { onChange(if (value >= max) min else value + 1) }
                    .padding(8.dp)
            )
        }
    }
}
