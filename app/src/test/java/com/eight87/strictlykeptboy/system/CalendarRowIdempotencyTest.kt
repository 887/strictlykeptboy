package com.eight87.strictlykeptboy.system

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.G.8 — calendar-row idempotency map persists + survives
 * re-open. First sync inserts → stores ID; second sync looks up + reuses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CalendarRowIdempotencyTest {

    private lateinit var store: SystemCalendarPrefsStore
    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val prefs = ctx.getSharedPreferences("idem_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        store = SystemCalendarPrefsStore.openForTest(prefs)
    }

    @Test fun missingLookupReturnsNull() {
        assertNull(store.calendarRowId("repo-1", "routines"))
    }

    @Test fun setRoundTrips() {
        store.setCalendarRowId("repo-1", "routines", 17L)
        assertEquals(17L, store.calendarRowId("repo-1", "routines"))
    }

    @Test fun secondSyncReusesId() {
        store.setCalendarRowId("repo-1", "routines", 17L)
        // Second sync (simulated) — lookup must reuse the same ID rather
        // than allocating a fresh one.
        val reused = store.calendarRowId("repo-1", "routines")
        assertEquals(17L, reused)
        // Setting the same key again is idempotent.
        store.setCalendarRowId("repo-1", "routines", 17L)
        assertEquals(17L, store.calendarRowId("repo-1", "routines"))
    }

    @Test fun reopenedStoreSeesPersistedId() {
        store.setCalendarRowId("repo-1", "routines", 42L)
        val prefs = ctx.getSharedPreferences("idem_test", Context.MODE_PRIVATE)
        val reopened = SystemCalendarPrefsStore.openForTest(prefs)
        assertEquals(42L, reopened.calendarRowId("repo-1", "routines"))
    }

    @Test fun clearForRepoLeavesOthersAlone() {
        store.setCalendarRowId("repo-1", "routines", 17L)
        store.setCalendarRowId("repo-1", "work", 18L)
        store.setCalendarRowId("repo-2", "shared", 19L)
        store.clearCalendarRowIdsFor("repo-1")
        assertNull(store.calendarRowId("repo-1", "routines"))
        assertNull(store.calendarRowId("repo-1", "work"))
        assertEquals(19L, store.calendarRowId("repo-2", "shared"))
    }
}
