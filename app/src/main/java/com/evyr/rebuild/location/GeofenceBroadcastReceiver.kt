package com.evyr.rebuild.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.evyr.rebuild.notification.ArrivalNudgeWorker
import com.evyr.rebuild.notification.DepartureCheckWorker
import com.google.android.gms.location.Geofence
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
            GeofenceDebugLog.log(context, "event.hasError() — code ${event.errorCode}")
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

        triggering.forEach { geofence ->
            val placeId = geofence.requestId.removePrefix("place_").toLongOrNull()
            if (placeId == null) {
                GeofenceDebugLog.log(context, "couldn't parse place id from '${geofence.requestId}'")
                return@forEach
            }

            val debounceKey = "last_${transition}_${geofence.requestId}"
            if (now - prefs.getLong(debounceKey, 0) < DEBOUNCE_WINDOW_MS) {
                GeofenceDebugLog.log(context, "place $placeId ${transitionName(transition)} suppressed by debounce")
                return@forEach
            }
            prefs.edit().putLong(debounceKey, now).apply()

            when (transition) {
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    GeofenceDebugLog.log(context, "place $placeId EXIT — enqueuing DepartureCheckWorker")
                    val request = OneTimeWorkRequestBuilder<DepartureCheckWorker>()
                        .setInputData(
                            Data.Builder().putLong(DepartureCheckWorker.KEY_PLACE_ID, placeId).build()
                        )
                        .build()
                    WorkManager.getInstance(context).enqueue(request)
                }
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
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

    private fun transitionName(transition: Int): String = when (transition) {
        Geofence.GEOFENCE_TRANSITION_ENTER -> "ENTER"
        Geofence.GEOFENCE_TRANSITION_EXIT -> "EXIT"
        else -> "?"
    }
}
