package com.eight87.strictlykeptboy.store

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase DDD.1 / DM-Y.1 — `mode.toml` codec.
 *
 * Per D.84 / D.86: per-repo default + per-calendar overrides + optional
 * write-back-target / dom-persona / dom-cadence / kept-since. IS committed
 * (transitions are history).
 *
 * SOLID:
 *  - **S:** Only mode.toml round-trip. Mode-transition history is implied
 *    by Git log; this file always reflects the *current* state.
 *  - **D:** Pure mapping; no I/O dependencies other than Path/Files.
 */
enum class RepoMode(val wire: String) {
    Free("free"),
    StrictlyKept("strictly-kept"),
    SelfKeep("self-keep");

    companion object {
        fun fromWire(s: String?): RepoMode = when (s?.trim()) {
            "free" -> Free
            "strictly-kept" -> StrictlyKept
            "self-keep" -> SelfKeep
            null -> Free
            else -> error("Unknown mode wire value: $s (DM-Y.1)")
        }
    }
}

enum class DomCadenceWire(val wire: String) {
    Realtime("realtime"),
    EndOfDay("end-of-day"),
    Weekly("weekly");

    companion object {
        fun fromWire(s: String?): DomCadenceWire = when (s?.trim()) {
            "realtime" -> Realtime
            "end-of-day", null -> EndOfDay
            "weekly" -> Weekly
            else -> error("Unknown dom_cadence: $s (DM-Y.1)")
        }
    }
}

data class ModeTomlData(
    val schemaVersion: Int = 1,
    val mode: RepoMode = RepoMode.Free,
    val writeBackTarget: String? = null,
    val domPersona: String? = null,
    val domCadence: DomCadenceWire? = null,
    val keptSince: String? = null,
    /** Per-calendar overrides: calendar-id -> override-mode. DM-Y.1 per-calendar block. */
    val calendarOverrides: Map<String, RepoMode> = emptyMap(),
) {
    /** Resolve the effective mode for a given calendar id. */
    fun resolveFor(calendarId: String?): RepoMode {
        if (calendarId == null) return mode
        return calendarOverrides[calendarId] ?: mode
    }

    companion object {
        const val FILE_NAME: String = "mode.toml"

        /** D.84 / DM-Y.1 default. */
        val Default: ModeTomlData = ModeTomlData()
    }
}

object ModeTomlCodec {
    fun toToml(data: ModeTomlData): String {
        val t = TomlTable().apply {
            putInt("schema_version", data.schemaVersion)
            putString("mode", data.mode.wire)
            putString("write_back_target", data.writeBackTarget)
            putString("dom_persona", data.domPersona)
            putString("dom_cadence", data.domCadence?.wire)
            putOffsetDateTime("kept_since", data.keptSince)
            if (data.calendarOverrides.isNotEmpty()) {
                val parent = TomlTable()
                data.calendarOverrides.forEach { (calId, calMode) ->
                    parent.sections[calId] = TomlTable().apply {
                        putString("mode", calMode.wire)
                    }
                }
                sections["calendars"] = parent
            }
        }
        return TomlWriter.emit(t)
    }

    fun fromToml(input: String): ModeTomlData {
        val table = TomlReader.parse(input)
        val schema = table.getInt("schema_version") ?: 1
        val mode = RepoMode.fromWire(table.getString("mode"))
        val wbTarget = table.getString("write_back_target")
        val persona = table.getString("dom_persona")
        val cadence = table.getString("dom_cadence")?.let { DomCadenceWire.fromWire(it) }
        val keptSince = table.getDateLike("kept_since")
        val overrides = table.sections["calendars"]?.sections?.mapValues { (_, sub) ->
            RepoMode.fromWire(sub.getString("mode"))
        } ?: emptyMap()
        return ModeTomlData(
            schemaVersion = schema,
            mode = mode,
            writeBackTarget = wbTarget,
            domPersona = persona,
            domCadence = cadence,
            keptSince = keptSince,
            calendarOverrides = overrides,
        )
    }

    fun write(repoRoot: Path, data: ModeTomlData): Path {
        val target = repoRoot.resolve(ModeTomlData.FILE_NAME)
        Files.write(target, toToml(data).toByteArray(StandardCharsets.UTF_8))
        return target
    }

    fun readOrDefault(repoRoot: Path): ModeTomlData {
        val target = repoRoot.resolve(ModeTomlData.FILE_NAME)
        if (!Files.isRegularFile(target)) return ModeTomlData.Default
        return fromToml(String(Files.readAllBytes(target), StandardCharsets.UTF_8))
    }
}
