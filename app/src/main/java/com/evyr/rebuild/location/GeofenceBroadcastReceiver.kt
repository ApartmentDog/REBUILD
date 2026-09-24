package com.evyr.rebuild.location

import android.content.BroadcastReceiver
import android.content.Context
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.evyr.rebuild.notification.ArrivalNudgeWorker
import com.evyr.rebuild.notification.DepartureCheckWorker
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import java.util.concurrent.TimeUnit

/**
 * Fires the two notification moments specified in the design doc, per geofenced place:
 *  - EXIT a place  -> immediate check: did I scan my NFC-linked items (wallet/keys/etc)
 *    today? Nags only about what's actually missing, fires nothing if all clear.
 *  - ENTER a place -> delayed check (5 min): have those items been re-scanned since
 *    arriving (i.e. "put away")? Same all-or-nothing logic.
 *
 * Both are soft nudges only — no Sync Ratio penalty tied to these firing or not,
 * per the "catch it in the moment, don't add guilt" design decision.
 *
 * Debounce is keyed per-place (via the geofence request ID, "place_<id>"), not
 * globally, so two different places transitioning close together don't suppress
 * each other's notifications.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        /** Worst location error we'll act on. Beyond this the fix can't
         *  distinguish sides of any geofence we'd realistically draw, so the
         *  transition is noise regardless of which way it points. */
        private const val MAX_TRUSTED_ACCURACY_M = 200f

        private const val ARRIVAL_CHECK_DELAY_MINUTES = 5L
        private const val DEBOUNCE_WINDOW_MS = 10 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        GeofenceDebugLog.log(context, "onReceive fired")

        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            GeofenceDebugLog.log(context, "fromIntent returned null — not a real geofence intent")
            return
        }
        if (event.hasError()) {
            // Real, previously-silent failure path. Common causes: location
            // services toggled off at the OS level, or the device dropped below
            // the API's minimum geofence-count/location-accuracy requirements.
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                // Location was switched off: Android has dropped every fence.
                GeofenceSync.markNeedsResync(context)
                GeofenceDebugLog.log(
                    context,
                    "location turned off — all geofences dropped, will re-register next time REBUILD opens"
                )
            } else {
                GeofenceDebugLog.log(context, "event.hasError() — code ${event.errorCode}")
            }
            return
        }

        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            transition != Geofence.GEOFENCE_TRANSITION_EXIT
        ) {
            GeofenceDebugLog.log(context, "ignored transition type $transition (not enter/exit)")
            return
        }

        val prefs = context.getSharedPreferences("geofence_debounce", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val triggering = event.triggeringGeofences
        if (triggering.isNullOrEmpty()) {
            GeofenceDebugLog.log(context, "transition $transition but triggeringGeofences is empty")
            return
        }

        // How good was the fix this transition was derived from? Play Services
        // doesn't run GPS for geofencing while the device is idle -- it falls
        // back to cell-tower and wifi positioning, which can be off by
        // kilometres. A fix with 2km of error says nothing about whether you
        // crossed a 100m boundary, but the transition arrives looking exactly
        // like one from a 10m GPS lock. This is how a departure fires for a
        // place you are nowhere near.
        val fixAccuracy = event.triggeringLocation?.accuracy
        if (fixAccuracy != null && fixAccuracy > MAX_TRUSTED_ACCURACY_M) {
            GeofenceDebugLog.log(
                context,
                "transition ${transitionName(transition)} ignored — location accurate to only ${fixAccuracy.toInt()}m"
            )
            return
        }

        triggering.forEach { geofence ->
            val placeId = geofence.requestId.removePrefix("place_").toLongOrNull()
            if (placeId == null) {
                GeofenceDebugLog.log(context, "couldn't parse place id from '${geofence.requestId}'")
                return@forEach
            }

            // Debounce is keyed per PLACE, not per place+transition. The old key
            // included the transition type, which gave ENTER and EXIT separate
            // windows -- so an EXIT could be immediately followed by an ENTER and
            // neither suppressed the other. That is the only failure mode that
            // actually occurs: standing still indoors, GPS drifts outside the
            // radius and back, and the app reports leaving and arriving minutes
            // apart without anyone moving.
            val entering = transition == Geofence.GEOFENCE_TRANSITION_ENTER
            val knownInside = GeofencePresence.isInside(context, placeId)
            val lastTransitionAt = prefs.getLong("last_transition_$placeId", 0)

            // First report after (re)registering: Play Services saying which
            // side of the fence the phone is already on. That's position, not
            // travel -- record it and fire nothing. Without this, re-registering
            // while home left a stale "outside" in place and the next real
            // departure was thrown away as "already outside".
            if (GeofencePresence.consumeSyncPending(context, placeId)) {
                GeofencePresence.setInside(context, placeId, entering, stampDeparture = false)
                GeofenceDebugLog.log(
                    context,
                    "place $placeId position after registering: ${if (entering) "inside" else "outside"}" +
                        if (knownInside != null && knownInside != entering) " (corrected)" else ""
                )
                return@forEach
            }

            // An EXIT with no recorded arrival is not trustworthy: you can't
            // leave somewhere you were never seen to enter. This happens when
            // Play Services holds stale "inside" state from a phantom ENTER and
            // only resolves it hours later -- which is how "Leaving Work" fires
            // at half past midnight from bed. Record the correction, stay quiet.
            if (!entering && knownInside == null) {
                GeofencePresence.setInside(context, placeId, false, stampDeparture = false)
                GeofenceDebugLog.log(
                    context,
                    "place $placeId EXIT ignored — no recorded arrival to leave from"
                )
                return@forEach
            }

            // Redundant: the OS is telling us something we already believe.
            if (knownInside != null && knownInside == entering) {
                GeofenceDebugLog.log(
                    context,
                    "place $placeId ${transitionName(transition)} ignored — already ${if (entering) "inside" else "outside"}"
                )
                return@forEach
            }

            // Bounce: a real crossing this soon after the last one means GPS
            // jitter, not travel. Nobody leaves and returns in a few minutes.
            if (now - lastTransitionAt < DEBOUNCE_WINDOW_MS) {
                val agoMin = (now - lastTransitionAt) / 60000
                GeofenceDebugLog.log(
                    context,
                    "place $placeId ${transitionName(transition)} suppressed — flipped ${agoMin}min after last transition (jitter)"
                )
                return@forEach
            }
            prefs.edit().putLong("last_transition_$placeId", now).apply()

            // Record what the fix looked like. When a transition still seems
            // wrong, this is the difference between "GPS said so" and "a cell
            // tower guessed" -- without it the log can't tell them apart.
            GeofenceDebugLog.log(
                context,
                "place $placeId ${transitionName(transition)} accepted — fix accurate to " +
                    (fixAccuracy?.let { "${it.toInt()}m" } ?: "unknown")
            )

            when (transition) {
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    GeofencePresence.setInside(context, placeId, false)
                    firePlaceReminders(context, placeId, com.evyr.rebuild.data.ReminderTrigger.DEPARTURE)
                    GeofenceDebugLog.log(context, "place $placeId EXIT — enqueuing DepartureCheckWorker")
                    val request = OneTimeWorkRequestBuilder<DepartureCheckWorker>()
                        .setInputData(
                            Data.Builder().putLong(DepartureCheckWorker.KEY_PLACE_ID, placeId).build()
                        )
                        .build()
                    WorkManager.getInstance(context).enqueue(request)
                }
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    GeofencePresence.setInside(context, placeId, true)
                    firePlaceReminders(context, placeId, com.evyr.rebuild.data.ReminderTrigger.ARRIVAL)
                    GeofenceDebugLog.log(context, "place $placeId ENTER — enqueuing ArrivalNudgeWorker (+${ARRIVAL_CHECK_DELAY_MINUTES}min)")
                    val request = OneTimeWorkRequestBuilder<ArrivalNudgeWorker>()
                        .setInitialDelay(ARRIVAL_CHECK_DELAY_MINUTES, TimeUnit.MINUTES)
                        .setInputData(
                            Data.Builder()
                                .putLong(ArrivalNudgeWorker.KEY_PLACE_ID, placeId)
                                .putLong(ArrivalNudgeWorker.KEY_ARRIVAL_EPOCH_MS, now)
                                .build()
                        )
                        .build()
                    WorkManager.getInstance(context).enqueue(request)
                }
            }
        }
    }

    /**
     * Fires any reminder set to go off on arrival at / departure from this
     * place, honouring its day-of-week filter. These never touch AlarmManager
     * -- the geofence transition is the whole trigger.
     */
    private fun firePlaceReminders(
        context: Context,
        placeId: Long,
        trigger: com.evyr.rebuild.data.ReminderTrigger
    ) {
        val appContext = context.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val repo = com.evyr.rebuild.data.AppRepository(
                com.evyr.rebuild.data.RebuildDatabase.getInstance(appContext)
            )
            // Quiet hours apply here but not to time-based reminders: you chose
            // the hour on those, so silencing them would be overriding an
            // explicit instruction. A geofence crossing picks its own moment,
            // which is exactly what quiet hours exist to contain.
            val prefs = repo.getNotificationPrefs()
            if (com.evyr.rebuild.notification.isWithinQuietHours(prefs.quietHoursStart, prefs.quietHoursEnd)) {
                GeofenceDebugLog.log(appContext, "place reminder suppressed — within quiet hours")
                return@launch
            }

            repo.remindersForPlace(placeId, trigger)
                .filter { it.enabled && com.evyr.rebuild.notification.ReminderScheduler.isDueToday(it) }
                .forEach { reminder ->
                    val directive = repo.directiveById(reminder.directiveId)
                    if (directive == null) {
                        repo.deleteReminder(reminder)
                        return@forEach
                    }

                    // Don't nag about something already done. Only for ARRIVAL:
                    // completing it counts until you next leave, because walking
                    // back in with your keys makes it live again. A DEPARTURE
                    // reminder is about what you carry on the way out, which
                    // having done it earlier in the day says nothing about.
                    if (trigger == com.evyr.rebuild.data.ReminderTrigger.ARRIVAL) {
                        val completedAt = repo.completedAtToday(reminder.directiveId)
                        val leftAt = GeofencePresence.lastDepartureAt(appContext, placeId)
                        if (completedAt != null && completedAt > leftAt) {
                            GeofenceDebugLog.log(
                                appContext,
                                "skipped ${directive.title} — already done since last departure"
                            )
                            return@forEach
                        }
                    }
                    com.evyr.rebuild.notification.NotificationHelper(appContext)
                        .showReminder(reminder.id, directive.title, reminder.customMessage)
                    GeofenceDebugLog.log(appContext, "fired ${trigger.name} reminder for ${directive.title}")
                }
        }
    }

    private fun transitionName(transition: Int): String = when (transition) {
        Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
        Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
        else -> "?"
    }
}
