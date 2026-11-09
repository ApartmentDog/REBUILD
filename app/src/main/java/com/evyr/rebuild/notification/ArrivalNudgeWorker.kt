package com.evyr.rebuild.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.RebuildDatabase

/**
 * Scheduled by GeofenceBroadcastReceiver on ENTER, after a short delay (5 min).
 * Checks whether NFC-linked Daily directives have been re-scanned since the
 * arrival timestamp — only nags about items that are still missing "put away"
 * confirmation. Fires nothing if everything's already been scanned.
 */
class ArrivalNudgeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_PLACE_ID = "place_id"
        const val KEY_ARRIVAL_EPOCH_MS = "arrival_epoch_ms"
    }

    override suspend fun doWork(): Result {
        val placeId = inputData.getLong(KEY_PLACE_ID, -1L)
        val arrivalEpochMs = inputData.getLong(KEY_ARRIVAL_EPOCH_MS, System.currentTimeMillis())
        com.evyr.rebuild.location.GeofenceDebugLog.log(applicationContext, "ArrivalNudgeWorker running for place $placeId")

        val repo = AppRepository(RebuildDatabase.getInstance(applicationContext))
        val prefs = repo.getNotificationPrefs()
        if (!prefs.geofenceNudgesEnabled) {
            com.evyr.rebuild.location.GeofenceDebugLog.log(applicationContext, "skipped: geofence nudges disabled in Settings")
            return Result.success()
        }
        if (isWithinQuietHours(prefs.quietHoursStart, prefs.quietHoursEnd)) {
            com.evyr.rebuild.location.GeofenceDebugLog.log(applicationContext, "skipped: within quiet hours")
            return Result.success()
        }

        val place = repo.getGeofencePlace(placeId)
        if (place == null) {
            com.evyr.rebuild.location.GeofenceDebugLog.log(applicationContext, "skipped: place $placeId not found in database")
            return Result.success()
        }
        // Location objectives for this place: separate from the put-away
        // checklist (and its per-place arrival toggle), since tying an
        // objective to a place is itself the request to be told on arrival.
        val objectives = repo.pendingObjectivesForPlace(placeId)
        if (objectives.isNotEmpty()) {
            NotificationHelper(applicationContext).showPlaceObjectives(
                placeId, place.label,
                objectives.map { (if (it.zone == com.evyr.rebuild.data.Zone.HARD) "🔥 " else "") + it.title }
            )
            com.evyr.rebuild.location.GeofenceDebugLog.log(
                applicationContext, "${place.label}: ${objectives.size} location objective(s) shown"
            )
        }

        if (!place.notifyOnArrival) {
            com.evyr.rebuild.location.GeofenceDebugLog.log(applicationContext, "skipped: arrival nudge disabled for ${place.label}")
            return Result.success()
        }

        // The arriving checklist: NFC items on the IN list plus this place's
        // checklist items marked IN, counting anything done since you last
        // left (ENTER can land minutes late). Same list the card shows.
        val missing = com.evyr.rebuild.nfc.DoorChecklistItems
            .build(applicationContext, repo, com.evyr.rebuild.nfc.DoorMode.ARRIVING, placeId)
            .filter { it.doneAt == null }
            .map { it.title }
        com.evyr.rebuild.location.GeofenceDebugLog.log(
            applicationContext,
            "${place.label}: ${missing.size} missing NFC-linked item(s)" +
                if (missing.isNotEmpty()) " — ${missing.joinToString(", ")}" else ""
        )

        if (missing.isNotEmpty()) {
            NotificationHelper(applicationContext).showArrivalNudge(placeId, place.label, arrivalEpochMs, missing)
            com.evyr.rebuild.location.GeofenceDebugLog.log(applicationContext, "notification shown")
        }
        return Result.success()
    }
}
