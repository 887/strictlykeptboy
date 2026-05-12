package com.eight87.strictlykeptboy.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.TestTagDetailPane
import com.eight87.strictlykeptboy.ui.adaptive.TestTagMasterDetailRow
import com.eight87.strictlykeptboy.ui.adaptive.TestTagMasterPane
import com.eight87.strictlykeptboy.ui.adaptive.WindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.schedule.SchedulePane
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import com.eight87.strictlykeptboy.ui.schedule.TestTagScheduleDetailEmpty
import com.eight87.strictlykeptboy.ui.schedule.TestTagScheduleMasterPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R.2 — schedule pane responds to the ambient `LocalWindowWidthSizeClass`:
 * Compact uses a single-pane layout (no master-detail Row); Medium and
 * Expanded show the two-pane layout with the right-pane empty state
 * when no event is focused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleMasterDetailTest {
    @get:Rule val composeRule = createComposeRule()

    private fun renderAt(widthClass: WindowWidthSizeClass) {
        val snapshot = MutableStateFlow(RepoSnapshot(emptyList(), emptyList(), emptyList()))
        val sources = MutableStateFlow(
            Renderer.Sources(emptyList(), emptyList(), emptyMap(), emptyList(), emptyList()),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                CompositionLocalProvider(LocalWindowWidthSizeClass provides widthClass) {
                    val state = ScheduleViewState(
                        scope = CoroutineScope(Dispatchers.Unconfined),
                        snapshotFlow = snapshot,
                        sourcesFlow = sources,
                    )
                    SchedulePane(activeRepoName = "demo-repo", state = state)
                }
            }
        }
    }

    @Test fun compact_does_not_render_two_pane() {
        renderAt(WindowWidthSizeClass.Compact)
        composeRule.onNodeWithTag(TestTagScheduleMasterPane).assertExists()
        composeRule.onNodeWithTag(TestTagMasterDetailRow).assertDoesNotExist()
    }

    @Test fun medium_renders_two_pane_with_empty_detail() {
        renderAt(WindowWidthSizeClass.Medium)
        composeRule.onNodeWithTag(TestTagMasterDetailRow).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagMasterPane).assertExists()
        composeRule.onNodeWithTag(TestTagDetailPane).assertExists()
        composeRule.onNodeWithTag(TestTagScheduleDetailEmpty).assertExists()
    }

    @Test fun expanded_renders_two_pane_with_empty_detail() {
        renderAt(WindowWidthSizeClass.Expanded)
        composeRule.onNodeWithTag(TestTagMasterDetailRow).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagDetailPane).assertExists()
        composeRule.onNodeWithTag(TestTagScheduleDetailEmpty).assertExists()
    }
}
