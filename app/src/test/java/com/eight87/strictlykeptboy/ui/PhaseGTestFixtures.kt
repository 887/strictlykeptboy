package com.eight87.strictlykeptboy.ui

import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RenderedDay
import com.eight87.strictlykeptboy.resolver.RenderedSchedule
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.ViewMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Shared test fixtures for Phase G UI tests. */
internal object PhaseGTestFixtures {
    val tz: ZoneId = ZoneId.of("UTC")

    fun band(
        id: String,
        date: LocalDate,
        startHour: Int,
        endHour: Int,
        lane: Int = 0,
        total: Int = 1,
        title: String = id,
        body: String = "",
        tags: List<String> = emptyList(),
        kind: com.eight87.strictlykeptboy.resolver.CalendarKind =
            com.eight87.strictlykeptboy.resolver.CalendarKind.Regular,
    ): DayBand {
        val start = ZonedDateTime.of(date.atTime(startHour, 0), tz)
        val end = ZonedDateTime.of(date.atTime(endHour, 0), tz)
        val inst = MaterializedInstance(
            source = InstanceSource.OneOff(EventRef(id)),
            calendar = CalendarRef("c1"),
            repo = RepoRef("r1"),
            originalStart = start,
            originalEnd = end,
            effectiveStart = start,
            effectiveEnd = end,
            title = title,
            body = body,
            tags = tags,
        )
        return DayBand(instance = inst, priority = 500, laneIndex = lane, totalLanes = total, kind = kind)
    }

    fun schedule(days: Map<LocalDate, List<DayBand>>, viewMode: ViewMode = ViewMode.Day): RenderedSchedule {
        val sorted = days.toSortedMap()
        val first = sorted.firstKey()
        val last = sorted.lastKey()
        return RenderedSchedule(
            rangeFrom = ZonedDateTime.of(first.atStartOfDay(), tz),
            rangeTo = ZonedDateTime.of(last.plusDays(1).atStartOfDay(), tz),
            viewMode = viewMode,
            days = sorted.entries.map { (d, bs) ->
                RenderedDay(date = d, bands = bs, densityBucket = if (bs.isEmpty()) 0 else 1)
            },
            sourceDigest = "digest",
        )
    }
}
