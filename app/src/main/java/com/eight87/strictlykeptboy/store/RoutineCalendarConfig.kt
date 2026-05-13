package com.eight87.strictlykeptboy.store

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase XX.7 / AT-G — additive routine fields on `calendar.toml`.
 *
 * A *routine* is not a new entity type: it is an ordinary calendar
 * whose `calendar.toml` carries the fields below. The resolver treats
 * routine calendars exactly like ordinary calendars; what makes them
 * special is the wizard scaffolds them `active_toggle = false` so the
 * routine acts as a *library* of atomic events that only materialize
 * via Phase XX.8 quick-start, instead of double-rendering alongside
 * materialized copies.
 *
 * Pure data + pure parser (SOLID-S / SOLID-D): no Android imports,
 * round-trips through the existing [TomlReader] / [TomlWriter] codec.
 * Callers responsible for picking up the file (typically the wizard
 * scaffolder, the calendar drawer, and Phase XX.8's routine picker).
 */
data class RoutineCalendarConfig(
    /** True iff this calendar is a routine library. */
    val routine: Boolean,
    /** Stable id for the routine kind (`morning` / `bed` / `workout` / custom). */
    val routineId: String? = null,
    /** ISO-8601 wall time (`HH:mm`) used as the default start in the quick-start sheet. */
    val routineDefaultStart: String? = null,
    /** Per AT-G.1: when false, the routine is library-only and the FAB hides its quick-start row. */
    val routineCanMaterialize: Boolean = true,
    /** Per AT-G.3 default-off so the routine doesn't double-render with materialized copies. */
    val activeToggle: Boolean = true,
) {
    companion object {
        /** Parse from a `calendar.toml` table. Returns a non-routine default if no routine flag set. */
        fun read(table: TomlTable): RoutineCalendarConfig {
            val routine = table.getBool("routine") ?: false
            return RoutineCalendarConfig(
                routine = routine,
                routineId = table.getString("routine_id"),
                routineDefaultStart = table.getString("routine_default_start"),
                routineCanMaterialize = table.getBool("routine_can_materialize") ?: routine,
                activeToggle = table.getBool("active_toggle") ?: (!routine),
            )
        }

        /** Convenience to read directly off a `calendar.toml` path. */
        fun readFrom(calendarTomlPath: Path): RoutineCalendarConfig {
            val bytes = Files.readAllBytes(calendarTomlPath)
            val text = String(bytes, StandardCharsets.UTF_8)
            return read(TomlReader.parse(text))
        }
    }

    /** Write additive fields into [table]. Existing non-routine fields are untouched. */
    fun writeInto(table: TomlTable) {
        table.putBool("routine", routine)
        routineId?.let { table.putString("routine_id", it) }
        routineDefaultStart?.let { table.putString("routine_default_start", it) }
        table.putBool("routine_can_materialize", routineCanMaterialize)
        table.putBool("active_toggle", activeToggle)
    }
}

/**
 * Locked routine kinds the wizard scaffolds (AT-G.2). Custom routine
 * calendars are still legal — these are just the wizard-seeded ones.
 */
object KnownRoutineIds {
    const val MORNING = "morning"
    const val BED = "bed"
    const val WORKOUT = "workout"

    val ALL = listOf(MORNING, BED, WORKOUT)
}
