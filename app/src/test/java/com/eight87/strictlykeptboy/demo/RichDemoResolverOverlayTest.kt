package com.eight87.strictlykeptboy.demo

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.CompletionState
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.ViewMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Round 2.20 Phase D.5 — resolver overlay regression for the rich demo.
 *
 * Loads the seeded rich-demo repo through the same parser path the
 * indexer uses, builds a [Renderer.Sources] + RepoSnapshot from disk
 * via [RichDemoResolverFixture], and asserts the renderer produces the
 * expected layered output for the three Phase D probe slots.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RichDemoResolverOverlayTest {

    @get:Rule val tmp = TemporaryFolder()

    private val tz: ZoneId = ZoneId.of("Europe/London")
    private val renderer = Renderer()
    private lateinit var fixture: RichDemoResolverFixture

    @Before fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences(
            "rd_overlay_${System.nanoTime()}",
            android.content.Context.MODE_PRIVATE,
        )
        prefs.edit().clear().commit()
        val seeder = RichDemoSeeder(ctx, prefs)
        val parent = tmp.newFolder("rd-overlay")
        val repoRoot: File = seeder.seedIfNeeded(parent).getOrThrow()
        fixture = RichDemoResolverFixture.load(repoRoot)
    }

    /** Wed 2026-05-20 12:15 — lunch-break + dom-overlay lunch-check-in stacked. */
    @Test fun `wed_12_15_stacks_lunch_and_dom_overlay`() = runTest {
        val date = LocalDate.parse("2026-05-20")
        val rendered = renderer.render(
            range = DateRange(date, date),
            viewMode = ViewMode.Day,
            snapshot = fixture.snapshot,
            sources = fixture.sources,
            renderTz = tz,
            now = LocalDateTime.parse("2026-05-20T12:15:00").atZone(tz),
        )
        val day = rendered.days.single()
        val workCal = CalendarRef(fixture.calendarUuid("work"))
        val domCal = CalendarRef(fixture.calendarUuid("dom-overlay"))

        val lunch = day.bands.firstOrNull { b ->
            b.instance.calendar == workCal &&
                b.instance.title.contains("Lunch", ignoreCase = true)
        }
        assertNotNull("expected work/lunch-break band at 12:15 Wed", lunch)
        val checkIn = day.bands.firstOrNull { b ->
            b.instance.calendar == domCal &&
                b.instance.title.contains("Lunch check-in", ignoreCase = true)
        }
        assertNotNull("expected dom-overlay/lunch-check-in band at 12:15 Wed", checkIn)
        // The two stack — not the same lane.
        assertTrue(
            "lunch + dom-overlay must occupy distinct lanes",
            lunch!!.laneIndex != checkIn!!.laneIndex,
        )
        // Neither should be tagged superseded — vacation window doesn't cover 2026-05-20.
        assertNull(lunch.supersededByCalendar)
        assertNull(checkIn.supersededByCalendar)
    }

    /**
     * Thu 2026-05-28 09:00 — inside convention vacation (2026-05-27..2026-05-30).
     * Expectation: kinky-rituals + cat-care + routines.morning-cage-check + holidays
     * stay visible; work/commute/gym/dom-overlay/social get tagged
     * `supersededByCalendar = vacation` (kept in the render with the paused
     * treatment per RV-O / 2.1.C.4).
     */
    @Test fun `thu_2026_05_28_09_00_supersedes_work_and_friends_but_not_rituals`() = runTest {
        val date = LocalDate.parse("2026-05-28")
        val rendered = renderer.render(
            range = DateRange(date, date),
            viewMode = ViewMode.Day,
            snapshot = fixture.snapshot,
            sources = fixture.sources,
            renderTz = tz,
            now = LocalDateTime.parse("2026-05-28T09:00:00").atZone(tz),
        )
        val day = rendered.days.single()

        val vacationRef = CalendarRef(fixture.calendarUuid("vacation"))
        val supersededCalendars = setOf("work", "commute", "gym", "dom-overlay", "social")
            .map { fixture.calendarUuid(it) }
            .map { CalendarRef(it) }
            .toSet()
        val preservedCalendars = setOf(
            "kinky-rituals", "cat-care", "holidays", "routines",
        )
            .map { fixture.calendarUuid(it) }
            .map { CalendarRef(it) }
            .toSet()

        // Every band on a superseded calendar must be tagged paused.
        val supersededBands = day.bands.filter { it.instance.calendar in supersededCalendars }
        assertTrue(
            "expected at least one band on a superseded calendar (work/commute/etc.)",
            supersededBands.isNotEmpty(),
        )
        for (band in supersededBands) {
            assertEquals(
                "band on ${band.instance.calendar.id} must be paused by vacation",
                vacationRef,
                band.supersededByCalendar,
            )
        }

        // Preserved calendars never get tagged superseded by vacation.
        val preservedBands = day.bands.filter { it.instance.calendar in preservedCalendars }
        assertTrue(
            "expected at least one preserved band (cage check / cat feed)",
            preservedBands.isNotEmpty(),
        )
        for (band in preservedBands) {
            assertNull(
                "preserved calendar ${band.instance.calendar.id} must not be paused",
                band.supersededByCalendar,
            )
        }
    }

    /**
     * Mon 2026-05-25 09:00 — UK Spring bank holiday (Whit Monday).
     * Expectation:
     * - work/daily-standup has an exception (kind=cancel) authored — no
     *   standup band at 09:00 on this date.
     * - holidays calendar shows the all-day Pfingstmontag/Spring-bank
     *   banner event.
     */
    @Test fun `mon_2026_05_25_holiday_cancels_standup_and_shows_holiday_banner`() = runTest {
        val date = LocalDate.parse("2026-05-25")
        val rendered = renderer.render(
            range = DateRange(date, date),
            viewMode = ViewMode.Day,
            snapshot = fixture.snapshot,
            sources = fixture.sources,
            renderTz = tz,
            now = LocalDateTime.parse("2026-05-25T09:00:00").atZone(tz),
        )
        val day = rendered.days.single()
        val workCal = CalendarRef(fixture.calendarUuid("work"))
        val holidaysCal = CalendarRef(fixture.calendarUuid("holidays"))

        // Standup is cancelled via `exceptions/<rule>/2026-05-25.md` mode=cancel.
        val standupAt9 = day.bands.firstOrNull { b ->
            b.instance.calendar == workCal &&
                b.instance.title.contains("standup", ignoreCase = true) &&
                b.instance.effectiveStart.hour == 9
        }
        assertNull(
            "daily-standup occurrence on 2026-05-25 must be cancelled by exception",
            standupAt9,
        )

        // Holiday banner is the all-day event.
        val holidayBanner = day.bands.firstOrNull { b -> b.instance.calendar == holidaysCal }
        assertNotNull("expected holiday banner on 2026-05-25", holidayBanner)
    }
}
