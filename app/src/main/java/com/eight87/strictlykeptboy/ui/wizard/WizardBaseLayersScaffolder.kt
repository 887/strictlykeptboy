package com.eight87.strictlykeptboy.ui.wizard

import com.eight87.strictlykeptboy.git.Uuid7
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.EntityWriter
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.RecurrenceRule
import com.eight87.strictlykeptboy.store.StandingTask
import com.eight87.strictlykeptboy.store.Task
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Walkthrough-2 — enrich the wizard's default scaffold so that a
 * fresh-install → wizard-finish lands a schedule comparable in density
 * to the rich-demo, without forcing the user to author calendars by
 * hand.
 *
 * Today's wizard scaffolds N role-based calendars + per-atom recurrences
 * + 5 onboarding standing tasks. The demo additionally ships:
 *
 *  - A `kind = base` daily-scaffold calendar ("Sleep & work hours")
 *    with sleep / morning-routine / evening wind-down recurrences,
 *    `non_superseable = true` so vacation doesn't pause the day's anchor.
 *  - A `kind = base` public-holidays calendar (priority 950,
 *    `non_superseable = true`) with `supersedes` pre-populated against
 *    every role + base layer the wizard just scaffolded. Seeded with
 *    the next twelve months of UK bank holidays as a starter set; the
 *    user can extend / swap region by editing the calendar on disk.
 *  - A `kind = base` empty vacation overlay (priority 900,
 *    `supersedes` pre-populated) that the user drops events into.
 *  - A handful of sample tasks in the onboarding todolist so the
 *    Tasks pane has content from first paint.
 *  - One upcoming sample one-off event in the next 7 days so the
 *    schedule isn't recurrences-only.
 *
 * All produced files round-trip through the existing TOML codec +
 * indexer + resolver (no new persistence surface). Re-running the
 * scaffolder over an existing repo is non-destructive: every check
 * is idempotent on file existence.
 *
 * SOLID-S: this file owns ONE concern — base-layer enrichment for
 * fresh wizard scaffolds. The per-role calendar materialization lives
 * in [WizardScaffolder]; this is layered on top after the bootstrap
 * has run + per-role calendars exist.
 */
object WizardBaseLayersScaffolder {

    /** Outcome counters; consumed by tests + the wizard outcome record. */
    data class Outcome(
        val baseCalendarId: String,
        val holidaysCalendarId: String,
        val vacationCalendarId: String,
        val recurrencesWritten: Int,
        val sampleTasksWritten: Int,
        val sampleEventsWritten: Int,
    )

    /**
     * Sentinel calendar `name` values for idempotency detection. Disk
     * layout puts each scaffolded calendar under `calendars/<uuid>/`
     * (matching [EntityPath] expectations); re-runs scan all calendar
     * dirs for a `calendar.toml` whose `name = ...` matches the
     * sentinel below before deciding to create a new one.
     */
    const val BASE_CALENDAR_NAME = "Sleep & work hours"
    const val HOLIDAYS_CALENDAR_NAME = "Public holidays"
    const val VACATION_CALENDAR_NAME = "Vacation"

