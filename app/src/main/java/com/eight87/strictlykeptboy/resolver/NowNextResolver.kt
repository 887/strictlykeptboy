package com.eight87.strictlykeptboy.resolver

import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Round 2.25 — pure resolver for the "Now / Next" surface shared
 * between (a) the bottom NowPlayingSheetHost peek row, (b) the
 * ongoing notification, and (c) the home / lockscreen widget(s).
 *
 * SOLID:
 *  - **S**: one reason to change — the definition of "what is now"
 *    and "what is next" lives here, nowhere else.
 *  - **D**: pure java.time; no Android imports, no Compose. Plain
 *    JVM testable via [NowNextResolverTest].
 *
 * See `docs/plans/round-2-25-now-next.md` for the design + D-2.25.a..b.
 */
data class BandRef(
    val title: String,
    val emoji: String?,
    val calendarDisplay: String,
    val start: ZonedDateTime,
    val end: ZonedDateTime,
)

/**
 * Snapshot of "currently active" + "earliest upcoming" events.
 *
 * `now_at` is the capture moment; [BandRef.start] / [BandRef.end] are
 * absolute, so the UI's 60-second tick can re-compute
 * `formatRelative(Duration.between(now_at, next.start))` without
 * re-querying the resolver (D-2.25.f).
 */
data class NowNextSnapshot(
    val now: BandRef?,
    val next: BandRef?,
    val now_at: Instant,
) {
    companion object {
        val Empty: NowNextSnapshot = NowNextSnapshot(now = null, next = null, now_at = Instant.EPOCH)
    }
}

/**
 * Pure derive: given `today` (and optionally `tomorrow`, to cover the
 * late-night cusp per D-2.25.b) materialized instances + a capture
 * instant, returns the now / next pair.
 *
 * Inputs are not required to be sorted; the resolver sorts internally.
 */
object NowNextResolver {

    fun derive(
        today: List<MaterializedInstance>,
        at: Instant,
        tomorrow: List<MaterializedInstance> = emptyList(),
    ): NowNextSnapshot {
        val all = (today + tomorrow).sortedBy { it.effectiveStart.toInstant() }
        // "now" = the latest-starting event whose [start, end) contains `at`.
        // If multiple overlap, prefer the one that started most recently
        // (a freshly-started focus block beats a long-running all-day band).
        val now = all
            .asSequence()
            .filter { inst ->
                val s = inst.effectiveStart.toInstant()
                val e = inst.effectiveEnd.toInstant()
                !s.isAfter(at) && e.isAfter(at)
            }
            .maxByOrNull { it.effectiveStart.toInstant() }
        // "next" = earliest event in the combined list whose start > now.
        val next = all
            .asSequence()
            .filter { it.effectiveStart.toInstant().isAfter(at) }
            .minByOrNull { it.effectiveStart.toInstant() }
        return NowNextSnapshot(
            now = now?.toBandRef(),
            next = next?.toBandRef(),
            now_at = at,
        )
    }

    private fun MaterializedInstance.toBandRef(): BandRef = BandRef(
        title = title,
        emoji = emoji,
        // Resolver doesn't carry calendar display name on the instance;
        // the caller-side projection enriches when available. Falling
        // back to the calendar ref id keeps the renderer total.
        calendarDisplay = calendar.id,
        start = effectiveStart,
        end = effectiveEnd,
    )

    /**
     * "in 2h 15m" / "in 12 min" / "now" / "in 3d 2h" formatter. Pure
     * (no locale lookups); UI layer can swap in a string-resource
     * variant later if i18n requires it.
     */
    fun formatRelative(d: Duration): String {
        val secs = d.seconds
        if (secs < 60) return "now"
        val totalMinutes = secs / 60
        if (totalMinutes < 60) return "in $totalMinutes min"
        val totalHours = totalMinutes / 60
        if (totalHours < 24) {
            val mins = totalMinutes % 60
            return if (mins == 0L) "in ${totalHours}h" else "in ${totalHours}h ${mins}m"
        }
        val days = totalHours / 24
        val hours = totalHours % 24
        return if (hours == 0L) "in ${days}d" else "in ${days}d ${hours}h"
    }
}
