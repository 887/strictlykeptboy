package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.1.B.3 — CalendarVisibilityPrefs JSON migration.
 *
 * Legacy entries (pre-2.1.B) carried `activeFromIso` + `activeUntilIso`
 * and no `repoId`. Those entries must decode without throwing and with
 * `repoId = ""`, falling back to id-only matching for back-compat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarVisibilityPrefsMigrationTest {

    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test fun legacy_entry_with_active_windows_decodes_with_repoId_blank() {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        // Legacy JSON: has activeFromIso + activeUntilIso, no repoId.
        val legacy = """
            {"ordered":[{"id":"cal-a","label":"Cal A","visible":true,
                         "activeFromIso":"2026-01-01","activeUntilIso":"2026-12-31"}]}
        """.trimIndent().replace("\n", "").replace("  ", "")
        prefs.edit().putString("list.Calendars", legacy).apply()

        val store = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        val s = store.state.value
        assertEquals(1, s.ordered.size)
        val e = s.ordered[0]
        assertEquals("cal-a", e.id)
        assertEquals("Cal A", e.label)
        assertEquals(true, e.visible)
        assertEquals("", e.repoId)
    }

    @Test fun new_entries_round_trip_with_repoId_qualifier() {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        val store = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        store.setEntries(
            listOf(
                VisibilityEntry(id = "cal-x", label = "X", visible = true, repoId = "repo-a"),
                VisibilityEntry(id = "cal-x", label = "X (other repo)", visible = false, repoId = "repo-b"),
            ),
        )
        val reopened = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        val ordered = reopened.state.value.ordered
        assertEquals(2, ordered.size)
        assertEquals("repo-a", ordered[0].repoId)
        assertEquals("repo-b", ordered[1].repoId)
        assertTrue(reopened.isVisible("cal-x", "repo-a"))
        assertFalse(reopened.isVisible("cal-x", "repo-b"))
    }

    @Test fun blank_repoId_matches_legacy_entries_by_id_only() {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        val store = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        // Seed a legacy-shaped entry (repoId = "").
        store.setEntries(listOf(VisibilityEntry(id = "cal-legacy", label = "L", visible = true)))
        // Subsequent toggle without a known repoId still finds the entry.
        store.setVisible("cal-legacy", false, repoId = "")
        assertFalse(store.isVisible("cal-legacy"))
    }
}