    suspend fun materialize(
        rootDir: File,
        identityId: String,
        tzId: String,
        roleCalendarIds: Collection<String>,
        onboardingTodolistId: String,
        now: () -> OffsetDateTime = { OffsetDateTime.now().withNano(0) },
    ): Outcome = withContext(Dispatchers.IO) {
        val root = rootDir.toPath()
        val ts = now().toString()

        // Each scaffolded calendar lives under `calendars/<uuid>/` per
        // the EntityPath convention. Idempotency keys off the calendar's
        // `name` field (sentinel constants above).
        val baseId = ensureCalendar(
            rootDir = rootDir,
            sentinelName = BASE_CALENDAR_NAME,
            tomlBuilder = { id ->
                TomlTable().apply {
                    putInt("schema_version", 1)
                    putString("id", id)
                    putString("kind", "base")
                    putString("name", BASE_CALENDAR_NAME)
                    putString("emoji", "🟦")
                    putInt("color_seed", 0x4A90C2)
                    putInt("priority", 100)
                    putString("tz_id", tzId)
                    putOffsetDateTime("created_at", ts)
                    putOffsetDateTime("updated_at", ts)
                    putString("author", identityId)
                    // non_superseable so vacation overlays do NOT pause
                    // the day-anchor (sleep + morning + evening blocks).
                    putBool("non_superseable", true)
                }
            },
        )
        val holidaysId = ensureCalendar(
            rootDir = rootDir,
            sentinelName = HOLIDAYS_CALENDAR_NAME,
            tomlBuilder = { id ->
                TomlTable().apply {
                    putInt("schema_version", 1)
                    putString("id", id)
                    putString("kind", "base")
                    putString("name", HOLIDAYS_CALENDAR_NAME)
                    putString("emoji", "🎌")
                    putInt("color_seed", 0xD4AF37.toInt())
                    putInt("priority", 950)
                    putString("tz_id", tzId)
                    putOffsetDateTime("created_at", ts)
                    putOffsetDateTime("updated_at", ts)
                    putString("author", identityId)
                    putBool("non_superseable", true)
                    putStringArray("supersedes", roleCalendarIds.toList())
                }
            },
        )
        val vacationId = ensureCalendar(
            rootDir = rootDir,
            sentinelName = VACATION_CALENDAR_NAME,
            tomlBuilder = { id ->
                TomlTable().apply {
                    putInt("schema_version", 1)
                    putString("id", id)
                    putString("kind", "base")
                    putString("name", VACATION_CALENDAR_NAME)
                    putString("emoji", "✈️")
                    putInt("color_seed", 0x6DB1BF)
                    putInt("priority", 900)
                    putString("tz_id", tzId)
                    putOffsetDateTime("created_at", ts)
                    putOffsetDateTime("updated_at", ts)
                    putString("author", identityId)
                    putStringArray("supersedes", roleCalendarIds.toList())
                }
            },
        )

        // Base-layer recurrences (sleep / morning routine / evening wind-down).
        var recurCount = 0
        recurCount += writeBaseRecurrenceIfMissing(
            rootDir, baseId, identityId, ts, tzId,
            slug = "sleep-block",
            title = "Sleep",
            dtstart = "2025-01-01T23:30:00",
            duration = "PT7H",
            emoji = "😴",
            body = "Sleep block — 23:30 → 06:30. Anchors the day.\n",
        )
        recurCount += writeBaseRecurrenceIfMissing(
            rootDir, baseId, identityId, ts, tzId,
            slug = "morning-routine",
            title = "Morning routine",
            dtstart = "2025-01-01T06:30:00",
            duration = "PT45M",
            emoji = "🌅",
            body = "Wake-up window — wash up, hydrate, settle into the day.\n",
        )
        recurCount += writeBaseRecurrenceIfMissing(
            rootDir, baseId, identityId, ts, tzId,
            slug = "evening-wind-down",
            title = "Evening wind-down",
            dtstart = "2025-01-01T22:00:00",
            duration = "PT1H30M",
            emoji = "🌙",
            body = "Wind-down window — close screens, lights low, prep for sleep.\n",
        )

        // A small starter set of bank holidays for the next 12 months.
        // UK calendar by default (Christmas / Boxing Day / New Year's Day —
        // a region-agnostic minimum; full national sets land on the
        // future country-code Phase). Each is an all-day yearly recurrence.
        for (holiday in upcomingStarterHolidays(now().toLocalDate())) {
            recurCount += writeAllDayHolidayIfMissing(
                rootDir = rootDir,
                calendarId = holidaysId,
                identityId = identityId,
                ts = ts,
                tzId = tzId,
                slug = holiday.slug,
                title = holiday.title,
                dtstart = holiday.dtstart,
                rrule = holiday.rrule,
                emoji = holiday.emoji,
            )
        }

        // Sample standing tasks — demonstrating Tasks pane density.
        var sampleTaskCount = 0
        val sampleStandingTasks: List<Pair<String, Int?>> = listOf(
            "Pick a sticker species you actually want" to 600,
            "Set your tone register in Settings → Identity" to 500,
            "Edit one of the role calendars to match your day" to 500,
            "Open the schedule on a typical weekday + adjust" to 450,
            "Optional — connect a git remote for backup" to 300,
        )
        for ((title, priority) in sampleStandingTasks) {
            if (writeSampleStandingTaskIfMissing(
                    rootDir = rootDir,
                    todolistId = onboardingTodolistId,
                    identityId = identityId,
                    ts = ts,
                    title = title,
                    priority = priority,
                )
            ) sampleTaskCount += 1
        }

        // One dated sample task in the next 3 days so the Today / Upcoming
        // filter renders something.
        val today = now().toLocalDate()
        val dueDate = today.plusDays(2).toString()
        if (writeSampleDatedTaskIfMissing(
                rootDir = rootDir,
                todolistId = onboardingTodolistId,
                identityId = identityId,
                ts = ts,
                title = "Quick win — review tomorrow's schedule",
                due = dueDate,
                priority = 700,
            )
        ) sampleTaskCount += 1

        // One sample one-off event in the next 7 days so the schedule
        // isn't recurrences-only at first paint. Lands on the first
        // role-calendar the bootstrap produced (always at least self-care).
        var sampleEventCount = 0
        roleCalendarIds.firstOrNull()?.let { firstRoleCalId ->
            val whenLocal = today.plusDays(3).atTime(11, 0)
            val whenIso = whenLocal.atZone(safeZone(tzId)).toOffsetDateTime().toString()
            val endIso = whenLocal.plusMinutes(45).atZone(safeZone(tzId)).toOffsetDateTime().toString()
            if (writeSampleEventIfMissing(
                    rootDir = rootDir,
                    calendarId = firstRoleCalId,
                    identityId = identityId,
                    ts = ts,
                    title = "Grocery run",
                    start = whenIso,
                    end = endIso,
                    emoji = "🛒",
                )
            ) sampleEventCount += 1
        }

        Outcome(
            baseCalendarId = baseId,
            holidaysCalendarId = holidaysId,
            vacationCalendarId = vacationId,
            recurrencesWritten = recurCount,
            sampleTasksWritten = sampleTaskCount,
            sampleEventsWritten = sampleEventCount,
        )
    }

