package com.evyr.rebuild.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.PlaceCondition
import com.evyr.rebuild.data.RebuildDatabase
import com.evyr.rebuild.data.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Everything that happens to a reminder after it's scheduled:
 *
 *  - no action: the daily alarm. Shows the reminder (if it should) and
 *    re-arms itself for tomorrow -- reschedule-on-fire, because repeating
 *    alarms have been inexact since API 19.
 *  - ACTION_FOLLOW_UP: a snooze or re-nag coming due.
 *  - ACTION_DONE / ACTION_SNOOZE_*: the buttons on the notification.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_NAG_COUNT = "nag_count"
        const val ACTION_FOLLOW_UP = "com.evyr.rebuild.REMINDER_FOLLOW_UP"
        const val ACTION_DONE = "com.evyr.rebuild.REMINDER_DONE"
        const val ACTION_SNOOZE_SHORT = "com.evyr.rebuild.REMINDER_SNOOZE_SHORT"
        const val ACTION_SNOOZE_LONG = "com.evyr.rebuild.REMINDER_SNOOZE_LONG"
        private const val SNOOZE_SHORT_MIN = 10
        private const val SNOOZE_LONG_MIN = 60
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0L) return
        val action = intent.action
        val nagCount = intent.getIntExtra(EXTRA_NAG_COUNT, 0)
        val appContext = context.applicationContext

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AppRepository(RebuildDatabase.getInstance(appContext))
                when (action) {
                    ACTION_DONE -> onDone(appContext, repo, reminderId)
                    ACTION_SNOOZE_SHORT -> onSnooze(appContext, reminderId, SNOOZE_SHORT_MIN)
                    ACTION_SNOOZE_LONG -> onSnooze(appContext, reminderId, SNOOZE_LONG_MIN)
                    ACTION_FOLLOW_UP -> fire(appContext, repo, reminderId, nagCount, isDaily = false)
                    else -> fire(appContext, repo, reminderId, nagCount = 0, isDaily = true)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun onDone(context: Context, repo: AppRepository, reminderId: Long) {
        NotificationHelper(context).cancelReminderNotification(reminderId)
        ReminderScheduler.cancelFollowUp(context, reminderId)
        val reminder = repo.getReminderById(reminderId) ?: return
        repo.completeFromNotification(reminder.directiveId)
        com.evyr.rebuild.widget.RebuildWidgetProvider.refresh(context)
    }

    private fun onSnooze(context: Context, reminderId: Long, minutes: Int) {
        NotificationHelper(context).cancelReminderNotification(reminderId)
        // A snooze restarts the re-nag count: you asked for this one.
        ReminderScheduler.scheduleFollowUp(context, reminderId, minutes, nagCount = 0)
    }

    private suspend fun fire(context: Context, repo: AppRepository, reminderId: Long, nagCount: Int, isDaily: Boolean) {
        val reminder = repo.getReminderById(reminderId)
        if (reminder == null || !reminder.enabled) return

        val directive = repo.directiveById(reminder.directiveId)

        // Orphan: the directive was deleted but its reminder survived (no
        // foreign key on directiveId). Delete it and don't reschedule, or it
        // fires daily forever with nothing to name it.
        if (directive == null) {
            ReminderScheduler.cancel(context, reminder)
            NotificationHelper(context).deleteReminderChannels(reminder.id)
            repo.deleteReminder(reminder)
            return
        }

        // Tomorrow's alarm is armed first, so nothing below can skip it.
        if (isDaily) ReminderScheduler.schedule(context, reminder)

        // The day filter and location gate decide whether the daily firing
        // happens at all. A follow-up only exists because that firing already
        // passed them (or you snoozed), so it isn't asked again.
        if (isDaily && !passesGates(context, reminder)) return

        // Already done today: nothing to remind about, and no re-nags either.
        if (repo.isSatisfiedToday(directive)) return

        NotificationHelper(context).showReminder(reminder, directive, nagCount)

        if (reminder.renagMinutes > 0 && nagCount < reminder.renagMax) {
            ReminderScheduler.scheduleFollowUp(context, reminder.id, reminder.renagMinutes, nagCount + 1)
        }
    }

    private fun passesGates(context: Context, reminder: Reminder): Boolean {
        if (!ReminderScheduler.isDueToday(reminder)) return false

        // Location gate. Presence comes from geofence transitions, so it can
        // be unknown if none has been seen for that place. Unknown fails
        // closed: better a missed nudge than one in the wrong place.
        val condition = reminder.placeCondition
        if (condition == PlaceCondition.ANYWHERE) return true
        val inside = reminder.placeId?.let {
            com.evyr.rebuild.location.GeofencePresence.isInside(context, it)
        }
        return when (condition) {
            PlaceCondition.AT_PLACE -> inside == true
            PlaceCondition.AWAY_FROM_PLACE -> inside == false
            else -> true
        }
    }
}
