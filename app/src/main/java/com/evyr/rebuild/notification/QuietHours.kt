package com.evyr.rebuild.notification

import java.util.Calendar

/**
 * True if the current hour falls within [start, end) quiet hours, wrapping past
 * midnight (e.g. start=22, end=7 covers 10pm-7am). Null start/end means no quiet
 * hours are configured, so nothing is ever suppressed.
 */
fun isWithinQuietHours(start: Int?, end: Int?): Boolean {
    if (start == null || end == null) return false
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return if (start <= end) {
        hour in start until end
    } else {
        hour >= start || hour < end
    }
}
