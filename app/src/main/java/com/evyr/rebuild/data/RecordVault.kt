package com.evyr.rebuild.data

import android.content.Context

/**
 * A second sealed code, earned by the first real milestone: a graduated
 * habit, or a 30-day run on a standing goal. Latched like HardModeVault, so
 * it stays open once earned.
 */
class RecordVault(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("rebuild_record", Context.MODE_PRIVATE)

    fun isUnlocked(earnedNow: Boolean): Boolean {
        if (prefs.getBoolean(KEY_UNLOCKED, false)) return true
        if (earnedNow) {
            prefs.edit().putBoolean(KEY_UNLOCKED, true).apply()
            return true
        }
        return false
    }

    /** True exactly once: the first time the code is shown after unlocking. */
    fun consumeReveal(): Boolean {
        if (!prefs.getBoolean(KEY_UNLOCKED, false)) return false
        if (prefs.getBoolean(KEY_REVEALED, false)) return false
        prefs.edit().putBoolean(KEY_REVEALED, true).apply()
        return true
    }

    companion object {
        const val CODE = "DOSSIER"
        const val RUN_DAYS = 30
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_REVEALED = "revealed"

        /** The milestone: any graduated habit, or a standing run of RUN_DAYS. */
        fun earned(directives: List<Directive>, streaks: List<Streak>): Boolean =
            directives.any { it.graduatedEpochMs != null } || streaks.any { it.currentStreakDays >= RUN_DAYS }
    }
}
