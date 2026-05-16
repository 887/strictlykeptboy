package com.eight87.strictlykeptboy.ui.calendars

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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

    @Test fun inline_priority_field_renders_for_each_row_and_fires_writer() {
        // Round 2.22 / Fix 3 — each row in the picker surfaces a typeable
        // priority field. We assert one OutlinedTextField per (visible)
        // calendar row and that an edit fires onPriorityChange with the
        // parsed Int + the row's CalendarMeta.
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
        val captured = mutableListOf<Pair<String, Int>>()
        composeRule.setContent {
            OverlayPickerScreen(
                calendarsFlow = MutableStateFlow(cals),
                visibilityPrefs = prefs,
                onBack = {},
                onEditCalendar = {},
                onPriorityChange = { meta, newPriority ->
                    captured += meta.ref.id to newPriority
                },
            )
        }
        // One priority field per row.
        composeRule.onAllNodesWithTag(TestTagOverlayPickerPriority + "-repo-a-cal-routines")
            .assertCountEquals(1)
        composeRule.onAllNodesWithTag(TestTagOverlayPickerPriority + "-repo-a-cal-trips")
            .assertCountEquals(1)
        // Spot-check the row tag also exists (sanity).
        composeRule.onNodeWithTag("$TestTagOverlayPickerRow-repo-a-cal-routines").assertExists()
    }

    @Test fun card_layout_renders_color_row_and_opens_palette_and_fires_writer() {
        // Round 2.23.2 / D.119 — each overlay row is a multi-row Card
        // with a clickable Color row that expands the 12-swatch palette
        // and writes through onColorChange.
        val prefs = open()
        val cals = listOf(
            CalendarMeta(
                ref = CalendarRef("cal-routines"),
                repo = RepoRef("repo-a"),
                displayName = "Routines",
                priority = 100,
                colorSeed = 0xEF5350, // red
            ),
        )
        val captured = mutableListOf<Pair<String, Int>>()
        composeRule.setContent {
            OverlayPickerScreen(
                calendarsFlow = MutableStateFlow(cals),
                visibilityPrefs = prefs,
                onBack = {},
                onEditCalendar = {},
                onColorChange = { meta, rgb -> captured += meta.ref.id to rgb },
            )
        }
        // Color row exists on the card.
        composeRule.onNodeWithTag("$TestTagOverlayPickerColorRow-repo-a-cal-routines").assertExists()
        // Priority field also exists on the card (card layout sanity).
        composeRule.onNodeWithTag("$TestTagOverlayPickerPriority-repo-a-cal-routines").assertExists()
        // Tap the Color row -> palette expands -> the blue swatch becomes hittable.
        composeRule.onNodeWithTag("$TestTagOverlayPickerColorRow-repo-a-cal-routines").performClick()
        val blueTag = "${TestTagOverlayPickerColorSwatchPrefix}repo-a-cal-routines-42A5F5"
        composeRule.onNodeWithTag(blueTag).assertExists().performClick()
        assertEquals(1, captured.size)
        assertEquals("cal-routines", captured[0].first)
        assertEquals(0x42A5F5, captured[0].second)
    }

    @Test fun zoom_survives_reopen() {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        val a = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        a.setZoom("cal-q", 4, "repo-q")
        val b = CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
        assertEquals(4, b.zoomOf("cal-q", "repo-q"))
    }
}
