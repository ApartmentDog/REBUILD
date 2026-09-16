package com.evyr.rebuild.location

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persisted trail of geofence-related events, viewable from Settings without a
 * cable or adb. Built specifically because the broadcast receiver had several
 * silent-failure paths (malformed intent, event.hasError(), an unrecognized
 * geofence request ID) that returned with zero trace anywhere -- if something
 * upstream changed (a Play Services update, location mode change, permission
 * silently revoked), there was no way to tell "never received anything" apart
 * from "received something and quietly dropped it."
 *
 * Plain JSON array in SharedPreferences rather than a Room table: this is
 * diagnostic scratch data, not something that needs migrations, backup
 * inclusion, or querying -- a bounded ring buffer is simpler and sufficient.
 */
object GeofenceDebugLog {

    private const val PREFS = "geofence_debug_log"
    private const val KEY_EVENTS = "events"
    private const val MAX_EVENTS = 50

    private val timeFmt = SimpleDateFormat("MM/dd HH:mm:ss", Locale.US)

    fun log(context: Context, message: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_EVENTS, "[]") ?: "[]"
        val array = runCatching { JSONArray(existing) }.getOrDefault(JSONArray())

        val entry = JSONObject().apply {
            put("t", timeFmt.format(Date()))
            put("m", message)
        }

        // Insert at the front, trim from the back -- newest-first is what the
        // Settings viewer wants without needing to reverse it there.
        val trimmed = JSONArray()
        trimmed.put(entry)
        for (i in 0 until minOf(array.length(), MAX_EVENTS - 1)) {
            trimmed.put(array.get(i))
        }

        prefs.edit().putString(KEY_EVENTS, trimmed.toString()).apply()
    }

    fun readAll(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_EVENTS, "[]") ?: "[]"
        val array = runCatching { JSONArray(existing) }.getOrDefault(JSONArray())
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            "${obj.getString("t")} — ${obj.getString("m")}"
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
