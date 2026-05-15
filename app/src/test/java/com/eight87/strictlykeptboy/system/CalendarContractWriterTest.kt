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
import com.eight87.strictlykeptboy.resolver.RepoRef
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
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.18.D.1 — insert + update + delete round-trip via a shadowed
 * `CalendarContract` provider.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CalendarContractWriterTest {

    private lateinit var ctx: Context
    private lateinit var writer: CalendarContractWriter

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val info = ProviderInfo().apply { authority = CalendarContract.AUTHORITY }
        Robolectric.buildContentProvider(FakeWriterProvider::class.java).create(info).get()
        FakeWriterProvider.events.clear()
        FakeWriterProvider.attendees.clear()
        FakeWriterProvider.nextEventId = 1L
        Shadows.shadowOf(ctx as Application).grantPermissions(
            android.Manifest.permission.WRITE_CALENDAR,
            android.Manifest.permission.READ_CALENDAR,
        )
        writer = CalendarContractWriter(ctx)
    }

    @Test fun insertReturnsNewId() = runBlocking {
        val input = sampleEvent()
        val id = writer.insertEvent(input, calendarId = 7L).getOrNull()
        assertNotNull(id)
        assertEquals(1, FakeWriterProvider.events.size)
        val row = FakeWriterProvider.events.values.first()
        assertEquals(7L, row.getAsLong(CalendarContract.Events.CALENDAR_ID))
        assertEquals("Test event", row.getAsString(CalendarContract.Events.TITLE))
    }

    @Test fun updateMutatesExistingRow() = runBlocking {
        val id = writer.insertEvent(sampleEvent(), 7L).getOrThrow()
        val updated = sampleEvent(title = "Renamed")
        val rows = writer.updateEvent(id, updated, 7L).getOrThrow()
        assertEquals(1, rows)
        assertEquals(
            "Renamed",
            FakeWriterProvider.events[id]?.getAsString(CalendarContract.Events.TITLE),
        )
    }

    @Test fun deleteAllRemovesRow() = runBlocking {
        val id = writer.insertEvent(sampleEvent(), 7L).getOrThrow()
        val rows = writer.deleteEvent(id, RecurringEditScope.All).getOrThrow()
        assertEquals(1, rows)
        assertTrue(FakeWriterProvider.events.isEmpty())
    }

    @Test fun deleteThisOnlyInsertsCanceledOverride() = runBlocking {
        val parentId = writer.insertEvent(sampleEvent(), 7L).getOrThrow()
        val instanceStart = 1_700_000_000_000L
        val rows = writer.deleteEvent(
            parentId,
            scope = RecurringEditScope.ThisOnly,
            instanceStartMs = instanceStart,
        ).getOrThrow()
        assertEquals(1, rows)
        val override = FakeWriterProvider.events.values.last()
        assertEquals(
            parentId,
            override.getAsLong(CalendarContract.Events.ORIGINAL_ID),
        )
        assertEquals(
            instanceStart,
            override.getAsLong(CalendarContract.Events.ORIGINAL_INSTANCE_TIME),
        )
        assertEquals(
            CalendarContract.Events.STATUS_CANCELED,
            override.getAsInteger(CalendarContract.Events.STATUS),
        )
    }

    @Test fun deleteThisAndFollowingCapsRrule() = runBlocking {
        val parentId = writer.insertEvent(sampleEvent(), 7L).getOrThrow()
        val rule = "FREQ=WEEKLY;BYDAY=MO"
        val rows = writer.deleteEvent(
            parentId,
            scope = RecurringEditScope.ThisAndFollowing,
            instanceStartMs = 1_700_000_000_000L,
            parentRrule = rule,
        ).getOrThrow()
        assertEquals(1, rows)
        val capped = FakeWriterProvider.events[parentId]
            ?.getAsString(CalendarContract.Events.RRULE)
        assertNotNull(capped)
        assertTrue("Expected UNTIL in $capped", capped!!.contains("UNTIL="))
    }

    @Test fun missingWritePermissionShortCircuits() = runBlocking {
        Shadows.shadowOf(ctx as Application)
            .denyPermissions(android.Manifest.permission.WRITE_CALENDAR)
        val r = writer.insertEvent(sampleEvent(), 7L)
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull() is SecurityException)
        assertTrue(FakeWriterProvider.events.isEmpty())
    }

    private fun sampleEvent(title: String = "Test event"): EventInput {
        val tz = ZoneId.of("Europe/Berlin")
        val start = ZonedDateTime.of(2026, 5, 16, 10, 0, 0, 0, tz)
        return EventInput(
            ref = EventRef("e1"),
            calendar = CalendarRef("7"),
            repo = RepoRef("system/com.google/x@y"),
            title = title,
            start = start,
            end = start.plusHours(1),
            isAllDay = false,
            body = "body",
            location = "Office",
        )
    }
}

internal class FakeWriterProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        values ?: return null
        return when {
            uri.toString().startsWith(CalendarContract.Events.CONTENT_URI.toString()) -> {
                val id = nextEventId++
                events[id] = ContentValues(values)
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            }
            else -> null
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        values ?: return 0
        // Events.CONTENT_URI/<id>
        if (uri.toString().startsWith(CalendarContract.Events.CONTENT_URI.toString())) {
            val id = uri.lastPathSegment?.toLongOrNull()
            if (id != null && events.containsKey(id)) {
                val merged = ContentValues(events[id]).apply { putAll(values) }
                events[id] = merged
                return 1
            }
            return 0
        }
        if (uri == CalendarContract.Attendees.CONTENT_URI) {
            // selectionArgs: [eventId, email]
            val ev = selectionArgs?.getOrNull(0)?.toLongOrNull() ?: return 0
            val email = selectionArgs.getOrNull(1) ?: return 0
            val row = attendees.firstOrNull {
                it.getAsLong(CalendarContract.Attendees.EVENT_ID) == ev &&
                    it.getAsString(CalendarContract.Attendees.ATTENDEE_EMAIL) == email
            } ?: return 0
            row.putAll(values)
            return 1
        }
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        if (uri.toString().startsWith(CalendarContract.Events.CONTENT_URI.toString())) {
            val id = uri.lastPathSegment?.toLongOrNull() ?: return 0
            return if (events.remove(id) != null) 1 else 0
        }
        return 0
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        // Minimal: return projection-shaped cursor for a single event by _ID.
        val cols = projection ?: arrayOf(CalendarContract.Events.DIRTY)
        val cursor = MatrixCursor(cols)
        if (uri.toString().startsWith(CalendarContract.Events.CONTENT_URI.toString())) {
            val id = uri.lastPathSegment?.toLongOrNull()
            val row = id?.let { events[it] }
            if (row != null) {
                cursor.addRow(cols.map { row.get(it) })
            }
        }
        return cursor
    }

    companion object {
        val events: MutableMap<Long, ContentValues> = LinkedHashMap()
        val attendees: MutableList<ContentValues> = mutableListOf()
        var nextEventId: Long = 1L
    }
}
