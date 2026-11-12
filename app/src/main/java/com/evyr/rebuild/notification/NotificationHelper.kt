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

    /** Objectives tied to a place, shown when you arrive there. One
     *  notification per place (4000 + placeId) so places don't overwrite each other. */
    fun showPlaceObjectives(placeId: Long, placeLabel: String, titles: List<String>) {
        val body = titles.joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_GEOFENCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📍 At $placeLabel — ${titles.size} objective${if (titles.size == 1) "" else "s"}")
            .setContentText(titles.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        NotificationManagerCompat.from(context).notify(4000 + placeId.toInt(), notification)
    }

    /** Morning digest: what's due today, by name. */
    fun showMorningSummary(titles: List<String>) {
        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_QUOTE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("TODAY: ${titles.size} DIRECTIVE${if (titles.size == 1) "" else "S"}")
            .setContentText(titles.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        NotificationManagerCompat.from(context).notify(2001, notification)
    }

    /** Evening digest: what's still open. Only sent when something is. */
    fun showEveningNudge(titles: List<String>) {
        val notification = NotificationCompat.Builder(context, CHANNEL_GEOFENCE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${titles.size} STILL OPEN TODAY")
            .setContentText(titles.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(titles.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        NotificationManagerCompat.from(context).notify(2002, notification)
    }

    /**
     * A directive reminder. Each reminder has its own notification channel,
     * because Android fixes sound and vibration per CHANNEL, not per
     * notification -- that's the only way one reminder can have a different
     * tone from another. The channel id carries a hash of the tone settings:
     * a channel's sound can't be changed after creation, so changing the tone
     * means a new channel and deleting the old one.
     *
     * ALARM type is insistent: the tone loops until the notification is
     * dealt with. It previously showed nothing at all -- the alarm fired the
     * receiver, which skipped the notification on the assumption that
     * setAlarmClock rings by itself. It doesn't.
     *
     * [nagCount] is 0 for the first firing, 1+ for re-nags.
     */
    fun showReminder(reminder: com.evyr.rebuild.data.Reminder, directive: com.evyr.rebuild.data.Directive, nagCount: Int = 0) {
        val isAlarm = reminder.reminderType == com.evyr.rebuild.data.ReminderType.ALARM
        val channelId = reminderChannelId(reminder)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager != null) {
            deleteReminderChannels(reminder.id, except = channelId)
            manager.createNotificationChannelGroup(
                android.app.NotificationChannelGroup(GROUP_REMINDERS, "Directive Reminders")
            )
            val sound = reminder.soundUri?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
                ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
            val channel = NotificationChannel(
                channelId, directive.title, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                group = GROUP_REMINDERS
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(if (isAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                enableVibration(reminder.vibrate)
                if (reminder.vibrate) vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
            }
            manager.createNotificationChannel(channel)
        }

        val notifId = reminderNotificationId(reminder.id)
        val body = buildString {
            reminder.customMessage?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (nagCount > 0) {
                if (isNotEmpty()) append(" · ")
                append("Still open (reminder ${nagCount + 1})")
            }
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(directive.title.uppercase())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
        if (body.isNotEmpty()) builder.setContentText(body)

        // DONE acts on the directive from the shade, no PIN, same as an NFC tap.
        // Left off where one tap can't mean "done": mindset lines and volume
        // trackers (which need an amount).
        val zone = directive.zone
        val canDone = zone != com.evyr.rebuild.data.Zone.MINDSET && (directive.targetOz ?: 0) <= 0
        if (canDone) {
            val label = if (directive.requiredScans > 1 &&
                (zone == com.evyr.rebuild.data.Zone.DAILY || zone == com.evyr.rebuild.data.Zone.WEEKLY)
            ) "+1" else "DONE"
            builder.addAction(0, label, ReminderScheduler.actionIntent(context, reminder.id, ReminderAlarmReceiver.ACTION_DONE))
        }
        builder.addAction(0, "10 MIN", ReminderScheduler.actionIntent(context, reminder.id, ReminderAlarmReceiver.ACTION_SNOOZE_SHORT))
        builder.addAction(0, "1 HR", ReminderScheduler.actionIntent(context, reminder.id, ReminderAlarmReceiver.ACTION_SNOOZE_LONG))

        val notification = builder.build()
        if (isAlarm) notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    fun cancelReminderNotification(reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderNotificationId(reminderId))
    }

    /** Removes this reminder's channels (all of them, or all but [except]). */
    fun deleteReminderChannels(reminderId: Long, except: String? = null) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val prefix = "${CHANNEL_REMINDER_PREFIX}${reminderId}_"
        manager.notificationChannels
            .filter { it.id.startsWith(prefix) && it.id != except }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }

    private fun reminderChannelId(reminder: com.evyr.rebuild.data.Reminder): String {
        val key = "${reminder.soundUri ?: "default"}|${reminder.vibrate}|${reminder.reminderType.name}"
        return "${CHANNEL_REMINDER_PREFIX}${reminder.id}_${key.hashCode().toUInt()}"
    }

    // 3000-offset keeps these clear of the fixed departure/arrival/summary ids.
    private fun reminderNotificationId(reminderId: Long): Int = 3000 + reminderId.toInt()
}

private const val CHANNEL_REMINDER_PREFIX = "rebuild_rem_"
private const val GROUP_REMINDERS = "rebuild_reminders_group"
