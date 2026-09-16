package com.evyr.rebuild.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full backup: a zip containing the whole database as JSON plus every media file
 * (notebook photos/videos, guided-session reference clips).
 *
 * Layout inside the archive:
 *   manifest.json      -- BackupEnvelope, every table
 *   media/<filename>   -- each file from filesDir/media, flat, original filenames
 *
 * Why paths get rewritten on restore: MediaAttachment.filePath and
 * SessionStep.referenceVideoPath hold absolute paths into app-private storage,
 * and that directory is wiped and recreated on reinstall. Restoring the JSON
 * alone would leave every attachment pointing at a file that no longer exists,
 * and it would fail silently -- entries would just show nothing. So media is
 * unpacked first, then each stored path is remapped by filename to its new
 * location before the rows are written.
 *
 * Not included, deliberately: the vision API key and the app PIN. Both live in
 * EncryptedSharedPreferences tied to this device's keystore, so they can't be
 * exported in any form that would decrypt elsewhere. After restoring onto a
 * fresh install you'll re-enter the API key and set a new PIN.
 */
object BackupArchive {

    private const val MANIFEST = "manifest.json"
    private const val MEDIA_DIR = "media"
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    sealed class Result {
        data class Success(val rowCount: Int, val mediaCount: Int, val bytes: Long) : Result()
        data class Error(val message: String) : Result()
    }

    private fun mediaDir(context: Context) = File(context.filesDir, MEDIA_DIR).apply { mkdirs() }

    suspend fun writeTo(context: Context, db: RebuildDatabase, out: Uri): Result =
        withContext(Dispatchers.IO) {
            try {
                val envelope = BackupManager.buildEnvelope(db)
                var mediaCount = 0

                context.contentResolver.openOutputStream(out)?.use { raw ->
                    ZipOutputStream(raw.buffered()).use { zip ->
                        zip.putNextEntry(ZipEntry(MANIFEST))
                        zip.write(json.encodeToString(BackupEnvelope.serializer(), envelope).toByteArray())
                        zip.closeEntry()

                        mediaDir(context).listFiles()?.forEach { f ->
                            if (f.isFile) {
                                zip.putNextEntry(ZipEntry("$MEDIA_DIR/${f.name}"))
                                f.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                                mediaCount++
                            }
                        }
                    }
                } ?: return@withContext Result.Error("Couldn't open that location for writing")

                val size = runCatching {
                    context.contentResolver.openFileDescriptor(out, "r")?.use { it.statSize }
                }.getOrNull() ?: 0L

                Result.Success(BackupManager.countRows(envelope), mediaCount, size)
            } catch (e: Exception) {
                Result.Error(e.message ?: "Backup failed")
            }
        }

    suspend fun restoreFrom(context: Context, db: RebuildDatabase, input: Uri): Result =
        withContext(Dispatchers.IO) {
            try {
                var manifestJson: String? = null
                val restoredMedia = mutableMapOf<String, String>() // filename -> new absolute path
                val dir = mediaDir(context)

                context.contentResolver.openInputStream(input)?.use { raw ->
                    ZipInputStream(raw.buffered()).use { zip ->
                        var entry = zip.nextEntry
                        while (entry != null) {
                            when {
                                entry.name == MANIFEST -> manifestJson = zip.readBytes().decodeToString()
                                entry.name.startsWith("$MEDIA_DIR/") && !entry.isDirectory -> {
                                    val name = entry.name.substringAfterLast('/')
                                    if (name.isNotBlank()) {
                                        val dest = File(dir, name)
                                        dest.outputStream().use { zip.copyTo(it) }
                                        restoredMedia[name] = dest.absolutePath
                                    }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                    }
                } ?: return@withContext Result.Error("Couldn't read that file")

                val text = manifestJson
                    ?: return@withContext Result.Error("That zip has no manifest.json — not a REBUILD backup")

                val envelope = json.decodeFromString(BackupEnvelope.serializer(), text)

                // Remap stored absolute paths onto the freshly-unpacked files.
                // Matching is by filename: the directory changes across installs,
                // the filename doesn't.
                fun remap(old: String?): String? {
                    if (old.isNullOrBlank()) return old
                    return restoredMedia[File(old).name] ?: old
                }

                val fixed = envelope.copy(
                    mediaAttachments = envelope.mediaAttachments.map {
                        it.copy(filePath = remap(it.filePath) ?: it.filePath)
                    },
                    sessionSteps = envelope.sessionSteps.map {
                        it.copy(referenceVideoPath = remap(it.referenceVideoPath))
                    }
                )

                BackupManager.applyEnvelope(db, fixed)
                Result.Success(BackupManager.countRows(fixed), restoredMedia.size, 0L)
            } catch (e: kotlinx.serialization.SerializationException) {
                Result.Error("That file doesn't look like a valid REBUILD backup")
            } catch (e: Exception) {
                Result.Error(e.message ?: "Restore failed")
            }
        }
}
