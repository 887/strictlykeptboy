package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.runtime.Immutable
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.ViewMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase F.4 — minimal state holder for SchedulePane.
 *
 * Picked over a full Hilt-injected ViewModel to keep Phase F bootable
 * without DI plumbing. Phase G replaces with a proper VM when view-mode
 * switching, persistence, and saved-state-handle plumbing land.
 */
@Immutable
data class ScheduleSnapshot(
    val date: LocalDate,
    val schedule: RenderedSchedule?,
)

class ScheduleViewState(
    private val scope: CoroutineScope,
    private val renderer: Renderer = Renderer(),
    private val snapshotFlow: StateFlow<RepoSnapshot>,
    private val sourcesFlow: StateFlow<Renderer.Sources>,
    initialDate: LocalDate = LocalDate.now(),
    private val tz: ZoneId = ZoneId.systemDefault(),
) {
    private val _date = MutableStateFlow(initialDate)
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    private val _rendered = MutableStateFlow<RenderedSchedule?>(null)
    val rendered: StateFlow<RenderedSchedule?> = _rendered.asStateFlow()

    init {
        scope.launch {
            combine(_date, snapshotFlow, sourcesFlow) { d, snap, src -> Triple(d, snap, src) }
                .collect { (d, snap, src) ->
                    _rendered.value = renderer.render(
                        range = DateRange(d, d),
                        viewMode = ViewMode.Day,
                        snapshot = snap,
                        sources = src,
                        renderTz = tz,
                    )
                }
        }
    }

    fun setDate(date: LocalDate) { _date.value = date }
}
