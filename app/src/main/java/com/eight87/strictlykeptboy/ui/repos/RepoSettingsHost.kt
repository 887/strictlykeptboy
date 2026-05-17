package com.eight87.strictlykeptboy.ui.repos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.Uuid7
import com.eight87.strictlykeptboy.git.auth.SecretsStore
import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.store.CalendarActivityConfig
import com.eight87.strictlykeptboy.store.IdentityPronouns
import com.eight87.strictlykeptboy.store.IdentityTomlCodec
import com.eight87.strictlykeptboy.store.ModeTomlCodec
import com.eight87.strictlykeptboy.store.RepoMode
import com.eight87.strictlykeptboy.store.RoutineCalendarConfig
import com.eight87.strictlykeptboy.store.SupersedenceConfig
import com.eight87.strictlykeptboy.store.TomlReader
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Round 2.5.B — single Composable wrapping `RepoSettingsScreen` with the
 * disk-backed wiring for the new Calendars / Identity / Mode sections.
 * Both the compact + two-pane branches above delegate to this to keep
 * the wiring DRY.
 *
 * Reads `<repoRoot>/calendars/<id>/calendar.toml` for the calendar list,
 * `<repoRoot>/identity.toml` for the per-repo identity snapshot, and
 * `<repoRoot>/mode.toml` for the mode. Writes round-trip through the
 * matching codecs + `GitRepoRegistry.get(repoId).commitAll(...)`. A
 * `refreshTick` state forces a re-read after each write so the UI
 * reflects the on-disk truth.
 */
@Composable
internal fun RepoSettingsHost(
    repo: RepoConfig,
    state: ReposViewState,
    secretsStore: SecretsStore?,
    scope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit,
    onAddRemote: () -> Unit,
    onOpenIdentities: () -> Unit,
    onShare: () -> Unit,
    onRemoved: () -> Unit,
) {
    val repoRoot: Path = remember(repo.repoId, repo.rootDir) { Path.of(repo.rootDir) }
    var refreshTick by remember { mutableStateOf(0) }
    var calendars by remember(repo.repoId) { mutableStateOf<List<CalendarMeta>>(emptyList()) }
    var identity by remember(repo.repoId) {
        mutableStateOf(PerRepoIdentitySnapshot())
    }
    var modeSnap by remember(repo.repoId) { mutableStateOf(RepoMode.Free) }
    // Calendar-edit sheet host.
    var pendingEdit by remember { mutableStateOf<CalendarMeta?>(null) }

    LaunchedEffect(repo.repoId, refreshTick) {
        val (cals, id, md) = withContext(Dispatchers.IO) {
            Triple(
                scanCalendars(repoRoot, repo.repoId, repo.colorSeed),
                runCatching { IdentityTomlCodec.readOrDefault(repoRoot) }.getOrNull(),
                runCatching { ModeTomlCodec.readOrDefault(repoRoot) }.getOrNull(),
            )
        }
        calendars = cals
        if (id != null) {
            identity = PerRepoIdentitySnapshot(
                praise = id.praiseTerm,
                pronouns = "${id.pronouns.subject}/${id.pronouns.obj}",
                honorific = id.honorificForDom,
            )
        }
        if (md != null) modeSnap = md.mode
    }

    RepoSettingsScreen(
        repo = repo,
        onBack = onBack,
        onUpdate = { newCfg -> scope.launch { state.store.update(newCfg) } },
        onAddRemote = onAddRemote,
        onRemoveRemote = { name ->
            scope.launch { state.store.removeRemote(repo.repoId, name) }
        },
        onSetPrimaryRemote = { name ->
            scope.launch { state.store.setPrimary(repo.repoId, name) }
        },
        onRemoveRepo = { deleteLocal ->
            scope.launch {
                state.store.remove(repo.repoId)
                secretsStore?.clearForRepo(repo.repoId)
                if (deleteLocal) {
                    runCatching { java.io.File(repo.rootDir).deleteRecursively() }
                }
                onRemoved()
            }
        },
        onOpenIdentities = onOpenIdentities,
        onShareRepo = onShare,
        onToggleRemoteReadOnly = { name, value ->
            scope.launch {
                val current = state.store.get(repo.repoId) ?: return@launch
                val updated = current.copy(
                    remotes = current.remotes.map { rb ->
                        if (rb.name == name) rb.copy(treatAsReadOnly = value) else rb
                    },
                )
                state.store.update(updated)
            }
        },
        calendars = calendars,
        onToggleCalendarActive = { cal, active ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    setCalendarActive(repoRoot, cal.ref.id, active)
                    GitRepoRegistry.get(repo.repoId)
                        ?.commitAll("calendar: toggle ${cal.displayName} active=$active")
                }
                refreshTick++
            }
        },
        onEditCalendar = { cal -> pendingEdit = cal },
        onAddCalendar = { name, kind, emoji ->
            scope.launch(Dispatchers.IO) {
                runCatching { createCalendar(repoRoot, name, kind, emoji) }
                runCatching {
                    GitRepoRegistry.get(repo.repoId)?.commitAll("calendar: add $name")
                }
                refreshTick++
            }
        },
        identitySnapshot = identity,
        onIdentityEdit = { snap ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val existing = runCatching {
                        IdentityTomlCodec.readOrDefault(repoRoot)
                    }.getOrNull()
                        ?: com.eight87.strictlykeptboy.store.IdentityTomlData.LockedDefaults
                    val parsedPronouns = parsePronounsPair(snap.pronouns) ?: existing.pronouns
                    val merged = existing.copy(
                        praiseTerm = snap.praise.ifBlank { existing.praiseTerm },
                        pronouns = parsedPronouns,
                        honorificForDom = snap.honorific.ifBlank { existing.honorificForDom },
                    )
                    IdentityTomlCodec.write(repoRoot, merged)
                    GitRepoRegistry.get(repo.repoId)?.commitAll("identity: update")
                }
                refreshTick++
            }
        },
        modeSnapshot = modeSnap,
        onModeEdit = { newMode ->
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val existing = runCatching {
                        ModeTomlCodec.readOrDefault(repoRoot)
                    }.getOrNull()
                        ?: com.eight87.strictlykeptboy.store.ModeTomlData.Default
                    ModeTomlCodec.write(repoRoot, existing.copy(mode = newMode))
                    GitRepoRegistry.get(repo.repoId)?.commitAll("mode: ${newMode.wire}")
                }
                refreshTick++
            }
        },
    )

    // CalendarSettingsSheet overlay — reuses the existing sheet (2.1.B.4).
    pendingEdit?.let { meta ->
        com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsSheet(
            calendar = meta,
            onDismiss = { pendingEdit = null },
            onSave = { draft ->
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        writeCalendarSheetDraft(repoRoot, draft)
                        GitRepoRegistry.get(repo.repoId)
                            ?.commitAll("calendar settings: ${meta.displayName}")
                    }
                    pendingEdit = null
                    refreshTick++
                }
            },
        )
    }
}

