package com.eight87.strictlykeptboy.ui.calendars

import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoStore
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

    suspend fun write(repoStore: RepoStore, draft: CalendarSettingsDraft) {
        withContext(Dispatchers.IO) {
            val meta = draft.calendar
            val cfg = repoStore.list().firstOrNull { it.repoId == meta.repo.id } ?: return@withContext
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
            // Round 2.21.B — identity (emoji + color). Drop existing
            // values, rewrite from draft (null emoji ⇒ clear, null
            // colorSeed ⇒ preserve existing meta.colorSeed).
            table.scalars.remove("emoji")
            draft.emoji?.takeIf { it.isNotBlank() }?.let { table.putString("emoji", it) }
            // Activity fields — drop+rewrite.
            table.scalars.remove("color_seed")
            table.aotables.remove("active_windows")
            table.aotables.remove("active_hours")
            CalendarActivityConfig(
                colorSeed = draft.colorSeed ?: meta.colorSeed,
                activeWindows = draft.activeWindows,
                activeHours = draft.activeHours,
                metaGroupField = meta.metaGroupField,
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

    /**
     * Round 2.22 / Fix 3 — single-scalar write for the OverlayPicker
     * inline priority editor. Reads the existing `calendar.toml` to
     * preserve every other key, rewrites only the top-level `priority`
     * scalar, and commits. Mirrors [write]'s read-merge-write pattern
     * but stays cheap because the inline editor only ever changes that
     * one scalar.
     */
    suspend fun writePriority(
        repoStore: RepoStore,
        repoId: String,
        calendarId: String,
        calendarDisplayName: String,
        newPriority: Int,
    ) {
        withContext(Dispatchers.IO) {
            val cfg = repoStore.list().firstOrNull { it.repoId == repoId } ?: return@withContext
            val root = Path.of(cfg.rootDir)
            val tomlPath = root.resolve("calendars/$calendarId/calendar.toml")
            Files.createDirectories(tomlPath.parent)
            val table: TomlTable = if (Files.isRegularFile(tomlPath)) {
                TomlReader.parse(String(Files.readAllBytes(tomlPath), StandardCharsets.UTF_8))
            } else {
                TomlTable().apply {
                    putString("id", calendarId)
                    putString("name", calendarDisplayName)
                }
            }
            table.putInt("priority", newPriority)
            Files.write(
                tomlPath,
                TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8),
            )
            GitRepoRegistry.get(repoId)?.commitAll(
                "calendar priority: $calendarDisplayName → $newPriority",
            )
        }
    }

    /**
     * Round 2.23.2 — single-scalar write for the OverlayPicker inline
     * color row. Reads the existing `calendar.toml` to preserve every
     * other key, rewrites only `color_seed`, and commits. Mirrors
     * [writePriority]'s shape.
     */
    suspend fun writeColorSeed(
        repoStore: RepoStore,
        repoId: String,
        calendarId: String,
        calendarDisplayName: String,
        colorSeed: Int,
    ) {
        withContext(Dispatchers.IO) {
            val cfg = repoStore.list().firstOrNull { it.repoId == repoId } ?: return@withContext
            val root = Path.of(cfg.rootDir)
            val tomlPath = root.resolve("calendars/$calendarId/calendar.toml")
            Files.createDirectories(tomlPath.parent)
            val table: TomlTable = if (Files.isRegularFile(tomlPath)) {
                TomlReader.parse(String(Files.readAllBytes(tomlPath), StandardCharsets.UTF_8))
            } else {
                TomlTable().apply {
                    putString("id", calendarId)
                    putString("name", calendarDisplayName)
                }
            }
            table.scalars.remove("color_seed")
            table.putInt("color_seed", colorSeed and 0xFFFFFF)
            Files.write(
                tomlPath,
                TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8),
            )
            GitRepoRegistry.get(repoId)?.commitAll(
                "calendar color: $calendarDisplayName → #%06X".format(colorSeed and 0xFFFFFF),
            )
        }
    }
}
