package com.evyr.rebuild.data

import android.content.Context

/**
 * Tracks the sealed cheat code that 🔥 hard objectives unlock.
 *
 * The unlock is latched in SharedPreferences: the live count comes from the
 * database (cleared HARD directives), but deleting a cleared objective later
 * shouldn't re-seal something already earned. A restore onto a fresh install
 * re-latches on its own as long as five cleared objectives come back with it.
 */
class HardModeVault(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("rebuild_vault", Context.MODE_PRIVATE)

    fun isUnlocked(clearedNow: Int): Boolean {
        if (prefs.getBoolean(KEY_UNLOCKED, false)) return true
        if (clearedNow >= REQUIRED) {
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
        const val REQUIRED = 5
        const val CODE = "HOARD"
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_REVEALED = "revealed"
    }
}