// -----------------------------------------------------------------------
// Disk helpers — scan + write per-repo calendar.toml files.
// -----------------------------------------------------------------------

private fun scanCalendars(
    repoRoot: Path,
    repoId: String,
    repoColorFallback: Int?,
): List<CalendarMeta> {
    val dir = repoRoot.resolve("calendars")
    if (!Files.isDirectory(dir)) return emptyList()
    val out = mutableListOf<CalendarMeta>()
    Files.list(dir).use { listing ->
        listing.forEach { calDir ->
            if (!Files.isDirectory(calDir)) return@forEach
            val calendarId = calDir.fileName.toString()
            val tomlPath = calDir.resolve("calendar.toml")
            if (!Files.isRegularFile(tomlPath)) return@forEach
            runCatching {
                val text = String(Files.readAllBytes(tomlPath), StandardCharsets.UTF_8)
                val t = TomlReader.parse(text)
                val displayName = t.getString("name") ?: t.getString("display_name") ?: calendarId
                val priority = t.getInt("priority") ?: 500
                val routine = RoutineCalendarConfig.read(t)
                val supersedence = SupersedenceConfig.read(t, hostCalendarId = calendarId)
                val activity = CalendarActivityConfig.read(t)
                val kindStr = t.getString("kind")
                val kind = when (kindStr?.lowercase()) {
                    "timebox" -> CalendarKind.Timebox
                    else -> CalendarKind.Regular
                }
                out += CalendarMeta(
                    ref = CalendarRef(calendarId),
                    repo = RepoRef(repoId),
                    displayName = displayName,
                    priority = priority,
                    activeToggle = routine.activeToggle,
                    activeWindows = activity.activeWindows.map {
                        com.eight87.strictlykeptboy.resolver.DateRange(
                            start = it.from,
                            endInclusive = it.to,
                        )
                    },
                    activeHours = activity.activeHours.map {
                        com.eight87.strictlykeptboy.resolver.HourRange(
                            day = it.day,
                            from = it.from,
                            to = it.to,
                        )
                    },
                    kind = kind,
                    supersedes = supersedence.supersedes.map { CalendarRef(it) },
                    colorSeed = activity.colorSeed ?: repoColorFallback,
                )
            }
        }
    }
    return out.sortedBy { it.displayName.lowercase() }
}

