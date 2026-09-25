package com.evyr.rebuild.nfc

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

enum class DoorMode { LEAVING, ARRIVING }

/**
 * Door checklist state. The same physical tag (keys, wallet) gets tapped both
 * when grabbing it and when putting it back, so a tap alone can't say which.
 * The door tag supplies that context:
 *
 *  - First door tap of the day is LEAVING, then it alternates (out, home,
 *    out...). The checklist card has a switch for when that guess is wrong.
 *  - Going ARRIVING opens a put-away window. Item taps inside it are
 *    recorded as "put away"; taps outside it as "grabbed".
 *
 * Kept in SharedPreferences rather than the database: it's device-local
 * session state, and item taps write here BEFORE the database update, so
 * when the tag list re-emits the checklist already sees the new times.
 */
object DoorState {

    private const val PREFS = "door_state"

    /** How long after walking in that item taps count as putting things away. */
    private const val PUT_AWAY_WINDOW_MS = 30 * 60 * 1000L

    /** While the Just Got Home card is on screen, keep the window open this far ahead. */
    private const val KEEP_OPEN_MS = 5 * 60 * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun todayKey() = LocalDate.now(ZoneId.systemDefault()).toString()

    fun startOfToday(): Long =
        LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** The door tag was tapped: pick the mode and record it. */
    fun modeForNewTap(context: Context): DoorMode {
        val p = prefs(context)
        val mode = when {
            p.getString("last_date", null) != todayKey() -> DoorMode.LEAVING
            p.getString("last_mode", null) == DoorMode.LEAVING.name -> DoorMode.ARRIVING
            else -> DoorMode.LEAVING
        }
        setMode(context, mode)
        return mode
    }

    /** Record a mode, from a door tap or the card's switch. */
    fun setMode(context: Context, mode: DoorMode) {
        val now = System.currentTimeMillis()
        val p = prefs(context)
        val e = p.edit()
            .putString("last_mode", mode.name)
            .putString("last_date", todayKey())
        when (mode) {
            DoorMode.ARRIVING -> e.putLong("arrived_at", now).putLong("window_end", now + PUT_AWAY_WINDOW_MS)
            // Heading out closes any open put-away window straight away.
            DoorMode.LEAVING -> if (p.getLong("window_end", 0L) > now) e.putLong("window_end", now)
        }
        e.apply()
    }

    /** While the Just Got Home card is showing, don't let the window lapse under it. */
    fun keepPutAwayWindowOpen(context: Context) {
        val p = prefs(context)
        val want = System.currentTimeMillis() + KEEP_OPEN_MS
        if (p.getLong("window_end", 0L) < want) p.edit().putLong("window_end", want).apply()
    }

    fun arrivedAt(context: Context): Long = prefs(context).getLong("arrived_at", 0L)

    private fun inPutAwayWindow(context: Context, now: Long) =
        now < prefs(context).getLong("window_end", 0L)

    /** Every item-tag tap goes through here, before the database is updated. */
    fun recordTap(context: Context, uid: String) {
        val now = System.currentTimeMillis()
        val key = if (inPutAwayWindow(context, now)) "put_$uid" else "grab_$uid"
        prefs(context).edit().putLong(key, now).apply()
    }

    fun grabbedAt(context: Context, uid: String): Long? =
        prefs(context).getLong("grab_$uid", 0L).takeIf { it > 0L }

    fun putAwayAt(context: Context, uid: String): Long? =
        prefs(context).getLong("put_$uid", 0L).takeIf { it > 0L }
}
