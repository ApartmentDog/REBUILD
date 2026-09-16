package com.evyr.rebuild.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.RebuildDatabase
import java.util.Calendar

/**
 * Scheduled by GeofenceBroadcastReceiver on EXIT. Checks whether NFC-linked Daily
 * directives (wallet, keys, etc.) have been scanned yet today — only nags about
 * items that are still missing. Fires nothing if everything's already been scanned.
 * Unlike ArrivalNudgeWorker this fires immediately, no delay, since the exit itself
 * is the moment that matters ("did I grab it on the way out").
 */
class DepartureCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_PLACE_ID = "place_id"
    }

    override suspend fun doWork(): Result {
        val placeId = inputData.getLong(KEY_PLACE_ID, -1L)
        com.evyr.rebuild.location.GeofenceDebugLog.log(applicationContext, "DepartureCheckWorker running for place $placeId")

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
        if (!place.notifyOnDeparture) {
            com.evyr.rebuild.location.GeofenceDebugLog.log(applicationContext, "skipped: departure nudge disabled for ${place.label}")
            return Result.success()
        }

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val missing = repo.nfcLinkedDirectivesMissingSince(startOfToday, placeId)
        com.evyr.rebuild.location.GeofenceDebugLog.log(
            applicationContext,
            "${place.label}: ${missing.size} missing NFC-linked item(s)" +
                if (missing.isNotEmpty()) " — ${missing.joinToString(", ")}" else ""
        )

        if (missing.isNotEmpty()) {
            NotificationHelper(applicationContext).showDepartureNudge(place.label, missing)
            com.evyr.rebuild.location.GeofenceDebugLog.log(applicationContext, "notification shown")
        }
        return Result.success()
    }
}
