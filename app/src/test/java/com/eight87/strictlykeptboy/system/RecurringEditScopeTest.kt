package com.eight87.strictlykeptboy.system

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.EventInput
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.RecurrenceInput
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.resolver.RuleRef
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.18.D.5 — each of the three RecurringEditScope branches yields
 * the expected ContentValues / Uri operations against the shadowed
 * CalendarContract provider.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RecurringEditScopeTest {

    private lateinit var ctx: Context
    private lateinit var writer: CalendarContractWriter

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val info = ProviderInfo().apply { authority = CalendarContract.AUTHORITY }
        Robolectric.buildContentProvider(FakeScopeProvider::class.java).create(info).get()
        FakeScopeProvider.events.clear()
        FakeScopeProvider.nextEventId = 1L
        Shadows.shadowOf(ctx as Application).grantPermissions(
            android.Manifest.permission.WRITE_CALENDAR,
        )
        writer = CalendarContractWriter(ctx)
    }

    @Test fun thisOnlyInsertsOverrideRow() = runBlocking {
        val parent = seedRecurringParent()
        val override = sampleEvent(title = "Moved")
        val newId = writer.insertRecurrenceOverride(
            parentEventId = parent,
            instanceStartMs = 1_700_000_000_000L,
            input = override,
            calendarId = 7L,
        ).getOrThrow()
        val row = FakeScopeProvider.events[newId]!!
        assertEquals(parent, row.getAsLong(CalendarContract.Events.ORIGINAL_ID))
        assertEquals(1_700_000_000_000L,
            row.getAsLong(CalendarContract.Events.ORIGINAL_INSTANCE_TIME))
        assertEquals("Moved", row.getAsString(CalendarContract.Events.TITLE))
    }

    @Test fun thisAndFollowingCapsParentRruleAndInsertsNewSeries() = runBlocking {
        val parent = seedRecurringParent()
        val cappedRows = writer.capRecurrenceUntil(
            parentEventId = parent,
            currentRrule = "FREQ=WEEKLY;BYDAY=MO",
            instanceStartMs = 1_700_000_000_000L,
        ).getOrThrow()
        assertEquals(1, cappedRows)
        val cappedRule = FakeScopeProvider.events[parent]!!
            .getAsString(CalendarContract.Events.RRULE)
        assertTrue(cappedRule.contains("UNTIL="))
        assertTrue(cappedRule.startsWith("FREQ=WEEKLY"))

        // Insert a fresh series for the user's edits.
        val tz = ZoneId.of("UTC")
        val newStart = ZonedDateTime.of(2026, 5, 16, 10, 0, 0, 0, tz)
        val rec = RecurrenceInput(
            rule = RuleRef("r2"),
            calendar = CalendarRef("7"),
            repo = RepoRef("r"),
            title = "Tail",
            dtstart = newStart,
            duration = Duration.ofHours(1),
            rrule = "FREQ=WEEKLY;BYDAY=MO",
            tzId = tz,
        )
        val tailId = writer.insertRecurrence(
            recurrence = rec,
            calendarId = 7L,
            title = "Tail",
            body = "",
            location = null,
        ).getOrThrow()
        val tail = FakeScopeProvider.events[tailId]!!
        assertEquals("FREQ=WEEKLY;BYDAY=MO",
            tail.getAsString(CalendarContract.Events.RRULE))
        // Recurring events use DURATION, not DTEND.
        assertEquals("PT1H", tail.getAsString(CalendarContract.Events.DURATION))
    }

    @Test fun allBranchUpdatesParentRow() = runBlocking {
        val parent = seedRecurringParent()
        val edit = sampleEvent(title = "Renamed series")
        val rows = writer.updateEvent(parent, edit, calendarId = 7L).getOrThrow()
        assertEquals(1, rows)
        assertEquals(
            "Renamed series",
            FakeScopeProvider.events[parent]!!.getAsString(CalendarContract.Events.TITLE),
        )
    }

    @Test fun rruleSerializerReplacesExistingUntil() {
        val rule = "FREQ=DAILY;UNTIL=20250101T000000Z"
        val capped = RecurrenceRuleSerializer.withUntil(rule, "20260101T000000Z")
        assertEquals("FREQ=DAILY;UNTIL=20260101T000000Z", capped)
    }

    @Test fun rruleSerializerReplacesCountWithUntil() {
        val rule = "FREQ=WEEKLY;COUNT=10"
        val capped = RecurrenceRuleSerializer.withUntil(rule, "20260101T000000Z")
        assertEquals("FREQ=WEEKLY;UNTIL=20260101T000000Z", capped)
    }

    @Test fun rruleSerializerAppendsUntilWhenAbsent() {
        val rule = "FREQ=MONTHLY;INTERVAL=2"
        val capped = RecurrenceRuleSerializer.withUntil(rule, "20260101T000000Z")
        assertEquals("FREQ=MONTHLY;INTERVAL=2;UNTIL=20260101T000000Z", capped)
    }

    private suspend fun seedRecurringParent(): Long {
        val tz = ZoneId.of("UTC")
        val start = ZonedDateTime.of(2025, 1, 6, 10, 0, 0, 0, tz)
        val input = EventInput(
            ref = EventRef("e0"),
            calendar = CalendarRef("7"),
            repo = RepoRef("r"),
            title = "Parent",
            start = start,
            end = start.plusHours(1),
        )
        return writer.insertEvent(input, 7L).getOrThrow()
    }

    private fun sampleEvent(title: String): EventInput {
        val tz = ZoneId.of("UTC")
        val start = ZonedDateTime.of(2026, 5, 16, 10, 0, 0, 0, tz)
        return EventInput(
            ref = EventRef("e1"),
            calendar = CalendarRef("7"),
            repo = RepoRef("r"),
            title = title,
            start = start,
            end = start.plusHours(1),
            body = "",
        )
    }
}

internal class FakeScopeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        values ?: return null
        val id = nextEventId++
        events[id] = ContentValues(values)
        return ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return 0
        return if (events.remove(id) != null) 1 else 0
    }
    override fun update(
        uri: Uri, values: ContentValues?,
        selection: String?, selectionArgs: Array<out String>?,
    ): Int {
        values ?: return 0
        val id = uri.lastPathSegment?.toLongOrNull() ?: return 0
        if (!events.containsKey(id)) return 0
        events[id]!!.putAll(values)
        return 1
    }
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor = MatrixCursor(projection ?: arrayOf("_id"))

    companion object {
        val events: MutableMap<Long, ContentValues> = LinkedHashMap()
        var nextEventId: Long = 1L
    }
}
