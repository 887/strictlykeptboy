package com.eight87.strictlykeptboy.ui.schedule

import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DayBand

/**
 * Round 2.21 Phase F.1 — UI-only collapse wrapper.
 *
 * A `GroupedDayBand` either carries a single [DayBand] (`children.size
 * == 1`, no collapse) or a multi-child collapsed band whose visual
 * extent is the union of children's effective intervals. The label
 * defaults to the shared `instance.group` (the literal value of the
 * `group` field on each event / rule — see Round 2.21.A.3).
 *
 * The underlying `MaterializedInstance` list does NOT change
 * (D-2.21.k); collapse is a render-time concern. Tap-to-expand toggles
 * `expanded`; zoom ≥ 3 auto-expands every group regardless of the
 * caret state because at that zoom there's vertical room for the
 * atoms to be legible on their own.
 */
data class GroupedDayBand(
    val children: List<DayBand>,
    val groupLabel: String?,
) {
    val isCollapsedGroup: Boolean get() = children.size > 1 && groupLabel != null
    val first: DayBand get() = children.first()
    val last: DayBand get() = children.last()
}

/** Default adjacency gap — bands closer than this collapse into a group. */
const val DEFAULT_GROUP_GAP_SECONDS: Long = 5L * 60L

/**
 * Round 2.21 Phase F.1 — collapse `bands` into [GroupedDayBand]s.
 *
 * Rules:
 *  1. Input must already be sorted by [DayBand.instance.effectiveStart].
 *     (Resolver output for a single day is sorted; this function does
 *     not re-sort to keep the contract narrow.)
 *  2. Adjacent bands whose calendar carries a non-blank `meta_group_field`
 *     AND share the same `instance.group` value AND have an inter-band
 *     gap ≤ [gapSeconds] collapse into one [GroupedDayBand].
 *  3. When [effectiveZoom] ≥ 3, the resolver still produces grouped
 *     bands — but the renderer auto-expands them (see ScheduleDayView).
 *     This function does NOT consult zoom; it produces the *maximal*
 *     grouping and the caller picks how to render.
 *
 * @param hasMetaGroup    per-calendar opt-in (sourced from
 *                        `CalendarMeta.metaGroupField`). A calendar
 *                        missing from the map is treated as opted-out.
 */
fun groupDayBands(
    bands: List<DayBand>,
    hasMetaGroup: Map<CalendarRef, Boolean>,
    gapSeconds: Long = DEFAULT_GROUP_GAP_SECONDS,
): List<GroupedDayBand> {
    if (bands.isEmpty()) return emptyList()
    val out = mutableListOf<GroupedDayBand>()
    var bucket = mutableListOf<DayBand>()

    fun flush() {
        if (bucket.isEmpty()) return
        val label = bucket.first().instance.group
        out.add(GroupedDayBand(children = bucket.toList(), groupLabel = label))
        bucket = mutableListOf()
    }

    for (band in bands) {
        val groupKey = band.instance.group
        val calendarOptedIn = hasMetaGroup[band.instance.calendar] == true
        val groupEligible = calendarOptedIn && !groupKey.isNullOrBlank()
        if (!groupEligible) {
            flush()
            out.add(GroupedDayBand(children = listOf(band), groupLabel = null))
            continue
        }
        if (bucket.isEmpty()) {
            bucket.add(band)
            continue
        }
        val tail = bucket.last()
        val sameGroup = tail.instance.group == groupKey &&
            tail.instance.calendar == band.instance.calendar
        val gap = band.instance.effectiveStart.toEpochSecond() -
            tail.instance.effectiveEnd.toEpochSecond()
        if (sameGroup && gap <= gapSeconds) {
            bucket.add(band)
        } else {
            flush()
            bucket.add(band)
        }
    }
    flush()
    return out
}

/**
 * Round 2.21 Phase F.2 — auto-expand threshold. At zoom levels >= 3 the
 * day grid is tall enough that even 5-minute atoms render as readable
 * bands, so collapsed groups expand back into their child bands without
 * the user tapping the caret.
 */
const val GROUP_AUTO_EXPAND_ZOOM: Int = 3

/** True when zoom-driven auto-expand should apply to a group. */
fun shouldAutoExpand(effectiveZoom: Int): Boolean = effectiveZoom >= GROUP_AUTO_EXPAND_ZOOM
