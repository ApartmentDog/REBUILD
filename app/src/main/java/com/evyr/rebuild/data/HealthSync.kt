package com.evyr.rebuild.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Ticks one chosen directive (the workout) when Samsung Health logs an
 * exercise session of MIN_MINUTES or more today, read through Health
 * Connect. Forgetting to tick is a real source of false misses; a record
 * that fills itself in is also more honest than memory at 11pm.
 * Read-only, one permission. Runs on app resume and before the evening
 * digest. The linked directive lives in prefs (rebuild_health).
 */
object HealthSync {
    private const val PREFS = "rebuild_health"
    private const val KEY_DIRECTIVE = "workout_directive_id"
    const val MIN_MINUTES = 10L

    val PERMISSIONS: Set<String> = setOf(HealthPermission.getReadPermission(ExerciseSessionRecord::class))

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAvailable(context: Context): Boolean =
        runCatching { HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE }.getOrDefault(false)

    fun linkedDirective(context: Context): Long? =
        prefs(context).getLong(KEY_DIRECTIVE, -1L).takeIf { it > 0 }

    fun setLinkedDirective(context: Context, id: Long?) {
        prefs(context).edit().apply { if (id == null) remove(KEY_DIRECTIVE) else putLong(KEY_DIRECTIVE, id) }.apply()
    }

    suspend fun hasPermission(context: Context): Boolean = runCatching {
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }.getOrDefault(false)

    /** True if it ticked the directive just now. */
    suspend fun syncToday(context: Context, repo: AppRepository): Boolean {
        val id = linkedDirective(context) ?: return false
        if (!isAvailable(context) || !hasPermission(context)) return false
        if (repo.completedAtToday(id) != null) return false
        return runCatching {
            val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
            val response = HealthConnectClient.getOrCreate(context).readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, Instant.now())
                )
            )
            val hit = response.records.any { Duration.between(it.startTime, it.endTime).toMinutes() >= MIN_MINUTES }
            if (hit) repo.markComplete(id, source = "health")
            hit
        }.getOrDefault(false)
    }
}
