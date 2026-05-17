package com.eight87.strictlykeptboy.store

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Round 2.1.B (schema extension) — sibling codec for the activity-windowing
 * + per-calendar color fields that live in `calendar.toml` alongside
 * the existing [RoutineCalendarConfig] + [SupersedenceConfig] blocks.
 *
 * Defended as a *sibling* (not an extension of [RoutineCalendarConfig])
 * because the responsibilities are different in nature (SOLID-S):
 *  - [RoutineCalendarConfig] toggles routine-library semantics
 *    (`routine_can_materialize`, `routine_default_start`, etc.).
 *  - [SupersedenceConfig] handles cross-calendar suppression + the
 *    off-schedule baseline-cadence envelope.
 *  - This codec handles *visibility windowing* — when (date range,
 *    weekly hours) the calendar is "live" — and the seed used to drive
 *    UI tinting. It also writes a separate cluster of TOML keys.
 *
 * Schema on disk (extensions to `calendar.toml`):
 *
 * ```toml
 * color_seed = 0x4FB7D3
 *
 * [[active_windows]]
 * from = 2026-01-01
 * to   = 2026-12-31
 *
 * [[active_hours]]
 * day  = "MON"
 * from = "09:00"
 * to   = "17:00"
 * ```
 *
 * **Back-compat:** existing `calendar.toml` files without any of these
 * keys parse cleanly into the empty default (no windows, no hours,
 * `colorSeed = null`). Round-trip is byte-stable for files written by
 * this codec; legacy files round-trip with the new keys absent if the
 * caller never sets them.
 *
 * Pure data + pure parser (SOLID-S / SOLID-D): no Android imports;
 * round-trips through the existing [TomlReader] / [TomlWriter] codec.
 */
data class CalendarActivityConfig(
    /** Per-calendar color seed for UI tinting. `null` ⇒ caller falls back. */
    val colorSeed: Int? = null,
    /** Date-range windows during which this calendar is active. */
    val activeWindows: List<DateRange> = emptyList(),
    /** Per-day-of-week hour ranges during which this calendar is active. */
    val activeHours: List<HourRange> = emptyList(),
    /**
     * Round 2.21.A.2 — opt-in atomic-event grouping. Names which TOML key
     * on this calendar's events carries the group label (e.g. `"phase"`,
     * `"category"`). v1: the engine reads events' typed `group` field
     * regardless of this string's value; presence of any non-empty value
     * here enables grouping. The literal string round-trips on disk so
     * later versions can honour arbitrary key names.
     */
    val metaGroupField: String? = null,
) {

    /** Inclusive-on-both-ends date interval. Negative ranges are dropped at parse-time. */
    data class DateRange(val from: LocalDate, val to: LocalDate) {
        init { require(!to.isBefore(from)) { "DateRange inverted ($from..$to)" } }
    }

    /** Per-day-of-week hour range. `to <= from` parsed as-is; UI handles cross-midnight. */
    data class HourRange(val day: DayOfWeek, val from: LocalTime, val to: LocalTime)

    companion object {
        fun read(table: TomlTable): CalendarActivityConfig {
            // Round 2.25 follow-up — accept either `color_seed = <int>` or the
            // canonical CalDAV-style `color = "#RRGGBB"` hex string. When both
            // are present, the int wins (it's the more specific key). Malformed
            // hex resolves to null without throwing — keeps legacy files safe.
            val colorSeed = table.getInt("color_seed")
                ?: table.getString("color")?.let { parseHexColorOrNull(it) }
            val activeWindows = readDateRangeArray(table, "active_windows")
            val activeHours = readHourRangeArray(table, "active_hours")
            val metaGroupField = table.getString("meta_group_field")?.takeIf { it.isNotBlank() }
            return CalendarActivityConfig(
                colorSeed = colorSeed,
                activeWindows = activeWindows,
                activeHours = activeHours,
                metaGroupField = metaGroupField,
            )
        }

        fun readFrom(calendarTomlPath: Path): CalendarActivityConfig {
            val bytes = Files.readAllBytes(calendarTomlPath)
            val text = String(bytes, StandardCharsets.UTF_8)
            return read(TomlReader.parse(text))
        }

        private fun readDateRangeArray(table: TomlTable, key: String): List<DateRange> {
            val rows = table.aotables[key] ?: return emptyList()
            val out = mutableListOf<DateRange>()
            for (row in rows) {
                val from = row.getDateLike("from")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                    ?: continue
                val to = row.getDateLike("to")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                    ?: continue
                if (to.isBefore(from)) continue
                out += DateRange(from, to)
            }
            return out
        }

        private fun readHourRangeArray(table: TomlTable, key: String): List<HourRange> {
            val rows = table.aotables[key] ?: return emptyList()
            val out = mutableListOf<HourRange>()
            for (row in rows) {
                val day = row.getString("day")?.let { parseDayOfWeek(it) } ?: continue
                val from = row.getString("from")?.let { parseLocalTimeOrNull(it) } ?: continue
                val to = row.getString("to")?.let { parseLocalTimeOrNull(it) } ?: continue
                out += HourRange(day, from, to)
            }
            return out
        }

        private fun parseDayOfWeek(raw: String): DayOfWeek? {
            val up = raw.trim().uppercase()
            return when (up) {
                "MON", "MONDAY" -> DayOfWeek.MONDAY
                "TUE", "TUES", "TUESDAY" -> DayOfWeek.TUESDAY
                "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY
                "THU", "THUR", "THURS", "THURSDAY" -> DayOfWeek.THURSDAY
                "FRI", "FRIDAY" -> DayOfWeek.FRIDAY
                "SAT", "SATURDAY" -> DayOfWeek.SATURDAY
                "SUN", "SUNDAY" -> DayOfWeek.SUNDAY
                else -> null
            }
        }

        private fun parseLocalTimeOrNull(raw: String): LocalTime? =
            runCatching { LocalTime.parse(raw.trim()) }.getOrNull()

        /**
         * Parse `#RRGGBB` / `RRGGBB` / `#AARRGGBB` / `AARRGGBB` into a signed
         * Int suitable for `Color(int)`. Whitespace tolerated. Returns null
         * for anything malformed — callers fall back to the hash-based seed.
         */
        private fun parseHexColorOrNull(raw: String): Int? {
            val s = raw.trim().removePrefix("#")
            if (s.length != 6 && s.length != 8) return null
            val parsed = runCatching { java.lang.Long.parseLong(s, 16) }.getOrNull() ?: return null
            // 6-hex form is opaque RGB; promote to 0xFFRRGGBB so the Int round-
            // trips through Color() without losing alpha.
            val rgba = if (s.length == 6) (0xFF000000L or parsed) else parsed
            return rgba.toInt()
        }
    }

    /** Write additive fields into [table]. Existing non-activity fields are untouched. */
    fun writeInto(table: TomlTable) {
        colorSeed?.let { table.putInt("color_seed", it) }
        metaGroupField?.takeIf { it.isNotBlank() }?.let { table.putString("meta_group_field", it) }
        if (activeWindows.isNotEmpty()) {
            val rows = activeWindows.map { r ->
                TomlTable().apply {
                    putLocalDate("from", r.from.toString())
                    putLocalDate("to", r.to.toString())
                }
            }.toMutableList()
            table.aotables["active_windows"] = rows
        }
        if (activeHours.isNotEmpty()) {
            val rows = activeHours.map { r ->
                TomlTable().apply {
                    putString("day", r.day.name.take(3))
                    putString("from", r.from.toString())
                    putString("to", r.to.toString())
                }
            }.toMutableList()
            table.aotables["active_hours"] = rows
        }
    }
}
