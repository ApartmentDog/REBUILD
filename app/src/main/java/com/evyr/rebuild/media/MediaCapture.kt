package com.evyr.rebuild.media

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creates a new file under app-private storage for camera/video capture output,
 * and returns a content:// URI via FileProvider that the system camera app can
 * write to. Files live in filesDir/media, matching file_paths.xml.
 */
object MediaCapture {
    private val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun createPhotoUri(context: Context): Pair<File, Uri> = createUri(context, "jpg")

    fun createVideoUri(context: Context): Pair<File, Uri> = createUri(context, "mp4")

    private fun createUri(context: Context, extension: String): Pair<File, Uri> {
        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(mediaDir, "REBUILD_${fmt.format(Date())}.$extension")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    /**
     * Copies a picked file into a named subdirectory of app-private storage and
     * returns the new absolute path, or null if the copy failed.
     *
     * subDir matters: "media" is swept into backup archives, "hidden" is not.
     */
    fun copyPickedFileIntoStorage(
        context: Context,
        sourceUri: Uri,
        subDir: String,
        extension: String
    ): String? {
        return try {
            val dir = File(context.filesDir, subDir).apply { mkdirs() }
            val dest = File(dir, "REBUILD_${fmt.format(Date())}.$extension")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Copies a video picked from the system file/gallery picker (content:// uri,
     * source unknown/untrusted lifetime) into app-private storage so it persists
     * and can be referenced by a stable file path. Returns the new file's absolute
     * path, or null if the copy failed (permission revoked, source deleted, etc).
     */
    fun copyPickedVideoIntoStorage(context: Context, sourceUri: Uri): String? {
        return try {
            val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
            val destFile = File(mediaDir, "REBUILD_ref_${fmt.format(Date())}.mp4")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            destFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}
