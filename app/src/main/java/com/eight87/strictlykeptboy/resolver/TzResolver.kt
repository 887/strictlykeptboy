package com.eight87.strictlykeptboy.resolver

import java.time.ZoneId

/**
 * DST policy (Round 2.24 Phase E — locked in decisions.md D.121).
 *
 * Source-of-truth instants on `Event` / `RecurrenceRule` are built by
 * the caller via `LocalDateTime.atZone(ZoneId)` (or the lib-recur
 * iterator, which routes through the same JDK rules). Two ambiguous
 * cases arise at DST transitions:
 *
 * - **D-2.24.g — Spring-forward (gap).** A local clock that falls into
 *   the missing hour (e.g. 02:30 on the US spring-forward day) snaps
 *   to the next valid instant — the same wall-clock minute one hour
 *   later. `ZonedDateTime.of(localDateTime, zone)` already implements
 *   this via `ZoneRules.getValidOffsets()` returning empty + falling
 *   through to the gap's `after()` offset. We keep the JDK default.
 *
 * - **D-2.24.h — Fall-back (overlap).** A local clock that occurs
 *   twice (e.g. 01:30 on the US fall-back day) resolves to the
 *   **earlier** offset (the pre-transition one). `ZonedDateTime.of`
 *   with no `preferredOffset` argument gives this; we keep the
 *   default. Selecting the later offset would silently shift a
 *   user's "before bed" event into "after midnight, again" land.
 *
 * Both rules are verified by
 * `app/src/test/java/com/eight87/strictlykeptboy/resolver/DstEdgeCaseTest.kt`,
 * which is the cited verification baseline in decisions.md D.121.
 * Recurring rules driven by dmfs lib-recur honour the same snap; the
 * daily-rule-across-DST case in that file is the regression-pin.
 *
 * If a future refactor needs to build a `ZonedDateTime` from local
 * parts inside this package, use the unadorned
 * `ZonedDateTime.of(LocalDateTime, ZoneId)` constructor — do NOT call
 * `ZonedDateTime.ofLocal(local, zone, preferredOffset)` with a
 * non-null preferred offset, or you will diverge from the policy.
 */

/**
 * Round 2.24 / Phase B — pure timezone resolution helpers.
 *
 * `TzResolver` is intentionally stateless and top-level: it owns one
 * decision (which `ZoneId` to use for a given event, given the chain of
 * possible overrides) and nothing else. See D-2.24.a + D-2.24.b for the
 * locked precedence rules.
 *
 * The full Phase B plan defines a four-arg `effectiveDisplayTz` shape;
 * Phase B.1 ships the two narrow helpers the Renderer needs today:
 *
 * - [sourceZone] — the zone an event's `start`/`end` instants are
 *   anchored to. Used by [RecurrenceMaterializer] + `fromOneOff` to tag
 *   `MaterializedInstance.sourceTzId` for the UI badge layer and by the
 *   renderer when converting to a caller-supplied display zone.
 * - [parseOrFallback] — the codec accepts arbitrary `tz_id` strings
 *   (hand-edited repos) so this is the canonical place to turn one into
 *   a `ZoneId` without surfacing a stacktrace on malformed input.
 */
object TzResolver {

    /**
     * Resolves the effective source-of-truth zone for an event:
     * `eventTzId` if set → `repoDefaultTzId` if set → [systemDefault].
     *
     * Each string input is parsed via [parseOrFallback] against the next
     * tier so malformed strings degrade gracefully instead of throwing.
     */
    fun sourceZone(
        eventTzId: String?,
        repoDefaultTzId: String?,
        systemDefault: ZoneId,
    ): ZoneId {
        val repoZone = parseOrFallback(repoDefaultTzId, systemDefault)
        return parseOrFallback(eventTzId, repoZone)
    }

    /**
     * Returns `ZoneId.of(maybeZoneId)` when [maybeZoneId] is a non-blank
     * parseable zone, otherwise [fallback]. Never throws — malformed or
     * unknown zone strings (e.g. `"Not/A_Zone"`) silently fall through.
     */
    fun parseOrFallback(maybeZoneId: String?, fallback: ZoneId): ZoneId {
        val trimmed = maybeZoneId?.takeIf { it.isNotBlank() } ?: return fallback
        return try {
            ZoneId.of(trimmed)
        } catch (_: java.time.DateTimeException) {
            fallback
        } catch (_: IllegalArgumentException) {
            fallback
        }
    }
}
