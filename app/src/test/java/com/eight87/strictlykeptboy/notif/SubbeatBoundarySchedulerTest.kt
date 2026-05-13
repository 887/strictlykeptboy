package com.eight87.strictlykeptboy.notif

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Phase XX.11 / AT-K.4 — sub-beat boundary alarm scheduling.
 *
 * Asserts that a 4-sub-beat event registers exactly 4 boundary alarms
 * at `start + 0`, `start + 25`, `start + 50`, `start + 75` seconds
 * (matching draft-atomic-activities.md AT-K.4 fixture).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SubbeatBoundarySchedulerTest {

    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test fun schedulesOneAlarmPerSubbeat() {
        val sched = SubbeatBoundaryScheduler(ctx())
        val start = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1)
        val series = SubbeatBoundaryScheduler.SubbeatSeries(
            repoId = "r1",
            eventId = "ev1",
            calendarId = "c1",
            eventTitle = "Brush teeth",
            startIso = start.toString(),
            subbeats = listOf(
                SubbeatBoundaryScheduler.Subbeat("a", 25),
                SubbeatBoundaryScheduler.Subbeat("b", 25),
                SubbeatBoundaryScheduler.Subbeat("c", 25),
                SubbeatBoundaryScheduler.Subbeat("d", 25),
            ),
        )
        val ids = sched.schedule(series, now = Instant.EPOCH)
        assertEquals(4, ids.size)
        assertEquals(
            listOf(
                "r1:ev1:subbeat:0",
                "r1:ev1:subbeat:1",
                "r1:ev1:subbeat:2",
                "r1:ev1:subbeat:3",
            ),
            ids,
        )
        val am = ctx().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduled = shadowOf(am).scheduledAlarms
        assertEquals(4, scheduled.size)
        val triggers = scheduled.map { it.triggerAtTime }.sorted()
        val base = start.toInstant().toEpochMilli()
        assertEquals(
            listOf(base, base + 25_000, base + 50_000, base + 75_000),
            triggers,
        )
    }

    @Test fun capsAt16SubbeatsPerEvent() {
        val sched = SubbeatBoundaryScheduler(ctx())
        val start = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1)
        val series = SubbeatBoundaryScheduler.SubbeatSeries(
            repoId = "r1",
            eventId = "ev-many",
            calendarId = "c1",
            eventTitle = "T",
            startIso = start.toString(),
            subbeats = (1..20).map { SubbeatBoundaryScheduler.Subbeat("s$it", 5) },
        )
        val ids = sched.schedule(series, now = Instant.EPOCH)
        assertEquals(SubbeatBoundaryScheduler.MAX_SUBBEATS_PER_EVENT, ids.size)
    }
}
