package com.eight87.strictlykeptboy.ui.calendars

import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.HourRange
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.store.CalendarActivityConfig
import com.eight87.strictlykeptboy.store.RoutineCalendarConfig
import com.eight87.strictlykeptboy.store.SupersedenceConfig
import com.eight87.strictlykeptboy.store.TomlReader
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlWriter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2.1.B.4 — calendar.toml round-trip after a `CalendarSettingsSheet`
 * Save. Asserts the codec write-out preserves the new fields and that
 * unrelated keys (e.g. baseline_cadence, routine flags) survive.
 */
class CalendarSettingsRoundTripTest {

    @Test fun save_then_read_back_yields_same_fields() {
        // Seed a pre-existing calendar.toml with a routine + baseline-cadence
        // block. The sheet only edits a subset; the rest must round-trip.
        val seedToml = """
            schema_version = 1
            id = "cal-work"
            name = "Work"
            priority = 500
            active_toggle = true
            tz_id = "Europe/Berlin"
            routine = false

            [baseline_cadence]
            weekdays = ["MON", "TUE"]
            window = ["09:00", "17:00"]
            timezone = "Europe/Berlin"
        """.trimIndent()
        val table = TomlReader.parse(seedToml)

        // Simulate the sheet's save path manually (no Android context).
        val meta = CalendarMeta(
            ref = CalendarRef("cal-work"),
            repo = RepoRef("repo-a"),
            displayName = "Work",
            priority = 500,
            activeWindows = listOf(DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))),
            activeHours = listOf(HourRange(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0))),
            colorSeed = 0xAA88FF,
        )
        val draft = CalendarSettingsDraft(
            calendar = meta,
            activeToggle = false,
            priority = 700,
            activeWindows = listOf(
                CalendarActivityConfig.DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)),
            ),
            activeHours = listOf(
                CalendarActivityConfig.HourRange(DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), LocalTime.of(18, 0)),
            ),
            supersedes = listOf("cal-routine"),
        )

        // Apply the same logic as CalendarSettingsWriter (sans Files /
        // GitRepoRegistry).
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
        SupersedenceConfig.read(table, hostCalendarId = meta.ref.id)
            .copy(supersedes = draft.supersedes)
            .writeInto(table)

        val written = TomlWriter.emit(table)
        val reread = TomlReader.parse(written)

        // Active fields round-trip.
        val activity = CalendarActivityConfig.read(reread)
        assertEquals(0xAA88FF, activity.colorSeed)
        assertEquals(1, activity.activeWindows.size)
        assertEquals(LocalDate.of(2026, 7, 1), activity.activeWindows[0].from)
        assertEquals(LocalDate.of(2026, 7, 31), activity.activeWindows[0].to)
        assertEquals(1, activity.activeHours.size)
        assertEquals(DayOfWeek.WEDNESDAY, activity.activeHours[0].day)
        assertEquals(LocalTime.of(10, 0), activity.activeHours[0].from)

        // Scalar fields.
        assertEquals(700, reread.getInt("priority"))
        assertEquals(false, reread.getBool("active_toggle"))
        assertEquals(listOf("cal-routine"), reread.getStringArray("supersedes"))

        // Untouched block survives.
        val supersedence = SupersedenceConfig.read(reread, hostCalendarId = "cal-work")
        val baseline = supersedence.baselineCadence
        assertTrue(baseline != null)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), baseline!!.weekdays)
    }

    // Round 2.21.B — identity (emoji + colorSeed) round-trip via the
    // draft → writer path.

    @Test fun identity_emoji_and_color_round_trip() {
        val table = TomlTable().apply {
            putString("id", "cal-routine")
            putString("name", "Routine")
            putInt("priority", 500)
            putBool("active_toggle", true)
            putString("emoji", "🛏️")
            putInt("color_seed", 0x111111)
        }
        val meta = CalendarMeta(
            ref = CalendarRef("cal-routine"),
            repo = RepoRef("repo-a"),
            displayName = "Routine",
            priority = 500,
            emoji = "🛏️",
            colorSeed = 0x111111,
        )
        val draft = CalendarSettingsDraft(
            calendar = meta,
            activeToggle = true,
            priority = 500,
            activeWindows = emptyList(),
            activeHours = emptyList(),
            supersedes = emptyList(),
            emoji = "🌅",
            colorSeed = 0xEC407A,
        )

        // Mirror CalendarSettingsWriter.write — identity branch.
        table.scalars.remove("emoji")
        draft.emoji?.takeIf { it.isNotBlank() }?.let { table.putString("emoji", it) }
        table.scalars.remove("color_seed")
        table.aotables.remove("active_windows")
        table.aotables.remove("active_hours")
        CalendarActivityConfig(
            colorSeed = draft.colorSeed ?: meta.colorSeed,
            activeWindows = draft.activeWindows,
            activeHours = draft.activeHours,
            metaGroupField = meta.metaGroupField,
        ).writeInto(table)

        val reread = TomlReader.parse(TomlWriter.emit(table))
        assertEquals("🌅", reread.getString("emoji"))
        assertEquals(0xEC407A, CalendarActivityConfig.read(reread).colorSeed)
    }

    @Test fun identity_blank_emoji_clears_disk() {
        val table = TomlTable().apply {
            putString("id", "cal-routine")
            putString("name", "Routine")
            putString("emoji", "🛏️")
        }
        val meta = CalendarMeta(
            ref = CalendarRef("cal-routine"),
            repo = RepoRef("repo-a"),
            displayName = "Routine",
            priority = 500,
            emoji = "🛏️",
        )
        val draft = CalendarSettingsDraft(
            calendar = meta,
            activeToggle = true,
            priority = 500,
            activeWindows = emptyList(),
            activeHours = emptyList(),
            supersedes = emptyList(),
            emoji = null,
            colorSeed = null,
        )

        table.scalars.remove("emoji")
        draft.emoji?.takeIf { it.isNotBlank() }?.let { table.putString("emoji", it) }

        val reread = TomlReader.parse(TomlWriter.emit(table))
        assertEquals(null, reread.getString("emoji"))
    }

    @Test fun identity_null_color_preserves_meta_color() {
        // When the user only edits emoji and never touches a swatch,
        // draft.colorSeed is null; writer must fall back to meta.colorSeed
        // so the existing value survives on disk.
        val table = TomlTable().apply { putString("id", "cal-x") }
        val meta = CalendarMeta(
            ref = CalendarRef("cal-x"),
            repo = RepoRef("repo-a"),
            displayName = "X",
            priority = 500,
            colorSeed = 0x4FB7D3,
        )
        val draft = CalendarSettingsDraft(
            calendar = meta,
            activeToggle = true,
            priority = 500,
            activeWindows = emptyList(),
            activeHours = emptyList(),
            supersedes = emptyList(),
            emoji = "✨",
            colorSeed = null,
        )

        table.scalars.remove("color_seed")
        CalendarActivityConfig(
            colorSeed = draft.colorSeed ?: meta.colorSeed,
        ).writeInto(table)

        val reread = TomlReader.parse(TomlWriter.emit(table))
        assertEquals(0x4FB7D3, CalendarActivityConfig.read(reread).colorSeed)
    }
}
