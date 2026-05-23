package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.DayBandSource
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.ui.share.isForeignBand
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

const val TestTagDayView = "ScheduleDayView"
const val TestTagDayBand = "DayBand"
const val TestTagDayBandPinnedTz = "DayBand-PinnedTz"
const val TestTagDayEmpty = "DayEmpty"
const val TestTagNowLine = "NowLine"

/**
 * Round 2.25.z (D.129) — sub-readable band tap-affordance test tag.
 * Present iff the rendered band height is below
 * [com.eight87.strictlykeptboy.resolver.AutoZoomResolver.READABLE_BAND_DP].
 */
const val TestTagSubReadableCue = "SubReadableCue"

/**
 * Pure helper for D.129 — at the given zoom level, would a band of
 * [durationMinutes] minutes render below the readability threshold?
 * Mirrors the per-zoom `LEVEL_DP_PER_HOUR` table so test code can
 * assert without spinning up Compose.
 */
fun isSubReadableBand(durationMinutes: Long, zoom: Int): Boolean {
    val dpPerHour = when (zoom) { 1 -> 40; 2 -> 80; 3 -> 160; 4 -> 320; else -> 80 }
    val bandDp = durationMinutes.coerceAtLeast(1L) * dpPerHour / 60.0
    return bandDp < com.eight87.strictlykeptboy.resolver.AutoZoomResolver.READABLE_BAND_DP
}

/**
 * Round 2.21 Phase D.3 — derive `HourHeight` from effective zoom
 * ∈ {1..4}. Zoom = 2 is the new default (≈ 80dp/h ≈ half a phone
 * screen per hour), replacing the legacy 60dp/h constant.
 *
 * `effectiveZoom = max(zoom of visible overlays)` — Schedule + Month
 * + Year ignore it (list / grid layouts); Day / 3-day / Week consume
 * it via this function.
 */
internal fun hourHeightForZoom(zoom: Int): androidx.compose.ui.unit.Dp = when (zoom) {
    1 -> 40.dp
    2 -> 80.dp
    3 -> 160.dp
    4 -> 320.dp
    else -> 80.dp
}

private val GutterWidth = 56.dp
/** Public mirror so sibling composables (3-day) can match the gutter width. */
internal val DayViewGutterWidth = GutterWidth

