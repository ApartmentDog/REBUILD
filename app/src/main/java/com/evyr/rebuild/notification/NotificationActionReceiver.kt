package com.evyr.rebuild.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

const val ACTION_MARK_DONE = "com.evyr.rebuild.ACTION_MARK_DONE"
const val ACTION_REMIND_LATER = "com.evyr.rebuild.ACTION_REMIND_LATER"
const val EXTRA_NOTIF_ID = "notif_id"

/**
 * Handles the two action buttons on the arrival nudge notification.
 * DONE just dismisses (the actual "mark complete" happens via NFC scan or the
 * dashboard checkbox — this button is for "yes I handled it, stop asking").
 * REMIND ME LATER re-schedules the same nudge ~15 min out via WorkManager.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId != -1) {
            NotificationManagerCompat.from(context).cancel(notifId)
        }

        when (intent.action) {
            ACTION_REMIND_LATER -> {
                val placeId = intent.getLongExtra(EXTRA_PLACE_ID, -1L)
                val arrivalEpochMs = intent.getLongExtra(EXTRA_ARRIVAL_EPOCH_MS, System.currentTimeMillis())
                val request = OneTimeWorkRequestBuilder<ArrivalNudgeWorker>()
                    .setInitialDelay(15, TimeUnit.MINUTES)
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putLong(ArrivalNudgeWorker.KEY_PLACE_ID, placeId)
                            .putLong(ArrivalNudgeWorker.KEY_ARRIVAL_EPOCH_MS, arrivalEpochMs)
                            .build()
                    )
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            }
            // ACTION_MARK_DONE: nothing further needed, just clearing the notification above is enough
        }
    }
}
