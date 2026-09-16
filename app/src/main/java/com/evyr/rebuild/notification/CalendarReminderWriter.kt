package com.evyr.rebuild.notification

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.evyr.rebuild.data.Reminder
import com.evyr.rebuild.data.RecurrenceType
import java.util.Calendar
import java.util.TimeZone

/**
 * Writes/updates/removes the CalendarContract event that a CALENDAR-type
 * reminder owns. One event per reminder, tracked via Reminder.calendarEventId
 * so edits update the same event instead of piling up duplicates.
 *
 * Requires READ_CALENDAR/WRITE_CALENDAR to already be granted -- callers
 * check that before invoking this (see ReminderManagementDialog).
 */
object CalendarReminderWriter {

    /** Picks the device's default writable calendar -- the first one with
     *  CAL_ACCESS_OWNER or better. Falls back to the first calendar found if
     *  none is explicitly owner-level, since some accounts don't report it. */
    private fun defaultCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )?.use { cursor ->
            var fallback: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val access = cursor.getInt(1)
                if (fallback == null) fallback = id
                if (access >= CalendarContract.Calendars.CAL_ACCESS_OWNER) return id
            }
            return fallback
        }
        return null
    }

    private fun nextTriggerTime(hour: Int, minute: Int): Calendar {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target
    }

    /** Builds an RRULE string for the reminder's calendar recurrence, or null
     *  for a one-shot event. */
    private fun rruleFor(reminder: Reminder): String? = when (reminder.calendarRecurrenceType) {
        RecurrenceType.NONE -> null
        RecurrenceType.SPECIFIC_DAYS -> {
            val dayMap = mapOf(1 to "SU", 2 to "MO", 3 to "TU", 4 to "WE", 5 to "TH", 6 to "FR", 7 to "SA")
            val days = reminder.calendarRecurrenceDays
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.mapNotNull { dayMap[it] }
                ?.joinToString(",")
            if (days.isNullOrBlank()) null else "FREQ=WEEKLY;BYDAY=$days"
        }
        RecurrenceType.INTERVAL -> {
            val n = reminder.calendarRecurrenceIntervalDays
            if (n == null || n < 1) null else "FREQ=DAILY;INTERVAL=$n"
        }
        RecurrenceType.MONTHLY -> "FREQ=MONTHLY"
    }

    /** Creates or updates the calendar event for this reminder. Returns the
     *  event ID to store back on the Reminder (or null if it couldn't write,
     *  e.g. no calendar available). */
    fun upsertEvent(context: Context, reminder: Reminder): Long? {
        // Clear out any existing event first -- simplest way to handle a type
        // or recurrence change without reconciling field-by-field.
        reminder.calendarEventId?.let { deleteEvent(context, it) }

        val calendarId = defaultCalendarId(context) ?: return null
        val target = nextTriggerTime(reminder.hour, reminder.minute)
        val title = reminder.customMessage?.takeIf { it.isNotBlank() } ?: "REBUILD reminder"
        val rrule = rruleFor(reminder)

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, target.timeInMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (rrule != null) {
                put(CalendarContract.Events.RRULE, rrule)
                // DTEND is not used with RRULE; DURATION covers each occurrence.
                put(CalendarContract.Events.DURATION, "PT15M")
            } else {
                put(CalendarContract.Events.DTEND, target.timeInMillis + 15 * 60 * 1000)
            }
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = ContentUris.parseId(uri)

        // One reminder alert at the event's start time so it actually notifies,
        // not just shows silently in the calendar.
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 0)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)

        return eventId
    }

    fun deleteEvent(context: Context, eventId: Long) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.delete(uri, null, null)
    }
}
