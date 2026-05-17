package com.eight87.strictlykeptboy.ui.together

import com.eight87.strictlykeptboy.resolver.CommonTimeFinder
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.TimeWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase N.2 — Together ViewModel.
 *
 * Holds the input state + result state. On submit, asks the
 * [BusySource] for the busy set and delegates to
 * [CommonTimeFinderPort]. Pure-data over narrow ports — no Android
 * imports here; both ports are wired by the composition root.
 */
class TogetherViewModel(
    private val scope: CoroutineScope,
    private val repoOptionsFlow: StateFlow<List<TogetherRepoOption>>,
    private val busySource: BusySource,
    private val finder: CommonTimeFinderPort,
) {
    private val _input = MutableStateFlow(TogetherInputState())
    val input: StateFlow<TogetherInputState> = _input.asStateFlow()

    private val _result = MutableStateFlow<TogetherResultState>(TogetherResultState.Idle)
    val result: StateFlow<TogetherResultState> = _result.asStateFlow()

    val repoOptions: StateFlow<List<TogetherRepoOption>> = repoOptionsFlow

    fun updateInput(transform: (TogetherInputState) -> TogetherInputState) {
        _input.value = transform(_input.value)
    }

    fun toggleRepo(repoId: String) = updateInput { s ->
        val next = if (repoId in s.selectedRepoIds) s.selectedRepoIds - repoId
        else s.selectedRepoIds + repoId
        s.copy(selectedRepoIds = next)
    }

    fun toggleDayOfWeek(day: java.time.DayOfWeek) = updateInput { s ->
        val next = if (day in s.daysOfWeek) s.daysOfWeek - day else s.daysOfWeek + day
        s.copy(daysOfWeek = next)
    }

    fun submit() {
        val state = _input.value
        if (!state.isSubmittable) return
        _result.value = TogetherResultState.Running
        scope.launch {
            val busy = busySource.busyFor(
                repoIds = state.selectedRepoIds,
                start = state.startDate,
                endInclusive = state.endDate,
                tzId = state.tzId,
            )
            val participants = busy.keys.toList().ifEmpty {
                state.selectedRepoIds.map { RepoRef(it) }
            }
            // Round 2.24 / D.4 — translate the form's repoId-keyed
            // participantTz map into a RepoRef-keyed map for the
            // finder. Empty map ⇒ pre-D.1 behaviour (back-compat).
            val participantTzByRef = state.participantTz
                .mapKeys { (repoId, _) -> RepoRef(repoId) }
            val slots = finder.find(
                CommonTimeFinder.Query(
                    participants = participants,
                    busyByParticipant = busy,
                    range = DateRange(state.startDate, state.endDate),
                    minDurationMinutes = state.durationMinutes,
                    window = TimeWindow(
                        daysOfWeek = state.daysOfWeek,
                        from = state.timeFrom,
                        toExclusive = state.timeTo,
                    ),
                    tzId = state.tzId,
                    participantTz = participantTzByRef,
                ),
            )
            _result.value = if (slots.isEmpty()) TogetherResultState.Empty
            else TogetherResultState.Results(slots)
        }
    }

    fun reset() {
        _result.value = TogetherResultState.Idle
    }
}
