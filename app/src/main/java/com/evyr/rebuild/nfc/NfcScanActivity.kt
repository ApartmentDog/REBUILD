package com.evyr.rebuild.nfc

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.RebuildDatabase
import com.evyr.rebuild.widget.RebuildWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles a tag tapped while REBUILD isn't open: records the scan, shows a toast,
 * and finishes without ever drawing a UI.
 *
 * Android has no true background listener for reading NFC tags -- the OS hands a
 * discovered tag to an Activity, full stop. So the closest thing to a silent
 * background scan is an Activity with a fully transparent theme that finishes
 * immediately. The screen flickers, a toast confirms, and you're done.
 *
 * Deliberately skips the PIN. The alternative is dumping you on the lock screen
 * every time you tap the wallet tray on your way out, which defeats the point.
 * The exposure is narrow: someone holding both the phone and a registered tag can
 * mark that one item complete and see nothing else -- no data, no navigation.
 *
 * Two Android constraints that no amount of code changes:
 *   - the phone must be unlocked; NFC doesn't dispatch to apps on a locked screen
 *   - TAG_DISCOVERED is the lowest-priority filter, so another NFC app registering
 *     something more specific can intercept these tags first
 */
class NfcScanActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handle(it) }
    }

    private fun handle(intent: Intent) {
        val helper = NfcHelper()
        val uid = helper.extractTagUid(intent)

        if (uid == null) {
            finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val repo = AppRepository(RebuildDatabase.getInstance(applicationContext))

            // Door tag: show the checklist instead of ticking anything off.
            if (repo.findTag(uid)?.opensChecklist == true) {
                withContext(Dispatchers.Main) {
                    startActivity(
                        Intent(applicationContext, DoorChecklistActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    finish()
                }
                return@launch
            }

            // Grab vs put-away, recorded before the database update so the
            // door checklist sees it when the tag list re-emits.
            DoorState.recordTap(applicationContext, uid)
            val outcome = repo.handleTagScan(uid, locationLabel = null)
            val message = when {
                outcome == null -> "Unregistered tag — open REBUILD to set it up"
                outcome.completed -> "${outcome.directiveTitle ?: "Scanned"} ✓"
                // Mid-progress on a multi-tap goal: show how many are left rather
                // than a bare checkmark that would imply it's finished.
                outcome.required > 1 -> "${outcome.directiveTitle}: ${outcome.count}/${outcome.required}"
                else -> "${outcome.directiveTitle ?: "Scanned"} ✓"
            }

            RebuildWidgetProvider.refresh(applicationContext)

            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
