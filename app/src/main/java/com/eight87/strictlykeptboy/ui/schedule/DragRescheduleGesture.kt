package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.InstanceSource
import java.time.OffsetDateTime

/**
 * Round 2.22 / Phase B UI follow-up — shared long-press-and-drag wiring
 * for [DayBand] surfaces across Day / Week / 3-day views.
 *
 * Hoists the cross-band [DragRescheduleUiState] in the host so a single
 * ghost-band element can render at the snapped target time. The pointer
 * detector lives on the band (child) so it takes precedence over the
 * grid's pinch-to-zoom transform detector (parent).
 *
 * Caller wiring:
 * 1. `remember { rememberDragRescheduleUiState() }` at view level.
 * 2. For each band, `Modifier.dragRescheduleBand(state, band, hourHeightPx, onDrop)`.
 * 3. Host composes a translucent ghost-band at `state.ghostTopPx` when
 *    `state.draggedBandId != null` (renderer's responsibility).
 */
class DragRescheduleUiState {
    val draggedBandIdState: MutableState<String?> = mutableStateOf(null)
    val dragOffsetYState: MutableState<Float> = mutableStateOf(0f)
    val snappedStartState: MutableState<OffsetDateTime?> = mutableStateOf(null)

    val draggedBandId: String? get() = draggedBandIdState.value
    val dragOffsetY: Float get() = dragOffsetYState.value
    val snappedStart: OffsetDateTime? get() = snappedStartState.value

    fun begin(bandId: String) {
        draggedBandIdState.value = bandId
        dragOffsetYState.value = 0f
        snappedStartState.value = null
    }

    fun update(deltaY: Float, snap: OffsetDateTime) {
        dragOffsetYState.value += deltaY
        snappedStartState.value = snap
    }

    fun end() {
        draggedBandIdState.value = null
        dragOffsetYState.value = 0f
        snappedStartState.value = null
    }
}

@Composable
fun rememberDragRescheduleUiState(): DragRescheduleUiState =
    remember { DragRescheduleUiState() }

/**
 * Long-press-then-drag detector for a single [DayBand]. The detector
 * is intentionally placed on the band surface so the parent grid's
 * pinch detector (`detectTransformGestures`) still wins when the user
 * starts a pinch outside any band.
 *
 * On drop ([onDrop]) the caller receives the snapped [OffsetDateTime]
 * and routes single-instance vs. recurring through
 * [DragRescheduleController].
 */
fun Modifier.dragRescheduleBand(
    state: DragRescheduleUiState,
    band: DayBand,
    hourHeightPx: Float,
    snapMinutes: Int = DragReschedule.DRAG_SNAP_MINUTES,
    onDrop: (DayBand, OffsetDateTime) -> Unit,
): Modifier = this.pointerInput(band.instance.instanceId, hourHeightPx) {
    detectDragGesturesAfterLongPress(
        onDragStart = { _: Offset ->
            state.begin(band.instance.instanceId)
        },
        onDragEnd = {
            val snapped = state.snappedStart
            state.end()
            if (snapped != null) onDrop(band, snapped)
        },
        onDragCancel = { state.end() },
        onDrag = { _, dragAmount ->
            val deltaY = dragAmount.y
            val baseStart = band.instance.effectiveStart
            val totalOffsetY = state.dragOffsetY + deltaY
            val minutesShift = if (hourHeightPx <= 0f) 0L
            else (totalOffsetY / hourHeightPx * 60f).toLong()
            val rawNew = baseStart.toOffsetDateTime().plusMinutes(minutesShift)
            val snapped = DragReschedule.snapToGrid(rawNew, snapMinutes)
            state.update(deltaY, snapped)
        },
    )
}

/** True when [band] is sourced from a recurring rule (multi-branch prompt path). */
fun DayBand.isRecurringInstance(): Boolean =
    instance.source is InstanceSource.RuleInstance

/** Rule id when [band] is a recurring instance, else `null`. */
fun DayBand.ruleId(): String? =
    (instance.source as? InstanceSource.RuleInstance)?.ruleId?.id

/** Original ZonedDateTime of the recurring instance, else `null`. */
fun DayBand.recurringOriginalDate(): java.time.LocalDate? =
    (instance.source as? InstanceSource.RuleInstance)?.originalStart?.toLocalDate()

/** Single-instance event id, else `null`. */
fun DayBand.singleEventId(): String? =
    (instance.source as? InstanceSource.OneOff)?.eventId?.id

