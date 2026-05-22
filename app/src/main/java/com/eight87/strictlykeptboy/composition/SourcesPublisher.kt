package com.eight87.strictlykeptboy.composition

import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.cache.entities.DeviationRow
import com.eight87.strictlykeptboy.cache.entities.EventRow
import com.eight87.strictlykeptboy.cache.entities.ExceptionRow
import com.eight87.strictlykeptboy.cache.entities.OverrideRow
import com.eight87.strictlykeptboy.cache.entities.RecurrenceRuleRow
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
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
import com.eight87.strictlykeptboy.resolver.RuleRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Round 2.1.A — bridge from the Room cache + repo store to
 * [Renderer.Sources] for the schedule pane's visible date range.
 *
 * Single Responsibility: translate the indexer's row-shaped cache into
 * the resolver's `Sources` value-bag, scoped to the supplied
 * `DateRange`. The richer typed metadata (priority overrides, busy
 * flag, all-day, allBusy=false events, exception modes) is carried
 * through as-is.
 *
 * Reactivity: re-emits whenever (a) the visible range changes, (b)
 * the repo set changes, or (c) any of the per-repo cache flows
 * relevant to the window emit. Rule / exception / deviation / override
 * tables don't expose a date-range query in the DAO surface, so they
 * follow the same "tick on events change + listAll" pattern as
 * [IndexerSnapshotPublisher]. Until the indexer or a follow-on phase
 * adds a `listAllFlow`, that's the minimum-disruption shape.
 *
 * All Row→Input conversions parse RFC-3339 timestamp strings — when
 * the row was indexed from a date-only file the resolver treats it as
 * UTC midnight (matches `EntityMapping.parseEpochMs`).
 */
