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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Round 2.18.D.7 — RSVP write finds the right attendee row by
 * `EVENT_ID + ATTENDEE_EMAIL`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RsvpWriteTest {

    private lateinit var ctx: Context
    private lateinit var writer: CalendarContractWriter

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val info = ProviderInfo().apply { authority = CalendarContract.AUTHORITY }
        Robolectric.buildContentProvider(FakeRsvpProvider::class.java).create(info).get()
        FakeRsvpProvider.attendees.clear()
        Shadows.shadowOf(ctx as Application).grantPermissions(
            android.Manifest.permission.WRITE_CALENDAR,
        )
        writer = CalendarContractWriter(ctx)
    }

    @Test fun acceptUpdatesMatchingAttendee() = runBlocking {
        FakeRsvpProvider.attendees.add(attendeeRow(42L, "me@example.com",
            CalendarContract.Attendees.ATTENDEE_STATUS_INVITED))
        FakeRsvpProvider.attendees.add(attendeeRow(42L, "other@example.com",
            CalendarContract.Attendees.ATTENDEE_STATUS_NONE))

        val ok = writer.respondToInvite(42L, "me@example.com", InviteResponse.Accepted)
            .getOrThrow()
        assertTrue(ok)
        val mine = FakeRsvpProvider.attendees.first {
            it.getAsString(CalendarContract.Attendees.ATTENDEE_EMAIL) == "me@example.com"
        }
        assertEquals(
            CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
            mine.getAsInteger(CalendarContract.Attendees.ATTENDEE_STATUS),
        )
        // Other attendee untouched.
        val other = FakeRsvpProvider.attendees.first {
            it.getAsString(CalendarContract.Attendees.ATTENDEE_EMAIL) == "other@example.com"
        }
        assertEquals(
            CalendarContract.Attendees.ATTENDEE_STATUS_NONE,
            other.getAsInteger(CalendarContract.Attendees.ATTENDEE_STATUS),
        )
    }

    @Test fun declineMissingAttendeeReturnsFalse() = runBlocking {
        val ok = writer.respondToInvite(42L, "nope@example.com", InviteResponse.Declined)
            .getOrThrow()
        assertFalse(ok)
    }

    @Test fun tentativeStatusCode() = runBlocking {
        FakeRsvpProvider.attendees.add(attendeeRow(7L, "x@y",
            CalendarContract.Attendees.ATTENDEE_STATUS_NONE))
        writer.respondToInvite(7L, "x@y", InviteResponse.Tentative).getOrThrow()
        val r = FakeRsvpProvider.attendees.first()
        assertEquals(
            CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE,
            r.getAsInteger(CalendarContract.Attendees.ATTENDEE_STATUS),
        )
    }

    private fun attendeeRow(eventId: Long, email: String, status: Int): ContentValues =
        ContentValues().apply {
            put(CalendarContract.Attendees.EVENT_ID, eventId)
            put(CalendarContract.Attendees.ATTENDEE_EMAIL, email)
            put(CalendarContract.Attendees.ATTENDEE_STATUS, status)
        }
}

internal class FakeRsvpProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri, values: ContentValues?,
        selection: String?, selectionArgs: Array<out String>?,
    ): Int {
        values ?: return 0
        if (uri == CalendarContract.Attendees.CONTENT_URI) {
            val ev = selectionArgs?.getOrNull(0)?.toLongOrNull() ?: return 0
            val email = selectionArgs.getOrNull(1) ?: return 0
            val row = attendees.firstOrNull {
                it.getAsLong(CalendarContract.Attendees.EVENT_ID) == ev &&
                    it.getAsString(CalendarContract.Attendees.ATTENDEE_EMAIL) == email
            } ?: return 0
            row.putAll(values)
            return 1
        }
        // Events.update for SELF_ATTENDEE_STATUS bump — accept silently.
        if (uri.toString().startsWith(CalendarContract.Events.CONTENT_URI.toString())) {
            return 1
        }
        return 0
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor = MatrixCursor(projection ?: arrayOf("_id"))

    companion object {
        val attendees: MutableList<ContentValues> = mutableListOf()
    }
}
