package com.evyr.rebuild.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.evyr.rebuild.MainActivity
import com.evyr.rebuild.R
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.data.RebuildDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Wide home-screen widget listing today's still-open Daily directives, with
 * one-tap completion.
 *
 * Directives flagged requiresNfcScan are shown but not tappable -- gating them
 * behind a physical tag scan is the entire point of that flag, and letting the
 * widget bypass it would quietly defeat it. They're marked [NFC] so it's obvious
 * why tapping does nothing.
 */
class RebuildWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_COMPLETE = "com.evyr.rebuild.widget.COMPLETE"
        const val EXTRA_DIRECTIVE_ID = "directive_id"
        // The layout has 10 rows; how many are used depends on how tall the
        // widget has been resized (see rowsFor). 4 fit at the default 2-cell height.
        private const val MIN_ROWS = 4
        private const val MAX_ROWS = 10
        private const val BASE_HEIGHT_DP = 110
        private const val ROW_HEIGHT_DP = 24
        private val ROW_IDS = intArrayOf(
            R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
            R.id.widget_row_4, R.id.widget_row_5, R.id.widget_row_6, R.id.widget_row_7,
            R.id.widget_row_8, R.id.widget_row_9
        )
        // Rows are now a container (accent bar + text) rather than a bare
        // TextView, so visibility and the tap PendingIntent apply to the
        // container -- toggling only the inner TextView would leave a bare
        // accent bar visible with no text next to it when a row is meant to be
        // hidden entirely.
        private val ROW_CONTAINER_IDS = intArrayOf(
            R.id.widget_row_0_container, R.id.widget_row_1_container,
            R.id.widget_row_2_container, R.id.widget_row_3_container,
            R.id.widget_row_4_container, R.id.widget_row_5_container,
            R.id.widget_row_6_container, R.id.widget_row_7_container,
            R.id.widget_row_8_container, R.id.widget_row_9_container
        )

        /** Redraws every placed instance. Call after anything that changes completion state. */
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, RebuildWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, RebuildWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // goAsync tells Android this provider isn't done when onUpdate returns.
        // Previously the query-and-redraw work ran in a plain fire-and-forget
        // CoroutineScope(...).launch with no lifecycle tether, so on phones that
        // kill background work aggressively the process could die before the
        // database query and RemoteViews update ever completed -- which shows up
        // exactly as "the widget is blank." A single tethered coroutine per
        // onUpdate call, rather than one per row/widget instance, so there's one
        // clear finish() and no risk of finishing early while a sibling is
        // mid-query.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AppRepository(RebuildDatabase.getInstance(context))
                appWidgetIds.forEach { render(context, manager, repo, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_COMPLETE) {
            val id = intent.getLongExtra(EXTRA_DIRECTIVE_ID, -1L)
            if (id <= 0L) return
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Real bug fixed here: this used to call markComplete
                    // unconditionally, which fully completes ANY directive in one
                    // tap -- including multi-tap ones like the water tracker
                    // (requiredScans > 1), which should only advance by one per
                    // tap the same way it does inside the app. incrementScanProgress
                    // already handles both cases correctly (completes outright when
                    // required <= 1, increments otherwise), so the widget now goes
                    // through the same path instead of its own separate logic that
                    // only ever knew about the single-tap case.
                    AppRepository(RebuildDatabase.getInstance(context))
                        .incrementScanProgress(id, source = "widget")
                    refresh(context)
                } finally {
                    pending.finish()
                }
            }
        }
    }

    /** Resizing the widget changes how many rows fit, so redraw. */
    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context, manager, AppRepository(RebuildDatabase.getInstance(context)), appWidgetId)
            } finally {
                pending.finish()
            }
        }
    }

    /** Rows that fit at the widget's current height. Of the two heights the
     *  launcher reports, MAX_HEIGHT is the portrait one. */
    private fun rowsFor(manager: AppWidgetManager, widgetId: Int): Int {
        val heightDp = manager.getAppWidgetOptions(widgetId)
            .getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (heightDp <= 0) return MIN_ROWS
        return (MIN_ROWS + (heightDp - BASE_HEIGHT_DP) / ROW_HEIGHT_DP).coerceIn(MIN_ROWS, MAX_ROWS)
    }

    private suspend fun render(context: Context, manager: AppWidgetManager, repo: AppRepository, widgetId: Int) {
        val rows = rowsFor(manager, widgetId)
        val open = repo.openDailyDirectivesToday()
        val sync = repo.computeSyncRatio()
        val scanProgress = repo.scanProgressTodayOnce()

        val v = RemoteViews(context.packageName, R.layout.widget_rebuild)
        v.setTextViewText(R.id.widget_sync, "$sync%")
        v.setOnClickPendingIntent(
            R.id.widget_header,
            PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        if (open.isEmpty()) {
            v.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            ROW_CONTAINER_IDS.forEach { v.setViewVisibility(it, View.GONE) }
            v.setViewVisibility(R.id.widget_more, View.GONE)
        } else {
            v.setViewVisibility(R.id.widget_empty, View.GONE)
            ROW_IDS.forEachIndexed { i, rowId ->
                val containerId = ROW_CONTAINER_IDS[i]
                val d = if (i < rows) open.getOrNull(i) else null
                if (d == null) {
                    v.setViewVisibility(containerId, View.GONE)
                } else {
                    v.setViewVisibility(containerId, View.VISIBLE)
                    val prefix = if (d.requiresNfcScan) "[NFC] " else "☐ "
                    // Multi-tap directives show their count (e.g. "1/3") so a
                    // glance at the widget matches what the app itself shows,
                    // rather than looking identical to a plain single-tap item.
                    val suffix = if (d.requiredScans > 1) {
                        " (${scanProgress[d.id] ?: 0}/${d.requiredScans})"
                    } else ""
                    v.setTextViewText(rowId, prefix + d.title.uppercase() + suffix)
                    v.setTextColor(rowId, if (d.requiresNfcScan) 0xFF7A7A80.toInt() else 0xFFE8E8EA.toInt())

                    if (!d.requiresNfcScan) {
                        v.setOnClickPendingIntent(
                            containerId,
                            PendingIntent.getBroadcast(
                                context,
                                // Request code must fit in Int and stay unique per row
                                // across widget instances; directive id truncated to
                                // Long -> Int could collide or overflow on a large id,
                                // so it's combined with the row slot and widgetId
                                // instead of used directly.
                                (widgetId * 31 + i),
                                Intent(context, RebuildWidgetProvider::class.java).apply {
                                    action = ACTION_COMPLETE
                                    putExtra(EXTRA_DIRECTIVE_ID, d.id)
                                },
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                    }
                }
            }
            val extra = open.size - rows
            if (extra > 0) {
                v.setViewVisibility(R.id.widget_more, View.VISIBLE)
                v.setTextViewText(R.id.widget_more, "+$extra MORE")
            } else {
                v.setViewVisibility(R.id.widget_more, View.GONE)
            }
        }

        manager.updateAppWidget(widgetId, v)
    }
}