class SourcesPublisher(
    private val db: CacheDatabase,
    private val repoStore: RepoStore,
    private val visibleRange: Flow<DateRange>,
    private val scope: CoroutineScope,
    /**
     * Round 2.18.C.0 — external (CalendarContract) events folded into the
     * resolver's source stream. Given the visible window + the set of
     * known system calendars (from `SystemCalendarsRepository`), this
     * provider emits `EventInput`s sourced from
     * [com.eight87.strictlykeptboy.system.SystemEventsBridge]. Default is
     * a no-op for tests / non-Android contexts. The flow re-emits on
     * provider notify + on window change.
     */
    private val externalEventsProvider: (DateRange) -> Flow<List<EventInput>> = { flowOf(emptyList()) },
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<Renderer.Sources> =
        combine(visibleRange, repoStore.state) { range, configs -> range to configs }
            .flatMapLatest { (range, configs) -> sourcesFlow(range, configs) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = EMPTY,
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun sourcesFlow(range: DateRange, configs: List<RepoConfig>): Flow<Renderer.Sources> {
        val zone = ZoneId.systemDefault()
        val fromMs = range.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = (range.endInclusive ?: range.start)
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // Round 2.18.C.0 — external events: a synthetic "repo" flow on
        // top of the configured file-backed repos. Re-emits whenever the
        // SystemEventsBridge ticker fires (or — for tests — a different
        // provider drives it).
        val externalFlow: Flow<List<EventInput>> = externalEventsProvider(range)
        // Per-repo tick: events flow in the window invalidates whenever
        // any event row touching the range changes. We listAll() the
        // other tables on the same tick.
        val perRepo: List<Flow<RepoData>> = configs.map { cfg ->
            db.events().byDateRange(cfg.repoId, fromMs, toMs)
                .map { winEvents ->
                    val rules = db.recurrenceRules().listAll(cfg.repoId)
                    val deviations = db.deviations().listAll(cfg.repoId)
                    val overrides = db.overrides().listAll(cfg.repoId)
                    val exceptions = db.exceptions().listAll(cfg.repoId)
                    RepoData(cfg, winEvents, rules, exceptions, deviations, overrides)
                }
        }
        val fileRepoMerged: Flow<Renderer.Sources> = if (perRepo.isEmpty()) {
            flowOf(EMPTY)
        } else {
            combine(perRepo) { array -> merge(array.toList(), zone) }
        }
        return combine(fileRepoMerged, externalFlow) { fileSrc, ext ->
            if (ext.isEmpty()) fileSrc
            else fileSrc.copy(events = fileSrc.events + ext)
        }
    }

    private fun merge(perRepo: List<RepoData>, zone: ZoneId): Renderer.Sources {
        val events = mutableListOf<EventInput>()
        val rules = mutableListOf<RecurrenceInput>()
        val exceptionsByRule = mutableMapOf<RuleRef, MutableList<ExceptionInput>>()
        val deviations = mutableListOf<DeviationInput>()
        val overrides = mutableListOf<OverrideInput>()
        for (rd in perRepo) {
            val repoRef = RepoRef(rd.cfg.repoId)
            rd.events.mapTo(events) { row -> toEventInput(row, repoRef, zone) }
            rd.rules.mapTo(rules) { row -> toRuleInput(row, repoRef) }
            for (row in rd.exceptions) {
                val ruleRef = RuleRef(row.ruleId)
                val input = toExceptionInput(row)
                exceptionsByRule.getOrPut(ruleRef) { mutableListOf() } += input
            }
            rd.deviations.forEach { row -> toDeviationInput(row, zone)?.let(deviations::add) }
            rd.overrides.forEach { row -> toOverrideInput(row)?.let(overrides::add) }
        }
        return Renderer.Sources(
            events = events,
            rules = rules,
            exceptionsByRule = exceptionsByRule.mapValues { it.value.toList() },
            deviations = deviations,
            overrides = overrides,
        )
    }

    private fun toEventInput(row: EventRow, repo: RepoRef, zone: ZoneId): EventInput {
        val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(row.startEpochMs), zone)
        val end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(row.endEpochMs), zone)
        return EventInput(
            ref = EventRef(row.id),
            calendar = CalendarRef(row.calendarId),
            repo = repo,
            title = row.title,
            start = start,
            end = end,
            isAllDay = row.allDay,
            emoji = row.emoji,
            body = row.body,
            tags = emptyList(),
            priorityOverride = row.priorityOverride,
            isPrivate = row.privateFlag,
            isBusy = row.busy,
            location = row.location,
            externalUid = row.externalUid,
            group = row.groupLabel,
            requiresResponse = row.requiresResponse,
            promptKind = com.eight87.strictlykeptboy.store.PromptKind.fromToml(row.promptKindRaw),
            promptTarget = com.eight87.strictlykeptboy.store.PromptTarget.fromToml(row.promptTargetRaw),
        )
    }

    private fun toRuleInput(row: RecurrenceRuleRow, repo: RepoRef): RecurrenceInput {
        val tz = runCatching { ZoneId.of(row.tzId) }.getOrDefault(ZoneId.systemDefault())
        val dtstart = parseZdt(row.dtstart, tz)
        val duration = runCatching { Duration.parse(row.duration) }.getOrDefault(Duration.ZERO)
        // Passive flag is not (yet) a column on RecurrenceRuleRow — derive
        // from the tag set so the on-disk `passive = true` field surfaces
        // without a Room migration. Demo + wizard authors both include
        // "passive" in the rule's tags when `passive = true`.
        // tagsJson shape is a JSON array of strings, e.g. ["routine","passive"];
        // substring check is sufficient since tag names cannot contain quotes.
        val isPassive = row.tagsJson.contains("\"passive\"") ||
            row.tagsJson.contains("\"inverted\"")
        return RecurrenceInput(
            rule = RuleRef(row.id),
            calendar = CalendarRef(row.calendarId),
            repo = repo,
            title = row.title,
            dtstart = dtstart,
            duration = duration,
            rrule = row.rrule,
            tzId = tz,
            active = row.active,
            emoji = row.emoji,
            body = row.body,
            group = row.groupLabel,
            requiresResponse = row.requiresResponse,
            promptKind = com.eight87.strictlykeptboy.store.PromptKind.fromToml(row.promptKindRaw),
            promptTarget = com.eight87.strictlykeptboy.store.PromptTarget.fromToml(row.promptTargetRaw),
            passive = isPassive,
        )
    }

    private fun toExceptionInput(row: ExceptionRow): ExceptionInput {
        val date = runCatching { LocalDate.parse(row.instanceDate) }
            .getOrDefault(LocalDate.EPOCH)
        return ExceptionInput(
            ruleId = RuleRef(row.ruleId),
            instanceDate = date,
            mode = row.mode,
            overrideStart = row.overrideStart?.let { parseZdt(it, ZoneId.systemDefault()) },
            overrideEnd = row.overrideEnd?.let { parseZdt(it, ZoneId.systemDefault()) },
            overrideTitle = row.overrideTitle,
            overrideLocation = row.overrideLocation,
            noteBody = row.body.ifEmpty { null },
        )
    }

    private fun toDeviationInput(row: DeviationRow, zone: ZoneId): DeviationInput? {
        val date = runCatching { LocalDate.parse(row.instanceDate) }
            .getOrDefault(LocalDate.EPOCH)
        val kind = com.eight87.strictlykeptboy.resolver.DeviationKind.fromWire(row.devKind)
            ?: return null
        return DeviationInput(
            targetId = row.targetId,
            instanceDate = date,
            kind = kind,
            at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(row.atEpochMs), zone),
            note = row.note,
        )
    }

    private fun toOverrideInput(row: OverrideRow): OverrideInput? {
        val date = runCatching { LocalDate.parse(row.instanceDate) }
            .getOrDefault(LocalDate.EPOCH)
        val rangeFrom = row.rangeFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val rangeTo = row.rangeTo?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val kind = when (row.overrideKind) {
            "force-show" -> com.eight87.strictlykeptboy.resolver.OverrideKind.ForceShow
            "force-show-for-range" ->
                com.eight87.strictlykeptboy.resolver.OverrideKind.ForceShowForRange(rangeFrom, rangeTo)
            else -> return null
        }
        return OverrideInput(
            supersededCalendar = CalendarRef(row.supersededCalendarId),
            eventId = row.eventId,
            instanceDate = date,
            kind = kind,
        )
    }

    /** Parse RFC-3339 timestamp strings, falling back to UTC date-anchor. */
    private fun parseZdt(s: String, fallbackTz: ZoneId): ZonedDateTime {
        if (s.isEmpty()) return ZonedDateTime.now(fallbackTz)
        return try {
            OffsetDateTime.parse(s).atZoneSameInstant(fallbackTz)
        } catch (_: Throwable) {
            try {
                LocalDateTime.parse(s).atZone(fallbackTz)
            } catch (_: Throwable) {
                try {
                    LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).withZoneSameInstant(fallbackTz)
                } catch (_: Throwable) {
                    ZonedDateTime.now(fallbackTz)
                }
            }
        }
    }

    private data class RepoData(
        val cfg: RepoConfig,
        val events: List<EventRow>,
        val rules: List<RecurrenceRuleRow>,
        val exceptions: List<ExceptionRow>,
        val deviations: List<DeviationRow>,
        val overrides: List<OverrideRow>,
    )

    companion object {
        val EMPTY: Renderer.Sources = Renderer.Sources(
            events = emptyList(),
            rules = emptyList(),
            exceptionsByRule = emptyMap(),
            deviations = emptyList(),
            overrides = emptyList(),
        )
    }
}