/** Phase F.4 — stateless day view consuming a narrow [DayBandSource]. */
@Composable
fun ScheduleDayView(
    date: LocalDate,
    dayBands: DayBandSource,
    modifier: Modifier = Modifier,
    onAddAt: (LocalTime) -> Unit = {},
    onBandTap: (DayBand) -> Unit = {},
    isToday: Boolean = date == LocalDate.now(),
    /** Phase CCC.10 / HV-G.3 — opens the trip wizard from the empty-state CTA. */
    onPlanTrip: (() -> Unit)? = null,
    /** Round 2.2.C.2 — default-write repo for `isForeignBand`. Empty = chip suppressed. */
    defaultWriteRepoId: String = "",
    /**
     * Round 2.21 Phase D.3 — effective zoom level ∈ {1..4}. Default 2
     * preserves the historic 60dp/h-ish density (now 80dp/h ≈ 1h per
     * half a phone screen). Callers wire `max(zoom of visible overlays)`
     * via [com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs.zoomOf].
     */
    effectiveZoom: Int = 2,
    /**
     * Round 2.21 Phase F.2 — per-calendar opt-in to atomic grouping.
     * Sourced from `CalendarMeta.metaGroupField != null`. Calendars
     * missing from this map are treated as opted-out (no grouping).
     */
    metaGroupByCalendar: Map<com.eight87.strictlykeptboy.resolver.CalendarRef, Boolean> = emptyMap(),
    /**
     * Round 2.21 Phase D.5 — pinch-to-zoom on the day-grid Box.
     * Called on `detectTransformGestures` release with the
     * topmost band at the gesture-center Y; host wires to
     * `CalendarVisibilityPrefs.setZoom(repoId, calendarId, newZoom)`.
     * The handler picks `newZoom` snapped to {1,2,3,4} based on
     * accumulated scale; the caller persists.
     */
    onPinchZoomBand: ((CalendarRef, RepoRef, Int) -> Unit)? = null,
    /**
     * Round 2.21 Phase D.5 — current per-overlay zoom lookup. Used
     * to compute the snap target relative to the band's *own*
     * zoom (not the global effective zoom). Defaults to
     * [effectiveZoom] when missing so tests / previews keep the
     * old single-zoom behaviour.
     */
    zoomFor: (CalendarRef, RepoRef) -> Int = { _, _ -> effectiveZoom },
    /**
     * Round 2.22 / Phase B UI follow-up — long-press-and-drag drop
     * callback. `null` ⇒ drag-to-reschedule disabled (tap path
     * preserved). Caller receives `(band, snappedNewStart)` and
     * dispatches to `DragRescheduleController` for the actual write.
     */
    onDragReschedule: ((DayBand, java.time.OffsetDateTime) -> Unit)? = null,
    /** When false, hides the weekday emoji header strip (3-day view supplies its own). */
    showWeekdayHeader: Boolean = true,
    /** When false, hides the left-hand hour gutter (3-day view shares one gutter). */
    showHourGutter: Boolean = true,
    /**
     * When non-null, this scroll state replaces the locally remembered
     * one — so multiple sibling DayViews (3-day, week) scroll in sync.
     */
    sharedScrollState: androidx.compose.foundation.ScrollState? = null,
    /**
     * When false, the DayView omits its own `verticalScroll` modifier
     * and the now-line auto-scroll `LaunchedEffect`. Used by the
     * Stacked-layout path on 3-day / Week, where multiple DayViews
     * stack inside an outer scroll container (user 2026-05-23).
     */
    internalScroll: Boolean = true,
) {
    // Sliding 24-hour window per user direction:
    //   - on today: anchor at `now - 12h` (snapped to the hour) so the
    //     red NowLine sits roughly mid-grid and the view spans 12h
    //     past + 12h future, crossing midnight when the user's "now"
    //     is in the morning or evening.
    //   - other days: anchor at the date's local-midnight, 24h.
    // The Y axis is "hours since windowStart" everywhere downstream;
    // local-midnight assumptions are gone from BandsLayer / HourLines.
    val zone = java.time.ZoneId.systemDefault()
    val windowStart: java.time.ZonedDateTime = remember(date, isToday) {
        if (isToday) {
            java.time.ZonedDateTime.now(zone)
                .minusHours(12)
                .truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        } else {
            date.atStartOfDay(zone)
        }
    }
    val windowEnd = remember(windowStart) { windowStart.plusHours(24) }
    // Union the rendered bands across the date(s) the window touches.
    // For non-today this is just [date]; for today's sliding window
    // it's [windowStart.toLocalDate(), windowEnd.toLocalDate()] which
    // are different when "now" puts the window across midnight.
    val bands: List<DayBand> = remember(dayBands, windowStart, windowEnd) {
        val d1 = windowStart.toLocalDate()
        val d2 = windowEnd.toLocalDate()
        val raw = if (d1 == d2) dayBands.bandsFor(d1)
        else dayBands.bandsFor(d1) + dayBands.bandsFor(d2)
        // Dedupe in case a band materializes against both dates (a
        // band that crosses midnight may be enumerated under either
        // calendar date depending on the resolver pass).
        raw.distinctBy { it.instance.instanceId }
            // Keep only bands that overlap the [windowStart, windowEnd)
            // interval — anything fully outside paints nothing.
            .filter { b ->
                b.instance.effectiveEnd.isAfter(windowStart) &&
                    b.instance.effectiveStart.isBefore(windowEnd)
            }
    }

    if (bands.isEmpty() && internalScroll) {
        EmptyScheduleState(
            modifier = modifier.fillMaxSize().testTag(TestTagDayEmpty),
            onPlanTrip = onPlanTrip,
        )
        return
    }

    val hourHeight = hourHeightForZoom(effectiveZoom)
    // Round 2.23 Phase B (D-2.23.c) — weekday emoji strip above the
    // hour grid so the Day view picks up the same visual separator the
    // Week / 3-day headers use.
    val scroll = sharedScrollState ?: rememberScrollState()
    val density = LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }
    // Auto-scroll: on today's sliding-window view, "now" is always
    // 12h from windowStart, so center it on the visible region (now
    // lands roughly half-way down). Non-today views scroll to a
    // reasonable morning anchor.
    LaunchedEffect(isToday, hourHeightPx, internalScroll) {
        if (!internalScroll) return@LaunchedEffect
        if (hourHeightPx <= 0f) return@LaunchedEffect
        if (isToday) {
            val targetPx = (12f * hourHeightPx) - (hourHeightPx * 6f)
            scroll.scrollTo(targetPx.toInt().coerceAtLeast(0))
        } else {
            scroll.scrollTo((7f * hourHeightPx).toInt().coerceAtLeast(0))
        }
    }
    // Round 2.21 Phase F.2 — per-group expansion state. Auto-expand
    // when zoom ≥ GROUP_AUTO_EXPAND_ZOOM (= 3); below that, collapsed
    // groups can still be expanded on caret-tap.
    val groups = remember(bands, metaGroupByCalendar) {
        groupDayBands(bands = bands, hasMetaGroup = metaGroupByCalendar)
    }
    val autoExpand = shouldAutoExpand(effectiveZoom)
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }
    // Long-press to select a band brings it to the front (paints last,
    // gets elevation). Tap-elsewhere (hour grid) or system-back clears.
    var selectedBandId by remember(date) { mutableStateOf<String?>(null) }
    if (selectedBandId != null) {
        androidx.activity.compose.BackHandler { selectedBandId = null }
    }
    val dragState = rememberDragRescheduleUiState()
    Column(
        modifier = (
            if (internalScroll) modifier.fillMaxSize() else modifier.fillMaxWidth()
            ).testTag(TestTagDayView),
    ) {
        if (showWeekdayHeader) {
            // Round 2.23 Phase B — weekday emoji strip.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.width(GutterWidth))
                Text(
                    text = "${emojiFor(date.dayOfWeek)}  ${date.dayOfWeek.name.take(3)} ${date.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (internalScroll) Modifier.verticalScroll(scroll) else Modifier),
    ) {
        if (showHourGutter) DayHourGutter(hourHeight = hourHeight, windowStart = windowStart)
        // Round 2.21 Phase D.5 — pinch-to-zoom on the day-grid Box.
        // detectTransformGestures fires on every pointer move; we
        // accumulate `pendingScale` and on the gesture-end (next
        // pointer-down resets) commit a snap to {1,2,3,4}.
        var pendingScale by remember { mutableStateOf(1f) }
        var pendingCenterY by remember { mutableStateOf(0f) }
        var pendingPanY by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(hourHeight * 24)
                .pointerInput(onPinchZoomBand, bands, hourHeightPx) {
                    if (onPinchZoomBand == null) return@pointerInput
                    // Only consume events when ≥2 pointers are down
                    // (pinch). Single-finger pans pass through to the
                    // outer verticalScroll so drag-to-scroll works.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var pressing = true
                        while (pressing) {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2) {
                                val zoom = event.calculateZoom()
                                val centroid = event.calculateCentroid()
                                if (zoom != 1f) {
                                    pendingScale *= zoom
                                    pendingCenterY = centroid.y
                                    val band = pickBandAtY(
                                        bands = bands,
                                        centerYPx = pendingCenterY,
                                        hourHeightPx = hourHeightPx,
                                    )
                                    if (band != null) {
                                        val current = zoomFor(band.instance.calendar, band.instance.repo)
                                        val target = snapZoomFromScale(current, pendingScale)
                                        if (target != current) {
                                            onPinchZoomBand(
                                                band.instance.calendar,
                                                band.instance.repo,
                                                target,
                                            )
                                            pendingScale = 1f
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            pressing = event.changes.any { it.pressed }
                        }
                        pendingScale = 1f
                    }
                },
        ) {
            HourLines(
                hourHeight = hourHeight,
                windowStart = windowStart,
                onTapHour = { hr ->
                    // Tap on empty grid clears any band selection;
                    // otherwise falls through to the add-at-hour
                    // action — `hr` is the absolute hour the user
                    // tapped (computed in HourLines against
                    // windowStart).
                    if (selectedBandId != null) selectedBandId = null
                    else onAddAt(LocalTime.of(hr, 0))
                },
            )
            BandsLayer(
                windowStart = windowStart,
                windowEnd = windowEnd,
                groups = groups,
                autoExpand = autoExpand,
                expandedKeys = expandedKeys,
                onToggleGroup = { key ->
                    expandedKeys = if (key in expandedKeys) expandedKeys - key
                    else expandedKeys + key
                },
                hourHeight = hourHeight,
                onBandTap = onBandTap,
                defaultWriteRepoId = defaultWriteRepoId,
                selectedBandId = selectedBandId,
                onSelectBand = { id -> selectedBandId = id },
                effectiveZoom = effectiveZoom,
            )
            // Round 2.22 / Phase B UI follow-up — translucent ghost band
            // following the finger, snapped to the grid.
            val snapped = dragState.snappedStart
            val draggedId = dragState.draggedBandId
            if (snapped != null && draggedId != null) {
                val draggedBand = bands.firstOrNull { it.instance.instanceId == draggedId }
                if (draggedBand != null) {
                    DragGhostBand(
                        band = draggedBand,
                        snappedStart = snapped,
                        hourHeight = hourHeight,
                    )
                }
            }
            if (isToday) NowLine(hourHeight = hourHeight, windowStart = windowStart)
        }
    }
    }
}

/** Stable per-group key — first child's instanceId is unique on the day. */
private fun groupKey(g: GroupedDayBand): String = "grp-${g.first.instance.instanceId}"

/**
 * Round 2.21 Phase F.2 — build a synthetic [DayBand] that visually
 * represents a collapsed [GroupedDayBand]. The instance spans the
 * union of children's time range; the title carries the group label
 * + "· N atoms" so the band reads as a folder. The synthetic
 * instanceId is prefixed `grp-` so tap handlers + caller code can
 * distinguish it from a real instance.
 */
private fun makeCollapsedSyntheticBand(g: GroupedDayBand): DayBand {
    val first = g.first.instance
    val last = g.last.instance
    val syntheticInstance = first.copy(
        effectiveEnd = last.effectiveEnd,
        title = "${g.groupLabel.orEmpty()} · ${g.children.size} atoms",
        source = first.source, // preserved so instanceId getter works
    )
    return g.first.copy(instance = syntheticInstance)
}

@Composable
internal fun DayHourGutter(
    hourHeight: Dp,
    windowStart: java.time.ZonedDateTime = java.time.LocalDate.now()
        .atStartOfDay(java.time.ZoneId.systemDefault()),
) {
    Column(modifier = Modifier.width(GutterWidth)) {
        for (i in 0 until 24) {
            val cellTime = windowStart.plusHours(i.toLong())
            val hr = cellTime.hour
            // Mark the midnight crossing visually so the user sees
            // where today rolls over into tomorrow (or yesterday into
            // today) — the hour label becomes "00 +1d" / "00".
            val label = if (hr == 0 && i != 0) "00⤴" else "%02d".format(hr)
            Box(
                modifier = Modifier.fillMaxWidth().height(hourHeight).padding(start = 8.dp, top = 2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Google-Calendar-style per-hour blocks: each hour is a rounded
 * Surface with a small vertical gap between hours so the grid reads
 * as discrete cells rather than one flat column.
 */
@Composable
private fun HourLines(
    hourHeight: Dp,
    windowStart: java.time.ZonedDateTime,
    onTapHour: (Int) -> Unit,
) {
    val gap = 2.dp
    val blockHeight = hourHeight - gap
    Column(modifier = Modifier.fillMaxSize()) {
        for (i in 0 until 24) {
            val cellHour = windowStart.plusHours(i.toLong()).hour
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
                onClick = { onTapHour(cellHour) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(blockHeight)
                    .padding(horizontal = 2.dp),
            ) {}
            Spacer(modifier = Modifier.height(gap))
        }
    }
}

@Composable
private fun BandsLayer(
    windowStart: java.time.ZonedDateTime,
    windowEnd: java.time.ZonedDateTime,
    groups: List<GroupedDayBand>,
    autoExpand: Boolean,
    expandedKeys: Set<String>,
    onToggleGroup: (String) -> Unit,
    hourHeight: Dp,
    onBandTap: (DayBand) -> Unit,
    defaultWriteRepoId: String,
    /** Instance-id of the currently long-pressed-to-front band, or null. */
    selectedBandId: String? = null,
    /** Long-press handler — caller stores the id and bumps it to front. */
    onSelectBand: (String?) -> Unit = {},
    effectiveZoom: Int = 2,
) {
    // Flatten groups to (band, syntheticGroupKey?). Synthetic key
    // tracks "this band is the folder for group X — taps should
    // expand, not open detail".
    data class Renderable(val band: DayBand, val groupKey: String?)
    val unsorted: List<Renderable> = groups.flatMap { g ->
        val key = "grp-${g.first.instance.instanceId}"
        if (!g.isCollapsedGroup) g.children.map { Renderable(it, null) }
        else if (autoExpand || key in expandedKeys) g.children.map { Renderable(it, null) }
        else listOf(Renderable(makeCollapsedSyntheticBand(g), key))
    }
    // Dependency-respecting cascade: anything that *depends on*
    // something else paints to the right of (and on top of) its
    // dependency target. Two sort keys, in order:
    //
    //   1. **Kind rank** — `Base` is the scaffolding everything else
    //      depends on, so it always lands at the lowest lane numbers
    //      regardless of priority. Regular / External / Timebox
    //      events stack on top of it.
    //   2. **Priority** (ascending) — within a kind, lower priority
    //      number = lower lane = more scaffold-like. Higher priority
    //      cascades further right and paints on top.
    //
    // Bands sharing both (kind-rank, priority) share a lane. Compose
    // paints in iteration order, so this sort means "dependencies
    // paint first → end up behind dependents".
    fun kindRank(k: com.eight87.strictlykeptboy.resolver.CalendarKind): Int = when (k) {
        com.eight87.strictlykeptboy.resolver.CalendarKind.Base -> 0
        com.eight87.strictlykeptboy.resolver.CalendarKind.External -> 1
        com.eight87.strictlykeptboy.resolver.CalendarKind.Regular -> 2
        com.eight87.strictlykeptboy.resolver.CalendarKind.Timebox -> 3
    }
    val byPaintOrder: List<Renderable> = unsorted.sortedWith(
        compareBy<Renderable> { kindRank(it.band.kind) }
            .thenBy { it.band.priority },
    )
    val laneIndexById: Map<String, Int> = run {
        val groupKeyToLane = linkedMapOf<Pair<Int, Int>, Int>()
        val map = mutableMapOf<String, Int>()
        byPaintOrder.forEach { r ->
            val key = kindRank(r.band.kind) to r.band.priority
            val lane = groupKeyToLane.getOrPut(key) { groupKeyToLane.size }
            map[r.band.instance.instanceId] = lane
        }
        map
    }
    // Selected-band brought to front: paint it dead last (= top of
    // the z-stack) with a halo + elevation per the visual treatment
    // below. Its lane-index keeps its priority-derived offset, so the
    // user's mental map of "which one is which" stays put.
    val selectedRenderable = selectedBandId?.let { id ->
        byPaintOrder.firstOrNull { it.band.instance.instanceId == id }
    }
    val renderables: List<Renderable> = if (selectedRenderable == null) byPaintOrder
    else byPaintOrder.filter { it !== selectedRenderable } + selectedRenderable
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = maxWidth
        // Cascade overlap: each successive priority-lane is shifted
        // right by [cascadeStep]; width shrinks by the same per-lane
        // amount so the lower-priority scaffold stays visible as a
        // clear vertical slab under higher-priority overlays.
        // 36dp is the minimum step where the lane-0 base layer reads
        // as a distinct visible band on phones — anything smaller
        // and the cascade collapses into a thin sliver that the eye
        // misses against the higher-priority overlay on top.
        val cascadeStep = 36.dp
        renderables.forEach { (band, groupKey) ->
            val laneIdx = laneIndexById[band.instance.instanceId] ?: 0
            // Lane 0 = the day's scaffolding (lowest priority value)
            // → full width, behind everything. Treated as "base" for
            // alpha purposes regardless of CalendarKind. Higher lanes
            // = events stacking on top with progressive right offset.
            val isBase = laneIdx == 0
            // Clamp cascadeX so the band always stays inside the column.
            // Without this, a high-lane band whose desired offset would
            // leave less than the 48dp minimum band-width gets its width
            // floored to 48dp at the original offset, pushing it past the
            // right edge (user-visible 2026-05-23: yellow band escaped).
            val minBandWidth = 48.dp
            val maxCascadeX = (widthPx - minBandWidth - 4.dp).coerceAtLeast(0.dp)
            val cascadeX = (cascadeStep * laneIdx).coerceAtMost(maxCascadeX)
            val bandWidth = (widthPx - cascadeX - 4.dp).coerceAtLeast(minBandWidth)

            val rawStart = band.instance.effectiveStart
            val rawEnd = band.instance.effectiveEnd
            // Clip the band to the visible 24h window. Math is now
            // "minutes since windowStart" everywhere — the local-
            // midnight assumption is gone, so a 23:30→06:30 sleep
            // block paints as a single slab on the sliding-window
            // view (instead of two halves on adjacent days).
            val start = if (rawStart.isBefore(windowStart)) windowStart else rawStart
            val end = if (rawEnd.isAfter(windowEnd)) windowEnd else rawEnd
            val startMinutes = java.time.Duration.between(windowStart, start)
                .toMinutes().toFloat().coerceAtLeast(0f)
            val endMinutes = java.time.Duration.between(windowStart, end)
                .toMinutes().toFloat()
                .coerceAtMost(24f * 60f)
            val clippedDurationMin = (endMinutes - startMinutes).coerceAtLeast(0f)
            val topDp = hourHeight * startMinutes / 60f
            val heightDp = hourHeight * clippedDurationMin.coerceAtLeast(15f) / 60f

            val isSuperseded = band.supersededByCalendar != null
            val isOffSchedule = band.offSchedule
            val bandAlpha = if (isSuperseded) 0.35f else 1f
            val authorId = band.instance.author?.id
            val showAuthorChip = authorId != null &&
                isForeignBand(band.instance.repo.id, defaultWriteRepoId)

            val isSelected = band.instance.instanceId == selectedBandId
            Box(
                modifier = Modifier
                    .offset(x = cascadeX, y = topDp)
                    .width(bandWidth)
                    .height(heightDp)
                    .padding(2.dp),
            ) {
                // Round 2.23 Phase A — per-calendar colorSeed wins on the
                // band background (D-2.23.b). Base-layer calendars paint as
                // a subdued scaffold (alpha ~0.18) so events on top read
                // cleanly at ~0.75 alpha.
                val seedColor = colorForSeed(band.accentColorSeed)
                val seedAlpha = if (isBase) 0.18f else 0.75f
                val baseFill = if (seedColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.surfaceContainer.copy(
                        alpha = if (isBase) 0.35f else 1f,
                    )
                } else {
                    seedColor.copy(alpha = seedAlpha)
                }
                val effectiveFill = baseFill.copy(alpha = baseFill.alpha * bandAlpha)
                // Tap = open detail (or expand group). Long-press =
                // bring-to-front: caller stores the id and we move
                // this band to the end of the iteration list above,
                // which is what paints it last (= on top). Tap on the
                // empty hour grid (or system back) clears the
                // selection. Drag-to-reschedule was removed per user
                // direction — dragging entries for time was bullshit.
                val selectionShape = RoundedCornerShape(12.dp)
                val haloModifier = if (isSelected) {
                    Modifier
                        .shadow(
                            elevation = 12.dp,
                            shape = selectionShape,
                            clip = false,
                        )
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = selectionShape,
                        )
                } else Modifier
                Surface(
                    color = effectiveFill,
                    shape = selectionShape,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(haloModifier)
                        .combinedClickable(
                            onClick = {
                                if (groupKey != null) onToggleGroup(groupKey)
                                else onBandTap(band)
                            },
                            onLongClick = {
                                onSelectBand(
                                    if (isSelected) null
                                    else band.instance.instanceId,
                                )
                            },
                        )
                        .testTag("$TestTagDayBand-${band.instance.instanceId}"),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 10-minute tick marks inside the band so the
                        // user can read elapsed time visually without
                        // needing a wider grid block per minute.
                        val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                        val minutesInBand = clippedDurationMin.coerceAtLeast(1f)
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val pxPerMin = size.height / minutesInBand
                            var m = 10
                            while (m < minutesInBand) {
                                val y = m * pxPerMin
                                drawLine(
                                    color = tickColor,
                                    start = androidx.compose.ui.geometry.Offset(8f, y),
                                    end = androidx.compose.ui.geometry.Offset(size.width - 4f, y),
                                    strokeWidth = 1f,
                                )
                                m += 10
                            }
                        }
                        // 4-dp left color stripe (2.2.C.1-paint).
                        // Round 2.18.C.4 — external events get a narrower
                        // 2-dp accent strip painted in the source-calendar
                        // color so the band reads as "from a system
                        // calendar" without losing skb's identity tint.
                        if (band.kind == com.eight87.strictlykeptboy.resolver.CalendarKind.External) {
                            BandLeftStripe(seed = band.accentColorSeed, widthDp = 2.dp)
                        } else {
                            BandLeftStripe(seed = band.accentColorSeed)
                        }

                        Column(modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Kind glyph (2.2.C.3-paint)
                                BandKindGlyph(kind = band.kind)
                                Spacer(modifier = Modifier.width(4.dp))
                                // Passive / active habit glyph: 📏 if the
                                // rule ticks by default, ⚡ if the user
                                // has to respond (keeper prompt etc.).
                                if (band.instance.passive) {
                                    BandPassiveHabitGlyph()
                                    Spacer(modifier = Modifier.width(4.dp))
                                } else if (band.instance.requiresResponse || band.instance.promptKind != null) {
                                    BandActiveHabitGlyph()
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                // Superseded leaf glyph (2.2.C.4-paint)
                                if (isSuperseded) {
                                    BandSupersededGlyph()
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                // Off-schedule warning glyph (2.2.C.5)
                                if (isOffSchedule) {
                                    BandOffScheduleGlyph()
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = (band.instance.emoji?.let { "$it  " } ?: "") + band.instance.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    textDecoration = if (isSuperseded) TextDecoration.LineThrough else null,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Text(
                                text = "%02d:%02d–%02d:%02d".format(
                                    start.hour, start.minute, end.hour, end.minute,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Author chip — top-right (2.2.C.2)
                        if (showAuthorChip) {
                            BandAuthorChip(
                                authorId = authorId!!,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp),
                            )
                        }
                        // Round 2.24 Phase C.3 — pinned-tz badge. When the
                        // event carries its own `sourceTzId` AND that zone
                        // differs from the band's display zone (set via
                        // `Renderer.render(displayTzId=...)`), render a
                        // small globe glyph in the bottom-end corner so the
                        // user can tell the band is in a foreign zone.
                        val srcTz = band.instance.sourceTzId
                        val displayZone = band.instance.effectiveStart.zone
                        val pinnedDifferent = srcTz != null &&
                            runCatching { java.time.ZoneId.of(srcTz) != displayZone }
                                .getOrDefault(false)
                        if (pinnedDifferent) {
                            Icon(
                                imageVector = Icons.Outlined.Public,
                                contentDescription = "pinned to $srcTz",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 4.dp, bottom = 4.dp)
                                    .size(14.dp)
                                    .testTag("$TestTagDayBandPinnedTz-${band.instance.instanceId}"),
                            )
                        }
                        // Round 2.25.z (D.129) — sub-readable cue. Only
                        // real bands (not synthetic group folders); the
                        // existing Surface.onClick already routes the tap
                        // to onBandTap → full-screen EventDetailScreen.
                        val rawDurationMin = clippedDurationMin.toLong()
                        if (groupKey == null && isSubReadableBand(rawDurationMin, effectiveZoom)) {
                            Text(
                                text = "…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 6.dp)
                                    .testTag(TestTagSubReadableCue),
                            )
                        }
                    }
                }
                // Dashed border for off-schedule bands (2.2.C.5)
                if (isOffSchedule) {
                    BandDashedBorder(color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DragGhostBand(
    band: DayBand,
    snappedStart: java.time.OffsetDateTime,
    hourHeight: Dp,
) {
    val durationMin = durationMinutes(band.instance.effectiveStart, band.instance.effectiveEnd)
        .coerceAtLeast(15f)
    val newMinutesFromMid = snappedStart.hour * 60f + snappedStart.minute
    val topDp = hourHeight * newMinutesFromMid / 60f
    val heightDp = hourHeight * durationMin / 60f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heightDp)
            .offset(y = topDp)
            .padding(horizontal = 4.dp)
            .testTag("DragGhostBand"),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "→ %02d:%02d".format(snappedStart.hour, snappedStart.minute),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = (band.instance.emoji?.let { "$it  " } ?: "") + band.instance.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NowLine(hourHeight: Dp, windowStart: java.time.ZonedDateTime) {
    var now by remember { mutableStateOf(java.time.ZonedDateTime.now(windowStart.zone)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.time.ZonedDateTime.now(windowStart.zone)
            delay(30_000L)
        }
    }
    val minutesIntoWindow =
        java.time.Duration.between(windowStart, now).toMinutes().toFloat()
    // Hide if now is outside the visible 24h window — e.g. the user
    // navigated to a non-today date and the line would land off-grid.
    if (minutesIntoWindow < 0f || minutesIntoWindow > 24f * 60f) return
    val topDp = hourHeight * minutesIntoWindow / 60f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .offset(y = topDp)
            .background(Color.Red)
            .testTag(TestTagNowLine),
    )
}

/**
 * Round 2.21 Phase D.5 — pick the topmost visible band that contains
 * the given Y coordinate (in pixels from the top of the day-grid
 * Box). When multiple bands overlap (different lanes), the first one
 * whose vertical interval contains [centerYPx] wins — i.e. the band
 * the user visually is touching. Lanes are horizontal subdivisions,
 * so vertical containment is the right hit test.
 */
internal fun pickBandAtY(
    bands: List<DayBand>,
    centerYPx: Float,
    hourHeightPx: Float,
): DayBand? {
    if (hourHeightPx <= 0f) return null
    fun minutesFromMidnight(z: ZonedDateTime): Float =
        (z.hour * 60 + z.minute + z.second / 60f)
    return bands.firstOrNull { band ->
        val topPx = hourHeightPx * minutesFromMidnight(band.instance.effectiveStart) / 60f
        val bottomPx = hourHeightPx * minutesFromMidnight(band.instance.effectiveEnd) / 60f
        centerYPx in topPx..bottomPx
    }
}

/**
 * Round 2.21 Phase D.5 — snap accumulated pinch scale to the next
 * zoom step. Pinch-out (>1) bumps up one step; pinch-in (<1) bumps
 * down one step. Thresholds are halfway in log-space (≈1.414 in,
 * ≈0.707 out) so a deliberate pinch lands one step; smaller jitter
 * leaves zoom unchanged.
 */
internal fun snapZoomFromScale(currentZoom: Int, scale: Float): Int {
    val clamped = currentZoom.coerceIn(1, 4)
    val target = when {
        scale >= 1.414f -> clamped + 1
        scale <= 0.707f -> clamped - 1
        else -> clamped
    }
    return target.coerceIn(1, 4)
}

private fun minutesFromMidnight(z: ZonedDateTime): Float =
    (z.hour * 60 + z.minute + z.second / 60f)

private fun durationMinutes(start: ZonedDateTime, end: ZonedDateTime): Float {
    val s = minutesFromMidnight(start)
    val e = minutesFromMidnight(end)
    return (e - s).coerceAtLeast(0f)
}
