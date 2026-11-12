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

    /**
     * PendingIntent for a non-daily event on a reminder: a follow-up firing
     * (snooze or re-nag) or a notification button. Told apart from the daily
     * alarm, and from each other, by the intent action -- PendingIntents with
     * different actions are different even with the same request code.
     */
    fun actionIntent(context: Context, reminderId: Long, action: String, nagCount: Int = 0): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            this.action = action
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderAlarmReceiver.EXTRA_NAG_COUNT, nagCount)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** One-off firing [minutes] from now: a snooze, or the next re-nag. Only
     *  one can be pending per reminder; a new one replaces it. */
    fun scheduleFollowUp(context: Context, reminderId: Long, minutes: Int, nagCount: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        val pi = actionIntent(context, reminderId, ReminderAlarmReceiver.ACTION_FOLLOW_UP, nagCount)
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancelFollowUp(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(actionIntent(context, reminderId, ReminderAlarmReceiver.ACTION_FOLLOW_UP))
    }

    /** False means Android may delay alarms by minutes (or far longer in
     *  Doze) -- reminders still fire, just not on time. API 31+ only; before
     *  that exact alarms needed no permission. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
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
        // the transition happens. There's no clock time to arm, so just make
        // sure no stale alarm is left over.
        //
        // Deliberately NOT cancel(): that treats a CALENDAR reminder as "delete
        // its calendar event", so routing through it here meant a CALENDAR
        // reminder switched to arrival lost its event on the next boot or app
        // start, every time, silently.
        if (reminder.triggerType != com.evyr.rebuild.data.ReminderTrigger.TIME) {
            cancelAlarmOnly(context, reminder)
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

    /** Clears any pending AlarmManager entry without touching calendar events. */
    private fun cancelAlarmOnly(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntentFor(context, reminder))
    }

    fun cancel(context: Context, reminder: Reminder) {
        // Any pending snooze/re-nag and anything on screen goes too. The
        // reminder's notification channels are left alone: this runs for a
        // disable as well as a delete, and they're cleaned up on the next
        // firing or by deleteChannels.
        cancelFollowUp(context, reminder.id)
        NotificationHelper(context).cancelReminderNotification(reminder.id)
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
            NotificationHelper(context).deleteReminderChannels(it.id)
            repo.deleteReminder(it)
        }
        repo.getAllEnabledReminders().forEach { schedule(context, it) }
    }
}
