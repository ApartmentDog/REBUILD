package com.evyr.rebuild.nfc

import android.content.Context
import com.evyr.rebuild.data.AppRepository
import com.evyr.rebuild.location.GeofencePresence

data class DoorItem(
    val title: String,
    /** Where the tag lives ("Key Hook") when the title comes from a directive. */
    val spot: String?,
    /** When it was grabbed / put away / done; null = not yet. */
    val doneAt: Long?
)

/**
 * One source of truth for what's on the leaving and arriving checklists, used
 * by both the geofence notifications and the checklist card so they always
 * agree. Two parts:
 *
 *  - NFC items on the OUT or IN list (toggles in NFC Tags). Done means
 *    grabbed since last put away (leaving) or put away since arriving.
 *  - Directives on the place's own checklist with OUT or IN on (toggles in
 *    Locations). Done means completed today (leaving) or since you last
 *    left the place (arriving). Skipped if an NFC tag already covers it.
 */
object DoorChecklistItems {

    suspend fun build(
        context: Context,
        repo: AppRepository,
        mode: DoorMode,
        placeId: Long?
    ): List<DoorItem> {
        val leaving = mode == DoorMode.LEAVING
        val tags = repo.allTags()

        val tagItems = tags
            .filter { !it.opensChecklist && (if (leaving) it.onLeavingList else it.onArrivingList) }
            .map { tag ->
                val title = tag.linkedDirectiveId?.let { repo.directiveById(it)?.title }
                DoorItem(
                    title = title ?: tag.label,
                    spot = tag.label.takeIf { title != null && !it.equals(title, ignoreCase = true) },
                    doneAt = if (leaving) DoorState.grabbedSincePutAway(context, tag)
                    else DoorState.putAwaySinceArriving(context, tag)
                )
            }

        val covered = tags.mapNotNull { it.linkedDirectiveId }.toSet()
        val baseline = if (leaving) {
            DoorState.startOfToday()
        } else {
            placeId?.let { GeofencePresence.lastDepartureAt(context, it) }?.takeIf { it > 0L }
                ?: DoorState.arrivedAt(context)
        }
        val placeItems = placeId?.let { repo.placeLinks(it) }.orEmpty()
            .filter { (if (leaving) it.onExit else it.onEnter) && it.directiveId !in covered }
            .mapNotNull { link ->
                repo.directiveById(link.directiveId)?.let { d ->
                    DoorItem(
                        title = d.title,
                        spot = null,
                        doneAt = repo.completedAtToday(d.id)?.takeIf { it >= baseline }
                    )
                }
            }

        return (tagItems + placeItems)
            .sortedWith(compareBy<DoorItem> { it.doneAt != null }.thenBy { it.title.lowercase() })
    }
}
