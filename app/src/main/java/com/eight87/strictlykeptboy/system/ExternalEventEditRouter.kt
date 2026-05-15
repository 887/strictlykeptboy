package com.eight87.strictlykeptboy.system

import com.eight87.strictlykeptboy.resolver.ExternalSource

/**
 * Round 2.18.D.2 — pure decision helper for the editor flow.
 *
 * Given a `MaterializedInstance.external` sidecar, returns the mode the
 * editor sheet should open in. Stateless, no Android imports — fully
 * unit-testable.
 *
 * Contract:
 *  - `external == null` → [EditorMode.WritableSkbRepo]: the legacy
 *    file-backed flow ([com.eight87.strictlykeptboy.store.EntityWriter]
 *    + Git commit) applies. Phase D does not touch this branch.
 *  - `external != null` AND `accessLevel >= CAL_ACCESS_CONTRIBUTOR (500)`
 *    → [EditorMode.WritableExternal]: save routes to
 *    [CalendarContractWriter] instead of `EntityWriter`.
 *  - `external != null` AND `accessLevel <  CAL_ACCESS_CONTRIBUTOR`
 *    → [EditorMode.ReadOnlyExternal]: sheet opens read-only with a
 *    "Read-only — owned by <account>" header.
 */
object ExternalEventEditRouter {

    /** `CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR`. */
    const val CAL_ACCESS_CONTRIBUTOR: Int = 500

    fun decide(external: ExternalSource?): EditorMode = when {
        external == null -> EditorMode.WritableSkbRepo
        external.accessLevel >= CAL_ACCESS_CONTRIBUTOR ->
            EditorMode.WritableExternal(external)
        else -> EditorMode.ReadOnlyExternal(external)
    }
}

sealed interface EditorMode {
    /** Non-external event; legacy file-backed editor + EntityWriter. */
    data object WritableSkbRepo : EditorMode

    /** External event, user has write access — route through CalendarContractWriter. */
    data class WritableExternal(val source: ExternalSource) : EditorMode

    /** External event, read-only — show owner header, hide save / delete. */
    data class ReadOnlyExternal(val source: ExternalSource) : EditorMode
}
