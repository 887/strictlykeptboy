package com.eight87.strictlykeptboy.ui.a11y

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import com.eight87.strictlykeptboy.resolver.Renderer
import com.eight87.strictlykeptboy.resolver.RepoSnapshot
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.scaffold.SkbAppShell
import com.eight87.strictlykeptboy.ui.scaffold.TestTagAppShell
import com.eight87.strictlykeptboy.ui.scaffold.TestTagShellRailItemPrefix
import com.eight87.strictlykeptboy.ui.scaffold.TestTagShellTopBar
import com.eight87.strictlykeptboy.ui.schedule.ScheduleViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase U.3 — large-text smoke test.
 *
 * Renders the [SkbAppShell] (top bar + left rail + Schedule content) at
 * 200% font scale. Robolectric does not report real-device clipping, but
 * it does surface composition-time exceptions thrown by Compose layout —
 * so this guards against the "throws at 2.0 scale" regression class.
 * Real visual verification lives in the AVD smoke loop.
 *
 * Rewritten for the nav-swap polish: the top bar is now part of
 * [SkbAppShell] (formerly [com.eight87.strictlykeptboy.ui.scaffold.SkbTopBar],
 * which has been deleted) and per-pane view-mode tabs live in the left
 * rail rather than a horizontal strip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LargeTextSnapshotTest {
    @get:Rule val composeRule = createComposeRule()

    private fun setShellAtFontScale(scale: Float) {
        val repoName = MutableStateFlow("very-long-repo-name-that-might-overflow")
        val snapshot = MutableStateFlow(RepoSnapshot(emptyList(), emptyList(), emptyList()))
        val sources = MutableStateFlow(
            Renderer.Sources(emptyList(), emptyList(), emptyMap(), emptyList(), emptyList()),
        )
        composeRule.setContent {
            val base = LocalDensity.current
            val scaled = Density(density = base.density, fontScale = scale)
            CompositionLocalProvider(LocalDensity provides scaled) {
                StrictlyKeptBoyTheme {
                    val state = ScheduleViewState(
                        scope = CoroutineScope(Dispatchers.Unconfined),
                        snapshotFlow = snapshot,
                        sourcesFlow = sources,
                    )
                    SkbAppShell(
                        context = com.eight87.strictlykeptboy.ui.scaffold.ShellContext(
                            activeRepoNameFlow = repoName,
                            scheduleState = state,
                        ),
                    )
                }
            }
        }
    }

    @Test fun shell_renders_at_200_font_scale() {
        setShellAtFontScale(2.0f)
        composeRule.onNodeWithTag(TestTagAppShell).assertExists()
        composeRule.onNodeWithTag(TestTagShellTopBar).assertExists()
    }

    @Test fun every_schedule_view_tab_renders_at_200_font_scale() {
        setShellAtFontScale(2.0f)
        ScheduleViewTab.entries.forEach {
            composeRule.onNodeWithTag("$TestTagShellRailItemPrefix${it.name}").assertExists()
        }
    }

    /** Sanity-check that the CompositionLocal override path delivered a 2.0 scale. */
    @Test fun composition_local_font_scale_can_be_overridden() {
        var observed: Float = 0f
        composeRule.setContent {
            val base = LocalDensity.current
            val scaled = Density(density = base.density, fontScale = 2.0f)
            CompositionLocalProvider(LocalDensity provides scaled) {
                observed = LocalDensity.current.fontScale
            }
        }
        check(observed >= 1.99f) { "expected fontScale 2.0, got $observed" }
    }
}
