package com.eight87.strictlykeptboy.store

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Phase XX.1 / AT-A.6 — deviation file pre-write validator.
 *
 * Pure logic: takes a proposed [Deviation] + the "set of valid (entity-id,
 * date) targets that the resolver currently surfaces" + a clock, and
 * returns a [Result]. No IO inside this file (per CLAUDE.md § Design
 * principles — SOLID: pure resolver-adjacent code).
 *
 * Rules (per draft-atomic-activities.md AT-A.6):
 *  - `kind` MUST be one of [VALID_KINDS]; `completed` is explicitly
 *    forbidden — that's the default-by-schedule state and writing a
 *    file for it would balloon the repo (AT-A.3 LOCKED).
 *  - `at` MUST NOT be in the future relative to the supplied clock.
 *  - `instance_date` MUST exist in the resolver's view at that date —
 *    callers may pass an empty set if they want to skip the existence
 *    check (offline CLI path), in which case only clock + kind are
 *    enforced.
 *  - When [Deviation.devKind] is `partial`, [Deviation.subbeatsCompleted]
 *    may be empty (means "started but completed nothing"); for other
 *    kinds it MUST be empty (partial is the only `kind` that owns the
 *    sub-beat array per AT-A.2 schema).
 */
object DeviationValidator {

    /** All accepted `kind` values per AT-A.3. */
    val VALID_KINDS: Set<String> = setOf(
        "skipped",
        "partial",
        "completed-early",
        "completed-late",
    )

    sealed interface Result {
        data object Ok : Result
        sealed interface Error : Result {
            val message: String
            data class InvalidKind(override val message: String) : Error
            data class FutureAt(override val message: String) : Error
            data class UnknownTarget(override val message: String) : Error
            data class SubbeatsOnNonPartial(override val message: String) : Error
        }
    }

    /**
     * @param knownTargets set of (targetId, instanceDate) pairs the
     *  resolver currently surfaces; pass `null` to skip the existence
     *  check (CLI / migration paths).
     */
    fun validate(
        deviation: Deviation,
        now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
        knownTargets: Set<Pair<String, LocalDate>>? = null,
    ): Result {
        if (deviation.devKind !in VALID_KINDS) {
            return Result.Error.InvalidKind(
                "kind must be one of $VALID_KINDS; got '${deviation.devKind}'. " +
                    "'completed' is forbidden — that's the inverted default state.",
            )
        }
        val atParsed = runCatching { OffsetDateTime.parse(deviation.at) }.getOrNull()
        if (atParsed != null && atParsed.isAfter(now)) {
            return Result.Error.FutureAt(
                "deviation.at '${deviation.at}' is in the future relative to '$now'",
            )
        }
        if (deviation.devKind != "partial" && deviation.subbeatsCompleted.isNotEmpty()) {
            return Result.Error.SubbeatsOnNonPartial(
                "subbeats_completed only meaningful when kind = 'partial'; " +
                    "got kind = '${deviation.devKind}'",
            )
        }
        if (knownTargets != null) {
            val day = runCatching { LocalDate.parse(deviation.instanceDate) }.getOrNull()
                ?: return Result.Error.UnknownTarget(
                    "instance_date '${deviation.instanceDate}' is not a valid yyyy-MM-dd",
                )
            val key = deviation.targetId to day
            if (key !in knownTargets) {
                return Result.Error.UnknownTarget(
                    "no scheduled occurrence of '${deviation.targetId}' on $day in resolver view",
                )
            }
        }
        return Result.Ok
    }
}
