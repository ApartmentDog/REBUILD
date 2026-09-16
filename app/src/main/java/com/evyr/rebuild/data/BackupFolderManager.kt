package com.evyr.rebuild.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Remembers the folder the user picked for automatic backups, plus the outcome of
 * the last run so Settings can show it instead of being a black box.
 *
 * The folder is a Storage Access Framework tree URI with persisted read/write
 * permission. That persistence is the whole point: a weekly background job has no
 * user present to pick a destination, and anything written to app-private storage
 * would be deleted by the uninstall it's meant to protect against. Pointing this
 * at a cloud-synced folder (Drive, etc.) means the backup leaves the device.
 */
class BackupFolderManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("rebuild_backup", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FOLDER = "folder_uri"
        private const val KEY_LAST_AT = "last_backup_at"
        private const val KEY_LAST_STATUS = "last_backup_status"
    }

    /** Takes persistable permission so the folder stays writable after a reboot. */
    fun setFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs.edit().putString(KEY_FOLDER, uri.toString()).apply()
    }

    fun folderUri(): Uri? = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse)

    fun hasFolder(): Boolean = folderUri() != null

    /** Human-readable folder name for Settings, or null if none picked. */
    fun folderLabel(): String? {
        val uri = folderUri() ?: return null
        return runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
            ?: uri.lastPathSegment
    }

    /**
     * Creates a new timestamped zip in the chosen folder and returns its URI.
     * Null if no folder is set or the folder is no longer writable (moved,
     * deleted, or permission revoked).
     */
    fun createBackupFile(): Uri? {
        val tree = folderUri() ?: return null
        val dir = runCatching { DocumentFile.fromTreeUri(context, tree) }.getOrNull() ?: return null
        if (!dir.canWrite()) return null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return dir.createFile("application/zip", "rebuild_backup_$stamp.zip")?.uri
    }

    fun recordResult(success: Boolean, detail: String) {
        prefs.edit()
            .putLong(KEY_LAST_AT, System.currentTimeMillis())
            .putString(KEY_LAST_STATUS, (if (success) "OK — " else "FAILED — ") + detail)
            .apply()
    }

    fun lastBackupAtEpochMs(): Long = prefs.getLong(KEY_LAST_AT, 0L)

    fun lastBackupStatus(): String? = prefs.getString(KEY_LAST_STATUS, null)
}
