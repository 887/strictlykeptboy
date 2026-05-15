package com.eight87.strictlykeptboy.notif

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.EventInput
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.ExternalSource
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.system.ExternalReminder
import com.eight87.strictlykeptboy.system.SystemRemindersReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.18.F.6 — observer-driven re-emit with a missing event ID
 * cancels its alarms via the diff-and-reschedule loop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ExternalReminderCancellationTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Before fun grantPerms() {
        Shadows.shadowOf(ctx() as Application)
            .grantPermissions(android.Manifest.permission.READ_CALENDAR)
    }

    private fun extEvent(eventId: Long): EventInput {
        val start = ZonedDateTime.now(ZoneId.of("UTC")).plusHours(4)
        return EventInput(
            ref = EventRef("ext-$eventId"),
            calendar = CalendarRef("100"),
            repo = RepoRef("system/com.google/alice"),
            title = "Standup",
            start = start,
            end = start.plusMinutes(30),
            external = ExternalSource(
                accountType = "com.google", accountName = "alice",
                eventId = eventId, accessLevel = 700, ownerAccount = null,
            ),
        )
    }

    @Test fun missingEventIdInReEmitCancelsAlarms() {
        val reader = object : SystemRemindersReader(ctx()) {
            override fun readForEvents(eventIds: List<Long>): Map<Long, List<ExternalReminder>> =
                eventIds.associateWith {
                    listOf(ExternalReminder(minutes = 15, method = 1))
                }
        }
        val ext = ExternalReminderScheduler(ctx(), remindersReader = reader)

        // First tick: 3 events scheduled.
        val first = ext.refresh(listOf(extEvent(1L), extEvent(2L), extEvent(3L)))
        assertEquals(3, first.armedAlarmIds.size)
        assertEquals(3, ext.currentTags().size)

        // Second tick: event 2 disappears (deleted / left window).
        val second = ext.refresh(listOf(extEvent(1L), extEvent(3L)))
        assertEquals(2, second.armedAlarmIds.size)
        val tags = ext.currentTags()
        assertEquals(2, tags.size)
        assertTrue(tags.keys.any { it.eventId == "1" })
        assertTrue(tags.keys.any { it.eventId == "3" })
        assertFalse("expected event 2's tag cancelled",
            tags.keys.any { it.eventId == "2" })
    }
}
