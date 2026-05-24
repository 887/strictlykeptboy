package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    /** W2-U-2 — wizard entry-point for the empty-state CTA. */
    onSetupWizard: (() -> Unit)? = null,
) {
    val bands = dayBands.bandsFor(date)

    if (bands.isEmpty()) {
        EmptyScheduleState(
            modifier = modifier.fillMaxSize().testTag(TestTagDayEmpty),
            onPlanTrip = onPlanTrip,
            onSetupWizard = onSetupWizard,
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
    // Round 2.21 Phase F.2 — per-group expansion state. Auto-expand
    // when zoom ≥ GROUP_AUTO_EXPAND_ZOOM (= 3); below that, collapsed
    // groups can still be expanded on caret-tap.
    val groups = remember(bands, metaGroupByCalendar) {
        groupDayBands(bands = bands, hasMetaGroup = metaGroupByCalendar)
    }
    val autoExpand = shouldAutoExpand(effectiveZoom)
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }
    val dragState = rememberDragRescheduleUiState()
    Column(modifier = modifier.fillMaxSize().testTag(TestTagDayView)) {
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
            .verticalScroll(scroll),
    ) {
        if (showHourGutter) DayHourGutter(hourHeight = hourHeight)
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
            if (isToday) NowLine(hourHeight = hourHeight)
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
internal fun DayHourGutter(hourHeight: Dp) {
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

/**
 * Google-Calendar-style per-hour blocks: each hour is a rounded
 * Surface with a small vertical gap between hours so the grid reads
 * as discrete cells rather than one flat column.
 */
@Composable
private fun HourLines(hourHeight: Dp, onTapHour: (Int) -> Unit) {
    val gap = 2.dp
    val blockHeight = hourHeight - gap
    Column(modifier = Modifier.fillMaxSize()) {
        for (hr in 0 until 24) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
                onClick = { onTapHour(hr) },
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
    effectiveZoom: Int = 2,
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
        // Cascade overlap: each successive lane is shifted right by
        // [cascadeStep]; width shrinks by the same per-lane amount so
        // earlier bands stay visible as a thin sliver under later ones.
        val cascadeStep = 14.dp
        renderables.forEach { (band, groupKey) ->
            val laneIdx = band.laneIndex.coerceAtLeast(0)
            val cascadeX = cascadeStep * laneIdx
            val bandWidth = (widthPx - cascadeX - 4.dp).coerceAtLeast(48.dp)

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
                    .offset(x = cascadeX, y = topDp)
                    .width(bandWidth)
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
                // Round 2.23 Phase A — per-calendar colorSeed wins on the
                // band background (D-2.23.b). When seed != 0, paint a tinted
                // fill at alpha 0.4 over the tonal scheme; foreground text
                // colour falls back to onSurface for legibility on the tint.
                val seedColor = colorForSeed(band.accentColorSeed)
                val baseFill = if (seedColor == Color.Unspecified) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    seedColor.copy(alpha = 0.4f)
                }
                val effectiveFill = baseFill.copy(alpha = baseFill.alpha * bandAlpha)
                Surface(
                    onClick = {
                        // Round 2.21 Phase F.2 — synthetic group folder taps
                        // toggle expansion; real bands open detail.
                        if (groupKey != null) onToggleGroup(groupKey) else onBandTap(band)
                    },
                    color = effectiveFill,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(dragModifier)
                        .testTag("$TestTagDayBand-${band.instance.instanceId}"),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 10-minute tick marks inside the band so the
                        // user can read elapsed time visually without
                        // needing a wider grid block per minute.
                        val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                        val minutesInBand = durationMinutes(start, end).coerceAtLeast(1f)
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
                        val rawDurationMin = durationMinutes(start, end).toLong()
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
