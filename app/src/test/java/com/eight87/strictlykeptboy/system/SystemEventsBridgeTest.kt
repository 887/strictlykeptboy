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
import org.junit.Assert.assertFalse
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

/**
 * Round 2.18.A.6 + A.13 — verify
 *  - permission denied → empty list
 *  - 3-event seed (all-day, recurring instance, declined) maps to
 *    [com.eight87.strictlykeptboy.resolver.EventInput] with
 *    `external` populated and the right `isBusy` flag
 *  - canceled events are dropped
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SystemEventsBridgeTest {

    private lateinit var ctx: Context
    private lateinit var bridge: SystemEventsBridge

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        // Register both providers under the same `com.android.calendar`
        // authority — Robolectric routes by URI path, so we use the
        // unified [FakeUnifiedCalendarProvider] for this test.
        val info = ProviderInfo().apply { authority = CalendarContract.AUTHORITY }
        Robolectric.buildContentProvider(FakeUnifiedCalendarProvider::class.java)
            .create(info).get()
        FakeUnifiedCalendarProvider.calendars.clear()
        FakeUnifiedCalendarProvider.events.clear()
        Shadows.shadowOf(ctx as Application)
            .grantPermissions(android.Manifest.permission.READ_CALENDAR)
        bridge = SystemEventsBridge(ctx)
    }

    @Test fun deniedPermissionEmitsEmpty() {
        Shadows.shadowOf(ctx as Application)
            .denyPermissions(android.Manifest.permission.READ_CALENDAR)
        seedThreeEvents()
        val out = bridge.readOnce(0L, 10L * DAY_MS, KNOWN_CALENDARS)
        assertTrue(out.isEmpty())
    }

    @Test fun mapsAllDayRecurringAndDeclined() {
        seedThreeEvents()
        val out = bridge.readOnce(0L, 10L * DAY_MS, KNOWN_CALENDARS)
        // Three events seeded; canceled one is filtered by the bridge.
        assertEquals(3, out.size)

        val allDay = out.first { it.title == "Holiday" }
        assertTrue(allDay.isAllDay)
        assertEquals("com.google", allDay.external!!.accountType)
        assertEquals(700, allDay.external!!.accessLevel)

        val recurring = out.first { it.title == "Standup" }
        assertNotNull(recurring.external)
        assertEquals(42L, recurring.external!!.eventId)
        // Per-instance ref encodes the begin so multiple instances are
        // distinguishable.
        assertTrue(recurring.ref.id.startsWith("ext-42@"))

        val declined = out.first { it.title == "BoringMtg" }
        assertFalse("declined events must not be marked busy", declined.isBusy)
    }

    @Test fun canceledEventsAreDropped() {
        FakeUnifiedCalendarProvider.calendars += FakeRow(
            id = 1L, accountName = "a", accountType = "com.google",
            name = "C", color = 0, accessLevel = 700, ownerAccount = "a",
            isPrimary = false, visible = true, syncEvents = true,
        )
        FakeUnifiedCalendarProvider.events += FakeEvent(
            eventId = 1L, calendarId = 1L,
            begin = 1_000L, end = 2_000L, allDay = false,
            title = "Canceled", description = "", location = null,
            tz = "UTC", rrule = null,
            status = CalendarContract.Events.STATUS_CANCELED,
            attendeeStatus = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
            accessLevel = CalendarContract.Events.ACCESS_DEFAULT,
            ownerAccount = "a",
        )
        val out = bridge.readOnce(0L, DAY_MS, KNOWN_CALENDARS_FOR_CANCEL)
        assertTrue(out.isEmpty())
    }

    @Test fun unknownCalendarIdIsDropped() {
        // Event references a calendar that wasn't passed in `knownCalendars`.
        FakeUnifiedCalendarProvider.events += FakeEvent(
            eventId = 99L, calendarId = 9999L, begin = 1L, end = 2L,
            allDay = false, title = "Orphan", description = "", location = null,
            tz = "UTC", rrule = null,
            status = CalendarContract.Events.STATUS_CONFIRMED,
            attendeeStatus = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
            accessLevel = CalendarContract.Events.ACCESS_DEFAULT,
            ownerAccount = "a",
        )
        val out = bridge.readOnce(0L, DAY_MS, emptyList())
        assertTrue(out.isEmpty())
    }

    private fun seedThreeEvents() {
        FakeUnifiedCalendarProvider.calendars.addAll(KNOWN_CALENDARS.map {
            FakeRow(
                id = it.id, accountName = it.accountName, accountType = it.accountType,
                name = it.displayName, color = it.color, accessLevel = it.accessLevel,
                ownerAccount = it.ownerAccount, isPrimary = it.isPrimary,
                visible = true, syncEvents = true,
            )
        })
        FakeUnifiedCalendarProvider.events += FakeEvent(
            eventId = 7L, calendarId = 1L,
            begin = 0L, end = DAY_MS, allDay = true,
            title = "Holiday", description = "", location = null,
            tz = "UTC", rrule = null,
            status = CalendarContract.Events.STATUS_CONFIRMED,
            attendeeStatus = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
            accessLevel = 700, ownerAccount = "alice@gmail.com",
        )
        FakeUnifiedCalendarProvider.events += FakeEvent(
            eventId = 42L, calendarId = 1L,
            begin = DAY_MS + 9 * HOUR_MS, end = DAY_MS + 10 * HOUR_MS, allDay = false,
            title = "Standup", description = "", location = null,
            tz = "UTC", rrule = "FREQ=WEEKLY;BYDAY=MO",
            status = CalendarContract.Events.STATUS_CONFIRMED,
            attendeeStatus = CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
            accessLevel = 700, ownerAccount = "alice@gmail.com",
        )
        FakeUnifiedCalendarProvider.events += FakeEvent(
            eventId = 99L, calendarId = 1L,
            begin = 2 * DAY_MS, end = 2 * DAY_MS + HOUR_MS, allDay = false,
            title = "BoringMtg", description = "", location = null,
            tz = "UTC", rrule = null,
            status = CalendarContract.Events.STATUS_CONFIRMED,
            attendeeStatus = CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
            accessLevel = 700, ownerAccount = "alice@gmail.com",
        )
    }

    companion object {
        private const val HOUR_MS = 3_600_000L
        private const val DAY_MS = 24L * HOUR_MS
        private val KNOWN_CALENDARS = listOf(
            SystemCalendar(
                id = 1L, accountName = "alice@gmail.com", accountType = "com.google",
                displayName = "Work", color = 0, accessLevel = 700,
                ownerAccount = "alice@gmail.com", isPrimary = true,
            ),
        )
        private val KNOWN_CALENDARS_FOR_CANCEL = listOf(
            SystemCalendar(
                id = 1L, accountName = "a", accountType = "com.google",
                displayName = "C", color = 0, accessLevel = 700,
                ownerAccount = "a", isPrimary = false,
            ),
        )
    }
}

/** In-memory event row for the fake provider. */
internal data class FakeEvent(
    val eventId: Long,
    val calendarId: Long,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val title: String,
    val description: String,
    val location: String?,
    val tz: String,
    val rrule: String?,
    val status: Int,
    val attendeeStatus: Int,
    val accessLevel: Int,
    val ownerAccount: String?,
)

