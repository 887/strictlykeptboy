package com.eight87.strictlykeptboy.ui.trip

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.Uuid7
import com.eight87.strictlykeptboy.store.AtomicTemplate
import com.eight87.strictlykeptboy.store.AtomicTemplateEntry
import com.eight87.strictlykeptboy.store.AtomicTemplateLoader
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.EntityWriter
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.RecurrenceRule
import com.eight87.strictlykeptboy.store.TemplateOrigin
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Phase CCC.8 / HV-F.8 — quick-trip materializer.
 *
 * Creates `calendars/cal-trip-<uuidv7>/` under an existing repo root:
 *  - `calendar.toml` carrying trip metadata + `supersedes` / `superseded_
 *    during` arrays (compact cut: arrays stay empty, supersedence picker
 *    is CCC.6 follow-up).
 *  - Travel-prep events back-filled from [atomic-travel-prep] per HV-B.
 *  - Flight-day events from [atomic-flight-day] per HV-C (only when
 *    [TripDraft.transport] = Flight).
 *  - One recurring rule from [atomic-vacation-daily] per HV-D, bounded
 *    by the trip window via RRULE UNTIL.
 *  - Single atomic git commit with HV-F.8's locked summary line.
 *
 * SOLID notes:
 *  - **S** — this object owns ONLY trip-overlay materialization. The
 *    wizard UI lives in `TripWizardNavHost`; the draft shape lives in
 *    `TripDraft`. The template parser is reused from `store/`.
 *  - **D** — templates are loaded via an [AssetReader] function-type so
 *    tests can inject filesystem-backed fixtures without dragging in a
 *    Robolectric `Context`.
 *  - **L** — every materialized [Event] satisfies the same contract as
 *    free-form-created events (same `EntityHeader`, same `calendarId`,
 *    same TOML round-trip). Resolver sees them as ordinary one-off events.
 */
object TripScaffolder {

    /** Template asset reader. Production wires this to `Context.assets.open`. */
    fun interface AssetReader {
        fun open(relativePath: String): InputStream
    }

    data class Outcome(
        val tripCalendarId: String,
        val rootDir: File,
        val prepEventCount: Int,
        val flightEventCount: Int,
        val vacationDailyRuleId: String?,
        val authorIdentity: AuthorIdentity,
    )

