package com.eight87.strictlykeptboy.auto

import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.store.IdentityTomlData

/**
 * Phase Q.2 / 2.1.G — ISP-narrow data source for the Android Auto
 * Today list (R.X.1).
 *
 * The Auto surface needs exactly one thing: the list of materialized
 * event instances that fall inside "today" (system tz), augmented with
 * resolver-tagged flags the driver-facing rendering cares about. It
 * does NOT need the full [com.eight87.strictlykeptboy.resolver.Renderer]
 * surface, the [com.eight87.strictlykeptboy.git.RepoStore], the
 * [com.eight87.strictlykeptboy.cache.CacheDatabase], or any other
 * god-handle.
 *
 * The concrete implementation lives in
 * [com.eight87.strictlykeptboy.composition.AppGraph.todayEventSource]
 * and is parked into [CarAppRuntime] so the [SkbCarAppService] can
 * locate it without a DI framework, mirroring
 * [com.eight87.strictlykeptboy.sync.SyncRuntime].
 *
 * Read-only by contract (Phase Q / UI-S.4); the Auto surface never
 * writes.
 */
fun interface TodayEventSource {
    /** Snapshot the events active today (system tz). Synchronous to keep the
     *  CarAppService template factory simple — the underlying data is already
     *  cached in memory by the composition root. */
    fun eventsForToday(): List<AutoEvent>
}

/**
 * Phase 2.1.G — row-shaped value type the Auto screens render.
 *
 * Wraps a [MaterializedInstance] with the resolver-tagged annotations
 * the driver-facing surface cares about (today only off-schedule —
 * D.80 / RV-Q). Keeping the wrapper here means [TodayScreen] and
 * [NextUpScreen] never reach into [com.eight87.strictlykeptboy.resolver.DayBand]
 * (which carries lane + supersedence + completion state that the Auto
 * surface deliberately ignores under the UI-S.4 read-only contract).
 *
 * `offSchedule` defaults to `false` so call-sites that only have a
 * raw [MaterializedInstance] (e.g. the AppGraph one-off fast path)
 * can construct an [AutoEvent] without threading the resolver through.
 */
data class AutoEvent(
    val instance: MaterializedInstance,
    val offSchedule: Boolean = false,
)

/**
 * Phase Q.1 / 2.1.G — Process-wide handle for [SkbCarAppService] to
 * reach the [TodayEventSource] + identity snapshot loader built by
 * the composition root.
 *
 * Mirrors [com.eight87.strictlykeptboy.sync.SyncRuntime]. Population
 * happens in [com.eight87.strictlykeptboy.composition.AppGraph.parkRuntimes];
 * the service reads it lazily on first session creation.
 *
 * `null` on either field means the phone app has not been launched
 * yet in this process — the service falls back to an empty source +
 * generic copy so the Auto screen renders an empty-state ListTemplate
 * instead of crashing.
 */
object CarAppRuntime {
    @Volatile var todayEventSource: TodayEventSource? = null
    /**
     * Phase 2.1.G.2 / 2.1.G.4 — identity snapshot for the active repo.
     *
     * Lambda-typed so the composition root can resolve lazily (avoiding
     * reading `identity.toml` on every Auto refresh). Returns `null`
     * when no repo is bound or the file is missing / malformed — Auto
     * screens fall back to plain copy in that case.
     */
    @Volatile var identityProvider: (() -> IdentityTomlData?)? = null
}
