package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.store.IdentityTomlData
import java.time.LocalDate
import java.time.ZoneId

/**
 * Phase 2.2.E.6 — ISP-narrow data source for briefing materialization.
 *
 * The WorkManager [BriefingWorker] runs in its own process-attached
 * context and cannot reach into [com.eight87.strictlykeptboy.composition.AppGraph]
 * for the live resolver snapshot directly (no Application handle to
 * locate it through). Mirroring the
 * [com.eight87.strictlykeptboy.auto.CarAppRuntime] parked-handle pattern
 * solves the same problem the same way: the composition root parks a
 * narrow interface into [BriefingRuntime] at app launch, and the worker
 * reads from it on each fire.
 *
 * `null` source ⇒ the phone-app process has not been launched yet in
 * this WorkManager wake-up cycle; the worker falls back to the
 * salutation-only Briefing (empty `instances`) so the user still sees
 * something at 07:00 / 21:00.
 *
 * SOLID:
 *  - **S:** declares the briefing's data hand-off only; no Android I/O,
 *    no notification posting.
 *  - **I:** the worker depends on this one-method interface, not on
 *    the full Renderer / RepoSnapshot / CacheDatabase surface.
 *  - **D:** the worker imports [BriefingSource], not the concrete
 *    AppGraph.
 */
fun interface BriefingSource {
    /**
     * Snapshot the materialized event instances that overlap [date] in
     * [zone]. Synchronous because the worker doesn't have a coroutine
     * scope to await against — the composition root's live snapshot is
     * already kept hot in memory.
     */
    fun instancesForDate(date: LocalDate, zone: ZoneId): List<MaterializedInstance>
}

/**
 * Phase 2.2.E.6 — process-wide parked handles for [BriefingWorker].
 *
 * Population happens in
 * [com.eight87.strictlykeptboy.composition.AppGraph.parkRuntimes];
 * the worker reads it lazily on each fire. Same lifetime + same
 * threading discipline as
 * [com.eight87.strictlykeptboy.auto.CarAppRuntime].
 *
 * Both fields default to `null` so a worker that fires before the
 * phone-app process has booted (rare — WorkManager schedules drive
 * fires while the app has been launched at least once) renders the
 * salutation-only Briefing rather than crashing.
 */
object BriefingRuntime {
    @Volatile var source: BriefingSource? = null
    /**
     * Identity snapshot for the salutation. Lambda-typed for the same
     * reason [com.eight87.strictlykeptboy.auto.CarAppRuntime.identityProvider]
     * is — the composition root resolves lazily so wizard edits + repo
     * flips are picked up without restarting the worker.
     */
    @Volatile var identityProvider: (() -> IdentityTomlData?)? = null
}
