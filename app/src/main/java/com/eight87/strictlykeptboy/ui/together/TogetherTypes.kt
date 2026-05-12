package com.eight87.strictlykeptboy.ui.together

import com.eight87.strictlykeptboy.resolver.CommonTimeFinder
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.TimeSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Phase N — Together UI types.
 *
 * Narrow data shapes the Compose layer + ViewModel consume. Kept in
 * one small file because none of these have independent reasons to
 * change. (R.X.4 SRP.)
 */

/**
 * A repo presented in the Together repo picker. Independent of the
 * Git layer's [com.eight87.strictlykeptboy.git.RepoConfig] so the UI
 * never touches the encrypted prefs store directly. (R.X.1 narrow
 * data interface.)
 */
data class TogetherRepoOption(
    val repoId: String,
    val displayName: String,
)

/** Form-bound state for the input section. */
data class TogetherInputState(
    val selectedRepoIds: Set<String> = emptySet(),
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now().plusDays(14),
    val durationMinutes: Int = DEFAULT_DURATION_MIN,
    val daysOfWeek: Set<DayOfWeek> = DayOfWeek.values().toSet(),
    val timeFrom: LocalTime = LocalTime.of(9, 0),
    val timeTo: LocalTime = LocalTime.of(22, 0),
    val tzId: ZoneId = ZoneId.systemDefault(),
) {
    val isSubmittable: Boolean
        get() = selectedRepoIds.isNotEmpty() &&
            durationMinutes in DURATION_MIN_RANGE &&
            !endDate.isBefore(startDate) &&
            daysOfWeek.isNotEmpty() &&
            timeTo != timeFrom

    companion object {
        const val DEFAULT_DURATION_MIN = 60
        val DURATION_MIN_RANGE = 15..480
    }
}

/** Result-state sealed hierarchy. R.X.2 — branch on sealed, not enum. */
sealed interface TogetherResultState {
    data object Idle : TogetherResultState
    data object Running : TogetherResultState
    data class Results(val slots: List<TimeSlot>) : TogetherResultState
    data object Empty : TogetherResultState
}

/**
 * ISP-narrow port for "look up busy sets by repo". The ViewModel
 * depends on this abstraction so tests can substitute a fake without
 * spinning up Room. (R.X.1 + R.X.8.)
 */
fun interface BusySource {
    suspend fun busyFor(
        repoIds: Set<String>,
        start: LocalDate,
        endInclusive: LocalDate,
        tzId: ZoneId,
    ): Map<RepoRef, List<MaterializedInstance>>
}

/**
 * ISP-narrow port for the finder. Wraps [CommonTimeFinder] so the
 * ViewModel never holds the concrete class. The composition root
 * (MainActivity / future AppGraph) wires the real implementation;
 * tests pass a fake. (R.X.3.)
 */
fun interface CommonTimeFinderPort {
    suspend fun find(query: CommonTimeFinder.Query): List<TimeSlot>
}
