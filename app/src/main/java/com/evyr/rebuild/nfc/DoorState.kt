package com.evyr.rebuild.nfc

import android.content.Context
import com.evyr.rebuild.data.NfcTag
import java.time.LocalDate
import java.time.ZoneId

enum class DoorMode { LEAVING, ARRIVING }

/**
 * Leaving / arriving state for the checklists, driven by geofence EXIT and
 * ENTER (see GeofenceBroadcastReceiver). The card's switch can override it.
 *
 * The same physical tag (keys, wallet) gets tapped both when grabbing it and
 * when putting it back, so a tap alone can't say which. Arriving opens a
 * put-away window: item taps inside it count as "put away", taps outside it
 * as "grabbed". Geofence ENTER can land minutes after you walked in, so on
 * ENTER any taps since you left are re-counted as put-away too.
 *
 * Kept in SharedPreferences: device-local session state. Item taps write
 * here BEFORE the database update, so an open checklist sees the new times
 * when the tag list re-emits.
 */
object DoorState {

    private const val PREFS = "door_state"

    /** How long after arriving that item taps count as putting things away. */
    private const val PUT_AWAY_WINDOW_MS = 30 * 60 * 1000L

    /** While the arriving card is on screen, keep the window open this far ahead. */
    private const val KEEP_OPEN_MS = 5 * 60 * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun startOfToday(): Long =
        LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Current mode, as last set by a geofence event or the card's switch. */
    fun currentMode(context: Context): DoorMode =
        prefs(context).getString("last_mode", null)?.let { runCatching { DoorMode.valueOf(it) }.getOrNull() }
            ?: DoorMode.LEAVING

    /** The place of the last geofence event, so the door tag's card knows its checklist. */
    fun lastPlaceId(context: Context): Long? =
        prefs(context).getLong("last_place", -1L).takeIf { it >= 0L }

    /** Geofence EXIT: heading out. Closes any open put-away window. */
    fun onGeofenceExit(context: Context, placeId: Long) {
        prefs(context).edit().putLong("last_place", placeId).apply()
        setMode(context, DoorMode.LEAVING)
    }

    /**
     * Geofence ENTER: arriving. [leftAt] is when you last left this place; taps
     * between then and now were you putting things back before the (late)
     * ENTER arrived, so they're moved from "grabbed" to "put away".
     */
    fun onGeofenceEnter(context: Context, placeId: Long, leftAt: Long) {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val e = p.edit()
        if (leftAt > 0L) {
            p.all.forEach { (key, value) ->
                if (key.startsWith("grab_") && value is Long && value > leftAt) {
                    e.remove(key)
                    val putKey = "put_" + key.removePrefix("grab_")
                    if (p.getLong(putKey, 0L) < value) e.putLong(putKey, value)
                }
            }
        }
        e.putLong("last_place", placeId)
            .putString("last_mode", DoorMode.ARRIVING.name)
            .putLong("arrived_at", if (leftAt > 0L) leftAt else now)
            .putLong("window_end", now + PUT_AWAY_WINDOW_MS)
            .apply()
    }

    /** Manual switch on the card. */
    fun setMode(context: Context, mode: DoorMode) {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val e = p.edit().putString("last_mode", mode.name)
        when (mode) {
            DoorMode.ARRIVING -> e.putLong("arrived_at", now).putLong("window_end", now + PUT_AWAY_WINDOW_MS)
            DoorMode.LEAVING -> if (p.getLong("window_end", 0L) > now) e.putLong("window_end", now)
        }
        e.apply()
    }

    /** While the arriving card is showing, don't let the window lapse under it. */
    fun keepPutAwayWindowOpen(context: Context) {
        val p = prefs(context)
        val want = System.currentTimeMillis() + KEEP_OPEN_MS
        if (p.getLong("window_end", 0L) < want) p.edit().putLong("window_end", want).apply()
    }

    fun arrivedAt(context: Context): Long = prefs(context).getLong("arrived_at", 0L)

    /** Every item-tag tap goes through here, before the database is updated. */
    fun recordTap(context: Context, uid: String) {
        val now = System.currentTimeMillis()
        val inWindow = now < prefs(context).getLong("window_end", 0L)
        prefs(context).edit().putLong(if (inWindow) "put_$uid" else "grab_$uid", now).apply()
    }

    private fun grabbedAt(context: Context, uid: String): Long? =
        prefs(context).getLong("grab_$uid", 0L).takeIf { it > 0L }

    private fun putAwayAt(context: Context, uid: String): Long? =
        prefs(context).getLong("put_$uid", 0L).takeIf { it > 0L }

    /**
     * Leaving: when this tag was grabbed since it was last put away (and
     * today), or null. Taps from before grab/put-away tracking existed fall
     * back to the tag's last scan time.
     */
    fun grabbedSincePutAway(context: Context, tag: NfcTag): Long? {
        val putAway = putAwayAt(context, tag.tagUid)
        val grabbed = grabbedAt(context, tag.tagUid)
            ?: tag.lastScanEpochMs.takeIf { putAway == null }
            ?: return null
        val cutoff = maxOf(startOfToday(), putAway ?: 0L)
        return grabbed.takeIf { it >= cutoff }
    }

    /** Arriving: when this tag was put away since you got in, or null. */
    fun putAwaySinceArriving(context: Context, tag: NfcTag): Long? {
        val arrived = arrivedAt(context).takeIf { it > 0L } ?: return null
        return putAwayAt(context, tag.tagUid)?.takeIf { it >= arrived }
    }
}
