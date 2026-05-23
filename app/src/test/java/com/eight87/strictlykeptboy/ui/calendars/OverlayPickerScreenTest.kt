package com.eight87.strictlykeptboy.ui.calendars

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs
import com.eight87.strictlykeptboy.ui.settings.ListKind
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.21 Phase C.5 — pure-state assertions for [OverlayPickerScreen]'s
 * persistence layer. The composable surface is best validated on the AVD;
 * here we cover the visibility-prefs round-trip the screen drives.
 *
 * Specifically: two repos × five calendars each — toggle parity, repo-
 * grouping order, and zoom round-trip via [CalendarVisibilityPrefs].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OverlayPickerScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun open(): CalendarVisibilityPrefs {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        return CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
    }

    @Test fun two_repos_five_calendars_each_toggle_parity() {
        val store = open()
        // Toggle every calendar off for repo-a, leave repo-b visible.
        (1..5).forEach { idx ->
            store.setVisible(id = "cal-$idx", visible = false, repoId = "repo-a")
        }
        // repo-b stays default-visible (no entry written).
        (1..5).forEach { idx ->
            assertFalse(store.isVisible("cal-$idx", "repo-a"))
            assertTrue(store.isVisible("cal-$idx", "repo-b"))
        }
    }

    @Test fun zoom_round_trips_per_overlay_keyed_by_repo_and_id() {
        val store = open()
        store.setZoom(id = "cal-routines", zoom = 3, repoId = "repo-a")
        store.setZoom(id = "cal-routines", zoom = 1, repoId = "repo-b")
        assertEquals(3, store.zoomOf("cal-routines", "repo-a"))
        assertEquals(1, store.zoomOf("cal-routines", "repo-b"))
    }

    @Test fun zoom_default_when_never_set_is_two() {
        val store = open()
        assertEquals(2, store.zoomOf("cal-fresh", "repo-x"))
    }

    @Test fun zoom_clamps_out_of_range_inputs() {
        val store = open()
        store.setZoom("c", zoom = 0, repoId = "r")
        assertEquals(1, store.zoomOf("c", "r"))
        store.setZoom("c", zoom = 99, repoId = "r")
        assertEquals(4, store.zoomOf("c", "r"))
    }

    @Test fun row_renders_for_each_calendar_and_chevron_opens_editor() {
        // Round 2026-05-23 — `OverlayCard` was simplified to a slim row
        // with toggle + chevron; per-row Color / Priority / Repo-name /
        // Hex-input editors moved into `CalendarSettingsSheet` reached
        // by tapping the row or the chevron. The legacy
        // `inline_priority_field_*` / `card_layout_*` / `hex_input_*` /
        // `repo_name_row_*` assertions used to pin that retired shape;
        // this test replaces all of them with the shape we actually
        // ship now: one row per calendar + tap routes to `onEditCalendar`.
        val prefs = open()
        val cals = listOf(
            CalendarMeta(
                ref = CalendarRef("cal-routines"),
                repo = RepoRef("repo-a"),
                displayName = "Routines",
                priority = 100,
            ),
            CalendarMeta(
                ref = CalendarRef("cal-trips"),
                repo = RepoRef("repo-a"),
                displayName = "Trips",
                priority = 250,
            ),
        )
        val edited = mutableListOf<String>()
        composeRule.setContent {
            OverlayPickerScreen(
                calendarsFlow = MutableStateFlow(cals),
                visibilityPrefs = prefs,
                onBack = {},
                onEditCalendar = { meta -> edited += meta.ref.id },
            )
        }
        composeRule.onNodeWithTag("$TestTagOverlayPickerRow-repo-a-cal-routines").assertExists()
        composeRule.onNodeWithTag("$TestTagOverlayPickerRow-repo-a-cal-trips").assertExists()
        composeRule.onNodeWithTag("$TestTagOverlayPickerEdit-repo-a-cal-routines").performClick()
        assertEquals(listOf("cal-routines"), edited)
    }

    @Test fun top_explainer_is_present_exactly_once() {
        // Round 2.23.5 / Fix 1 — per-row "higher wins tiebreaks" helper
        // is gone; replaced by a single top-of-screen caption.
        val prefs = open()
        val cals = (1..3).map { i ->
            CalendarMeta(
                ref = CalendarRef("cal-$i"),
                repo = RepoRef("repo-a"),
                displayName = "Cal $i",
                priority = 100,
            )
        }
        composeRule.setContent {
            OverlayPickerScreen(
                calendarsFlow = MutableStateFlow(cals),
                visibilityPrefs = prefs,
                onBack = {},
                onEditCalendar = {},
            )
        }
        composeRule.onAllNodesWithTag(TestTagOverlayPickerExplainer).assertCountEquals(1)
    }

    @Test fun no_guid_repo_header_strip_rendered_anymore() {
        // Round 2.23.5 / Fix 2 — the per-repo GUID header above each
        // group of cards is deleted; identity moves into each card.
        val prefs = open()
        val cals = listOf(
            CalendarMeta(
                ref = CalendarRef("cal-a"),
                repo = RepoRef("repo-deadbeef"),
                displayName = "A",
                priority = 100,
            ),
        )
        composeRule.setContent {
            OverlayPickerScreen(
                calendarsFlow = MutableStateFlow(cals),
                visibilityPrefs = prefs,
                onBack = {},
                onEditCalendar = {},
            )
        }
        composeRule.onAllNodesWithTag("$TestTagOverlayPickerRepoHeader-repo-deadbeef")
            .assertCountEquals(0)
    }

    // Round 2026-05-23 — repo_name_row_renders_resolved_display_name,
    // repo_name_row_falls_back_to_truncated_guid_when_unresolved, and
    // hex_input_fires_color_writer_when_six_chars_typed deleted along
    // with the retired inline editor rows (color, priority, hex,
    // repo-name). The replacement editor is `CalendarSettingsSheet` —
    // its own test suite covers those assertions.

    @Test fun zoom_survives_reopen() {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        val a = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        a.setZoom("cal-q", 4, "repo-q")
        val b = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        assertEquals(4, b.zoomOf("cal-q", "repo-q"))
    }
}
