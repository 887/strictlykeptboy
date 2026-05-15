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
 * Round 2.18.A.1 + A.13 — verify
 *  - permission-denied path emits empty list cleanly
 *  - permission-granted path returns one [SystemCalendar] per row that
 *    matches `VISIBLE = 1 AND SYNC_EVENTS = 1`
 *  - hidden + non-syncing rows are filtered out
 *
 * Strategy: register a fake [FakeCalendarProvider] backed by an in-memory
 * row list as the `com.android.calendar` ContentProvider authority. The
 * production [CalendarContractBridge] then queries it via the normal
 * ContentResolver path. `Shadows.shadowOf(application).grantPermissions(...)`
 * toggles the runtime permission state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CalendarContractBridgeTest {

    private lateinit var ctx: Context
    private lateinit var bridge: CalendarContractBridge

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val info = ProviderInfo().apply { authority = CalendarContract.AUTHORITY }
        val provider = Robolectric.buildContentProvider(FakeCalendarProvider::class.java)
            .create(info).get()
        FakeCalendarProvider.rows.clear()
        bridge = CalendarContractBridge(ctx)
        // Ensure permission is granted by default; individual tests can revoke.
        Shadows.shadowOf(ctx as Application)
            .grantPermissions(android.Manifest.permission.READ_CALENDAR)
        // Provider is created above; reference to keep var alive.
        provider
    }

    @Test fun deniedPermissionEmitsEmpty() {
        Shadows.shadowOf(ctx as Application)
            .denyPermissions(android.Manifest.permission.READ_CALENDAR)
        FakeCalendarProvider.rows += visibleSyncing(id = 1, name = "Visible")
        val out = bridge.readOnce()
        assertTrue(out.isEmpty())
    }

    @Test fun filtersVisibleAndSyncing() {
        FakeCalendarProvider.rows += visibleSyncing(id = 1, name = "Work")
        FakeCalendarProvider.rows += visibleSyncing(id = 2, name = "Personal", primary = true)
        FakeCalendarProvider.rows += FakeRow(
            id = 3, accountName = "x@x", accountType = "com.google", name = "Hidden",
            color = 0, accessLevel = 700, ownerAccount = "x@x", isPrimary = false,
            visible = false, syncEvents = true,
        )
        FakeCalendarProvider.rows += FakeRow(
            id = 4, accountName = "x@x", accountType = "com.google", name = "NotSyncing",
            color = 0, accessLevel = 700, ownerAccount = "x@x", isPrimary = false,
            visible = true, syncEvents = false,
        )

        val out = bridge.readOnce()
        assertEquals(2, out.size)
        assertEquals(setOf("Work", "Personal"), out.map { it.displayName }.toSet())
        val personal = out.first { it.displayName == "Personal" }
        assertEquals(true, personal.isPrimary)
        assertEquals("com.google", personal.accountType)
    }

    @Test fun emptyTableEmitsEmpty() {
        val out = bridge.readOnce()
        assertTrue(out.isEmpty())
    }

    @Test fun securityExceptionFallsThroughToEmpty() {
        FakeCalendarProvider.throwSecurity = true
        try {
            FakeCalendarProvider.rows += visibleSyncing(id = 1, name = "X")
            val out = bridge.readOnce()
            assertTrue(out.isEmpty())
        } finally {
            FakeCalendarProvider.throwSecurity = false
        }
    }

    private fun visibleSyncing(id: Long, name: String, primary: Boolean = false) = FakeRow(
        id = id, accountName = "alice@gmail.com", accountType = "com.google",
        name = name, color = 0xFF4285F4.toInt(), accessLevel = 700,
        ownerAccount = "alice@gmail.com", isPrimary = primary,
        visible = true, syncEvents = true,
    )
}

/** In-memory row for the fake content provider. */
internal data class FakeRow(
    val id: Long,
    val accountName: String,
    val accountType: String,
    val name: String,
    val color: Int,
    val accessLevel: Int,
    val ownerAccount: String?,
    val isPrimary: Boolean,
    val visible: Boolean,
    val syncEvents: Boolean,
)

/**
 * Minimal `CalendarContract.Calendars`-shaped content provider for Robolectric.
 * Only implements the columns + filter the bridge actually queries.
 */
internal class FakeCalendarProvider : ContentProvider() {
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
        if (throwSecurity) throw SecurityException("denied by test")
        val cols = projection ?: arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.SYNC_EVENTS,
            CalendarContract.Calendars.VISIBLE,
        )
        val cursor = MatrixCursor(cols)
        // Apply the same filter the bridge passes — VISIBLE=1 AND SYNC_EVENTS=1.
        val wantVisible = selection?.contains("VISIBLE = 1") ?: selection?.contains("visible = 1") ?: false
        val wantSyncing = selection?.contains("sync_events = 1") ?: selection?.contains("SYNC_EVENTS = 1") ?: false
        val effectiveVisible = selection?.contains("visible = 1") == true || selection?.contains("VISIBLE = 1") == true
        val effectiveSync = selection?.contains("sync_events = 1") == true || selection?.contains("SYNC_EVENTS = 1") == true
        for (r in rows) {
            if (effectiveVisible && !r.visible) continue
            if (effectiveSync && !r.syncEvents) continue
            val row = arrayOfNulls<Any?>(cols.size)
            cols.forEachIndexed { i, col ->
                row[i] = when (col) {
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

    companion object {
        val rows: MutableList<FakeRow> = mutableListOf()
        @JvmField var throwSecurity: Boolean = false
    }
}