    // --- helpers -----------------------------------------------------------

    private fun ensureCalendar(
        rootDir: File,
        sentinelName: String,
        tomlBuilder: (id: String) -> TomlTable,
    ): String {
        val calendarsRoot = rootDir.toPath().resolve("calendars")
        Files.createDirectories(calendarsRoot)
        // Idempotent — scan existing calendar.toml files for a matching
        // name = sentinel. Re-use that id rather than creating a new one.
        Files.list(calendarsRoot).use { stream ->
            for (sub in stream) {
                if (!Files.isDirectory(sub)) continue
                val toml = sub.resolve("calendar.toml")
                if (!Files.exists(toml)) continue
                val text = String(Files.readAllBytes(toml), StandardCharsets.UTF_8)
                if (text.contains("name = \"$sentinelName\"")) {
                    val existing = Regex("""^id\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
                        .find(text)?.groupValues?.getOrNull(1)
                    if (existing != null) return existing
                }
            }
        }
        val id = Uuid7.generate().toString()
        val dir = calendarsRoot.resolve(id)
        Files.createDirectories(dir.resolve("events"))
        Files.createDirectories(dir.resolve("recurrences"))
        Files.createDirectories(dir.resolve("exceptions"))
        Files.createDirectories(dir.resolve("deviations"))
        val table = tomlBuilder(id)
        Files.write(
            dir.resolve("calendar.toml"),
            TomlWriter.emit(table).toByteArray(StandardCharsets.UTF_8),
        )
        return id
    }

    private suspend fun writeBaseRecurrenceIfMissing(
        rootDir: File,
        calendarId: String,
        identityId: String,
        ts: String,
        tzId: String,
        slug: String,
        title: String,
        dtstart: String,
        duration: String,
        emoji: String,
        body: String,
    ): Int {
        val rid = Uuid7.generate().toString()
        // Idempotent check — match by title in the recurrences/ dir.
        val recurDir = rootDir.toPath().resolve("calendars/$calendarId/recurrences")
        Files.createDirectories(recurDir)
        val alreadyPresent = Files.list(recurDir).use { stream ->
            stream.anyMatch { p ->
                runCatching {
                    val txt = String(Files.readAllBytes(p), StandardCharsets.UTF_8)
                    txt.contains("title = \"$title\"")
                }.getOrDefault(false)
            }
        }
        if (alreadyPresent) return 0
        val rule = RecurrenceRule(
            header = EntityHeader(
                id = rid,
                createdAt = ts,
                updatedAt = ts,
                author = identityId,
            ),
            title = title,
            dtstart = dtstart,
            duration = duration,
            tzId = tzId,
            rrule = "FREQ=DAILY",
            calendarId = calendarId,
            tags = listOf("base", slug),
            emoji = emoji,
            body = body,
        )
        EntityWriter.write(rootDir, rule)
        return 1
    }

    private suspend fun writeAllDayHolidayIfMissing(
        rootDir: File,
        calendarId: String,
        identityId: String,
        ts: String,
        tzId: String,
        slug: String,
        title: String,
        dtstart: String,
        rrule: String,
        emoji: String,
    ): Int {
        val rid = Uuid7.generate().toString()
        val recurDir = rootDir.toPath().resolve("calendars/$calendarId/recurrences")
        Files.createDirectories(recurDir)
        val alreadyPresent = Files.list(recurDir).use { stream ->
            stream.anyMatch { p ->
                runCatching {
                    val txt = String(Files.readAllBytes(p), StandardCharsets.UTF_8)
                    txt.contains("title = \"$title\"")
                }.getOrDefault(false)
            }
        }
        if (alreadyPresent) return 0
        // All-day-ish recurrence — duration P1D, dtstart at midnight.
        // RecurrenceRule has no dedicated all_day flag (see Entities.kt
        // RecurrenceRule schema); resolver treats P1D-at-midnight as a
        // day band. RRULE FREQ=YEARLY produces one occurrence per year.
        val rule = RecurrenceRule(
            header = EntityHeader(
                id = rid,
                createdAt = ts,
                updatedAt = ts,
                author = identityId,
            ),
            title = title,
            dtstart = dtstart,
            duration = "P1D",
            tzId = tzId,
            rrule = rrule,
            calendarId = calendarId,
            tags = listOf("holiday"),
            emoji = emoji,
            body = "$title — auto-seeded by wizard. Edit / extend on disk.\n",
        )
        EntityWriter.write(rootDir, rule)
        return 1
    }

    private suspend fun writeSampleStandingTaskIfMissing(
        rootDir: File,
        todolistId: String,
        identityId: String,
        ts: String,
        title: String,
        priority: Int?,
    ): Boolean {
        val standingDir = rootDir.toPath().resolve("todolists/$todolistId/standing")
        Files.createDirectories(standingDir)
        val alreadyPresent = Files.list(standingDir).use { stream ->
            stream.anyMatch { p ->
                runCatching {
                    val txt = String(Files.readAllBytes(p), StandardCharsets.UTF_8)
                    txt.contains("title = \"$title\"")
                }.getOrDefault(false)
            }
        }
        if (alreadyPresent) return false
        val task = StandingTask(
            header = EntityHeader(
                id = Uuid7.generate().toString(),
                createdAt = ts,
                updatedAt = ts,
                author = identityId,
            ),
            title = title,
            todolistId = todolistId,
            priority = priority,
            tags = listOf("wizard-sample"),
        )
        EntityWriter.write(rootDir, task)
        return true
    }

    private suspend fun writeSampleDatedTaskIfMissing(
        rootDir: File,
        todolistId: String,
        identityId: String,
        ts: String,
        title: String,
        due: String,
        priority: Int?,
    ): Boolean {
        val tasksDir = rootDir.toPath().resolve("todolists/$todolistId/tasks")
        Files.createDirectories(tasksDir)
        // Walk all bucket dirs to check; cheap for fresh repos.
        val alreadyPresent = runCatching {
            Files.walk(tasksDir).use { stream ->
                stream.anyMatch { p ->
                    if (!p.toString().endsWith(".md")) return@anyMatch false
                    runCatching {
                        val txt = String(Files.readAllBytes(p), StandardCharsets.UTF_8)
                        txt.contains("title = \"$title\"")
                    }.getOrDefault(false)
                }
            }
        }.getOrDefault(false)
        if (alreadyPresent) return false
        val task = Task(
            header = EntityHeader(
                id = Uuid7.generate().toString(),
                createdAt = ts,
                updatedAt = ts,
                author = identityId,
            ),
            title = title,
            todolistId = todolistId,
            due = due,
            priority = priority,
            tags = listOf("wizard-sample"),
        )
        EntityWriter.write(rootDir, task)
        return true
    }

    private suspend fun writeSampleEventIfMissing(
        rootDir: File,
        calendarId: String,
        identityId: String,
        ts: String,
        title: String,
        start: String,
        end: String,
        emoji: String,
    ): Boolean {
        val eventsDir = rootDir.toPath().resolve("calendars/$calendarId/events")
        Files.createDirectories(eventsDir)
        val alreadyPresent = runCatching {
            Files.walk(eventsDir).use { stream ->
                stream.anyMatch { p ->
                    if (!p.toString().endsWith(".md")) return@anyMatch false
                    runCatching {
                        val txt = String(Files.readAllBytes(p), StandardCharsets.UTF_8)
                        txt.contains("title = \"$title\"")
                    }.getOrDefault(false)
                }
            }
        }.getOrDefault(false)
        if (alreadyPresent) return false
        val event = Event(
            header = EntityHeader(
                id = Uuid7.generate().toString(),
                createdAt = ts,
                updatedAt = ts,
                author = identityId,
            ),
            title = title,
            start = start,
            end = end,
            calendarId = calendarId,
            tags = listOf("wizard-sample"),
            emoji = emoji,
            body = "Sample event seeded by the wizard. Tap to edit or delete.\n",
        )
        EntityWriter.write(rootDir, event)
        return true
    }

    private fun safeZone(tzId: String): ZoneId =
        runCatching { ZoneId.of(tzId) }.getOrDefault(ZoneId.systemDefault())

    /** Holiday seed entry. dtstart in the calendar's tz (LocalDateTime form). */
    private data class HolidaySeed(
        val slug: String,
        val title: String,
        val dtstart: String,
        val rrule: String,
        val emoji: String,
    )

    private fun upcomingStarterHolidays(today: LocalDate): List<HolidaySeed> {
        // A tiny region-agnostic starter set. The user can extend or
        // replace via disk edits; a future country_code field will let
        // the wizard pick a national set.
        val year = today.year
        val df = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        fun atMidnight(d: LocalDate): String = d.atTime(0, 0).format(df)
        return listOf(
            HolidaySeed(
                slug = "new-years-day",
                title = "New Year's Day",
                dtstart = atMidnight(LocalDate.of(year, 1, 1)),
                rrule = "FREQ=YEARLY;BYMONTH=1;BYMONTHDAY=1",
                emoji = "🎉",
            ),
            HolidaySeed(
                slug = "christmas-day",
                title = "Christmas Day",
                dtstart = atMidnight(LocalDate.of(year, 12, 25)),
                rrule = "FREQ=YEARLY;BYMONTH=12;BYMONTHDAY=25",
                emoji = "🎄",
            ),
            HolidaySeed(
                slug = "boxing-day",
                title = "Boxing Day",
                dtstart = atMidnight(LocalDate.of(year, 12, 26)),
                rrule = "FREQ=YEARLY;BYMONTH=12;BYMONTHDAY=26",
                emoji = "📦",
            ),
            HolidaySeed(
                slug = "spring-bank-holiday",
                title = "Spring bank holiday",
                dtstart = atMidnight(
                    LocalDate.of(year, 5, 1)
                        .with(TemporalAdjusters.lastInMonth(java.time.DayOfWeek.MONDAY)),
                ),
                rrule = "FREQ=YEARLY;BYMONTH=5;BYDAY=-1MO",
                emoji = "🇬🇧",
            ),
        )
    }
}
