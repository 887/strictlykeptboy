package com.eight87.strictlykeptboy.composition

import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.TodolistMeta
import com.eight87.strictlykeptboy.resolver.TodolistRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Round 2.1.A — bridge from the Room cache + repo store to
 * [com.eight87.strictlykeptboy.resolver.RepoSnapshot] for the
 * resolver pipeline.
 *
 * Single Responsibility: derive the (`calendars`, `todolists`, `repos`)
 * triple of the [RepoSnapshot] from the indexer's persisted rows across
 * every configured repo, and re-emit whenever any input changes.
 *
 * Calendar / todolist metadata is synthesized from the distinct
 * `calendarId` / `todolistId` values appearing in events + rules +
 * tasks. The richer per-calendar TOML (priority, supersedes, active
 * windows, baselineCadenceDays) is deferred to a follow-on phase — the
 * resolver tolerates default-valued metadata (active = true, priority
 * 500, system tz) and just renders everything.
 *
 * Reactivity: re-emits on every [RepoStore.state] change. Within a
 * given repo set, it also re-reads on every multi-repo event-rows
 * emission (DB invalidation when the indexer writes any event row),
 * which catches new calendars showing up after the user creates the
 * first event on a brand-new calendar. Rule-only repos are picked up
 * on the next event change or repo-store change — acceptable for a
 * Phase 2.1.A bridge; a per-DAO `listAllFlow` is the follow-on
 * optimisation (currently blocked by the "do not modify
 * CacheDatabase" constraint).
 *
 * Concurrency: all DB reads inside the flow run on [Dispatchers.IO]
 * via `.flowOn(Dispatchers.IO)`. Shared via
 * `stateIn(SharingStarted.Eagerly)` so the resolver always has a value
 * to read.
 */
class IndexerSnapshotPublisher(
    private val db: CacheDatabase,
    private val repoStore: RepoStore,
    private val scope: CoroutineScope,
    /**
     * Round 2.18.A.8 — optional pipe of system-calendar metas
     * ([com.eight87.strictlykeptboy.system.SystemCalendarsRepository.state]).
     * When non-null, the synthesized `RepoSnapshot.calendars` list is the
     * union of file-backed calendars + every emission from this flow.
     * Defaults to an empty flow so existing call-sites (and tests) do
     * not need to know about system calendars.
     */
    private val externalCalendars: StateFlow<List<CalendarMeta>> =
        MutableStateFlow<List<CalendarMeta>>(emptyList()).asStateFlow(),
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RepoSnapshot> =
        repoStore.state
            .flatMapLatest { configs -> snapshotFlow(configs) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = EMPTY,
            )

    private fun snapshotFlow(configs: List<RepoConfig>): Flow<RepoSnapshot> {
        val tick: Flow<Unit> = if (configs.isEmpty()) {
            // Still need a "first emission" pulse so external calendars
            // alone can populate the snapshot when zero file-backed
            // repos are configured.
            flowOf(Unit)
        } else {
            db.events()
                .multiRepoByDateRange(FAR_PAST_MS, FAR_FUTURE_MS)
                .map { Unit }
        }
        // Combine the (per-config) tick with the external calendar
        // metas flow so any change on either side re-emits.
        return combine(tick, externalCalendars) { _, externals ->
            readAll(configs, externals)
        }
    }

    private suspend fun readAll(
        configs: List<RepoConfig>,
        externalCalendars: List<CalendarMeta>,
    ): RepoSnapshot {
        val externalRepoRefs = externalCalendars.map { it.repo }.distinct()
        val fileRepoEntries = configs.map {
            RepoSnapshot.RepoEntry(ref = RepoRef(it.repoId), lastIndexedHeadSha = null)
        }
        val externalRepoEntries = externalRepoRefs.map {
            // Synthetic repos have no Git HEAD; signal that with null.
            RepoSnapshot.RepoEntry(ref = it, lastIndexedHeadSha = null)
        }
        val repos = fileRepoEntries + externalRepoEntries
        val calendars = mutableListOf<CalendarMeta>()
        calendars += externalCalendars
        val todolists = mutableListOf<TodolistMeta>()
        for (cfg in configs) {
            val repoRef = RepoRef(cfg.repoId)
            val events = db.events().listAll(cfg.repoId)
            val rules = db.recurrenceRules().listAll(cfg.repoId)
            val tasks = db.tasks().listAll(cfg.repoId)
            val standing = db.standingTasks().listAll(cfg.repoId)

            val calendarIds = buildSet {
                events.forEach { add(it.calendarId) }
                rules.forEach { add(it.calendarId) }
            }
            calendarIds.mapTo(calendars) { id ->
                CalendarMeta(
                    ref = CalendarRef(id),
                    repo = repoRef,
                    displayName = id,
                    priority = DEFAULT_PRIORITY,
                )
            }

            val todolistIds = buildSet {
                tasks.forEach { add(it.todolistId) }
                standing.forEach { add(it.todolistId) }
            }
            todolistIds.mapTo(todolists) { id ->
                TodolistMeta(
                    ref = TodolistRef(id),
                    repo = repoRef,
                    displayName = id,
                )
            }
        }
        return RepoSnapshot(repos = repos, calendars = calendars, todolists = todolists)
    }

    companion object {
        private const val DEFAULT_PRIORITY = 500
        // Wide window guaranteed to cover every event row.
        private const val FAR_PAST_MS = -62_135_596_800_000L // year 0001 UTC midnight
        private const val FAR_FUTURE_MS = 253_402_300_799_000L // year 9999-12-31

        val EMPTY: RepoSnapshot = RepoSnapshot(emptyList(), emptyList(), emptyList())
    }
}
