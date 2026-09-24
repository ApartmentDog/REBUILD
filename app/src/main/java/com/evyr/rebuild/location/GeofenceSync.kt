package com.evyr.rebuild.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.evyr.rebuild.data.RebuildDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The one place geofences get (re)registered from outside the Locations
 * screen. Android drops every app's geofences on reboot, after an app update
 * or reinstall, when Play services data is cleared, and when location is
 * switched off. Previously nothing re-registered them except opening the
 * Locations screen, so after any of those, departures silently stopped firing
 * until you happened to visit that screen.
 *
 * Always reads places from the database rather than taking a list from UI
 * state, so it can't run against an empty list that simply hasn't loaded yet.
 */
object GeofenceSync {

    private const val PREFS = "geofence_sync"

    /** Belt and braces: re-register on app open if the last success is older. */
    private const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Something dropped or failed to register the fences; redo it next chance. */
    fun markNeedsResync(context: Context) {
        prefs(context).edit().putBoolean("needs_resync", true).apply()
    }

    /**
     * For app open and the Locations screen: only re-registers when something
     * is known to be wrong or the last success is over a day old. Not on every
     * open, because each registration consumes one position report per place,
     * and opening the app right before walking out shouldn't risk the real
     * departure being mistaken for one.
     */
    suspend fun syncIfNeeded(context: Context, reason: String) {
        val p = prefs(context)
        val stale = System.currentTimeMillis() - p.getLong("last_ok", 0L) > STALE_AFTER_MS
        if (p.getBoolean("needs_resync", false) || stale) {
            reregister(context, reason)
        }
    }

    /** Clears whatever is registered and registers every saved place. */
    suspend fun reregister(context: Context, reason: String): Boolean {
        val app = context.applicationContext

        val fine = granted(app, Manifest.permission.ACCESS_FINE_LOCATION)
        val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(app, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (!fine || !background) {
            GeofenceDebugLog.log(
                app,
                "can't register geofences ($reason) — " +
                    if (!fine) "location permission not granted"
                    else "location isn't set to 'Allow all the time', so fences can't fire with the app closed"
            )
            markNeedsResync(app)
            return false
        }

        val places = RebuildDatabase.getInstance(app).geofencePlaceDao().observeAll().first()

        val result = withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine<Pair<Boolean, String?>> { cont ->
                GeofenceManager(app).reconcileGeofences(places) { ok, error ->
                    if (cont.isActive) cont.resume(ok to error)
                }
            }
        }

        val ok = result?.first == true
        if (ok) {
            prefs(app).edit()
                .putLong("last_ok", System.currentTimeMillis())
                .putBoolean("needs_resync", false)
                .apply()
        } else {
            markNeedsResync(app)
        }

        GeofenceDebugLog.log(
            app,
            when {
                result == null -> "geofence registration ($reason) timed out waiting on Play services"
                ok -> "registered ${places.size} place(s) — $reason"
                else -> "geofence registration failed ($reason): ${result.second}"
            }
        )
        return ok
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
