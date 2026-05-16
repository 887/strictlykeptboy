package com.eight87.strictlykeptboy.demo

import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.DeviationInput
import com.eight87.strictlykeptboy.resolver.EventInput
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.ExceptionInput
import com.eight87.strictlykeptboy.resolver.OverrideInput
import com.eight87.strictlykeptboy.resolver.RecurrenceInput
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.RuleRef
import com.eight87.strictlykeptboy.store.Deviation
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Exception as StoreException
import com.eight87.strictlykeptboy.store.FrontmatterReader
import com.eight87.strictlykeptboy.store.ParseResult
import com.eight87.strictlykeptboy.store.RecurrenceRule
import com.eight87.strictlykeptboy.store.RepoScanner
import com.eight87.strictlykeptboy.store.TomlReader
import com.eight87.strictlykeptboy.store.TomlTable
import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Round 2.20 Phase D — load the seeded rich-demo repo into the resolver's
 * value-bag types.
 *
 * The production indexer takes the disk tree → Room rows → `EventInput` /
 * `RecurrenceInput` via [com.eight87.strictlykeptboy.composition.SourcesPublisher].
 * For tests we skip Room and go disk → resolver directly: [RepoScanner.scanAll]
 * produces typed entities, this loader converts them into the resolver's
 * `Sources` shape, and reads `calendar.toml` siblings to build the
 * snapshot's `CalendarMeta` list (UUID-keyed to match the events'
 * `calendar_id` field, with `supersedes` re-resolved from folder-name slugs
 * into the corresponding UUIDs so the resolver pipeline sees coherent refs).
 *
 * Outside the scope of Phase D's tests: parsing every TOML field that
 * production code reads (active_hours, baselineCadenceDays, etc.). We
 * surface only what the Phase D probes need.
 */
