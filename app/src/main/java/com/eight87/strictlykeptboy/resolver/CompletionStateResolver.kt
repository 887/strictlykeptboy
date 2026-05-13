package com.eight87.strictlykeptboy.resolver

import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Phase XX.2 / AT-B / RV-R.1-2 — pure completion-state resolver.
 *
 * Extracts the inverted-default algorithm out of [OverlayResolver] so the
 * UI, ViewModels, and tests can call it without owning the whole overlay
 * machine. The overlay layer still tags each [DayBand]; this module is
 * the single source of truth for *how* the tag is computed.
 *
 * Pure function: no IO, no `android.*`. Callers materialize their input
 * tuple (start, end, optional deviation) before calling here.
 *
 * Algorithm (LOCKED, per AT-B.2 + RV-R.2):
 *   1. `start > now`                    → Scheduled
 *   2. `start <= now < end`, no dev     → InProgress
 *   3. `now >= end`, no dev             → CompletedBySchedule (the inversion)
 *   4. any past-or-current WITH dev     → that dev's kind
 *
 * Exception (`kind = "cancel"`) shadows any deviation per AT-B.3; the
 * caller must drop cancelled occurrences before reaching this function
 * (consistent with [RecurrenceMaterializer] semantics where cancelled
 * exceptions are filtered out at expansion time).
 */
object CompletionStateResolver {

    /**
     * Minimal pure input shape — narrower than [MaterializedInstance] so
     * unrelated callers (notification end-alarm flip, ViewModel preview,
     * widget) can satisfy it without dragging the whole resolver graph
     * in (SOLID-I).
     */
    data class Input(
        val targetId: String,
        val occurrenceDate: LocalDate,
        val start: ZonedDateTime,
        val end: ZonedDateTime,
    )

    /**
     * @param deviation matching deviation for this `(targetId, occurrenceDate)`,
     *   or `null` if none exists.
     */
    fun resolve(input: Input, deviation: DeviationInput?, now: ZonedDateTime): CompletionState {
        if (deviation != null) {
            return mapDeviationKind(deviation.kind)
        }
        return when {
            now.isBefore(input.start) -> CompletionState.Scheduled
            now.isBefore(input.end) -> CompletionState.InProgress
            else -> CompletionState.CompletedBySchedule
        }
    }

    /** Convenience for callers holding a [MaterializedInstance]. */
    fun resolveFor(
        instance: MaterializedInstance,
        deviations: List<DeviationInput>,
        now: ZonedDateTime,
    ): CompletionState {
        val targetId = when (val s = instance.source) {
            is InstanceSource.OneOff -> s.eventId.id
            is InstanceSource.RuleInstance -> s.ruleId.id
        }
        val day = instance.effectiveStart.toLocalDate()
        val dev = deviations.firstOrNull { it.targetId == targetId && it.instanceDate == day }
        return resolve(
            Input(targetId, day, instance.effectiveStart, instance.effectiveEnd),
            dev,
            now,
        )
    }

    /**
     * Stable map from a deviation `kind` string to [CompletionState]. An
     * unknown string degrades to [CompletionState.Scheduled] so an
     * upstream rename / typo can't crash the resolver — the validator
     * (DeviationValidator) is the right place to reject those.
     */
    fun mapDeviationKind(kind: String): CompletionState = when (kind) {
        "skipped" -> CompletionState.Skipped
        "partial" -> CompletionState.PartiallyDone
        "completed-early" -> CompletionState.CompletedEarly
        "completed-late" -> CompletionState.CompletedLate
        else -> CompletionState.Scheduled
    }
}

/**
 * AT-B.5 visual treatment — the *what the tile should communicate*, not
 * the literal Material Color. The UI layer (Phase F) maps this to actual
 * `MaterialTheme.colorScheme` tokens. Locked here so Compose and tests
 * agree on the affordance vocabulary.
 *
 * Hard constraints (LOCKED): never red, never warning glyphs, never
 * blinking. Every state is affirmative or neutral.
 */
data class CompletionVisualTreatment(
    val state: CompletionState,
    val fill: FillStyle,
    val dim: Boolean,
    val glyph: Glyph?,
) {
    enum class FillStyle { Filled, OutlineOnly, HalfFilled, SoftProgressArc }
    enum class Glyph { Check, ClockOffset }

    companion object {
        fun forState(s: CompletionState): CompletionVisualTreatment = when (s) {
            CompletionState.CompletedBySchedule ->
                CompletionVisualTreatment(s, FillStyle.Filled, dim = true, glyph = Glyph.Check)
            CompletionState.Skipped ->
                CompletionVisualTreatment(s, FillStyle.OutlineOnly, dim = true, glyph = null)
            CompletionState.PartiallyDone ->
                CompletionVisualTreatment(s, FillStyle.HalfFilled, dim = false, glyph = null)
            CompletionState.CompletedEarly,
            CompletionState.CompletedLate ->
                CompletionVisualTreatment(s, FillStyle.Filled, dim = false, glyph = Glyph.ClockOffset)
            CompletionState.InProgress ->
                CompletionVisualTreatment(s, FillStyle.SoftProgressArc, dim = false, glyph = null)
            CompletionState.Scheduled ->
                CompletionVisualTreatment(s, FillStyle.Filled, dim = false, glyph = null)
        }
    }
}
