package com.evyr.rebuild.data

import android.content.Context

/**
 * "First thing tomorrow", set in the evening check-in and opened by the
 * morning summary. Deciding the night before takes one decision out of the
 * morning. Per date, so a stale plan never shows on the wrong day.
 */
object DayPlan {
    private const val PREFS = "rebuild_dayplan"

    fun setFirstMove(context: Context, date: String, text: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("first_$date", text.trim().ifBlank { null }).apply()
    }

    fun firstMove(context: Context, date: String): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("first_$date", null)
}
