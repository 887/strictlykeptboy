package com.eight87.strictlykeptboy.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.WindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.scaffold.SkbAppShell
import com.eight87.strictlykeptboy.ui.scaffold.TestTagAppShell
import com.eight87.strictlykeptboy.ui.scaffold.TestTagShellDestPrefix
import com.eight87.strictlykeptboy.ui.scaffold.TestTagShellRailItemPrefix
import com.eight87.strictlykeptboy.ui.scaffold.TestTagShellTopBar
import com.eight87.strictlykeptboy.ui.scaffold.TopDestination
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import com.eight87.strictlykeptboy.ui.tasks.TaskViewTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Nav-swap polish — asserts the inverted layout.
 *
 *  - Phase 2.2.A.2: the top-bar icon row is restricted to READ surfaces
 *    (Schedule / Tasks / Reviews). The full `TopDestination` enum stays
 *    at 7 cases — Wizard / Together / Repos / Settings are still valid
 *    routing targets, just not rendered as icon-buttons in the row.
 *    Pinned at 3 buttons so accidental re-additions get caught.
 *  - The left rail shows view-mode entries per the active destination.
 *  - Schedule rail has 5 entries (Day / Week / Month / Agenda / Year).
 *  - Tasks rail has 5 entries (Combined / Today / Per-list / Shopping /
 *    Standing).
 *  - Together / Repos / Wizard / Reviews / Settings contribute zero
 *    rail entries.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppShellNavigationSwapTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setShell(width: WindowWidthSizeClass = WindowWidthSizeClass.Compact) {
        val repoName = MutableStateFlow("demo-repo")
        val snapshot = MutableStateFlow(RepoSnapshot(emptyList(), emptyList(), emptyList()))
        val sources = MutableStateFlow(
            Renderer.Sources(emptyList(), emptyList(), emptyMap(), emptyList(), emptyList()),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                CompositionLocalProvider(LocalWindowWidthSizeClass provides width) {
                    val state = ScheduleViewState(
                        scope = CoroutineScope(Dispatchers.Unconfined),
                        snapshotFlow = snapshot,
                        sourcesFlow = sources,
                    )
                    SkbAppShell(activeRepoNameFlow = repoName, scheduleState = state)
                }
            }
        }
    }

    @Test fun top_bar_carries_only_read_surface_destination_buttons() {
        setShell()
        composeRule.onNodeWithTag(TestTagAppShell).assertExists()
        composeRule.onNodeWithTag(TestTagShellTopBar).assertExists()
        // Phase 2.2.A.2 — the enum stays at 7 cases (routing-only for the
        // hidden four), but only the 3 READ surfaces render as buttons.
        assert(TopDestination.entries.size == 7) {
            "Expected 7 TopDestination entries, got ${TopDestination.entries.size}"
        }
        val rendered = listOf(
            TopDestination.Schedule,
            TopDestination.Tasks,
            TopDestination.Reviews,
        )
        rendered.forEach { dest ->
            composeRule
                .onNodeWithTag("$TestTagShellDestPrefix${dest.name}")
                .assertExists()
                .assertHasClickAction()
        }
        // The remaining four destinations are still in the enum (routing
        // targets via avatar / Repos "+" / `wizardEntryRequest` / gear),
        // but they must NOT render as icon-buttons in the top row.
        val hidden = listOf(
            TopDestination.Together,
            TopDestination.Repos,
            TopDestination.Wizard,
            TopDestination.Settings,
        )
        hidden.forEach { dest ->
            composeRule
                .onNodeWithTag("$TestTagShellDestPrefix${dest.name}")
                .assertDoesNotExist()
        }
    }

    @Test fun schedule_rail_has_five_view_mode_entries_by_default() {
        setShell()
        // Default destination is Schedule; rail = Day/Week/Month/Agenda/Year.
        ScheduleViewTab.entries.forEach { tab ->
            composeRule
                .onNodeWithTag("$TestTagShellRailItemPrefix${tab.name}")
                .assertExists()
        }
    }

    @Test fun selecting_tasks_destination_swaps_rail_to_task_view_modes() {
        setShell()
        composeRule
            .onNodeWithTag("$TestTagShellDestPrefix${TopDestination.Tasks.name}")
            .performClick()
        TaskViewTab.entries.forEach { tab ->
            composeRule
                .onNodeWithTag("$TestTagShellRailItemPrefix${tab.name}")
                .assertExists()
        }
        // And the schedule rail entries should no longer be in the tree.
        composeRule
            .onNodeWithTag("$TestTagShellRailItemPrefix${ScheduleViewTab.Day.name}")
            .assertDoesNotExist()
    }

    @Test fun wizard_entry_request_routes_to_wizard_pane_without_top_bar_button() {
        // Phase 2.2.A.3 — even though `TopDestination.Wizard` no longer
        // renders an icon-button in the top row, publishing a value into
        // `wizardEntryRequest` must still flip the shell's `selected`
        // state to Wizard (the `LaunchedEffect` collector at ~line 281).
        // We verify by asserting the WizardNavHost root testTag appears.
        val repoName = MutableStateFlow("demo-repo")
        val snapshot = MutableStateFlow(RepoSnapshot(emptyList(), emptyList(), emptyList()))
        val sources = MutableStateFlow(
            Renderer.Sources(emptyList(), emptyList(), emptyMap(), emptyList(), emptyList()),
        )
        val entryReq = MutableStateFlow<
            com.eight87.strictlykeptboy.ui.wizard.WizardScreen?
        >(com.eight87.strictlykeptboy.ui.wizard.WizardScreen.Welcome)
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                CompositionLocalProvider(LocalWindowWidthSizeClass provides WindowWidthSizeClass.Compact) {
                    val state = ScheduleViewState(
                        scope = CoroutineScope(Dispatchers.Unconfined),
                        snapshotFlow = snapshot,
                        sourcesFlow = sources,
                    )
                    SkbAppShell(
                        activeRepoNameFlow = repoName,
                        scheduleState = state,
                        wizardEntryRequest = entryReq,
                    )
                }
            }
        }
        composeRule
            .onNodeWithTag(com.eight87.strictlykeptboy.ui.wizard.TestTagWizard)
            .assertExists()
        // And confirm there is no Wizard top-bar button (icon row stays
        // filtered to read surfaces).
        composeRule
            .onNodeWithTag("$TestTagShellDestPrefix${TopDestination.Wizard.name}")
            .assertDoesNotExist()
    }

    @Test fun every_rendered_destination_button_is_clickable() {
        setShell()
        // Phase 2.2.A.2 — only the 3 read-surface buttons render in the
        // icon row; hidden destinations are reached via other affordances
        // (bat avatar → Repos, Repos "+" → Wizard, gear → Settings) which
        // route through `selected = TopDestination.X` directly.
        listOf(TopDestination.Schedule, TopDestination.Tasks, TopDestination.Reviews)
            .forEach { dest ->
                composeRule
                    .onNodeWithTag("$TestTagShellDestPrefix${dest.name}")
                    .assertExists()
                    .assertHasClickAction()
            }
    }
}
