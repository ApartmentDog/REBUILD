package com.evyr.rebuild.location

import android.content.Context

/**
 * Tracks which geofence places you're currently inside, so a time-based
 * reminder can be gated on location without taking a fresh GPS fix when it
 * fires.
 *
 * Written by GeofenceBroadcastReceiver as ENTER/EXIT events arrive. That means
 * it's only as good as those events: if the app misses a transition, presence
 * is stale until the next one. A reminder gated on AT_PLACE therefore fails
 * closed (stays quiet) rather than firing on a guess -- an occasional missed
 * nudge beats one that goes off at work.
 */
object GeofencePresence {

    private const val PREFS = "geofence_presence"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun setInside(context: Context, placeId: Long, inside: Boolean) {
        prefs(context).edit().putBoolean("place_$placeId", inside).apply()
    }

    /** Null when we've never seen a transition for this place -- distinct from
     *  a known "outside", so callers can tell "not there" from "no idea". */
    fun isInside(context: Context, placeId: Long): Boolean? {
        val p = prefs(context)
        val key = "place_$placeId"
        return if (p.contains(key)) p.getBoolean(key, false) else null
    }

    fun clear(context: Context, placeId: Long) {
        prefs(context).edit().remove("place_$placeId").apply()
    }
}
