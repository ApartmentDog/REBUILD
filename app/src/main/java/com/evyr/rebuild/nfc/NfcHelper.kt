package com.evyr.rebuild.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag

/**
 * Wraps foreground-dispatch NFC reading. Call enableForegroundDispatch in onResume
 * and disableForegroundDispatch in onPause of the hosting Activity.
 *
 * Usage in MainActivity:
 *   private lateinit var nfcHelper: NfcHelper
 *   override fun onResume() { super.onResume(); nfcHelper.enableForegroundDispatch(this) }
 *   override fun onPause() { super.onPause(); nfcHelper.disableForegroundDispatch(this) }
 *   override fun onNewIntent(intent: Intent) {
 *       super.onNewIntent(intent)
 *       nfcHelper.extractTagUid(intent)?.let { uid -> viewModel.handleTagScan(uid, locationLabel = null) }
 *   }
 */
class NfcHelper {

    private var nfcAdapter: NfcAdapter? = null

    fun init(activity: Activity) {
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
    }

    fun isNfcAvailable(): Boolean = nfcAdapter != null

    fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled == true

    fun enableForegroundDispatch(activity: Activity) {
        val adapter = nfcAdapter ?: return
        val intent = Intent(activity, activity.javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            activity, 0, intent,
            PendingIntent.FLAG_MUTABLE
        )
        // A null IntentFilter[] here is unreliable across OEM NFC stacks -- some
        // devices simply never route the tap through foreground dispatch without
        // explicit filters, even though the manifest declares TAG_DISCOVERED.
        // Covering both TAG_DISCOVERED (any tag) and NDEF_DISCOVERED (NDEF-formatted
        // tags specifically) so nothing gets missed regardless of tag type.
        val tagFilter = android.content.IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        val ndefFilter = android.content.IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        val filters = arrayOf(tagFilter, ndefFilter)
        adapter.enableForegroundDispatch(activity, pendingIntent, filters, null)
    }

    fun disableForegroundDispatch(activity: Activity) {
        nfcAdapter?.disableForegroundDispatch(activity)
    }

    /**
     * Writes the label as an NDEF text record to the tag currently in the intent.
     * Must be called from onNewIntent while the tag is still held to the phone.
     * Returns a WriteResult describing exactly what happened instead of a bare boolean,
     * so the UI can show *why* a write failed rather than pretending it succeeded.
     */
    fun writeLabelToTag(intent: Intent, label: String): WriteResult {
        val tag: android.nfc.Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            ?: return WriteResult.Error("No tag detected in intent")
        val ndef = android.nfc.tech.Ndef.get(tag)
            ?: return WriteResult.NotNdefCapable

        return try {
            ndef.connect()
            if (!ndef.isWritable) {
                ndef.close()
                return WriteResult.NotWritable
            }
            val record = android.nfc.NdefRecord.createTextRecord("en", label)
            val message = android.nfc.NdefMessage(arrayOf(record))
            if (message.byteArrayLength > ndef.maxSize) {
                ndef.close()
                return WriteResult.TooSmall(needed = message.byteArrayLength, available = ndef.maxSize)
            }
            ndef.writeNdefMessage(message)
            ndef.close()
            WriteResult.Success
        } catch (e: Exception) {
            WriteResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Reads back the label from a tag's NDEF message, for write verification.
     * Returns null if the tag has no NDEF message, no text record, or reading fails.
     */
    fun readLabelFromTag(intent: Intent): String? {
        val tag: android.nfc.Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return null
        val ndef = android.nfc.tech.Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            val message = ndef.ndefMessage ?: return null
            ndef.close()
            message.records.firstOrNull()?.let { record ->
                val payload = record.payload
                if (payload.isEmpty()) return null
                val languageCodeLength = payload[0].toInt() and 0x3F
                String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Result of an NFC NDEF write attempt. */
    sealed class WriteResult {
        object Success : WriteResult()
        object NotNdefCapable : WriteResult()
        object NotWritable : WriteResult()
        data class TooSmall(val needed: Int, val available: Int) : WriteResult()
        data class Error(val message: String) : WriteResult()
    }

    /** Extracts a stable hex UID string from a scanned tag intent, or null if not an NFC tag intent. */
    fun extractTagUid(intent: Intent): String? {
        val tag: Tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return null
        return tag.id.joinToString("") { "%02X".format(it) }
    }
}
