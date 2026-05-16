package com.eight87.strictlykeptboy.ui.scaffold

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.calendars.TestTagOverlayPickerButton
import com.eight87.strictlykeptboy.ui.settings.CalendarVisibilityPrefs
import com.eight87.strictlykeptboy.ui.settings.ListKind
import com.eight87.strictlykeptboy.ui.theming.RepoIconKind
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.22 / Fix 2 — the overlay-picker entry-point now lives at
 * the bottom of the left rail (tonearmboy LibraryRail parity), not the
 * top bar. Asserts the picker button mounts when picker wiring is
 * supplied and collapses when it isn't.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkbScheduleRailTest {

    @get:Rule val composeRule = createComposeRule()

    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun openPrefs(): CalendarVisibilityPrefs {
        val prefs = ctx.getSharedPreferences("list_visibility_v1", Context.MODE_PRIVATE)
        return CalendarVisibilityPrefs.openForTest(prefs, ListKind.Calendars)
    }

    @Test fun overlay_picker_button_renders_at_rail_bottom_when_wired() {
        val cals = MutableStateFlow(
            listOf(
                CalendarMeta(
                    ref = CalendarRef("cal-routines"),
                    repo = RepoRef("repo-a"),
                    displayName = "Routines",
                    priority = 100,
                ),
            ),
        )
        val prefs = openPrefs()
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RailColumn(
                    items = emptyList(),
                    activeIconKind = RepoIconKind.Sticker("bat"),
                    onAccountTap = {},
                    onSettingsTap = {},
                    overlayPickerCalendars = cals,
                    overlayPickerPrefs = prefs,
                    onOverlayPickerClick = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagOverlayPickerButton).assertExists()
        // The picker button itself wraps an IconButton with a click action.
    }

    @Test fun overlay_picker_button_absent_when_wiring_is_null() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                RailColumn(
                    items = emptyList(),
                    activeIconKind = RepoIconKind.Sticker("bat"),
                    onAccountTap = {},
                    onSettingsTap = {},
                    overlayPickerCalendars = null,
                    overlayPickerPrefs = null,
                    onOverlayPickerClick = {},
                )
            }
        }
        composeRule.onNodeWithTag(TestTagOverlayPickerButton).assertDoesNotExist()
    }
}
