package com.eight87.strictlykeptboy.notif

import android.app.AlarmManager
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.18.F — given external events with reminders, alarms are
 * scheduled via [ExternalReminderScheduler], tagged with external repo
 * + event ids.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ExternalReminderScheduleTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Before fun grantPerms() {
        Shadows.shadowOf(ctx() as Application)
            .grantPermissions(android.Manifest.permission.READ_CALENDAR)
    }

    private fun extEvent(eventId: Long, startInHours: Long): EventInput {
        val start = ZonedDateTime.now(ZoneId.of("UTC")).plusHours(startInHours)
        return EventInput(
            ref = EventRef("ext-$eventId"),
            calendar = CalendarRef("100"),
            repo = RepoRef("system/com.google/alice@example.com"),
            title = "Stand-up $eventId",
            start = start,
            end = start.plusMinutes(30),
            external = ExternalSource(
                accountType = "com.google",
                accountName = "alice@example.com",
                eventId = eventId,
                accessLevel = 700,
                ownerAccount = "alice@example.com",
            ),
        )
    }

    @Test fun schedulesAlarmsForTwoExternalEventsWithThreeRemindersEach() {
        val fakeReader = object : SystemRemindersReader(ctx()) {
            override fun readForEvents(eventIds: List<Long>): Map<Long, List<ExternalReminder>> =
                eventIds.associateWith {
                    listOf(
                        ExternalReminder(minutes = 10, method = 1),
                        ExternalReminder(minutes = 60, method = 1),
                        ExternalReminder(minutes = 1440, method = 4),
                    )
                }
        }
        val ext = ExternalReminderScheduler(ctx(), remindersReader = fakeReader)
        val refresh = ext.refresh(listOf(extEvent(101L, 48), extEvent(102L, 72)))

        // 2 events × 3 reminders = 6 alarms
        assertEquals(6, refresh.armedAlarmIds.size)
        assertEquals(2, refresh.scheduledEventCount)

        val tags = ext.currentTags()
        assertTrue(tags.containsKey(
            ExternalReminderScheduler.EventKey(
                "external/com.google/alice@example.com", "101",
            ),
        ))
        // Tag format: external repoId + eventId pulled through
        val anyAlarmId = refresh.armedAlarmIds.first()
        assertTrue(
            "expected external repoId prefix in alarm id: $anyAlarmId",
            anyAlarmId.startsWith("external/com.google/alice@example.com:"),
        )

        val am = ctx().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        assertEquals(6, shadowOf(am).scheduledAlarms.size)
    }

    @Test fun filtersOutEmailAndSmsMethods() {
        val fakeReader = object : SystemRemindersReader(ctx()) {
            override fun readForEvents(eventIds: List<Long>): Map<Long, List<ExternalReminder>> =
                eventIds.associateWith {
                    listOf(
                        ExternalReminder(minutes = 10, method = 1), // ALERT — kept
                        ExternalReminder(minutes = 30, method = 2), // EMAIL — dropped
                        ExternalReminder(minutes = 60, method = 3), // SMS — dropped
                        ExternalReminder(minutes = 90, method = 4), // ALARM — kept
                    )
                }
        }
        val ext = ExternalReminderScheduler(ctx(), remindersReader = fakeReader)
        val r = ext.refresh(listOf(extEvent(200L, 5)))
        assertEquals(2, r.armedAlarmIds.size)
    }
}
