package com.evyr.rebuild.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.evyr.rebuild.data.GeofencePlace
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Registers geofences for "arrive at / leave home" style reminders.
 * Requires ACCESS_FINE_LOCATION + ACCESS_BACKGROUND_LOCATION permissions granted
 * before calling registerGeofence, and Google Play services location artifact
 * (com.google.android.gms:play-services-location) in build.gradle.
 *
 * IMPORTANT: Android requires background location to be requested as a *separate*,
 * clearly-explained permission step after foreground location is already granted —
 * see PermissionFlow.kt for the staged request UX.
 */
class GeofenceManager(private val context: Context) {

    companion object {
        /** 0 asks Play Services for its fastest available detection instead of
         *  waiting up to a fixed delay. Real-world lag still won't hit zero --
         *  Play Services batches location checks against battery, typically
         *  landing somewhere in the 1-3 minute range regardless of this
         *  setting -- but this removes the 5-minute ceiling that was here
         *  before and uses more battery for it. */
        private const val RESPONSIVENESS_MS = 0
    }


    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    /**
     * Registers with Google's Geofencing API. addGeofences() is asynchronous and can
     * fail -- most commonly background location not fully granted, Play services
     * unavailable, or the device's geofence limit hit -- and until now nothing
     * observed the result at all. The GeofencePlace row always saves to the local
     * database regardless of whether this succeeds, since that's a separate local
     * insert, so a failed registration looked identical to a working one: the place
     * would show up correctly configured in Locations while never actually
     * triggering an ENTER/EXIT, silently, with no error surfaced anywhere.
     */
    @SuppressLint("MissingPermission") // caller must verify permissions before invoking
    fun registerGeofence(
        place: GeofencePlace,
        onResult: (success: Boolean, error: String?) -> Unit = { _, _ -> }
    ) {
        val geofence = Geofence.Builder()
            .setRequestId("place_${place.id}")
            .setCircularRegion(place.latitude, place.longitude, place.radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            // Responsiveness defaults to 0 -- "report the instant anything looks
            // different" -- which makes Play Services act on single noisy fixes
            // and announce crossings that never happened. A few minutes of slack
            // lets it corroborate before reporting.
            .setNotificationResponsiveness(RESPONSIVENESS_MS)
            .build()

        // Report which side of the fence the phone is on right now, so the
        // app starts from a known position; the receiver records that report
        // as state and fires nothing (see GeofencePresence.markSyncPending).
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)
            .addGeofence(geofence)
            .build()

        GeofencePresence.markSyncPending(context, listOf(place.id))
        geofencingClient.addGeofences(request, geofencePendingIntent())
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message ?: e.javaClass.simpleName) }
    }

    /**
     * Unregisters a single geofence by its request id. Same asynchronous-failure
     * risk as registerGeofence above -- removeGeofences() can fail (Play
     * services unavailable, transient error) with nothing observing it, which
     * is exactly how a deleted GeofencePlace can leave an orphaned OS-level
     * geofence still firing ENTER/EXIT for a place ID the database no longer
     * has a row for. Now takes the same onResult callback pattern.
     */
    fun removeGeofence(place: GeofencePlace, onResult: (success: Boolean, error: String?) -> Unit = { _, _ -> }) {
        geofencingClient.removeGeofences(listOf("place_${place.id}"))
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message ?: e.javaClass.simpleName) }
    }

    /**
     * Removes every geofence currently registered with Play Services, then
     * re-registers only the places passed in -- cleans up orphans left behind
     * by a past removeGeofence() call that failed silently (see
     * removeGeofence's doc comment above). Play Services has no "list all my
     * registered geofences" API to diff against, so surgical removal of just
     * the stale ones isn't possible; clearing everything under this app's
     * PendingIntent and re-adding the current set is blunt but reliable --
     * no partial/orphaned state can survive a reconciliation pass.
     */
    @SuppressLint("MissingPermission") // caller must verify permissions before invoking
    fun reconcileGeofences(
        currentPlaces: List<GeofencePlace>,
        onResult: (success: Boolean, error: String?) -> Unit = { _, _ -> }
    ) {
        geofencingClient.removeGeofences(geofencePendingIntent())
            .addOnSuccessListener {
                if (currentPlaces.isEmpty()) {
                    onResult(true, null)
                    return@addOnSuccessListener
                }
                val geofences = currentPlaces.map { place ->
                    Geofence.Builder()
                        .setRequestId("place_${place.id}")
                        .setCircularRegion(place.latitude, place.longitude, place.radiusMeters)
                        .setExpirationDuration(Geofence.NEVER_EXPIRE)
                        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                        // Responsiveness defaults to 0 -- "report the instant anything looks
                        // different" -- which makes Play Services act on single noisy fixes
                        // and announce crossings that never happened. A few minutes of slack
                        // lets it corroborate before reporting.
                        .setNotificationResponsiveness(RESPONSIVENESS_MS)
                        .build()
                }
                // Previously no initial trigger here, to avoid synthetic
                // reminders. But that left the app blind to where the phone
                // actually was after re-registering, so a stale "outside" made
                // the next real departure get ignored. Now Play Services reports
                // the current side of every fence, and the receiver records
                // those reports silently as state (markSyncPending below).
                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_EXIT)
                    .addGeofences(geofences)
                    .build()
                GeofencePresence.markSyncPending(context, currentPlaces.map { it.id })
                geofencingClient.addGeofences(request, geofencePendingIntent())
                    .addOnSuccessListener { onResult(true, null) }
                    .addOnFailureListener { e -> onResult(false, e.message ?: e.javaClass.simpleName) }
            }
            .addOnFailureListener { e -> onResult(false, e.message ?: e.javaClass.simpleName) }
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}
