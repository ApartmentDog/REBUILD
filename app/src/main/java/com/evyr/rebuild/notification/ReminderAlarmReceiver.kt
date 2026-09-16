package com.evyr.rebuild.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.RebuildDatabase
import com.evyr.rebuild.data.ReminderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires a scheduled reminder and reschedules itself for the same time tomorrow.
 * The reschedule-on-fire pattern (rather than AlarmManager.setRepeating, which
 * has been inexact/batched since API 19) is what actually gives an exact daily
 * repeat.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId <= 0L) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AppRepository(RebuildDatabase.getInstance(context))
                val reminder = repo.getReminderById(reminderId)
                if (reminder == null || !reminder.enabled) return@launch

                val directive = repo.directiveById(reminder.directiveId)

                // Orphan: the directive was deleted but its reminder survived
                // (no foreign key on directiveId). It used to fall back to the
                // title "Reminder" and re-arm itself, so a deleted directive
                // left a nameless notification firing daily forever. Delete it
                // instead, and don't reschedule.
                if (directive == null) {
                    ReminderScheduler.cancel(context, reminder)
                    repo.deleteReminder(reminder)
                    return@launch
                }

                // ALARM type rings via the system alarm UI itself (see
                // ReminderScheduler.schedule) -- a notification on top would
                // just be a redundant second popup for the same firing.
                if (reminder.reminderType != ReminderType.ALARM) {
                    NotificationHelper(context).showReminder(reminder.id, directive.title, reminder.customMessage)
                }

                // Re-arm for tomorrow at the same time -- this is what makes it
                // repeat daily rather than firing once.
                ReminderScheduler.schedule(context, reminder)
            } finally {
                pending.finish()
            }
        }
    }
}
