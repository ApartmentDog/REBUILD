package com.evyr.rebuild.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Writes a full backup zip into the folder chosen in Settings.
 *
 * Scheduled weekly for Tuesday 00:01. WorkManager guarantees the work runs, not
 * that it runs at that exact minute -- Doze and app standby can push it later,
 * and it will run at the next opportunity instead of being skipped. For a backup
 * that's the right trade: reliability over punctuality.
 */
class WeeklyBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val folders = BackupFolderManager(applicationContext)

        val target = folders.createBackupFile()
        if (target == null) {
            folders.recordResult(false, "no backup folder set, or it's no longer writable")
            // Not a retry: nothing will change until the user picks a folder again.
            return Result.success()
        }

        val db = RebuildDatabase.getInstance(applicationContext)
        return when (val r = BackupArchive.writeTo(applicationContext, db, target)) {
            is BackupArchive.Result.Success -> {
                folders.recordResult(true, "${r.rowCount} rows, ${r.mediaCount} media files")
                Result.success()
            }
            is BackupArchive.Result.Error -> {
                folders.recordResult(false, r.message)
                Result.retry()
            }
        }
    }

    companion object {
        private const val WORK_NAME = "rebuild_weekly_backup"

        /**
         * Milliseconds from now until the next Tuesday at 00:01 local time. If it's
         * currently Tuesday but past 00:01, this lands on next week's Tuesday rather
         * than firing immediately.
         */
        private fun delayToNextTuesday0001(): Long {
            val now = Calendar.getInstance()
            val next = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!next.after(now)) next.add(Calendar.WEEK_OF_YEAR, 1)
            return next.timeInMillis - now.timeInMillis
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyBackupWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delayToNextTuesday0001(), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            // KEEP: don't reset the schedule (and push the next run out a full week)
            // every time the app launches.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
