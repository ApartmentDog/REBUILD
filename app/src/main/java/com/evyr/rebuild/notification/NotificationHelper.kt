package com.evyr.rebuild.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.evyr.rebuild.R

// Three channels instead of one shared "rebuild_reminders" channel. Android sets
// notification sound at the CHANNEL level (immutable once created), not per
// notification, so "different sounds per type" requires separate channels --
// this is the standard, correct pattern, not a workaround. Each channel is also
// independently visible and configurable by the user in Android's own
// per-app notification settings (they can silence just one type, change its
// sound, etc.) which a single shared channel couldn't offer at all.
private const val CHANNEL_GEOFENCE = "rebuild_geofence"
private const val CHANNEL_DAILY_QUOTE = "rebuild_daily_quote"
private const val CHANNEL_REMINDERS = "rebuild_reminders"

private const val NOTIF_ID_DEPARTURE = 1001
private const val NOTIF_ID_ARRIVAL = 1002
const val EXTRA_PLACE_ID = "place_id"
const val EXTRA_ARRIVAL_EPOCH_MS = "arrival_epoch_ms"

class NotificationHelper(private val context: Context) {

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        val audioAttrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        // Each channel gets a distinct system default sound rather than a bundled
        // custom .mp3 -- no new audio assets, no licensing question, and it's
        // genuinely how most apps differentiate channels. The user can still swap
        // any of these for their own sound/silence in Android's per-channel
        // notification settings; this just makes them different out of the box.
        // Notification, ringtone, and alarm are the three genuinely distinct
        // system default sound categories available without bundling custom
        // audio files -- using the same category twice (e.g. notification sound
        // for two different channels) wouldn't actually sound different.
        val geofenceChannel = NotificationChannel(
            CHANNEL_GEOFENCE, "Geofence Nudges", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Leaving or arriving at a place with unscanned items"
            setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION), audioAttrs)
        }

        val dailyQuoteChannel = NotificationChannel(
            CHANNEL_DAILY_QUOTE, "Daily Quote & Summary", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Morning directive summary"
            setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE), audioAttrs)
        }

        val remindersChannel = NotificationChannel(
            CHANNEL_REMINDERS, "Directive Reminders", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Custom scheduled reminders for specific directives"
            // getDefaultUri (not getActualDefaultRingtoneUri) deliberately: the
            // latter can return null if the underlying system setting is unset,
            // which would make this channel silent -- the opposite of wanting a
            // distinct, always-present sound for reminders specifically.
            setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM), audioAttrs)
        }

        manager?.createNotificationChannel(geofenceChannel)
        manager?.createNotificationChannel(dailyQuoteChannel)
        manager?.createNotificationChannel(remindersChannel)
    }

    private fun openAppIntent(): PendingIntent {
        val i = Intent(context, com.evyr.rebuild.MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Opens the leaving/arriving checklist card for a place. */
    private fun checklistIntent(mode: com.evyr.rebuild.nfc.DoorMode, placeId: Long, requestCode: Int): PendingIntent {
        val i = Intent(context, com.evyr.rebuild.nfc.DoorChecklistActivity::class.java)
            .putExtra(com.evyr.rebuild.nfc.DoorChecklistActivity.EXTRA_MODE, mode.name)
            .putExtra(com.evyr.rebuild.nfc.DoorChecklistActivity.EXTRA_PLACE_ID, placeId)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Leaving checklist: fires on geofence EXIT with whatever's still not with you. Tap opens the live card. */
    fun showDepartureNudge(placeId: Long, placeLabel: String, missingItems: List<String>) {
        val doneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_DONE; putExtra(EXTRA_NOTIF_ID, NOTIF_ID_DEPARTURE)
        }
        val donePI = PendingIntent.getBroadcast(context, 3, doneIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val body = "Still need: ${missingItems.joinToString(", ")}"
        val notification = NotificationCompat.Builder(context, CHANNEL_GEOFENCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Leaving $placeLabel")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(checklistIntent(com.evyr.rebuild.nfc.DoorMode.LEAVING, placeId, 10))
            .addAction(0, "GOT THEM", donePI)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_DEPARTURE, notification)
    }

    /**
     * "Did you put them away?" — fires after arriving at a geofenced place, only for
     * NFC-linked items that haven't been re-scanned since this specific arrival.
     * placeId + arrivalEpochMs are threaded onto the REMIND ME LATER action so the
     * re-scheduled check re-runs against the same place and arrival time.
     */
    fun showArrivalNudge(placeId: Long, placeLabel: String, arrivalEpochMs: Long, missingItems: List<String>) {
        val doneIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_MARK_DONE
            putExtra(EXTRA_NOTIF_ID, NOTIF_ID_ARRIVAL)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context, 1, doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val laterIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_REMIND_LATER
            putExtra(EXTRA_NOTIF_ID, NOTIF_ID_ARRIVAL)
            putExtra(EXTRA_PLACE_ID, placeId)
            putExtra(EXTRA_ARRIVAL_EPOCH_MS, arrivalEpochMs)
        }
        val laterPendingIntent = PendingIntent.getBroadcast(
            context, 2, laterIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = "Put away: ${missingItems.joinToString(", ")}"
        val notification = NotificationCompat.Builder(context, CHANNEL_GEOFENCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Arrived at $placeLabel")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(checklistIntent(com.evyr.rebuild.nfc.DoorMode.ARRIVING, placeId, 11))
            .addAction(0, "DONE", donePendingIntent)
            .addAction(0, "REMIND ME LATER", laterPendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_ARRIVAL, notification)
    }

    fun showMorningSummary(dueCount: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_QUOTE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("TODAY'S DIRECTIVES")
            .setContentText("$dueCount items due today")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(2001, notification)
    }

    /**
     * Custom scheduled reminder for a specific directive, fired by
     * ReminderAlarmReceiver at the time the user set. Uses the reminder's own
     * custom message if one was set, otherwise falls back to the directive's
     * title so a reminder with no message still says something meaningful.
     */
    fun showReminder(reminderId: Long, directiveTitle: String, customMessage: String?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(directiveTitle.uppercase())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        // Only set body text when there's a real message. The old fallback was
        // the literal word "Reminder", which told you nothing the title and the
        // notification channel didn't already say.
        customMessage?.takeIf { it.isNotBlank() }?.let { builder.setContentText(it) }
        val notification = builder
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        // 3000-offset id range keeps these from ever colliding with the fixed
        // departure/arrival/summary ids above, since reminder ids are user-created
        // and open-ended rather than a small fixed set.
        NotificationManagerCompat.from(context).notify(3000 + reminderId.toInt(), notification)
    }
}
