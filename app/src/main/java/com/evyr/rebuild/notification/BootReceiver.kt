package com.evyr.rebuild.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.RebuildDatabase
import com.evyr.rebuild.location.GeofenceSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms everything the OS throws away:
 *  - Reboot clears AlarmManager alarms and every geofence.
 *  - An app update (MY_PACKAGE_REPLACED) is re-registered too, as cheap
 *    insurance -- each APK install from CI is an update.
 * Geofences used to be skipped here entirely, so after a restart departures
 * silently stopped firing until the Locations screen happened to be opened.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> "after reboot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "after app update"
            else -> return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AppRepository(RebuildDatabase.getInstance(context))
                ReminderScheduler.rescheduleAll(context, repo)
                GeofenceSync.reregister(context, reason)
            } finally {
                pending.finish()
            }
        }
    }
}
