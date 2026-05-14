package com.eight87.strictlykeptboy.store

import com.eight87.strictlykeptboy.git.Uuid7
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime

/**
 * Phase 2.1.F.7 — system `cal-briefings` calendar seed.
 *
 * Two recurring template events live on this calendar (`morning-briefing`
 * at 07:00 and `evening-briefing` at 21:00). They are written with
 * `auto_generated = true` on the calendar meta so future re-runs can
 * detect and refresh without trampling user edits.
 *
 * Idempotent: if `calendars/cal-briefings/calendar.toml` already exists
 * on disk, [seed] returns the existing calendar id without overwriting.
 *
 * SOLID-S: writes only; firing path lives in [BriefingWorker]. SOLID-D:
 * pure file I/O — no Android imports.
 */
object CalBriefingsSeed {

    /** Slug used both as directory + canonical calendar id. */
    const val CAL_ID: String = "cal-briefings"

    data class Outcome(
        val calendarId: String,
        val morningRuleId: String,
        val eveningRuleId: String,
        val wasNewlyCreated: Boolean,
    )

    /**
     * Seed `cal-briefings/` under [rootDir]. Returns the canonical
     * calendar id and rule ids. Existing seeds are detected by file
     * presence and not modified.
     *
     * @param author commit author for the event headers.
     * @param tzId default timezone for the calendar + dtstart.
     * @param now epoch source (test seam).
     */
    fun seed(
        rootDir: File,
        author: String = "me",
        tzId: String = java.time.ZoneId.systemDefault().id,
        now: () -> OffsetDateTime = { OffsetDateTime.now().withNano(0) },
    ): Outcome {
        val root: Path = rootDir.toPath()
        val calDir = root.resolve("calendars").resolve(CAL_ID)
        val calToml = calDir.resolve("calendar.toml")
        if (Files.isRegularFile(calToml)) {
            // Idempotent: return a synthetic outcome — rule ids aren't
            // re-read here (callers don't need them; the worker walks
            // events/* at fire time).
            return Outcome(
                calendarId = CAL_ID,
                morningRuleId = "",
                eveningRuleId = "",
                wasNewlyCreated = false,
            )
        }

        Files.createDirectories(calDir)
        val table = TomlTable().apply {
            putInt("schema_version", 1)
            putString("id", CAL_ID)
            putString("kind", "calendar")
            putString("name", "Briefings")
            putString("role", "briefings")
            putInt("priority", 990)
            putString("emoji", "📰")
            putString("tz_id", tzId)
            putBool("auto_generated", true)
        }
        Files.write(calToml, TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8))

        val ts = now().toString()
        val morningId = Uuid7.generate().toString()
        val eveningId = Uuid7.generate().toString()
        val morning = RecurrenceRule(
            header = EntityHeader(
                id = morningId, createdAt = ts, updatedAt = ts, author = author,
            ),
            title = "Morning briefing",
            dtstart = "2025-01-01T07:00:00",
            duration = "PT5M",
            tzId = tzId,
            rrule = "FREQ=DAILY",
            calendarId = CAL_ID,
            tags = listOf("briefing", "system"),
            emoji = "🌅",
        )
        val evening = RecurrenceRule(
            header = EntityHeader(
                id = eveningId, createdAt = ts, updatedAt = ts, author = author,
            ),
            title = "Evening briefing",
            dtstart = "2025-01-01T21:00:00",
            duration = "PT5M",
            tzId = tzId,
            rrule = "FREQ=DAILY",
            calendarId = CAL_ID,
            tags = listOf("briefing", "system"),
            emoji = "🌇",
        )
        kotlinx.coroutines.runBlocking {
            EntityWriter.write(rootDir, morning)
            EntityWriter.write(rootDir, evening)
        }
        return Outcome(
            calendarId = CAL_ID,
            morningRuleId = morningId,
            eveningRuleId = eveningId,
            wasNewlyCreated = true,
        )
    }
}
