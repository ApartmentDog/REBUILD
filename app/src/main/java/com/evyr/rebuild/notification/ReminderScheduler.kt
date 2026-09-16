package com.evyr.rebuild.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.evyr.rebuild.data.Reminder
import com.evyr.rebuild.data.ReminderType
import java.util.Calendar

/**
 * Schedules exact daily alarms for reminders. AlarmManager rather than
 * WorkManager: a reminder is "notify me at 7:30am" specifically, and
 * WorkManager's periodic work has no guarantee of firing at a precise clock
 * time -- only roughly on schedule, which isn't what a reminder set for a
 * specific time means.
 *
 * Daily repetition is NOT done via AlarmManager.setRepeating -- as of API 19
 * all repeating alarms are inexact and batched by the OS for battery reasons,
 * which defeats the point of an exact time. Instead each firing reschedules
 * itself for the same time tomorrow from inside ReminderAlarmReceiver.
 */
object ReminderScheduler {

    private fun pendingIntentFor(context: Context, reminder: Reminder): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Next occurrence of hour:minute -- today if that time hasn't passed yet, tomorrow otherwise. */
    private fun nextTriggerTime(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    fun schedule(context: Context, reminder: Reminder) {
        if (!reminder.enabled) {
            cancel(context, reminder)
            return
        }
        // ARRIVAL/DEPARTURE reminders are fired by GeofenceBroadcastReceiver when
        // the transition happens. There's no clock time to arm.
        if (reminder.triggerType != com.evyr.rebuild.data.ReminderTrigger.TIME) {
            cancel(context, reminder)
            return
        }

        // CALENDAR reminders don't use AlarmManager at all -- the event itself
        // (with its own CalendarContract.Reminders alert) is what notifies.
        // Writing/updating that event is handled by CalendarReminderWriter,
        // called separately wherever a CALENDAR reminder is saved.
        if (reminder.reminderType == ReminderType.CALENDAR) return

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextTriggerTime(reminder.hour, reminder.minute)
        val pendingIntent = pendingIntentFor(context, reminder)

        if (reminder.reminderType == ReminderType.ALARM) {
            // Rings like a real alarm clock -- status bar alarm icon, uses the
            // system's full-screen alarm UI on lock screen. Android doesn't
            // allow third-party apps to add rows to the Clock app's own alarm
            // list, so this is the closest equivalent: same firing mechanism
            // the Clock app itself would use.
            val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val showPendingIntent = if (showIntent != null) {
                PendingIntent.getActivity(
                    context, reminder.id.toInt(), showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null
            val info = AlarmManager.AlarmClockInfo(triggerAt, showPendingIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
            return
        }

        // NOTIFICATION (default): existing behavior, unchanged.
        // canScheduleExactAlarms is API 31+; below that, exact alarms didn't
        // require this permission at all, so there's nothing to check.
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            // Falls back to an inexact alarm rather than silently not scheduling
            // anything -- the reminder will still fire, just not necessarily at
            // the exact minute, until the user grants the exact-alarm permission.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context, reminder: Reminder) {
        if (reminder.reminderType == ReminderType.CALENDAR) {
            reminder.calendarEventId?.let { CalendarReminderWriter.deleteEvent(context, it) }
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntentFor(context, reminder))
    }

    /**
     * Whether a reminder's day-of-week filter includes today. A null or blank
     * days string means every day, which is how reminders behaved before the
     * filter existed -- so pre-existing ones keep working untouched.
     */
    fun isDueToday(reminder: com.evyr.rebuild.data.Reminder): Boolean {
        val days = reminder.days?.takeIf { it.isNotBlank() } ?: return true
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        return days.split(",").mapNotNull { it.trim().toIntOrNull() }.contains(today)
    }

    /** Re-arms every enabled reminder -- called after boot (alarms don't survive
     *  a reboot) and after any reminder is added/edited.
     *
     *  Purges orphans first: reminders whose directive was deleted out from
     *  under them. There's no foreign key on directiveId, so they survive the
     *  delete and keep firing daily with nothing to name them. Clearing them
     *  here means they go away on the next boot or reminder edit rather than
     *  only when each one next fires. */
    suspend fun rescheduleAll(context: Context, repo: com.evyr.rebuild.data.AppRepository) {
        repo.orphanedReminders().forEach {
            cancel(context, it)
            repo.deleteReminder(it)
        }
        repo.getAllEnabledReminders().forEach { schedule(context, it) }
    }
}
