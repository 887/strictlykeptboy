package com.eight87.strictlykeptboy.system

import android.app.Application
import android.content.Context
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.EventInput
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.ExternalSource
import com.eight87.strictlykeptboy.resolver.RepoRef
import kotlinx.coroutines.runBlocking
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
 * Round 2.18.D.10 — every writable affordance (insert, update, delete,
 * RSVP) blocks when `WRITE_CALENDAR` is denied. The editor-mode router
 * is also asserted to keep the read-only branch on low-access events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WritePermissionGateTest {

    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
    }

    @Test fun deniedPermissionFailsEveryWritePath() = runBlocking {
        Shadows.shadowOf(ctx as Application)
            .denyPermissions(android.Manifest.permission.WRITE_CALENDAR)
        val writer = CalendarContractWriter(ctx)
        val tz = ZoneId.of("UTC")
        val start = ZonedDateTime.of(2026, 5, 16, 10, 0, 0, 0, tz)
        val input = EventInput(
            ref = EventRef("e1"),
            calendar = CalendarRef("7"),
            repo = RepoRef("r"),
            title = "t",
            start = start,
            end = start.plusHours(1),
        )

        assertTrue(writer.insertEvent(input, 7L).isFailure)
        assertTrue(writer.updateEvent(1L, input, 7L).isFailure)
        assertTrue(writer.deleteEvent(1L).isFailure)
        assertTrue(writer.respondToInvite(1L, "me@x", InviteResponse.Accepted).isFailure)
        assertFalse(writer.hasWritePermission())
    }

    @Test fun grantedPermissionLetsTheGuardThrough() {
        Shadows.shadowOf(ctx as Application)
            .grantPermissions(android.Manifest.permission.WRITE_CALENDAR)
        assertTrue(CalendarContractWriter(ctx).hasWritePermission())
    }

    @Test fun editorRouterReadOnlyForLowAccess() {
        val src = ExternalSource(
            accountType = "com.google",
            accountName = "x@y",
            eventId = 42L,
            accessLevel = CalendarContract.Calendars.CAL_ACCESS_READ,
            ownerAccount = "x@y",
        )
        val mode = ExternalEventEditRouter.decide(src)
        assertTrue(mode is EditorMode.ReadOnlyExternal)
    }

    @Test fun editorRouterWritableAtContributor() {
        val src = ExternalSource(
            accountType = "com.google",
            accountName = "x@y",
            eventId = 42L,
            accessLevel = CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
            ownerAccount = "x@y",
        )
        val mode = ExternalEventEditRouter.decide(src)
        assertTrue(mode is EditorMode.WritableExternal)
    }

    @Test fun editorRouterFileBackedWhenNoExternal() {
        val mode = ExternalEventEditRouter.decide(null)
        assertEquals(EditorMode.WritableSkbRepo, mode)
    }
}
