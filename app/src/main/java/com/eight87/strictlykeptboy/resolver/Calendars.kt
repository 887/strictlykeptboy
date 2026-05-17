package com.eight87.strictlykeptboy.resolver

import java.time.ZoneId

/**
 * Round 2.18.A.3 — kind of calendar surface.
 *
 * - [Regular]: file-backed event calendar in a skb repo.
 * - [Timebox]: file-backed timebox calendar (focus sessions, work blocks).
 * - [External]: synthetic calendar backed by Android's CalendarContract
 *   (Google, Exchange, iCloud, etc. — provided by the OS sync adapters).
 *   External calendars are read-only in Phase A; Phase D adds write-back.
 *   Resolver treats `External` as regular-equivalent for active-windows,
 *   priority, supersedence, and inversion semantics — the distinction
 *   only matters for the writeback layer + UI badges.
 */
enum class CalendarKind { Regular, Timebox, External }

/**
 * Resolver-facing calendar metadata. Caller (UI / view-model layer)
 * builds this from a parsed `calendars/<id>/calendar.md` plus any
 * runtime settings overrides; the resolver only reads.
 *
 * `priority` is a per-overlay integer per D.20 (1..1000; 999 = special).
 * `supersedes` lists the calendar IDs this calendar suppresses while
 * its own active-windows match — see RV-O / HV-E.
 */
data class CalendarMeta(
    val ref: CalendarRef,
    val repo: RepoRef,
    val displayName: String,
    val priority: Int,
    val activeToggle: Boolean = true,
    val activeWindows: List<DateRange> = emptyList(),
    val activeHours: List<HourRange> = emptyList(),
    val tzId: ZoneId = ZoneId.systemDefault(),
    val kind: CalendarKind = CalendarKind.Regular,
    val supersedes: List<CalendarRef> = emptyList(),
    /**
     * Baseline cadence in days for off-schedule detection (RV-P).
     * `null` disables the check. When set, any event whose distance
     * from its calendar's nearest recurrence is greater than this is
     * tagged `offSchedule`.
     */
    val baselineCadenceDays: Int? = null,
    val colorSeed: Int? = null,
    /**
     * Round 2.18.A.11 — `CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL`
     * for external calendars (one of `CAL_ACCESS_*` constants). `null` for
     * non-external calendars. Drives the edit-affordance gating in the UI:
     * the detail sheet hides edit / delete actions when this is below
     * `CAL_ACCESS_CONTRIBUTOR` (500).
     */
    val externalAccessLevel: Int? = null,
    /**
     * Round 2.21.A.2 — opt-in atomic-event grouping. When non-blank,
     * the renderer's grouping pass (Phase F) collapses adjacent events
     * sharing the same `group` label into a single visual band at low
     * zoom levels. The literal string echoes `meta_group_field` from
     * `calendar.toml`; only its non-emptiness is consulted by v1.
     */
    val metaGroupField: String? = null,
    /**
     * Round 2.21.B — single-grapheme emoji used as the overlay's
     * identity glyph in the picker + day-band chips. Sourced from
     * `emoji = "..."` at the top level of `calendar.toml`.
     */
    val emoji: String? = null,
) {
    /**
     * Round 2.18.C — parses the `(accountType, accountName)` tuple
     * embedded in [repo].id for external calendars. Format is
     * `system/<accountType>/<accountName>`. Returns `null` for
     * non-external repos (kind != External) or repos whose id doesn't
     * match the synthetic shape.
     */
    val externalAccount: Pair<String, String>?
        get() = if (kind != CalendarKind.External) null else {
            val parts = repo.id.split('/', limit = 3)
            if (parts.size == 3 && parts[0] == "system") parts[1] to parts[2] else null
        }
}

/**
 * Round 2.18.A.7 — sidecar source-of-origin tag for events that came in
 * via [com.eight87.strictlykeptboy.system.SystemEventsBridge] from
 * Android's `CalendarContract`. Carries the writeback-relevant tuple so
 * Phase D can route an edit back to the right account.
 *
 * Defended: resolver code never reads this; only the UI badge layer +
 * the (future) writeback path consult it. Existing file-backed events
 * leave it `null` — those go through `GitRepo.commitAll`.
 */
data class ExternalSource(
    val accountType: String,
    val accountName: String,
    /** `CalendarContract.Events._ID`. */
    val eventId: Long,
    /** One of `CalendarContract.Calendars.CAL_ACCESS_*`. */
    val accessLevel: Int,
    /** Owner email/account, may be `null` for some sync adapters. */
    val ownerAccount: String? = null,
)

data class TodolistMeta(
    val ref: TodolistRef,
    val repo: RepoRef,
    val displayName: String,
    val activeToggle: Boolean = true,
    val activeWindows: List<DateRange> = emptyList(),
    val activeHours: List<HourRange> = emptyList(),
    val tzId: ZoneId = ZoneId.systemDefault(),
)

/**
 * Frozen view of all repos at one point in time. The resolver memoizes
 * on `contentHash`, which is a stable hash of `(repoId, lastIndexedHeadSha)`
 * tuples — when any repo's HEAD changes, the cache key changes.
 */
data class RepoSnapshot(
    val repos: List<RepoEntry>,
    val calendars: List<CalendarMeta>,
    val todolists: List<TodolistMeta>,
) {
    data class RepoEntry(val ref: RepoRef, val lastIndexedHeadSha: String?)

    val contentHash: String by lazy {
        val canon = repos
            .sortedBy { it.ref.id }
            .joinToString("\n") { "${it.ref.id}=${it.lastIndexedHeadSha ?: ""}" }
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(canon.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
