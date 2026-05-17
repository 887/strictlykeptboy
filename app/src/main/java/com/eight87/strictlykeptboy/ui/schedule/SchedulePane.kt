package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.MasterDetailLayout
import com.eight87.strictlykeptboy.ui.adaptive.WindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs
import java.time.DayOfWeek
import java.time.Instant
import java.time.temporal.TemporalAdjusters

const val TestTagScheduleDetailEmpty = "ScheduleDetailEmpty"
const val TestTagScheduleMasterPane = "ScheduleMasterPane"

/**
 * Stateful parent — owns the active-view-tab + repo-switcher callbacks.
 *
 * Phase R.2 — on Medium/Expanded width classes this pane becomes a
 * `Row(master | detail)` where the right pane always renders the
 * focused event's detail. On Compact (phone) the legacy modal bottom
 * sheet behaviour is preserved.
 *
 * Phase FFF — accepts an optional [EventCreateController]. When set,
 * an `EventCreateFab` overlays the bottom-end and the controller's
 * sheet renders on top.
 */
@Composable
fun SchedulePane(
    activeRepoName: String,
    state: ScheduleViewState,
    modifier: Modifier = Modifier,
    onPersistTab: (ScheduleViewTab) -> Unit = {},
    onSyncClick: () -> Unit = {},
    eventCreateController: EventCreateController? = null,
    eventCreateEnabled: Boolean = true,
    /** Phase CCC.10 / HV-G.3 — opens the quick-trip wizard from the FAB long-press or schedule empty-state. */
    onPlanTrip: () -> Unit = {},
    /**
     * Round 2.1.B.2 — visibility prefs powering the calendar chip strip.
     * When `null`, the chip strip is suppressed (back-compat with
     * preview / test entry-points that don't wire the multirepo path).
     */
    calendarVisibility: CalendarVisibilityPrefs? = null,
    /**
     * Round 2.1.B.2 — long-press handler for chips. Hosts route this to
     * [CalendarSettingsSheet]. `null` ⇒ no-op.
     */
    onLongPressCalendar: ((CalendarMeta) -> Unit)? = null,
    /**
     * Round 2.22 / Phase B UI follow-up — single-instance drop handler.
     * Caller wires to [DragRescheduleController.handleSingleDrop].
     * `null` ⇒ drag-to-reschedule disabled.
     */
    onSingleDrop: ((DayBand, java.time.OffsetDateTime) -> Unit)? = null,
    /**
     * Round 2.22 / Phase B UI follow-up — recurring drop handler with
     * user's branch choice. Caller wires to
     * [DragRescheduleController.handleRecurringDrop].
     */
    onRecurringDrop: ((DayBand, java.time.OffsetDateTime, DragRescheduleController.RecurringChoice) -> Unit)? = null,
    /**
     * Round 2.25 follow-up — when wired, band taps on the compact (phone)
     * path route the chosen [DayBand] up to the host shell instead of
     * mounting [EventDetailScreen] inline. The host then renders
     * `EventDetailScreen` above the chrome (top-bar + left rail), so the
     * detail surface is genuinely full-screen. When null, falls back to
     * the legacy in-pane mount (used by previews / tests that don't plumb
     * the hoist).
     */
    onOpenEventDetailFullScreen: ((DayBand) -> Unit)? = null,
    /**
     * Round 2.24 Phase C — repo-default tz (from `repo.toml`
     * `default_tz_id`) used as the "Repo default (…)" quick-pick in the
     * [DisplayTzChip]. `null` ⇒ no quick-pick row shown, picker still
     * lets the user search any zone.
     */
    repoDefaultTzId: String? = null,
) {
    androidx.compose.runtime.LaunchedEffect(state) {
        com.eight87.strictlykeptboy.perf.PerfTraceRecorder.begin(
            com.eight87.strictlykeptboy.perf.PerfTraceRecorder.Section.SchedulePaneFirstRender,
        )
        com.eight87.strictlykeptboy.perf.PerfTraceRecorder.end()
    }
    val widthClass = LocalWindowWidthSizeClass.current
    val rendered by state.rendered.collectAsState()

    var detailBand by remember { mutableStateOf<DayBand?>(null) }
    // Round 2.22 / Phase B UI follow-up — pending recurring drop awaits
    // the user's branch choice in the prompt dialog.
    var pendingRecurringDrop by remember {
        mutableStateOf<Pair<DayBand, java.time.OffsetDateTime>?>(null)
    }
    val onDragReschedule: ((DayBand, java.time.OffsetDateTime) -> Unit)? = remember(onSingleDrop, onRecurringDrop) {
        if (onSingleDrop == null && onRecurringDrop == null) null
        else { band, newStart ->
            if (band.isRecurringInstance()) {
                if (onRecurringDrop != null) {
                    pendingRecurringDrop = band to newStart
                }
            } else {
                onSingleDrop?.invoke(band, newStart)
            }
        }
    }

    val activeNowBand = remember(rendered) {
        rendered?.let { rs -> findActiveBand(rs.days.flatMap { it.bands }) }
    }
    val effectiveDetail = detailBand ?: if (widthClass.isTwoPane()) activeNowBand else null

    Box(modifier = modifier.fillMaxSize()) {
        if (widthClass.isTwoPane()) {
            MasterDetailLayout(
                modifier = Modifier.fillMaxSize(),
                widthClass = widthClass,
                master = {
                    Column(modifier = Modifier.testTag(TestTagScheduleMasterPane)) {
                        // Round 2.21 Phase C.3 — chip strip removed (D-2.21.d);
                        // overlay management lives in OverlayPickerScreen reached
                        // via the new top-bar button.
                        @Suppress("UNUSED_EXPRESSION") calendarVisibility
                        @Suppress("UNUSED_EXPRESSION") onLongPressCalendar
                        ScheduleMasterContent(
                            activeRepoName = activeRepoName,
                            state = state,
                            onPersistTab = onPersistTab,
                            onSyncClick = onSyncClick,
                            onBandTap = { detailBand = it },
                            onPlanTrip = onPlanTrip,
                            calendarVisibility = calendarVisibility,
                            onDragReschedule = onDragReschedule,
                            repoDefaultTzId = repoDefaultTzId,
                        )
                    }
                },
                detail = {
                    if (effectiveDetail != null) {
                        EventDetailContent(band = effectiveDetail)
                    } else {
                        ScheduleDetailEmptyState()
                    }
                },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().testTag(TestTagScheduleMasterPane)) {
                // Round 2.21 Phase C.3 — chip strip removed (D-2.21.d).
                @Suppress("UNUSED_EXPRESSION") onLongPressCalendar
                ScheduleMasterContent(
                    activeRepoName = activeRepoName,
                    state = state,
                    onPersistTab = onPersistTab,
                    onSyncClick = onSyncClick,
                    // Round 2.25 follow-up — when the host wired
                    // `onOpenEventDetailFullScreen`, route band taps up
                    // there so the detail surface is hoisted above the
                    // shell chrome (rail + top-bar). Otherwise fall back
                    // to the in-pane mount preserved below.
                    onBandTap = { band ->
                        if (onOpenEventDetailFullScreen != null) {
                            onOpenEventDetailFullScreen.invoke(band)
                        } else {
                            detailBand = band
                        }
                    },
                    onPlanTrip = onPlanTrip,
                    // Round 2.23 Phase C — needed so the ZoomLevelRow
                    // mounts in compact (phone) mode too.
                    calendarVisibility = calendarVisibility,
                    onDragReschedule = onDragReschedule,
                    repoDefaultTzId = repoDefaultTzId,
                )
            }
            // Round 2.23 Phase D (D-2.23.d) — full-screen event detail
            // replaces the legacy ModalBottomSheet for the compact path.
            // Round 2.25 follow-up — only render the in-pane fallback
            // when the host did NOT wire `onOpenEventDetailFullScreen`.
            // The wired path hoists to SkbAppShell so the detail surface
            // truly covers the rail + top-bar (user feedback 2026-05-17:
            // "clicking on an appointment should open it as a fullscreen
            // overlay not as this inline one").
            if (onOpenEventDetailFullScreen == null) {
                detailBand?.let { band ->
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    EventDetailScreen(
                        band = band,
                        onBack = { detailBand = null },
                        onEdit = {
                            android.widget.Toast.makeText(
                                ctx,
                                "Event editor coming in Round 3",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
        }

        pendingRecurringDrop?.let { (band, newStart) ->
            DragRescheduleRecurrencePrompt(
                eventTitle = band.instance.title,
                onChoice = { choice ->
                    pendingRecurringDrop = null
                    onRecurringDrop?.invoke(band, newStart, choice)
                },
                onDismiss = { pendingRecurringDrop = null },
            )
        }

        if (eventCreateController != null) {
            EventCreateFab(
                onClick = {
                    eventCreateController.openSheet(
                        defaultStart = state.date.value.atTime(12, 0)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toOffsetDateTime(),
                    )
                },
                onLongPressPlanTrip = onPlanTrip,
                enabled = eventCreateEnabled,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
            val sheetOpen by eventCreateController.sheetOpen.collectAsState()
            val sheetState by eventCreateController.state.collectAsState()
            if (sheetOpen) {
                EventCreateSheet(
                    state = sheetState,
                    onDismiss = { eventCreateController.closeSheet() },
                    onTabChange = eventCreateController::setTab,
                    onDraftChange = eventCreateController::setDraft,
                    onConfirmFreeForm = eventCreateController::confirmFreeForm,
                    onPickTemplate = eventCreateController::pickTemplate,
                    onConfirmTemplate = eventCreateController::confirmTemplate,
                    onCancelTemplate = eventCreateController::cancelTemplate,
                    onOverlapScheduleAnyway = eventCreateController::overlapScheduleAnyway,
                    onOverlapPickDifferent = eventCreateController::overlapPickDifferent,
                    onOverlapCancel = eventCreateController::overlapCancel,
                )
            }
        }
    }
}

@Composable
private fun ScheduleMasterContent(
    activeRepoName: String,
    state: ScheduleViewState,
    onPersistTab: (ScheduleViewTab) -> Unit,
    onSyncClick: () -> Unit,
    onBandTap: (DayBand) -> Unit,
    onPlanTrip: (() -> Unit)? = null,
    calendarVisibility: CalendarVisibilityPrefs? = null,
    onDragReschedule: ((DayBand, java.time.OffsetDateTime) -> Unit)? = null,
    repoDefaultTzId: String? = null,
) {
    val selectedTab by state.selectedTab.collectAsState()
    val date by state.date.collectAsState()
    val rendered by state.rendered.collectAsState()
    // Round 2.21 Phase D.3 — effective zoom = max(zoom of currently
    // visible overlays). Falls back to default 2 when prefs / calendars
    // aren't wired (tests / previews).
    val calendars by (state.calendarsFlow ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList()))
        .collectAsState()
    val visState by (calendarVisibility?.state
        ?: kotlinx.coroutines.flow.MutableStateFlow(
            com.eight87.strictlykeptboy.ui.settings.VisibilityState(),
        )).collectAsState()
    val metaGroupByCalendar = remember(calendars) {
        calendars.associate { it.ref to !it.metaGroupField.isNullOrBlank() }
    }
    val effectiveZoom = remember(calendars, visState, rendered, date) {
        // Round 2.23 Phase C (D-2.23.a) — global override wins when set.
        val override = visState.globalZoomOverride
        if (override != null) {
            override.coerceIn(
                com.eight87.strictlykeptboy.ui.settings.ZOOM_MIN,
                com.eight87.strictlykeptboy.ui.settings.ZOOM_MAX,
            )
        } else {
            // Round 2.25.y (D.128) — grouped-aware density-driven Auto.
            // Collapse atoms in the same group into a single effective
            // band (e.g. 5×5-min morning routine → one ~25-min band),
            // then pick the smallest zoom level where the shortest
            // *effective* band clears the readability threshold. Atom-
            // dense calendars no longer force level 4; instead the user
            // sees readable "Morning routine · N atoms" bands at lower
            // zooms, with the caret to expand on demand.
            val daysOfBands = rendered?.days?.map { it.bands }.orEmpty()
            val effectiveMinutes = effectiveBandMinutesForAutoZoom(
                daysOfBands = daysOfBands,
                hasMetaGroup = metaGroupByCalendar,
            )
            com.eight87.strictlykeptboy.resolver.AutoZoomResolver
                .deriveFromEffectiveBandMinutes(effectiveMinutes)
                .coerceIn(
                    com.eight87.strictlykeptboy.ui.settings.ZOOM_MIN,
                    com.eight87.strictlykeptboy.ui.settings.ZOOM_MAX,
                )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val _repo = activeRepoName
        @Suppress("UNUSED_VARIABLE") val _sync = onSyncClick
        when (selectedTab) {
            // Round 2.21 Phase E.2 — Schedule (agenda list) mirrors
            // Google Calendar's Schedule view; resolver range is the
            // week containing `date` per ScheduleViewState.
            ScheduleViewTab.Schedule -> ScheduleAgendaView(
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
                onBandTap = onBandTap,
            )
            // Round 2.21 Phase E.3 — 3-day timeline anchored at `date`.
            ScheduleViewTab.ThreeDay -> Column(modifier = Modifier.fillMaxSize()) {
                if (calendarVisibility != null) {
                    ZoomLevelRow(
                        selectedOverride = visState.globalZoomOverride,
                        onSelect = { calendarVisibility.setGlobalZoomOverride(it) },
                    )
                    DisplayTzChip(
                        displayTzId = visState.displayTzId,
                        repoDefaultTzId = repoDefaultTzId,
                        onSelect = { calendarVisibility.setDisplayTzId(it) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                ScheduleThreeDayView(
                    anchor = date,
                    schedule = rendered,
                    modifier = Modifier.fillMaxSize(),
                    onBandTap = onBandTap,
                    effectiveZoom = effectiveZoom,
                    onDragReschedule = onDragReschedule,
                )
            }
            ScheduleViewTab.Day -> Column(modifier = Modifier.fillMaxSize()) {
                if (calendarVisibility != null) {
                    ZoomLevelRow(
                        selectedOverride = visState.globalZoomOverride,
                        onSelect = { calendarVisibility.setGlobalZoomOverride(it) },
                    )
                    DisplayTzChip(
                        displayTzId = visState.displayTzId,
                        repoDefaultTzId = repoDefaultTzId,
                        onSelect = { calendarVisibility.setDisplayTzId(it) },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                ScheduleDayView(
                date = date,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
                onBandTap = onBandTap,
                onPlanTrip = onPlanTrip,
                effectiveZoom = effectiveZoom,
                metaGroupByCalendar = metaGroupByCalendar,
                // Round 2.21 Phase D.5 — pinch-to-zoom on the day grid.
                // The picker's per-row segmented control covers the
                // deliberate path; pinch is the gesture-convenience layer
                // (D-2.21.i). Persists via the same `setZoom` surface.
                onPinchZoomBand = calendarVisibility?.let { prefs ->
                    { calRef, repoRef, newZoom ->
                        prefs.setZoom(calRef.id, newZoom, repoRef.id)
                    }
                },
                zoomFor = { calRef, repoRef ->
                    calendarVisibility?.zoomOf(calRef.id, repoRef.id)
                        ?: effectiveZoom
                },
                onDragReschedule = onDragReschedule,
                )
            }
            ScheduleViewTab.Week -> {
                val weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ScheduleWeekView(
                    weekStart = weekStart,
                    schedule = rendered,
                    modifier = Modifier.fillMaxSize(),
                    onBandTap = onBandTap,
                    onSwipeWeek = { delta -> state.setDate(date.plusWeeks(delta.toLong())) },
                    effectiveZoom = effectiveZoom,
                    onDragReschedule = onDragReschedule,
                )
            }
            ScheduleViewTab.Month -> ScheduleMonthView(
                monthAnchor = date,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
                onDayTap = { d ->
                    state.setDate(d)
                    state.setSelectedTab(ScheduleViewTab.Day)
                    onPersistTab(ScheduleViewTab.Day)
                },
                onBandTap = onBandTap,
            )
            ScheduleViewTab.Agenda -> ScheduleTimeboxView(
                date = date,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
                onBandTap = onBandTap,
                onPlanTrip = onPlanTrip,
            )
            ScheduleViewTab.Year -> ScheduleYearView(
                year = date.year,
                schedule = rendered,
                modifier = Modifier.fillMaxSize(),
                onMonthTap = { m ->
                    state.setDate(date.withMonth(m.value).withDayOfMonth(1))
                    state.setSelectedTab(ScheduleViewTab.Month)
                    onPersistTab(ScheduleViewTab.Month)
                },
            )
        }
    }
}

@Composable
private fun ScheduleDetailEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(TestTagScheduleDetailEmpty),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.schedule_detail_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun findActiveBand(bands: List<DayBand>, now: Instant = Instant.now()): DayBand? =
    bands.firstOrNull { b ->
        val start = b.instance.effectiveStart.toInstant()
        val end = b.instance.effectiveEnd.toInstant()
        !now.isBefore(start) && now.isBefore(end)
    }
