package com.eight87.strictlykeptboy.demo

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.ViewMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Round 2.20 Phase D.7 — supersedence regression.
 *
 * Vacation overlays days 12–15 (Wed 2026-05-27 → Sat 2026-05-30) and
 * lists `supersedes = ["work", "commute", "gym", "dom-overlay", "social"]`.
 * Per D.75–D.78 the resolver tags every band on a listed calendar with
 * `supersededByCalendar = vacation` while the vacation event is active,
 * but the non-superseable kinky-rituals / cat-care / holidays calendars
 * plus the routines.morning-cage-check rule are left untagged.
 *
 * The `force-show` override on `weekly-daddy-meetup/2026-05-31.md`
 * resurfaces the Sunday brunch on the day AFTER vacation ends. (The
 * Sunday 2026-05-31 brunch is technically outside the vacation window
 * already; the override is bound to the supersededCalendar = vacation
 * for declarative round-tripping and is a no-op once vacation is done.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RichDemoSupersedenceTest {

    @get:Rule val tmp = TemporaryFolder()

    private val tz: ZoneId = ZoneId.of("Europe/London")
    private val renderer = Renderer()
    private lateinit var fixture: RichDemoResolverFixture

    @Before fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences(
            "rd_supers_${System.nanoTime()}",
            android.content.Context.MODE_PRIVATE,
        )
        prefs.edit().clear().commit()
        val seeder = RichDemoSeeder(ctx, prefs)
        val parent = tmp.newFolder("rd-supers")
        fixture = RichDemoResolverFixture.load(seeder.seedIfNeeded(parent).getOrThrow())
    }

    @Test fun `vacation_supersedes_work_commute_gym_domoverlay_social_across_window`() = runTest {
        val vacationRef = CalendarRef(fixture.calendarUuid("vacation"))
        // Round 2026-05-23 — vacation became a special BASE layer that
        // also suppresses the morning-routine timebox stack while
        // active (Brighton weekend = clean canvas to plan trip-specific
        // events on top, no routine clutter). Cat-care + holidays +
        // kinky-rituals stay (non_superseable).
        val supersededRefs = listOf("work", "commute", "gym", "dom-overlay", "social", "routines")
            .map { CalendarRef(fixture.calendarUuid(it)) }
            .toSet()
        val preservedRefs = listOf("kinky-rituals", "cat-care", "holidays")
            .map { CalendarRef(fixture.calendarUuid(it)) }
            .toSet()

        // Walk every day Wed 2026-05-27 → Sat 2026-05-30.
        for (date in datesFrom("2026-05-27", "2026-05-30")) {
            val rendered = renderer.render(
                range = DateRange(date, date),
                viewMode = ViewMode.Day,
                snapshot = fixture.snapshot,
                sources = fixture.sources,
                renderTz = tz,
                now = LocalDateTime.of(date, java.time.LocalTime.of(9, 0)).atZone(tz),
            )
            val day = rendered.days.single()

            val supersededBands = day.bands.filter { it.instance.calendar in supersededRefs }
            // We don't require every weekday have all 5 calendars firing —
            // dom-overlay only emits weekdays etc. — but every superseded band
            // we DO see on these days must be paused.
            for (band in supersededBands) {
                assertEquals(
                    "$date: band on superseded calendar ${band.instance.calendar.id} must be paused",
                    vacationRef,
                    band.supersededByCalendar,
                )
            }

            val preservedBands = day.bands.filter { it.instance.calendar in preservedRefs }
            for (band in preservedBands) {
                assertNull(
                    "$date: preserved calendar ${band.instance.calendar.id} must not be paused",
                    band.supersededByCalendar,
                )
            }
        }
    }

    @Test fun `force_show_override_resurfaces_weekly_daddy_meetup`() = runTest {
        // The override is keyed to event id `weekly-daddy-meetup` /
        // 2026-05-31. The vacation event itself ended Sat 2026-05-30, so
        // 2026-05-31 is post-vacation and the rule fires normally. The
        // override exists for declarative redundancy — assert it's at
        // least carried through as a parsed OverrideInput so the
        // resolver could honor it had vacation extended.
        val override = fixture.sources.overrides.firstOrNull {
            it.kind is com.eight87.strictlykeptboy.resolver.OverrideKind.ForceShow &&
                it.instanceDate == LocalDate.parse("2026-05-31")
        }
        assertNotNull(
            "expected force-show override at 2026-05-31 from " +
                "calendars/social/overrides/vacation/<weekly-daddy-meetup>/2026-05-31.md",
            override,
        )
        // Override must point at the vacation calendar (the suppressor it
        // re-includes against).
        assertEquals(
            CalendarRef(fixture.calendarUuid("vacation")),
            override!!.supersededCalendar,
        )

        // And the Sunday-brunch band must render on 2026-05-31 — the
        // weekly-daddy-meetup rule recurs FREQ=WEEKLY;BYDAY=SU at 11:00.
        val date = LocalDate.parse("2026-05-31")
        val rendered = renderer.render(
            range = DateRange(date, date),
            viewMode = ViewMode.Day,
            snapshot = fixture.snapshot,
            sources = fixture.sources,
            renderTz = tz,
            now = LocalDateTime.parse("2026-05-31T08:00:00").atZone(tz),
        )
        val brunch = rendered.days.single().bands.firstOrNull { b ->
            b.instance.title.contains("Daddy", ignoreCase = true) ||
                b.instance.title.contains("brunch", ignoreCase = true)
        }
        assertNotNull("expected weekly daddy meetup on 2026-05-31", brunch)
    }

    private fun datesFrom(start: String, endInclusive: String): List<LocalDate> {
        val from = LocalDate.parse(start)
        val to = LocalDate.parse(endInclusive)
        return generateSequence(from) { d -> if (d.isBefore(to)) d.plusDays(1) else null }.toList()
    }
}
