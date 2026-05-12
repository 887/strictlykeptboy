package com.eight87.strictlykeptboy.resolver

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Test-only helpers that keep individual cases readable. */
internal object Factories {
    val TZ_BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    val TZ_UTC: ZoneId = ZoneId.of("UTC")

    fun zdt(s: String, tz: ZoneId = TZ_BERLIN): ZonedDateTime =
        LocalDateTime.parse(s).atZone(tz)

    fun cal(
        id: String,
        repo: String = "r1",
        priority: Int = 500,
        tz: ZoneId = TZ_BERLIN,
        active: Boolean = true,
        windows: List<DateRange> = emptyList(),
        hours: List<HourRange> = emptyList(),
        supersedes: List<String> = emptyList(),
        baselineCadenceDays: Int? = null,
    ) = CalendarMeta(
        ref = CalendarRef(id),
        repo = RepoRef(repo),
        displayName = id,
        priority = priority,
        activeToggle = active,
        activeWindows = windows,
        activeHours = hours,
        tzId = tz,
        supersedes = supersedes.map { CalendarRef(it) },
        baselineCadenceDays = baselineCadenceDays,
    )

    fun snapshot(vararg cals: CalendarMeta, repos: List<RepoSnapshot.RepoEntry> = listOf(
        RepoSnapshot.RepoEntry(RepoRef("r1"), "sha-r1"),
    )) = RepoSnapshot(repos = repos, calendars = cals.toList(), todolists = emptyList())

    fun event(
        id: String,
        cal: String,
        start: String,
        end: String,
        tz: ZoneId = TZ_BERLIN,
        priorityOverride: Int? = null,
        repo: String = "r1",
        busy: Boolean = true,
    ) = EventInput(
        ref = EventRef(id),
        calendar = CalendarRef(cal),
        repo = RepoRef(repo),
        title = id,
        start = zdt(start, tz),
        end = zdt(end, tz),
        priorityOverride = priorityOverride,
        isBusy = busy,
    )

    fun rule(
        ruleId: String,
        cal: String,
        dtstart: String,
        duration: Duration,
        rrule: String,
        tz: ZoneId = TZ_BERLIN,
        repo: String = "r1",
    ) = RecurrenceInput(
        rule = RuleRef(ruleId),
        calendar = CalendarRef(cal),
        repo = RepoRef(repo),
        title = ruleId,
        dtstart = zdt(dtstart, tz),
        duration = duration,
        rrule = rrule,
        tzId = tz,
    )
}
