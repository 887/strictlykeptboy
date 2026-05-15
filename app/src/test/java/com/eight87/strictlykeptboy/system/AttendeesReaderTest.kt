package com.eight87.strictlykeptboy.system

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Round 2.18.C.8 — assert the attendees reader maps `Attendees.*` columns
 * + status codes correctly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AttendeesReaderTest {

    private lateinit var ctx: Context
    private lateinit var reader: SystemAttendeesReader

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val info = ProviderInfo().apply { authority = CalendarContract.AUTHORITY }
        Robolectric.buildContentProvider(FakeAttendeesProvider::class.java).create(info).get()
        FakeAttendeesProvider.attendees.clear()
        Shadows.shadowOf(ctx as Application)
            .grantPermissions(android.Manifest.permission.READ_CALENDAR)
        reader = SystemAttendeesReader(ctx)
    }

    @Test fun missingPermissionReturnsEmpty() {
        Shadows.shadowOf(ctx as Application)
            .denyPermissions(android.Manifest.permission.READ_CALENDAR)
        FakeAttendeesProvider.attendees += FakeAttendeeRow(
            eventId = 42L, name = "Alice", email = "alice@example.com",
            status = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
            relationship = 0,
        )
        assertTrue(reader.readForEvent(42L).isEmpty())
    }

    @Test fun mapsAllStatusCodes() {
        FakeAttendeesProvider.attendees.addAll(listOf(
            FakeAttendeeRow(42L, "Alice", "alice@example.com",
                CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED, 0),
            FakeAttendeeRow(42L, "Bob", "bob@example.com",
                CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED, 0),
            FakeAttendeeRow(42L, "Carol", "carol@example.com",
                CalendarContract.Attendees.ATTENDEE_STATUS_INVITED, 0),
            FakeAttendeeRow(42L, "Dave", "dave@example.com",
                CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE, 0),
        ))
        val out = reader.readForEvent(42L)
        assertEquals(4, out.size)
        // Static constants in the contract:
        assertEquals(1, CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED)
        assertEquals(2, CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED)
        assertEquals(3, CalendarContract.Attendees.ATTENDEE_STATUS_INVITED)
        assertEquals(4, CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE)
        assertEquals(
            setOf(1, 2, 3, 4),
            out.map { it.statusCode }.toSet(),
        )
        val alice = out.first { it.displayName == "Alice" }
        assertEquals("alice@example.com", alice.email)
        assertEquals(1, alice.statusCode)
    }

    @Test fun emptyAttendeesYieldsEmptyList() {
        // Event with no attendees → no rows.
        assertTrue(reader.readForEvent(99L).isEmpty())
    }
}

internal data class FakeAttendeeRow(
    val eventId: Long,
    val name: String,
    val email: String,
    val status: Int,
    val relationship: Int,
)

internal class FakeAttendeesProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?,
        selection: String?, selectionArgs: Array<out String>?,
    ): Int = 0

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        // Filter by eventId from selectionArgs.
        val eventId = selectionArgs?.firstOrNull()?.toLongOrNull()
        val cols = projection ?: arrayOf(
            CalendarContract.Attendees.ATTENDEE_NAME,
            CalendarContract.Attendees.ATTENDEE_EMAIL,
            CalendarContract.Attendees.ATTENDEE_STATUS,
            CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
        )
        val cursor = MatrixCursor(cols)
        for (r in attendees) {
            if (eventId != null && r.eventId != eventId) continue
            val row = cols.map { col ->
                when (col) {
                    CalendarContract.Attendees.ATTENDEE_NAME -> r.name
                    CalendarContract.Attendees.ATTENDEE_EMAIL -> r.email
                    CalendarContract.Attendees.ATTENDEE_STATUS -> r.status
                    CalendarContract.Attendees.ATTENDEE_RELATIONSHIP -> r.relationship
                    else -> null
                }
            }
            cursor.addRow(row)
        }
        return cursor
    }

    companion object {
        val attendees: MutableList<FakeAttendeeRow> = mutableListOf()
    }
}
