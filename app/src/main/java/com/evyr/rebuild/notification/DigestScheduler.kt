package com.evyr.rebuild.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.NotificationPrefs
import com.evyr.rebuild.data.RebuildDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The two daily digests: a morning list of what's due, and an evening nudge
 * that only fires if something is still open. Same reschedule-on-fire pattern
 * as ReminderScheduler. The "Morning summary" toggle existed in Settings long
 * before this did -- nothing ever sent it.
 */
object DigestScheduler {

    const val ACTION_MORNING = "com.evyr.rebuild.DIGEST_MORNING"
    const val ACTION_EVENING = "com.evyr.rebuild.DIGEST_EVENING"

    private fun intentFor(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, DigestReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Arms or cancels both digests to match [prefs]. Safe to call any time. */
    fun scheduleAll(context: Context, prefs: NotificationPrefs) {
        arm(context, ACTION_MORNING, prefs.morningSummaryEnabled, prefs.morningSummaryHour)
        arm(context, ACTION_EVENING, prefs.eveningNudgeEnabled, prefs.eveningNudgeHour)
    }

    private fun arm(context: Context, action: String, enabled: Boolean, hour: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = intentFor(context, action)
        if (!enabled) {
            alarmManager.cancel(pi)
            return
        }
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        if (ReminderScheduler.canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pi)
        }
    }
}

class DigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AppRepository(RebuildDatabase.getInstance(appContext))
                val prefs = repo.getNotificationPrefs()
                // Re-arm first so a failure below can't break the daily cycle.
                DigestScheduler.scheduleAll(appContext, prefs)

                val open = repo.openDailyDirectivesToday().map { it.title }
                if (open.isEmpty()) return@launch
                val helper = NotificationHelper(appContext)
                when (action) {
                    DigestScheduler.ACTION_MORNING ->
                        if (prefs.morningSummaryEnabled) helper.showMorningSummary(open)
                    DigestScheduler.ACTION_EVENING ->
                        if (prefs.eveningNudgeEnabled) helper.showEveningNudge(open)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
