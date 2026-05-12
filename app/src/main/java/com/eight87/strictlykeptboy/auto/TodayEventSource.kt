package com.eight87.strictlykeptboy.auto

import com.eight87.strictlykeptboy.resolver.MaterializedInstance

/**
 * Phase Q.2 — ISP-narrow data source for the Android Auto Today list (R.X.1).
 *
 * The Auto surface needs exactly one thing: the list of materialized event
 * instances that fall inside "today" (system tz). It does NOT need the
 * full [com.eight87.strictlykeptboy.resolver.Renderer] surface, the
 * [com.eight87.strictlykeptboy.git.RepoStore], the
 * [com.eight87.strictlykeptboy.cache.CacheDatabase], or any other god-handle.
 *
 * The concrete implementation lives in
 * [com.eight87.strictlykeptboy.composition.AppGraph.todayEventSource] and
 * is parked into [CarAppRuntime] so the [SkbCarAppService] can locate it
 * without a DI framework, mirroring [com.eight87.strictlykeptboy.sync.SyncRuntime].
 *
 * Read-only by contract (Phase Q / UI-S.4); the Auto surface never writes.
 */
fun interface TodayEventSource {
    /** Snapshot the events active today (system tz). Synchronous to keep the
     *  CarAppService template factory simple — the underlying data is already
     *  cached in memory by the composition root. */
    fun eventsForToday(): List<MaterializedInstance>
}

/**
 * Process-wide handle for [SkbCarAppService] to reach the
 * [TodayEventSource] built by the composition root.
 *
 * Mirrors [com.eight87.strictlykeptboy.sync.SyncRuntime]. Population
 * happens in [com.eight87.strictlykeptboy.composition.AppGraph.parkRuntimes];
 * the service reads it lazily on first session creation.
 *
 * `null` means the phone app has not been launched yet in this process —
 * the service falls back to an empty source so the Auto screen renders an
 * empty-state ListTemplate instead of crashing.
 */
object CarAppRuntime {
    @Volatile var todayEventSource: TodayEventSource? = null
}