data class RichDemoResolverFixture(
    val snapshot: RepoSnapshot,
    val sources: Renderer.Sources,
    val folderToUuid: Map<String, String>,
) {
    fun calendarUuid(folderName: String): String =
        folderToUuid[folderName] ?: error("no calendar folder '$folderName' in rich-demo")

    companion object {
        private const val REPO_ID = "rich-demo"
        private val TZ_LONDON: ZoneId = ZoneId.of("Europe/London")

        suspend fun load(repoRoot: File): RichDemoResolverFixture {
            val results = RepoScanner.scanAll(repoRoot)

            // Pass 1 — calendar.toml siblings give us folder → UUID + supersedes.
            val folderToUuid = mutableMapOf<String, String>()
            val supersedesByFolder = mutableMapOf<String, List<String>>()
            val priorityByFolder = mutableMapOf<String, Int>()
            val nonSuperseable = mutableSetOf<String>()
            val calendarsDir = File(repoRoot, "calendars")
            if (calendarsDir.isDirectory) {
                calendarsDir.listFiles().orEmpty()
                    .filter { it.isDirectory }
                    .forEach { dir ->
                        val tomlFile = File(dir, "calendar.toml")
                        if (!tomlFile.isFile) return@forEach
                        val table = runCatching {
                            TomlReader.parse(tomlFile.readText(Charsets.UTF_8))
                        }.getOrNull() ?: return@forEach
                        val id = table.getString("id") ?: return@forEach
                        folderToUuid[dir.name] = id
                        supersedesByFolder[dir.name] = table.getStringArray("supersedes") ?: emptyList()
                        priorityByFolder[dir.name] = table.getInt("priority") ?: 500
                        if (table.getBool("non_superseable") == true) {
                            nonSuperseable += dir.name
                        }
                    }
            }

            // Build a synthetic active-windows list per calendar from the
            // dates its one-off events cover. The rich-demo authors a
            // single `vacation` calendar with two trip events (convention
            // 2026-05-27→05-30 and local weekend 2026-05-23→05-24); the
            // resolver's supersedence path only fires when the supersedor's
            // CalendarMeta is "active" at the sample instant. Without these
            // windows the vacation calendar would be active every day and
            // erroneously supersede work/commute/etc. all the time.
            val activeWindowsByUuid = mutableMapOf<String, MutableList<DateRange>>()
            File(repoRoot, "calendars").listFiles().orEmpty()
                .filter { it.isDirectory }
                .forEach { dir ->
                    val uuid = folderToUuid[dir.name] ?: return@forEach
                    val eventsDir = File(dir, "events")
                    if (!eventsDir.isDirectory) return@forEach
                    eventsDir.walkTopDown()
                        .filter { it.isFile && it.name.endsWith(".md") }
                        .forEach { f ->
                            val txt = runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
                                ?: return@forEach
                            val doc = FrontmatterReader.parse(txt)
                            val t = doc.frontmatter
                            val startStr = t.getDateLike("start") ?: return@forEach
                            val endStr = t.getDateLike("end") ?: startStr
                            val startDate = runCatching {
                                parseZdtWithTz(startStr, TZ_LONDON).toLocalDate()
                            }.getOrNull() ?: return@forEach
                            val endDate = runCatching {
                                parseZdtWithTz(endStr, TZ_LONDON).toLocalDate()
                            }.getOrDefault(startDate)
                            activeWindowsByUuid.getOrPut(uuid) { mutableListOf() } +=
                                DateRange(startDate, endDate)
                        }
                }

            // Calendars in the resolver snapshot. CalendarRef.id = UUID so it
            // matches the events' `calendar_id` field. `supersedes` is
            // mapped folder-name → UUID so the resolver compares like
            // with like. activeWindows is supplied for any calendar that
            // declares `supersedes` so the resolver only suppresses other
            // calendars on days where this one actually has an event.
            val calendars = folderToUuid.map { (folder, uuid) ->
                val supersedesFolders = supersedesByFolder[folder].orEmpty()
                val supersedesRefs = supersedesFolders
                    .filter { it !in nonSuperseable }
                    .mapNotNull { folderToUuid[it] }
                    .map { CalendarRef(it) }
                val windows = if (supersedesRefs.isNotEmpty()) {
                    activeWindowsByUuid[uuid].orEmpty().toList()
                } else {
                    emptyList()
                }
                CalendarMeta(
                    ref = CalendarRef(uuid),
                    repo = RepoRef(REPO_ID),
                    displayName = folder,
                    priority = priorityByFolder[folder] ?: 500,
                    tzId = TZ_LONDON,
                    activeWindows = windows,
                    supersedes = supersedesRefs,
                )
            }

            // Pass 2 — entities → resolver inputs.
            val events = mutableListOf<EventInput>()
            val rules = mutableListOf<RecurrenceInput>()
            val exceptionsByRule = mutableMapOf<RuleRef, MutableList<ExceptionInput>>()
            val deviations = mutableListOf<DeviationInput>()
            val overrides = mutableListOf<OverrideInput>()

            // UUID → folder for resolving calendar references on inputs (no-op
            // here because we key by UUID directly).
            for (r in results) {
                if (r !is ParseResult.Success) continue
                when (val e = r.entity) {
                    is Event -> events += toEventInput(e)
                    is RecurrenceRule -> rules += toRecurrenceInput(e)
                    is StoreException -> {
                        val key = RuleRef(e.ruleId)
                        exceptionsByRule.getOrPut(key) { mutableListOf() } += toExceptionInput(e)
                    }
                    is Deviation -> deviations += toDeviationInput(e)
                    else -> Unit
                }
            }

            // The rich-demo authors the force-show override at
            // `calendars/social/overrides/<vacation-cal-uuid>/<event-id>/<date>.md`
            // — the scanner falls through to RawEntity (non-canonical path).
            // Phase D parses the file directly so the resolver receives a
            // populated `overrides` list without us moving the file (the
            // override path convention is a bigger Phase A authoring debt
            // tracked separately).
            overrides += loadNonCanonicalOverrides(repoRoot)

            val snapshot = RepoSnapshot(
                repos = listOf(RepoSnapshot.RepoEntry(RepoRef(REPO_ID), lastIndexedHeadSha = null)),
                calendars = calendars,
                todolists = emptyList(),
            )
            val sources = Renderer.Sources(
                events = events,
                rules = rules,
                exceptionsByRule = exceptionsByRule.mapValues { it.value.toList() },
                deviations = deviations,
                overrides = overrides,
            )
            return RichDemoResolverFixture(snapshot, sources, folderToUuid.toMap())
        }

        // --- conversions -------------------------------------------------

        private fun toEventInput(e: Event): EventInput {
            val start = parseZdt(e.start)
            val end = parseZdt(e.end)
            return EventInput(
                ref = EventRef(e.id),
                calendar = CalendarRef(e.calendarId),
                repo = RepoRef(REPO_ID),
                title = e.title,
                start = start,
                end = end,
                isAllDay = e.allDay,
                emoji = e.emoji,
                body = e.body,
                tags = e.tags,
                priorityOverride = e.priorityOverride,
                isPrivate = e.private,
                isBusy = e.busy,
                location = e.location,
            )
        }

        private fun toRecurrenceInput(r: RecurrenceRule): RecurrenceInput {
            val tz = runCatching { ZoneId.of(r.tzId) }.getOrDefault(TZ_LONDON)
            val dtstart = parseZdtWithTz(r.dtstart, tz)
            val duration = runCatching { Duration.parse(r.duration) }.getOrDefault(Duration.ZERO)
            return RecurrenceInput(
                rule = RuleRef(r.id),
                calendar = CalendarRef(r.calendarId),
                repo = RepoRef(REPO_ID),
                title = r.title,
                dtstart = dtstart,
                duration = duration,
                rrule = r.rrule,
                tzId = tz,
                active = r.active,
                emoji = r.emoji,
                body = r.body,
                tags = r.tags,
                isBusy = r.busy,
            )
        }

        private fun toExceptionInput(e: StoreException): ExceptionInput =
            ExceptionInput(
                ruleId = RuleRef(e.ruleId),
                instanceDate = LocalDate.parse(e.instanceDate),
                mode = e.mode,
                overrideStart = e.overrideStart?.let { parseZdt(it) },
                overrideEnd = e.overrideEnd?.let { parseZdt(it) },
                overrideTitle = e.overrideTitle,
                overrideLocation = e.overrideLocation,
                noteBody = e.body.ifEmpty { null },
            )

        private fun toDeviationInput(d: Deviation): DeviationInput =
            DeviationInput(
                targetId = d.targetId,
                instanceDate = LocalDate.parse(d.instanceDate),
                kind = d.devKind,
                at = parseZdt(d.at),
                note = d.note,
            )

        private fun parseZdt(s: String): ZonedDateTime = parseZdtWithTz(s, TZ_LONDON)

        private fun parseZdtWithTz(s: String, tz: ZoneId): ZonedDateTime {
            if (s.isEmpty()) return ZonedDateTime.now(tz)
            return try {
                OffsetDateTime.parse(s).atZoneSameInstant(tz)
            } catch (_: Throwable) {
                try {
                    LocalDateTime.parse(s).atZone(tz)
                } catch (_: Throwable) {
                    LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).withZoneSameInstant(tz)
                }
            }
        }

        /**
         * Walks every `(cal)/overrides/(sup)/(event-id)/<yyyy-mm-dd>.md` under
         * `calendars/` and parses the frontmatter directly. The canonical
         * location for overrides is at the repo root (`overrides/<cal-id>/...`)
         * — the rich-demo's path is technically non-canonical, so the
         * RepoScanner classifies these as RawEntity. We re-parse here so the
         * resolver still sees the supersedence force-show signal.
         */
        private fun loadNonCanonicalOverrides(repoRoot: File): List<OverrideInput> {
            val out = mutableListOf<OverrideInput>()
            val calendars = File(repoRoot, "calendars")
            if (!calendars.isDirectory) return out
            calendars.listFiles().orEmpty().forEach { calDir ->
                val overridesDir = File(calDir, "overrides")
                if (!overridesDir.isDirectory) return@forEach
                Files.walk(overridesDir.toPath()).use { stream ->
                    stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".md") }
                        .forEach { path ->
                            val text = runCatching {
                                String(Files.readAllBytes(path), Charsets.UTF_8)
                            }.getOrNull() ?: return@forEach
                            val doc = FrontmatterReader.parse(text)
                            val table: TomlTable = doc.frontmatter
                            val kind = table.getString("override_kind") ?: return@forEach
                            val supId = table.getString("superseded_calendar_id")
                                ?: return@forEach
                            val eventId = table.getString("event_id") ?: return@forEach
                            val instance = table.getDateLike("instance_date")
                                ?: return@forEach
                            val rangeFrom = table.getDateLike("from")?.let {
                                runCatching { LocalDate.parse(it) }.getOrNull()
                            }
                            val rangeTo = table.getDateLike("to")?.let {
                                runCatching { LocalDate.parse(it) }.getOrNull()
                            }
                            out += OverrideInput(
                                supersededCalendar = CalendarRef(supId),
                                eventId = eventId,
                                instanceDate = LocalDate.parse(instance),
                                kind = kind,
                                rangeFrom = rangeFrom,
                                rangeTo = rangeTo,
                            )
                        }
                }
            }
            return out
        }
    }
}