private fun setCalendarActive(repoRoot: Path, calendarId: String, active: Boolean) {
    val tomlPath = repoRoot.resolve("calendars/$calendarId/calendar.toml")
    if (!Files.isRegularFile(tomlPath)) return
    val table = TomlReader.parse(
        String(Files.readAllBytes(tomlPath), StandardCharsets.UTF_8),
    )
    val routine = RoutineCalendarConfig.read(table).copy(activeToggle = active)
    routine.writeInto(table)
    Files.write(tomlPath, TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8))
}

private fun createCalendar(
    repoRoot: Path,
    name: String,
    kind: CalendarKind,
    emoji: String?,
) {
    val id = Uuid7.generate().toString()
    val dir = repoRoot.resolve("calendars/$id")
    Files.createDirectories(dir)
    val table = TomlTable().apply {
        putString("id", id)
        putString("name", name)
        putString("kind", if (kind == CalendarKind.Timebox) "timebox" else "regular")
        emoji?.takeIf { it.isNotBlank() }?.let { putString("emoji", it) }
        putInt("priority", 500)
    }
    RoutineCalendarConfig(routine = false, activeToggle = true).writeInto(table)
    val tomlPath = dir.resolve("calendar.toml")
    Files.write(tomlPath, TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8))
}

private fun writeCalendarSheetDraft(
    repoRoot: Path,
    draft: com.eight87.strictlykeptboy.ui.calendars.CalendarSettingsDraft,
) {
    val meta = draft.calendar
    val tomlPath = repoRoot.resolve("calendars/${meta.ref.id}/calendar.toml")
    Files.createDirectories(tomlPath.parent)
    val table: TomlTable = if (Files.isRegularFile(tomlPath)) {
        TomlReader.parse(String(Files.readAllBytes(tomlPath), StandardCharsets.UTF_8))
    } else {
        TomlTable().apply {
            putString("id", meta.ref.id)
            putString("name", meta.displayName)
        }
    }
    val existingRoutine = RoutineCalendarConfig.read(table)
    existingRoutine.copy(activeToggle = draft.activeToggle).writeInto(table)
    table.putInt("priority", draft.priority)
    table.scalars.remove("supersedes")
    if (draft.supersedes.isNotEmpty()) {
        table.putStringArray("supersedes", draft.supersedes)
    }
    table.scalars.remove("color_seed")
    table.aotables.remove("active_windows")
    table.aotables.remove("active_hours")
    CalendarActivityConfig(
        colorSeed = meta.colorSeed,
        activeWindows = draft.activeWindows,
        activeHours = draft.activeHours,
    ).writeInto(table)
    val keepSup = SupersedenceConfig.read(table, hostCalendarId = meta.ref.id)
        .copy(supersedes = draft.supersedes)
    keepSup.writeInto(table)
    Files.write(tomlPath, TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8))
}

private fun parsePronounsPair(flat: String): IdentityPronouns? {
    val trimmed = flat.trim()
    if (trimmed.isBlank()) return null
    return when (trimmed.lowercase()) {
        "he/him", "he" -> IdentityPronouns.HeHim
        "she/her", "she" -> IdentityPronouns.SheHer
        "they/them", "they" -> IdentityPronouns.TheyThem
        else -> {
            val parts = trimmed.split('/').map { it.trim() }
            if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                IdentityPronouns(
                    subject = parts[0],
                    obj = parts[1],
                    possessive = parts.getOrNull(2) ?: (parts[1] + "s"),
                    reflexive = parts.getOrNull(3) ?: (parts[1] + "self"),
                )
            } else null
        }
    }
}
