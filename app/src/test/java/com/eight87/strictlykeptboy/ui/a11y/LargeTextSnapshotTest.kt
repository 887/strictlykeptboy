package com.eight87.strictlykeptboy.ui.a11y

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.scaffold.SkbTopBar
import com.eight87.strictlykeptboy.ui.scaffold.TestTagTopBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase U.3 — large-text smoke test.
 *
 * Renders representative panes at 200% font scale. Robolectric does not
 * report real-device clipping; it does, however, surface composition-time
 * exceptions thrown by Compose layout (overflow, infinite measure, etc.)
 * — so this guards against the regressive class of "throws at 2.0 scale"
 * bugs. Real visual clipping verification lives in the AVD smoke loop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LargeTextSnapshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun top_bar_renders_at_200_font_scale() {
        composeRule.setContent {
            // Forcibly inject a 2.0 fontScale Density on top of the Robolectric
            // configuration to cover both pathways (resources + composition local).
            val base = LocalDensity.current
            val scaled = Density(density = base.density, fontScale = 2.0f)
            CompositionLocalProvider(LocalDensity provides scaled) {
                StrictlyKeptBoyTheme {
                    SkbTopBar(
                        activeRepoName = "very-long-repo-name-that-might-overflow",
                        selectedViewTab = ScheduleViewTab.Day,
                        onSelectViewTab = {},
                        onRepoSwitcherClick = {},
                        onSyncClick = {},
                        onIdentityClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithTag(TestTagTopBar).assertExists()
    }

    @Test fun every_view_tab_renders_at_200_font_scale() {
        composeRule.setContent {
            val base = LocalDensity.current
            val scaled = Density(density = base.density, fontScale = 2.0f)
            CompositionLocalProvider(LocalDensity provides scaled) {
                StrictlyKeptBoyTheme {
                    SkbTopBar(
                        activeRepoName = "r",
                        selectedViewTab = ScheduleViewTab.Week,
                        onSelectViewTab = {},
                        onRepoSwitcherClick = {},
                        onSyncClick = {},
                        onIdentityClick = {},
                    )
                }
            }
        }
        ScheduleViewTab.entries.forEach {
            composeRule.onNodeWithTag("ViewTab-${it.name}").assertExists()
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
                StrictlyKeptBoyTheme {
                    SkbTopBar(
                        activeRepoName = "r",
                        selectedViewTab = ScheduleViewTab.Day,
                        onSelectViewTab = {},
                        onRepoSwitcherClick = {},
                        onSyncClick = {},
                        onIdentityClick = {},
                    )
                }
            }
        }
        check(observed >= 1.99f) { "expected fontScale 2.0, got $observed" }
    }
}
