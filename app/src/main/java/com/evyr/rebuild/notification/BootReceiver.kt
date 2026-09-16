package com.evyr.rebuild.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.RebuildDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager alarms are cleared on reboot -- this re-schedules every enabled
 * reminder when the device finishes booting, so a reminder set once keeps
 * firing daily across restarts rather than silently stopping after the next
 * time the phone powers off. The RECEIVE_BOOT_COMPLETED permission was already
 * declared in the manifest but nothing was actually registered to use it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AppRepository(RebuildDatabase.getInstance(context))
                ReminderScheduler.rescheduleAll(context, repo)
            } finally {
                pending.finish()
            }
        }
    }
}
