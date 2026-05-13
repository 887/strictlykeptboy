package com.eight87.strictlykeptboy.ui.calendars

import com.eight87.strictlykeptboy.composition.AppGraph
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.store.CalendarActivityConfig
import com.eight87.strictlykeptboy.store.RoutineCalendarConfig
import com.eight87.strictlykeptboy.store.SupersedenceConfig
import com.eight87.strictlykeptboy.store.TomlReader
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlValue
import com.eight87.strictlykeptboy.store.TomlWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Round 2.1.B.4 — disk + commit side of [CalendarSettingsSheet].
 *
 * Reads the existing `calendars/<id>/calendar.toml` (so untouched keys
 * survive), overwrites the fields the sheet owns, then commits via the
 * per-repo GitRepo from [GitRepoRegistry]. Runs on [Dispatchers.IO].
 *
 * SOLID-S: codec + commit orchestration only; the sheet itself stays
 * input-only.
 */
object CalendarSettingsWriter {

    suspend fun write(graph: AppGraph, draft: CalendarSettingsDraft) {
        withContext(Dispatchers.IO) {
            val meta = draft.calendar
            val cfg = graph.repoStore.list().firstOrNull { it.repoId == meta.repo.id } ?: return@withContext
            val root = Path.of(cfg.rootDir)
            val tomlPath = root.resolve("calendars/${meta.ref.id}/calendar.toml")
            Files.createDirectories(tomlPath.parent)
            // Read existing table (so unrelated keys round-trip), else
            // start fresh with the canonical scalars.
            val table: TomlTable = if (Files.isRegularFile(tomlPath)) {
                TomlReader.parse(String(Files.readAllBytes(tomlPath), StandardCharsets.UTF_8))
            } else {
                TomlTable().apply {
                    putString("id", meta.ref.id)
                    putString("name", meta.displayName)
                }
            }
            // Routine fields — preserve existing routine flags, only
            // overwrite active_toggle.
            val existingRoutine = RoutineCalendarConfig.read(table)
            existingRoutine.copy(activeToggle = draft.activeToggle).writeInto(table)
            // Priority + supersedes — top-level scalars.
            table.putInt("priority", draft.priority)
            // Drop existing supersedes, rewrite from draft.
            table.scalars.remove("supersedes")
            if (draft.supersedes.isNotEmpty()) {
                table.putStringArray("supersedes", draft.supersedes)
            }
            // Activity fields — drop+rewrite.
            table.scalars.remove("color_seed")
            table.aotables.remove("active_windows")
            table.aotables.remove("active_hours")
            CalendarActivityConfig(
                colorSeed = meta.colorSeed, // preserved; sheet doesn't edit color
                activeWindows = draft.activeWindows,
                activeHours = draft.activeHours,
            ).writeInto(table)
            // Preserve SupersedenceConfig sub-block (baseline-cadence,
            // non-superseable, superseded_during) — only `supersedes`
            // moves; the rest is read+re-written verbatim.
            val keepSupersedence = SupersedenceConfig.read(table, hostCalendarId = meta.ref.id)
                .copy(supersedes = draft.supersedes)
            keepSupersedence.writeInto(table)
            Files.write(
                tomlPath,
                TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8),
            )
            GitRepoRegistry.get(meta.repo.id)?.commitAll("calendar settings: ${meta.displayName}")
        }
    }
}
