package com.evyr.rebuild.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Remembers whether today's mindset quote has already been shown, so it appears
 * once on the first unlock of each day rather than every time the app is opened.
 *
 * Plain SharedPreferences rather than a Room table: it's a single date string
 * with no history and no relationships, so a table (and the schema bump that
 * comes with it) would be overkill.
 */
class DailyQuoteTracker(context: Context) {

    private val prefs = context.getSharedPreferences("rebuild_daily_quote", Context.MODE_PRIVATE)
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun todayKey(): String = fmt.format(Date())

    /** True if the quote hasn't been shown yet today. */
    fun shouldShowToday(): Boolean = prefs.getString("last_shown_date", null) != todayKey()

    fun markShownToday() {
        prefs.edit().putString("last_shown_date", todayKey()).apply()
    }

    /** For testing / "show it again" -- clears today's marker. */
    fun reset() {
        prefs.edit().remove("last_shown_date").apply()
    }

    /**
     * Directive IDs of the last couple of days' quotes, most recent first.
     * DailyQuoteScreen excludes these from today's random pick when the
     * mindset pool is large enough to spare them, so a small handful of
     * lines doesn't keep repeating two days running.
     */
    fun recentlyShownIds(): List<Long> =
        prefs.getString("recent_ids", "")
            .orEmpty()
            .split(",")
            .mapNotNull { it.toLongOrNull() }

    /** Records today's pick and trims the history to the last 2. */
    fun recordShown(directiveId: Long) {
        val updated = (listOf(directiveId) + recentlyShownIds()).distinct().take(2)
        prefs.edit().putString("recent_ids", updated.joinToString(",")).apply()
    }
}
