package com.eight87.strictlykeptboy.demo

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.resolver.CompletionState
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.ViewMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
 * Round 2.20 Phase D.6 — inverted habits regression.
 *
 * The rich demo authors five inverted-default habits (D.54):
 *
 *   - `no-phone-after-22`   (routines, daily 22:00)
 *   - `no-coffee-after-14`  (routines, daily 14:00)
 *   - `cage-stays-on`       (kinky-rituals, daily 23:30)
 *   - `bedtime-by-2330`     (routines, daily 23:30)
 *   - `no-skipping-breakfast` (routines, daily 07:00)
 *
 * For a past slot with no seeded deviation the resolver must report
 * `CompletedBySchedule`. For a slot with a seeded deviation it must
 * surface the deviation's translated state (`partial` → `PartiallyDone`
 * etc., via [com.eight87.strictlykeptboy.resolver.CompletionStateResolver.mapDeviationKind]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RichDemoInvertedHabitTest {

    @get:Rule val tmp = TemporaryFolder()

    private val tz: ZoneId = ZoneId.of("Europe/London")
    private val renderer = Renderer()
    private lateinit var fixture: RichDemoResolverFixture

    @Before fun setUp() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences(
            "rd_inverted_${System.nanoTime()}",
            android.content.Context.MODE_PRIVATE,
        )
        prefs.edit().clear().commit()
        val seeder = RichDemoSeeder(ctx, prefs)
        val parent = tmp.newFolder("rd-inverted")
        fixture = RichDemoResolverFixture.load(seeder.seedIfNeeded(parent).getOrThrow())
    }

    /**
     * `bedtime-by-2330` on 2026-05-19 has a seeded deviation
     * (`deviation_kind = "skipped"` — up until 01:00 sprint emergency).
     * Renderer must tag the band with `Skipped`.
     */
    @Test fun `seeded_deviation_surfaces_translated_state`() = runTest {
        val date = LocalDate.parse("2026-05-19")
        val now = LocalDateTime.parse("2026-05-21T08:00:00").atZone(tz)
        val rendered = renderer.render(
            range = DateRange(date, date),
            viewMode = ViewMode.Day,
            snapshot = fixture.snapshot,
            sources = fixture.sources,
            renderTz = tz,
            now = now,
        )
        val bedtimeBand = rendered.days.single().bands.firstOrNull { b ->
            b.instance.title.contains("bedtime", ignoreCase = true)
        }
        assertNotNull("expected bedtime-by-2330 band on 2026-05-19", bedtimeBand)
        assertEquals(
            "seeded deviation must NOT surface as CompletedBySchedule",
            CompletionState.Skipped,
            bedtimeBand!!.completionState,
        )
    }

    /**
     * `no-phone-after-22` on 2026-05-17 has no seeded deviation. With
     * `now` after the rule's fire time the resolver's inverted-default
     * semantics surface `CompletedBySchedule`.
     */
    @Test fun `no_seeded_deviation_falls_back_to_completed_by_schedule`() = runTest {
        val date = LocalDate.parse("2026-05-17")
        val now = LocalDateTime.parse("2026-05-18T08:00:00").atZone(tz)
        val rendered = renderer.render(
            range = DateRange(date, date),
            viewMode = ViewMode.Day,
            snapshot = fixture.snapshot,
            sources = fixture.sources,
            renderTz = tz,
            now = now,
        )
        val noPhoneBand = rendered.days.single().bands.firstOrNull { b ->
            b.instance.title.contains("phone", ignoreCase = true) &&
                b.instance.effectiveStart.hour == 22
        }
        assertNotNull("expected no-phone-after-22 band on 2026-05-17 22:00", noPhoneBand)
        assertEquals(
            "no deviation + past-fire-time must surface as CompletedBySchedule",
            CompletionState.CompletedBySchedule,
            noPhoneBand!!.completionState,
        )
    }

    /**
     * Cage-stays-on on 2026-05-16 has a seeded deviation
     * (`deviation_kind = "partial"` — cage off 14:00–14:30 for shower).
     */
    @Test fun `cage_stays_on_deviation_2026_05_16`() = runTest {
        val date = LocalDate.parse("2026-05-16")
        val now = LocalDateTime.parse("2026-05-17T08:00:00").atZone(tz)
        val rendered = renderer.render(
            range = DateRange(date, date),
            viewMode = ViewMode.Day,
            snapshot = fixture.snapshot,
            sources = fixture.sources,
            renderTz = tz,
            now = now,
        )
        val cageBand = rendered.days.single().bands.firstOrNull { b ->
            b.instance.title.contains("cage", ignoreCase = true) &&
                b.instance.title.contains("stays", ignoreCase = true)
        }
        assertNotNull("expected cage-stays-on band on 2026-05-16", cageBand)
        assertNotEquals(
            "cage-stays-on deviation must NOT collapse to CompletedBySchedule",
            CompletionState.CompletedBySchedule,
            cageBand!!.completionState,
        )
    }
}
