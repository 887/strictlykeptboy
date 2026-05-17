package com.eight87.strictlykeptboy.resolver

import java.time.ZoneId

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
