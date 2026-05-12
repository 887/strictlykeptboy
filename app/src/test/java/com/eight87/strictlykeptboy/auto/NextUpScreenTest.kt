package com.eight87.strictlykeptboy.auto

import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.TestCarContext
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RepoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase Q.3 — [NextUpScreen] template factory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class NextUpScreenTest {

    private val tz = ZoneId.of("UTC")
    private val base = ZonedDateTime.now(tz).withHour(9).withMinute(0).withSecond(0).withNano(0)

    private fun fixture(title: String, hour: Int) = MaterializedInstance(
        source = InstanceSource.OneOff(EventRef("evt-$hour")),
        calendar = CalendarRef("cal-a"),
        repo = RepoRef("repo-a"),
        originalStart = base.withHour(hour),
        originalEnd = base.withHour(hour + 1),
        effectiveStart = base.withHour(hour),
        effectiveEnd = base.withHour(hour + 1),
        title = title,
        body = "",
    )

    @Test fun next_up_pane_carries_title_duration_and_followups_in_order() {
        val ctx = TestCarContext.createCarContext(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
        val focus = fixture("Standup", 9)
        val all = listOf(focus, fixture("Pair", 11), fixture("Lunch", 12), fixture("Gym", 17))
        val screen = NextUpScreen(ctx, focus, all)

        val tmpl = screen.onGetTemplate() as PaneTemplate
        val rows = tmpl.pane.rows
        // Row 0 = focus title, 1 = duration, 2 = time-until, then 3 follow-ups.
        assertTrue(rows.isNotEmpty())
        assertTrue(rows[0].title!!.toString().contains("Standup"))
        assertTrue(rows[1].title!!.toString().startsWith("Duration:"))
        assertTrue(rows[2].title!!.toString().startsWith("Starts "))
        assertEquals(6, rows.size) // focus + duration + time-until + 3 follow-ups
        assertTrue(rows[3].title!!.toString().contains("Pair"))
        assertTrue(rows[4].title!!.toString().contains("Lunch"))
        assertTrue(rows[5].title!!.toString().contains("Gym"))
    }

    @Test fun next_up_with_no_followups_shows_only_focus_rows() {
        val ctx = TestCarContext.createCarContext(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
        val focus = fixture("Standup", 9)
        val screen = NextUpScreen(ctx, focus, listOf(focus))

        val tmpl = screen.onGetTemplate() as PaneTemplate
        assertEquals(3, tmpl.pane.rows.size)
        // Open-in-app action attached.
        assertEquals(1, tmpl.pane.actions.size)
        assertEquals("Open in app", tmpl.pane.actions[0].title!!.toString())
    }
}
