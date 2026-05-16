package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.HorizontalDivider
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
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.ui.share.isForeignBand
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

const val TestTagDayView = "ScheduleDayView"
const val TestTagDayBand = "DayBand"
const val TestTagDayEmpty = "DayEmpty"
const val TestTagNowLine = "NowLine"

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

/** Phase F.4 — stateless day view consuming a [RenderedSchedule]. */
@Composable
fun ScheduleDayView(
    date: LocalDate,
    schedule: RenderedSchedule?,
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
) {
    val day = schedule?.days?.firstOrNull { it.date == date }
    val bands = day?.bands.orEmpty()

    if (bands.isEmpty()) {
        EmptyScheduleState(
            modifier = modifier.fillMaxSize().testTag(TestTagDayEmpty),
            onPlanTrip = onPlanTrip,
        )
        return
    }

    val hourHeight = hourHeightForZoom(effectiveZoom)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val hourHeightPx = with(density) { hourHeight.toPx() }
    // Round 2.21 Phase F.2 — per-group expansion state. Auto-expand
    // when zoom ≥ GROUP_AUTO_EXPAND_ZOOM (= 3); below that, collapsed
    // groups can still be expanded on caret-tap.
    val groups = remember(bands, metaGroupByCalendar) {
        groupDayBands(bands = bands, hasMetaGroup = metaGroupByCalendar)
    }
    val autoExpand = shouldAutoExpand(effectiveZoom)
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }
    val dragState = rememberDragRescheduleUiState()
    Row(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .testTag(TestTagDayView),
    ) {
        HourGutter(hourHeight = hourHeight)
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
                    detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
                        pendingScale *= zoom
                        pendingCenterY = centroid.y
                        pendingPanY += pan.y
                        // Commit a snap when accumulated scale leaves a
                        // half-step band. Threshold log2: 0.5 ≈ "down a
                        // level"; 2.0 ≈ "up a level". We snap on every
                        // gesture move past the threshold so the user
                        // sees the band redraw mid-gesture.
                        val band = pickBandAtY(
                            bands = bands,
                            centerYPx = pendingCenterY,
                            hourHeightPx = hourHeightPx,
                        ) ?: return@detectTransformGestures
                        val current = zoomFor(band.instance.calendar, band.instance.repo)
                        val target = snapZoomFromScale(current, pendingScale)
                        if (target != current) {
                            onPinchZoomBand(
                                band.instance.calendar,
                                band.instance.repo,
                                target,
                            )
                            // Reset accumulator so each snap-step requires
                            // a fresh pinch span.
                            pendingScale = 1f
                        }
                    }
                },
        ) {
            HourLines(hourHeight = hourHeight, onTapHour = { hr -> onAddAt(LocalTime.of(hr, 0)) })
            BandsLayer(
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
                dragState = dragState,
                hourHeightPx = hourHeightPx,
                onDragReschedule = onDragReschedule,
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
            if (isToday) NowLine(hourHeight = hourHeight)
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
private fun HourGutter(hourHeight: Dp) {
    Column(modifier = Modifier.width(GutterWidth)) {
        for (hr in 0 until 24) {
            Box(
                modifier = Modifier.fillMaxWidth().height(hourHeight).padding(start = 8.dp, top = 2.dp),
            ) {
                Text(
                    text = "%02d".format(hr),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HourLines(hourHeight: Dp, onTapHour: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        for (hr in 0 until 24) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(hourHeight)
                    .clickable { onTapHour(hr) },
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun BandsLayer(
    groups: List<GroupedDayBand>,
    autoExpand: Boolean,
    expandedKeys: Set<String>,
    onToggleGroup: (String) -> Unit,
    hourHeight: Dp,
    onBandTap: (DayBand) -> Unit,
    defaultWriteRepoId: String,
    dragState: DragRescheduleUiState? = null,
    hourHeightPx: Float = 0f,
    onDragReschedule: ((DayBand, java.time.OffsetDateTime) -> Unit)? = null,
) {
    // Flatten groups to (band, syntheticGroupKey?). Synthetic key
    // tracks "this band is the folder for group X — taps should
    // expand, not open detail".
    data class Renderable(val band: DayBand, val groupKey: String?)
    val renderables: List<Renderable> = groups.flatMap { g ->
        val key = "grp-${g.first.instance.instanceId}"
        if (!g.isCollapsedGroup) g.children.map { Renderable(it, null) }
        else if (autoExpand || key in expandedKeys) g.children.map { Renderable(it, null) }
        else listOf(Renderable(makeCollapsedSyntheticBand(g), key))
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = maxWidth
        renderables.forEach { (band, groupKey) ->
            val laneWidth = widthPx / band.totalLanes.coerceAtLeast(1)
            val laneOffsetX = laneWidth * band.laneIndex

            val start = band.instance.effectiveStart
            val end = band.instance.effectiveEnd
            val topDp = hourHeight * minutesFromMidnight(start) / 60f
            val heightDp = hourHeight * durationMinutes(start, end).coerceAtLeast(15f) / 60f

            val isSuperseded = band.supersededByCalendar != null
            val isOffSchedule = band.offSchedule
            val bandAlpha = if (isSuperseded) 0.35f else 1f
            val authorId = band.instance.author?.id
            val showAuthorChip = authorId != null &&
                isForeignBand(band.instance.repo.id, defaultWriteRepoId)

            Box(
                modifier = Modifier
                    .offset(x = laneOffsetX, y = topDp)
                    .width(laneWidth - 4.dp)
                    .height(heightDp)
                    .padding(2.dp),
            ) {
                val dragModifier = if (
                    dragState != null && onDragReschedule != null && groupKey == null
                ) {
                    Modifier.dragRescheduleBand(
                        state = dragState,
                        band = band,
                        hourHeightPx = hourHeightPx,
                        onDrop = onDragReschedule,
                    )
                } else Modifier
                Surface(
                    onClick = {
                        // Round 2.21 Phase F.2 — synthetic group folder taps
                        // toggle expansion; real bands open detail.
                        if (groupKey != null) onToggleGroup(groupKey) else onBandTap(band)
                    },
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = bandAlpha),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(dragModifier)
                        .testTag("$TestTagDayBand-${band.instance.instanceId}"),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
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
                                    text = band.instance.title,
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
                    text = band.instance.title,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NowLine(hourHeight: Dp) {
    var now by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = java.time.LocalTime.now()
            delay(30_000L)
        }
    }
    val topDp = hourHeight * (now.hour * 60 + now.minute) / 60f
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
