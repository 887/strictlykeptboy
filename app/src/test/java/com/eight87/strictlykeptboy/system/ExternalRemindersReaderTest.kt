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
 * Round 2.18.C.9 — assert the reminders reader maps `Reminders.MINUTES`
 * + `Reminders.METHOD` correctly and re-checks permission per query.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ExternalRemindersReaderTest {

    private lateinit var ctx: Context
    private lateinit var reader: SystemRemindersReader

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val info = ProviderInfo().apply { authority = CalendarContract.AUTHORITY }
        Robolectric.buildContentProvider(FakeRemindersProvider::class.java).create(info).get()
        FakeRemindersProvider.reminders.clear()
        Shadows.shadowOf(ctx as Application)
            .grantPermissions(android.Manifest.permission.READ_CALENDAR)
        reader = SystemRemindersReader(ctx)
    }

    @Test fun missingPermissionReturnsEmpty() {
        Shadows.shadowOf(ctx as Application)
            .denyPermissions(android.Manifest.permission.READ_CALENDAR)
        FakeRemindersProvider.reminders += FakeReminderRow(42L, 10, CalendarContract.Reminders.METHOD_ALERT)
        assertTrue(reader.readForEvent(42L).isEmpty())
    }

    @Test fun mapsMinutesAndMethod() {
        FakeRemindersProvider.reminders.addAll(listOf(
            FakeReminderRow(42L, 0, CalendarContract.Reminders.METHOD_ALERT),
            FakeReminderRow(42L, 10, CalendarContract.Reminders.METHOD_EMAIL),
            FakeReminderRow(42L, 60, CalendarContract.Reminders.METHOD_ALARM),
        ))
        val out = reader.readForEvent(42L)
        assertEquals(3, out.size)
        assertEquals(
            setOf(0, 10, 60),
            out.map { it.minutes }.toSet(),
        )
        val email = out.first { it.minutes == 10 }
        assertEquals(CalendarContract.Reminders.METHOD_EMAIL, email.method)
    }
}

internal data class FakeReminderRow(
    val eventId: Long,
    val minutes: Int,
    val method: Int,
)

internal class FakeRemindersProvider : ContentProvider() {
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
        val eventId = selectionArgs?.firstOrNull()?.toLongOrNull()
        val cols = projection ?: arrayOf(
            CalendarContract.Reminders.MINUTES,
            CalendarContract.Reminders.METHOD,
        )
        val cursor = MatrixCursor(cols)
        for (r in reminders) {
            if (eventId != null && r.eventId != eventId) continue
            val row = cols.map { col ->
                when (col) {
                    CalendarContract.Reminders.MINUTES -> r.minutes
                    CalendarContract.Reminders.METHOD -> r.method
                    else -> null
                }
            }
            cursor.addRow(row)
        }
        return cursor
    }

    companion object {
        val reminders: MutableList<FakeReminderRow> = mutableListOf()
    }
}
