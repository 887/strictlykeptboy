package com.eight87.strictlykeptboy.ui.calendars

import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round 2.18.C.2 — predicate-level coverage for the read-only badge.
 *
 * The composable layer wires `Icons.Filled.Lock` onto the chip's
 * trailingIcon slot when `externalAccessLevel < CAL_ACCESS_CONTRIBUTOR`
 * and disables the edit path; here we lock down the predicate + the
 * `externalAccount` parser they both rely on.
 */
class ReadOnlyBadgeTest {

    private fun meta(
        accessLevel: Int?,
        kind: CalendarKind = CalendarKind.External,
        repoId: String = "system/com.google/alice@gmail.com",
    ) = CalendarMeta(
        ref = CalendarRef("1"),
        repo = RepoRef(repoId),
        displayName = "Work",
        priority = 500,
        kind = kind,
        externalAccessLevel = accessLevel,
    )

    @Test fun calAccessReadIsReadOnly() {
        // CAL_ACCESS_READ = 200
        val m = meta(200)
        assertTrue(m.externalAccessLevel!! < CAL_ACCESS_CONTRIBUTOR)
    }

    @Test fun calAccessFreebusyIsReadOnly() {
        // CAL_ACCESS_FREEBUSY = 100
        val m = meta(100)
        assertTrue(m.externalAccessLevel!! < CAL_ACCESS_CONTRIBUTOR)
    }

    @Test fun calAccessContributorIsEditable() {
        // CAL_ACCESS_CONTRIBUTOR = 500
        val m = meta(500)
        assertFalse(m.externalAccessLevel!! < CAL_ACCESS_CONTRIBUTOR)
    }

    @Test fun calAccessOwnerIsEditable() {
        // CAL_ACCESS_OWNER = 700
        val m = meta(700)
        assertFalse(m.externalAccessLevel!! < CAL_ACCESS_CONTRIBUTOR)
    }

    @Test fun externalAccountParserDecodesRepoId() {
        val m = meta(200)
        val account = m.externalAccount
        assertNotNull(account)
        assertEquals("com.google" to "alice@gmail.com", account)
    }

    @Test fun externalAccountReturnsNullForNonExternalKinds() {
        val m = meta(accessLevel = null, kind = CalendarKind.Regular, repoId = "repo-a")
        assertNull(m.externalAccount)
    }
}
