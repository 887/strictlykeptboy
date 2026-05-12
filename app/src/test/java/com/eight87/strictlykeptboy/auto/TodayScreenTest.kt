package com.eight87.strictlykeptboy.auto

import androidx.car.app.model.ListTemplate
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
 * Phase Q.2 — [TodayScreen] template factory.
 *
 * Uses the `androidx.car.app:app-testing` `TestCarContext` so the
 * Robolectric host can stand up a real [androidx.car.app.CarContext]
 * without a host service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class TodayScreenTest {

    private val tz = ZoneId.of("UTC")
    private val today = ZonedDateTime.now(tz).withHour(9).withMinute(0).withSecond(0).withNano(0)

    private fun fixture(title: String, hour: Int) = MaterializedInstance(
        source = InstanceSource.OneOff(EventRef("evt-$hour")),
        calendar = CalendarRef("cal-a"),
        repo = RepoRef("repo-a"),
        originalStart = today.withHour(hour),
        originalEnd = today.withHour(hour + 1),
        effectiveStart = today.withHour(hour),
        effectiveEnd = today.withHour(hour + 1),
        title = title,
        body = "",
    )

    @Test fun today_with_events_lists_each_event_in_start_order() {
        val ctx = TestCarContext.createCarContext(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
        val source = TodayEventSource {
            listOf(fixture("Pair", 14), fixture("Standup", 9), fixture("Lunch", 12))
        }
        val screen = TodayScreen(ctx, source)

        val tmpl = screen.onGetTemplate() as ListTemplate
        val rows = tmpl.singleList!!.items
        assertEquals(3, rows.size)
        // Sorted ascending by start time.
        assertTrue((rows[0] as androidx.car.app.model.Row).title!!.toString().contains("Standup"))
        assertTrue((rows[1] as androidx.car.app.model.Row).title!!.toString().contains("Lunch"))
        assertTrue((rows[2] as androidx.car.app.model.Row).title!!.toString().contains("Pair"))
    }

    @Test fun today_with_no_events_renders_no_items_message() {
        val ctx = TestCarContext.createCarContext(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
        val source = TodayEventSource { emptyList() }
        val screen = TodayScreen(ctx, source)

        val tmpl = screen.onGetTemplate() as ListTemplate
        val msg = tmpl.singleList!!.noItemsMessage
        assertEquals("Nothing scheduled today.", msg!!.toString())
    }
}
