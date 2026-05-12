package com.eight87.strictlykeptboy.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.scaffold.AppScaffold
import com.eight87.strictlykeptboy.ui.scaffold.TestTagAppScaffold
import com.eight87.strictlykeptboy.ui.scaffold.TestTagDestPrefix
import com.eight87.strictlykeptboy.ui.scaffold.TopDestination
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppScaffoldTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun renders_all_five_destinations() {
        val repoName = MutableStateFlow("demo-repo")
        val snapshot = MutableStateFlow(RepoSnapshot(emptyList(), emptyList(), emptyList()))
        val sources = MutableStateFlow(
            Renderer.Sources(emptyList(), emptyList(), emptyMap(), emptyList(), emptyList()),
        )
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                val state = ScheduleViewState(
                    scope = CoroutineScope(Dispatchers.Unconfined),
                    snapshotFlow = snapshot,
                    sourcesFlow = sources,
                )
                AppScaffold(activeRepoNameFlow = repoName, scheduleState = state)
            }
        }

        composeRule.onNodeWithTag(TestTagAppScaffold).assertExists()
        TopDestination.entries.forEach { dest ->
            // Rail items wrap the icon in a merged semantics node; descend the
            // unmerged tree to find the testTag we placed on the inner Icon.
            composeRule
                .onNodeWithTag("$TestTagDestPrefix${dest.name}", useUnmergedTree = true)
                .assertExists()
        }
    }
}
