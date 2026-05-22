package com.eight87.strictlykeptboy.composition

import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.HourRange
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.store.CalendarActivityConfig
import com.eight87.strictlykeptboy.store.RoutineCalendarConfig
import com.eight87.strictlykeptboy.store.SupersedenceConfig
import com.eight87.strictlykeptboy.store.TomlReader
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
 * **Schema extension (Round 2.1.B):** `active_windows`, `active_hours`,
 * and `color_seed` are read through the sibling [CalendarActivityConfig]
 * codec, alongside the existing [RoutineCalendarConfig] +
 * [SupersedenceConfig] reads. Back-compat: legacy `calendar.toml` files
 * without the new keys yield empty defaults.
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
                    val dirName = calDir.fileName.toString()
                    val tomlPath = calDir.resolve("calendar.toml")
                    val parsed = readCalendarToml(tomlPath, dirName) ?: return@forEach
                    // Round 2.25 follow-up — the indexer keys events by
                    // their frontmatter `calendar_id = "<uuid>"`, not by
                    // the calendar's directory name. Prefer the TOML
                    // `id` field as the canonical CalendarRef id so the
                    // enriched CalendarMeta merges correctly into the
                    // snapshot consumed by OverlayResolver (otherwise
                    // every band falls through to the
                    // `inst.calendar.id.hashCode()` fallback hue and
                    // the per-calendar colorSeed never reaches paint).
                    val calendarId = parsed.canonicalId ?: dirName
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
            val kind = when (table.getString("kind")?.lowercase()) {
                "timebox" -> com.eight87.strictlykeptboy.resolver.CalendarKind.Timebox
                "base" -> com.eight87.strictlykeptboy.resolver.CalendarKind.Base
                else -> com.eight87.strictlykeptboy.resolver.CalendarKind.Regular
            }
            ParsedCalendarToml(
                canonicalId = table.getString("id")?.takeIf { it.isNotBlank() },
                displayName = table.getString("name")
                    ?: table.getString("display_name")
                    ?: calendarId,
                priority = table.getInt("priority") ?: DEFAULT_PRIORITY,
                routine = RoutineCalendarConfig.read(table),
                supersedence = SupersedenceConfig.read(table, hostCalendarId = calendarId),
                activity = CalendarActivityConfig.read(table),
                emoji = table.getString("emoji"),
                tzId = table.getString("tz_id")?.let {
                    runCatching { java.time.ZoneId.of(it) }.getOrNull()
                },
                kind = kind,
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
        val activeWindows = parsed.activity.activeWindows.map { r ->
            DateRange(start = r.from, endInclusive = r.to)
        }
        val activeHours = parsed.activity.activeHours.map { h ->
            HourRange(day = h.day, from = h.from, to = h.to)
        }
        return base.copy(
            displayName = parsed.displayName,
            priority = parsed.priority,
            activeToggle = parsed.routine.activeToggle,
            activeWindows = activeWindows.ifEmpty { base.activeWindows },
            activeHours = activeHours.ifEmpty { base.activeHours },
            supersedes = parsed.supersedence.supersedes.map { CalendarRef(it) },
            tzId = parsed.tzId ?: base.tzId,
            colorSeed = parsed.activity.colorSeed ?: base.colorSeed ?: repoColorFallback,
            metaGroupField = parsed.activity.metaGroupField ?: base.metaGroupField,
            emoji = parsed.emoji ?: base.emoji,
            kind = parsed.kind,
        )
    }

    /** Parsed-but-not-yet-overlaid `calendar.toml` view. */
    private data class ParsedCalendarToml(
        /**
         * Round 2.25 follow-up — the canonical CalendarRef id from the
         * TOML `id = "<uuid>"` field. `null` when the file omits the
         * key, in which case the directory name is used as a fallback.
         * The indexer keys events by this UUID, so getting this right
         * is load-bearing for the snapshot merge.
         */
        val canonicalId: String? = null,
        val displayName: String,
        val priority: Int,
        val routine: RoutineCalendarConfig,
        val supersedence: SupersedenceConfig,
        val activity: CalendarActivityConfig,
        val emoji: String?,
        val tzId: java.time.ZoneId?,
        val kind: com.eight87.strictlykeptboy.resolver.CalendarKind =
            com.eight87.strictlykeptboy.resolver.CalendarKind.Regular,
    )

    companion object {
        private const val DEFAULT_PRIORITY = 500
    }
}