/**
 * Routes Calendars + Instances queries to in-memory tables. URI-based
 * dispatch: anything under `instances/when/...` returns events; anything
 * under `calendars` returns calendars. The bridge under test uses
 * `Instances.CONTENT_URI` (no segments appended in the production path
 * — it constructs `instances/when/<begin>/<end>` via the public helper),
 * so we just match on the prefix.
 */
internal class FakeUnifiedCalendarProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?,
        selection: String?, selectionArgs: Array<out String>?,
    ): Int = 0

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val path = uri.path.orEmpty()
        return when {
            path.contains("instances") -> queryEvents(projection)
            else -> queryCalendars(projection)
        }
    }

    private fun queryCalendars(projection: Array<out String>?): Cursor {
        val cols = projection ?: arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        )
        val cursor = MatrixCursor(cols)
        for (r in calendars) {
            val row = cols.map { col ->
                when (col) {
                    CalendarContract.Calendars._ID -> r.id
                    CalendarContract.Calendars.ACCOUNT_NAME -> r.accountName
                    CalendarContract.Calendars.ACCOUNT_TYPE -> r.accountType
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME -> r.name
                    CalendarContract.Calendars.CALENDAR_COLOR -> r.color
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL -> r.accessLevel
                    CalendarContract.Calendars.OWNER_ACCOUNT -> r.ownerAccount
                    CalendarContract.Calendars.IS_PRIMARY -> if (r.isPrimary) 1 else 0
                    CalendarContract.Calendars.SYNC_EVENTS -> if (r.syncEvents) 1 else 0
                    CalendarContract.Calendars.VISIBLE -> if (r.visible) 1 else 0
                    else -> null
                }
            }
            cursor.addRow(row)
        }
        return cursor
    }

    private fun queryEvents(projection: Array<out String>?): Cursor {
        val cols = projection ?: arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
        )
        val cursor = MatrixCursor(cols)
        for (e in events) {
            val row = cols.map { col ->
                when (col) {
                    CalendarContract.Instances.EVENT_ID -> e.eventId
                    CalendarContract.Instances.BEGIN -> e.begin
                    CalendarContract.Instances.END -> e.end
                    CalendarContract.Instances.ALL_DAY -> if (e.allDay) 1 else 0
                    CalendarContract.Instances.TITLE -> e.title
                    CalendarContract.Instances.DESCRIPTION -> e.description
                    CalendarContract.Instances.EVENT_LOCATION -> e.location
                    CalendarContract.Instances.EVENT_TIMEZONE -> e.tz
                    CalendarContract.Instances.CALENDAR_ID -> e.calendarId
                    CalendarContract.Instances.RRULE -> e.rrule
                    CalendarContract.Instances.STATUS -> e.status
                    CalendarContract.Instances.SELF_ATTENDEE_STATUS -> e.attendeeStatus
                    CalendarContract.Instances.CALENDAR_ACCESS_LEVEL -> e.accessLevel
                    CalendarContract.Instances.OWNER_ACCOUNT -> e.ownerAccount
                    else -> null
                }
            }
            cursor.addRow(row)
        }
        return cursor
    }

    companion object {
        val calendars: MutableList<FakeRow> = mutableListOf()
        val events: MutableList<FakeEvent> = mutableListOf()
    }
}