    /**
     * Materialize [draft] under an existing repo at [repoRoot].
     *
     * @param repoRoot the calendar repo's working tree root (must already be a Git repo).
     * @param draft validated trip draft. Caller is responsible for [TripDraft.isComplete].
     * @param assets reader for shipped TOML templates (production: `Context.assets`).
     * @param author committer identity for the atomic commit.
     * @param repoId git-repo id (for [GitRepo.open] re-entry).
     * @param tzId default timezone for materialized events.
     * @param now optional clock for tests.
     */
    suspend fun materialize(
        repoRoot: File,
        draft: TripDraft,
        assets: AssetReader,
        author: AuthorIdentity,
        repoId: String,
        tzId: String = ZoneId.systemDefault().id,
        now: () -> OffsetDateTime = { OffsetDateTime.now().withNano(0) },
    ): Outcome = withContext(Dispatchers.IO) {
        require(draft.isComplete) { "TripDraft is incomplete: $draft" }
        val startDate = draft.startDate!!
        val endDate = draft.endDate!!
        val tripCalId = "cal-trip-${Uuid7.generate()}"
        val ts = now().toString()

        // calendar.toml
        val tripCalDir = repoRoot.toPath().resolve("calendars/$tripCalId")
        Files.createDirectories(tripCalDir)
        val calToml = TomlTable().apply {
            putInt("schema_version", 1)
            putString("id", tripCalId)
            putString("kind", "calendar")
            putString("name", draft.name.ifBlank { "trip" })
            putString("role", "travel")
            putInt("priority", 600)
            putString("emoji", "🧳")
            putString("tz_id", tzId)
            putString("destination", draft.destination)
            putString("transport_mode", draft.transport.id)
            putInt("traveler_count", draft.travelerCount)
            putString("trip_start", startDate.toString())
            putString("trip_end", endDate.toString())
            putStringArray("tags", listOf("travel", "trip-overlay"))
        }
        Files.write(
            tripCalDir.resolve("calendar.toml"),
            TomlWriter.emit(calToml).toByteArray(StandardCharsets.UTF_8),
        )

        // Travel-prep events
        val prepTemplate = loadTemplate(assets, "templates/atomic-travel-prep.toml")
        val prepEntries = prepTemplate.entries.filter { eligibleForCompactCut(it, draft) }
        var prepCount = 0
        for (entry in prepEntries) {
            val lead = entry.leadOffsetDays ?: continue
            val eventDate = startDate.minusDays(lead.toLong())
            val event = Event(
                header = EntityHeader(
                    id = Uuid7.generate().toString(),
                    createdAt = ts,
                    updatedAt = ts,
                    author = author.name,
                ),
                title = entry.renderTitle(neutralMode = false),
                start = eventDate.atTime(9, 0).atOffset(java.time.ZoneOffset.UTC).toString(),
                end = eventDate.atTime(9, 0)
                    .plusMinutes(entry.durationMinutes.toLong())
                    .atOffset(java.time.ZoneOffset.UTC).toString(),
                calendarId = tripCalId,
                tags = entry.tags + TemplateOrigin.tagsFor(
                    origin = TemplateOrigin.WIZARD,
                    templateId = prepTemplate.templateId,
                    entryId = entry.id,
                ) + listOf("travel-prep"),
                emoji = "🧳",
                priorityOverride = 600,
                private = entry.privacyFlag,
                materializedFrom = prepTemplate.templateId,
                materializedSourceEvent = entry.id,
                materializedAt = ts,
            )
            EntityWriter.write(repoRoot, event)
            prepCount += 1
        }

        // Flight-day events (only if Flight; one assumed leg on departure day)
        var flightCount = 0
        if (draft.transport == TransportMode.Flight) {
            val flightTemplate = loadTemplate(assets, "templates/atomic-flight-day.toml")
            for (entry in flightTemplate.entries) {
                val offset = entry.offsetMinutesFromDeparture
                    ?: entry.offsetMinutesFromArrival
                    ?: continue
                val anchor = startDate.atTime(14, 0) // assume 14:00 scheduled departure
                val flightStart = anchor.plusMinutes(offset.toLong())
                val event = Event(
                    header = EntityHeader(
                        id = Uuid7.generate().toString(),
                        createdAt = ts,
                        updatedAt = ts,
                        author = author.name,
                    ),
                    title = entry.renderTitle(neutralMode = false),
                    start = flightStart.atOffset(java.time.ZoneOffset.UTC).toString(),
                    end = flightStart.plusMinutes(entry.durationMinutes.toLong())
                        .atOffset(java.time.ZoneOffset.UTC).toString(),
                    calendarId = tripCalId,
                    tags = entry.tags + TemplateOrigin.tagsFor(
                        origin = TemplateOrigin.WIZARD,
                        templateId = flightTemplate.templateId,
                        entryId = entry.id,
                    ) + listOf("flight-day"),
                    emoji = "✈️",
                    priorityOverride = 700,
                    private = entry.privacyFlag,
                    materializedFrom = flightTemplate.templateId,
                    materializedSourceEvent = entry.id,
                    materializedAt = ts,
                )
                EntityWriter.write(repoRoot, event)
                flightCount += 1
            }
        }

        // Vacation-daily recurring rule
        var dailyRuleId: String? = null
        if (draft.includeVacationDaily) {
            val dailyTemplate = loadTemplate(assets, "templates/atomic-vacation-daily.toml")
            // For the compact cut, emit ONE rule per template-entry, bounded by trip window.
            // (HV-D.1: every entry is its own recurring anchor.)
            for ((idx, entry) in dailyTemplate.entries.withIndex()) {
                val ruleId = Uuid7.generate().toString()
                val rule = RecurrenceRule(
                    header = EntityHeader(
                        id = ruleId,
                        createdAt = ts,
                        updatedAt = ts,
                        author = author.name,
                    ),
                    title = entry.renderTitle(neutralMode = false),
                    dtstart = startDate.atTime(9, 0).toString(),
                    duration = "PT${entry.durationMinutes}M",
                    tzId = tzId,
                    rrule = "FREQ=DAILY;UNTIL=${untilCompact(endDate)}",
                    calendarId = tripCalId,
                    tags = entry.tags + TemplateOrigin.tagsFor(
                        origin = TemplateOrigin.WIZARD,
                        templateId = dailyTemplate.templateId,
                        entryId = entry.id,
                    ) + listOf("vacation-daily"),
                    emoji = "🏖️",
                )
                EntityWriter.write(repoRoot, rule)
                if (idx == 0) dailyRuleId = ruleId
            }
        }

        // Atomic commit — open the existing repo as a no-remote handle. Trip-overlay
        // commits don't push from this code path; the repo's regular sync service
        // picks them up on the next configured sync.
        val gitRepo = GitRepo.open(
            rootDir = repoRoot,
            repoId = repoId,
            remotes = emptyList(),
            primaryRemote = null,
            authorIdentity = author,
        )
        val msg = "vacation overlay \"${draft.name}\" $startDate..$endDate: " +
            "$prepCount prep, $flightCount flight, " +
            "${if (draft.includeVacationDaily) "daily anchors" else "no daily anchors"}"
        gitRepo.commitAll(msg)
        gitRepo.close()

        Outcome(
            tripCalendarId = tripCalId,
            rootDir = repoRoot,
            prepEventCount = prepCount,
            flightEventCount = flightCount,
            vacationDailyRuleId = dailyRuleId,
            authorIdentity = author,
        )
    }

    /**
     * Compact-cut conditional filter. Skips entries gated on parameters we
     * don't collect in the 4-screen wizard (visa.required, etc.); HV-F.3's
     * full per-entry toggle preview is a CCC.3 follow-up.
     */
    private fun eligibleForCompactCut(entry: AtomicTemplateEntry, draft: TripDraft): Boolean {
        if (entry.leadOffsetDays == null) return false
        // `pack-kink-kit` sub-beat gated on draft toggle.
        if (entry.id == "pack-kink-kit" && !draft.packKinkKit) return false
        // visa-apply only if user implicitly travels internationally;
        // compact cut treats Flight as "international plausibly enough" — keep it.
        // visa.required full toggle is CCC.3 follow-up; skip visa-apply by default
        // (less surprising for car/train domestic trips).
        if (entry.id == "visa-apply" && draft.transport != TransportMode.Flight) return false
        return true
    }

    private fun loadTemplate(reader: AssetReader, path: String): AtomicTemplate =
        AtomicTemplateLoader.load(reader.open(path))

    /** RFC5545 UNTIL: `YYYYMMDDT000000Z` (compact, UTC). */
    private fun untilCompact(date: LocalDate): String =
        "${date.year}${"%02d".format(date.monthValue)}${"%02d".format(date.dayOfMonth)}T235959Z"
}
