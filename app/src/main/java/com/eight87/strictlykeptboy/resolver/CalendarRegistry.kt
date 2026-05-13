package com.eight87.strictlykeptboy.resolver

import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.store.EntityPath
import com.eight87.strictlykeptboy.store.RoutineCalendarConfig
import com.eight87.strictlykeptboy.store.SupersedenceConfig
import com.eight87.strictlykeptboy.store.TomlReader
import com.eight87.strictlykeptboy.store.TomlTable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Round 2.1.B.1 — calendars-first aggregator across all configured repos.
 *
 * Reads `calendars/<id>/calendar.toml` from every [RepoConfig.rootDir]
 * via the existing [RoutineCalendarConfig] + [SupersedenceConfig] codecs
 * and **overlays** the parsed fields onto the synthesized
 * [com.eight87.strictlykeptboy.composition.IndexerSnapshotPublisher]
 * snapshot. Synthesized defaults (active = true, priority = 500,
 * displayName = calendarId) are preserved when a calendar row exists
 * in the cache but no `calendar.toml` is present on disk.
 *
 * Merge by `(repoId, calendarId)`: prefer real-toml fields, fall back
 * to synthesized defaults. Calendars only on disk (no events / rules
 * yet) are still listed — `CalendarFilterChipStrip` (2.1.B.2) must show
 * them so the user can author into them.
 *
 * **Schema availability note (deviation flagged):** the existing TOML
 * codecs cover `name`, `priority`, `supersedes`, `routine.active_toggle`.
 * They do **not** carry `active_windows`, `active_hours`, or
 * `color_seed` (per the brief: "every field you need already exists in
 * `RoutineCalendarConfig` / `SupersedenceConfig`"; in practice only a
 * subset does). This registry surfaces what's available and leaves the
 * other fields at resolver-defaults (empty / null). The
 * `CalendarSettingsSheet` (2.1.B.4) is therefore deferred until those
 * three TOML keys are added in a follow-on commit — the constraint
 * "don't extend the schema" was honoured.
 *
 * Reactivity: combines `RepoStore.state` with the synthesized snapshot
 * `StateFlow<RepoSnapshot>` and re-reads disk on every emission. File
 * I/O runs on [Dispatchers.IO]. For now there is no file-watcher —
 * `Settings → calendar edit` round-trips through `GitRepo.commitAll`
 * which triggers `RepoStore.state` notifications (and even without
 * that, the next event/rule index pulse re-runs the read). A debounced
 * watcher is a 2.1.L follow-on.
 */
class CalendarRegistry(
    private val repoStore: RepoStore,
    private val synthesizedSnapshot: StateFlow<RepoSnapshot>,
    private val scope: CoroutineScope,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<List<CalendarMeta>> =
        repoStore.state
            .flatMapLatest { configs -> overlayFlow(configs) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList(),
            )

    private fun overlayFlow(configs: List<RepoConfig>): Flow<List<CalendarMeta>> {
        if (configs.isEmpty()) return flowOf(emptyList())
        return combine(flowOf(configs), synthesizedSnapshot) { cfgs, snap ->
            overlay(cfgs, snap)
        }
    }

    private fun overlay(configs: List<RepoConfig>, snap: RepoSnapshot): List<CalendarMeta> {
        // Index synthesized snapshot by (repoId, calendarId) for fast lookup.
        val byKey: Map<Pair<String, String>, CalendarMeta> = snap.calendars
            .associateBy { it.repo.id to it.ref.id }

        // Disk-derived calendars across all configs.
        val diskKeys = mutableSetOf<Pair<String, String>>()
        val merged = mutableListOf<CalendarMeta>()
        for (cfg in configs) {
            val repoRoot = runCatching { Path.of(cfg.rootDir) }.getOrNull() ?: continue
            val calendarsDir = repoRoot.resolve("calendars")
            if (!Files.isDirectory(calendarsDir)) continue
            Files.list(calendarsDir).use { listing ->
                listing.forEach { calDir ->
                    if (!Files.isDirectory(calDir)) return@forEach
                    val calendarId = calDir.fileName.toString()
                    val tomlPath = calDir.resolve("calendar.toml")
                    val parsed = readCalendarToml(tomlPath, calendarId) ?: return@forEach
                    diskKeys += cfg.repoId to calendarId
                    val key = cfg.repoId to calendarId
                    val synth = byKey[key]
                    merged += applyOverlay(
                        repoRef = RepoRef(cfg.repoId),
                        calendarId = calendarId,
                        synth = synth,
                        parsed = parsed,
                        repoColorFallback = cfg.colorSeed,
                    )
                }
            }
        }
        // Pass-through any synthesized calendars NOT covered by disk
        // (events exist but no calendar.toml — rare; happens during
        // bootstrap or after a manual edit that left the metadata
        // behind).
        for ((key, meta) in byKey) {
            if (key !in diskKeys) merged += meta
        }
        return merged
    }

    private fun readCalendarToml(path: Path, calendarId: String): ParsedCalendarToml? {
        if (!Files.isRegularFile(path)) return null
        return runCatching {
            val text = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
            val table = TomlReader.parse(text)
            ParsedCalendarToml(
                displayName = table.getString("name")
                    ?: table.getString("display_name")
                    ?: calendarId,
                priority = table.getInt("priority") ?: DEFAULT_PRIORITY,
                routine = RoutineCalendarConfig.read(table),
                supersedence = SupersedenceConfig.read(table, hostCalendarId = calendarId),
                emoji = table.getString("emoji"),
                tzId = table.getString("tz_id")?.let {
                    runCatching { java.time.ZoneId.of(it) }.getOrNull()
                },
            )
        }.getOrNull()
    }

    private fun applyOverlay(
        repoRef: RepoRef,
        calendarId: String,
        synth: CalendarMeta?,
        parsed: ParsedCalendarToml,
        repoColorFallback: Int?,
    ): CalendarMeta {
        val base = synth ?: CalendarMeta(
            ref = CalendarRef(calendarId),
            repo = repoRef,
            displayName = parsed.displayName,
            priority = parsed.priority,
        )
        return base.copy(
            displayName = parsed.displayName,
            priority = parsed.priority,
            activeToggle = parsed.routine.activeToggle,
            supersedes = parsed.supersedence.supersedes.map { CalendarRef(it) },
            tzId = parsed.tzId ?: base.tzId,
            colorSeed = base.colorSeed ?: repoColorFallback,
        )
    }

    /** Parsed-but-not-yet-overlaid `calendar.toml` view. */
    private data class ParsedCalendarToml(
        val displayName: String,
        val priority: Int,
        val routine: RoutineCalendarConfig,
        val supersedence: SupersedenceConfig,
        val emoji: String?,
        val tzId: java.time.ZoneId?,
    )

    companion object {
        private const val DEFAULT_PRIORITY = 500
    }
}
