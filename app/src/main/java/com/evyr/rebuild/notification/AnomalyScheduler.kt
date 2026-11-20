package com.evyr.rebuild.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.RebuildDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

/**
 * Surprise challenges. Once a day, at a random minute between 10:00 and
 * 20:00, there's a CHANCE an Angel shows up: a small physical task with a
 * one-hour window and a Sync Ratio bonus if it's cleared. Unpredictable
 * rewards hold attention far better than fixed ones; kept rare and small so
 * it stays a treat rather than a slot machine.
 *
 * The planned roll time is stored, so re-arming on every app open doesn't
 * keep reshuffling it past the point of ever firing.
 */
object AnomalyScheduler {
    const val ACTION_ROLL = "com.evyr.rebuild.ANOMALY_ROLL"
    const val ACTION_CLEAR = "com.evyr.rebuild.ANOMALY_CLEAR"
    const val EXTRA_ID = "anomaly_id"
    const val CHANCE = 0.4
    const val DURATION_MS = 60 * 60_000L
    const val BOOST = 5
    private const val WINDOW_START_HOUR = 10
    private const val WINDOW_MINUTES = 600
    private const val PREFS = "rebuild_anomaly"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_NEXT = "next_roll_ms"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply()
        ensureScheduled(context)
    }

    private fun rollIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, AnomalyReceiver::class.java).apply { action = ACTION_ROLL },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun clearIntent(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, AnomalyReceiver::class.java).apply {
            action = ACTION_CLEAR
            putExtra(EXTRA_ID, id)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Arms the stored roll time, or picks one. Safe to call any time. */
    fun ensureScheduled(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = rollIntent(context)
        if (!isEnabled(context)) {
            am.cancel(pi)
            prefs(context).edit().remove(KEY_NEXT).apply()
            return
        }
        val now = System.currentTimeMillis()
        val saved = prefs(context).getLong(KEY_NEXT, 0L)
        val at = if (saved > now) saved else pickNext(now, tomorrowOnly = false)
        prefs(context).edit().putLong(KEY_NEXT, at).apply()
        arm(context, am, pi, at)
    }

    /** After a roll: the next one is tomorrow. */
    fun scheduleTomorrow(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val at = pickNext(System.currentTimeMillis(), tomorrowOnly = true)
        prefs(context).edit().putLong(KEY_NEXT, at).apply()
        if (isEnabled(context)) arm(context, am, rollIntent(context), at)
    }

    private fun arm(context: Context, am: AlarmManager, pi: PendingIntent, at: Long) {
        if (ReminderScheduler.canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pickNext(now: Long, tomorrowOnly: Boolean): Long {
        val offset = Random.nextInt(WINDOW_MINUTES)
        fun at(daysAhead: Int) = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, WINDOW_START_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MINUTE, offset)
        }.timeInMillis
        if (!tomorrowOnly) {
            val today = at(0)
            if (today > now + 60_000L) return today
        }
        return at(1)
    }
}

/** What shows up, and what it asks for. */
object AnomalyPool {
    private val names = listOf(
        "SACHIEL", "SHAMSHEL", "RAMIEL", "GAGHIEL", "ISRAFEL", "SANDALPHON", "MATARAEL",
        "SAHAQUIEL", "IRUEL", "LELIEL", "BARDIEL", "ZERUEL", "ARAEL", "ARMISAEL"
    )
    private val tasks = listOf(
        "20 push-ups",
        "30 squats",
        "Hold a plank for 60 seconds",
        "Drink a full glass of water",
        "Clear one surface completely",
        "Put away 10 things",
        "Stand against a wall, posture check, 60 seconds",
        "Two minutes of voice warm-up",
        "Step outside for 5 minutes",
        "10 slow breaths, eyes closed",
        "Stretch neck and shoulders for 2 minutes",
        "Sketch whatever's in front of you for 5 minutes",
        "Read 5 pages of anything",
        "Message someone you haven't talked to this week",
        "Write one honest sentence about today in the notebook"
    )

    fun pick(): String = "${names.random()}: ${tasks.random()}"
}

class AnomalyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AppRepository(RebuildDatabase.getInstance(appContext))
                when (action) {
                    AnomalyScheduler.ACTION_ROLL -> {
                        AnomalyScheduler.scheduleTomorrow(appContext)
                        if (!AnomalyScheduler.isEnabled(appContext)) return@launch
                        val prefs = repo.getNotificationPrefs()
                        if (isWithinQuietHours(prefs.quietHoursStart, prefs.quietHoursEnd)) return@launch
                        if (Random.nextDouble() > AnomalyScheduler.CHANCE) return@launch
                        val a = repo.spawnAnomaly(AnomalyPool.pick(), AnomalyScheduler.DURATION_MS, AnomalyScheduler.BOOST)
                            ?: return@launch
                        NotificationHelper(appContext).showAnomaly(a)
                    }
                    AnomalyScheduler.ACTION_CLEAR -> {
                        val id = intent.getLongExtra(AnomalyScheduler.EXTRA_ID, -1L)
                        if (id > 0) repo.clearAnomaly(id)
                        NotificationHelper(appContext).cancelAnomaly()
                        com.evyr.rebuild.widget.RebuildWidgetProvider.refresh(appContext)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
