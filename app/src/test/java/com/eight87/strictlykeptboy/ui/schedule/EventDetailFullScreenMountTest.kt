package com.eight87.strictlykeptboy.ui.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DayBand
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.InstanceSource
import com.eight87.strictlykeptboy.resolver.MaterializedInstance
import com.eight87.strictlykeptboy.resolver.RepoRef
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.WindowWidthSizeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.25 follow-up — `EventDetailScreen` must mount above the shell's
 * left rail (the rail tag must still be in the tree, but the detail
 * screen's testTag must be present *outside* any per-pane sub-tree,
 * which here we verify by composing both at the same Box root and
 * asserting the detail screen exists when wired).
 *
 * The structural guarantee being pinned: when `pendingEventDetail` is
 * set, an [EventDetailScreen] renders at the outer-Box level (same
 * pattern Round 2.22 used for the OverlayPickerScreen mount). This
 * keeps the rail / top-bar behind a full-cover Surface instead of
 * leaking through, as it did when the detail screen was nested inside
 * the destination-content Box (user feedback 2026-05-17: "clicking on
 * an appointment should open it as a fullscreen overlay not as this
 * inline one").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EventDetailFullScreenMountTest {
    @get:Rule val composeRule = createComposeRule()

    private val tz = ZoneId.of("UTC")

    private fun makeBand(): DayBand {
        val date = LocalDate.of(2026, 5, 17)
        val start = ZonedDateTime.of(date.atTime(10, 0), tz)
        val end = ZonedDateTime.of(date.atTime(11, 0), tz)
        val inst = MaterializedInstance(
            source = InstanceSource.OneOff(EventRef("e1")),
            calendar = CalendarRef("c1"),
            repo = RepoRef("r1"),
            originalStart = start,
            originalEnd = end,
            effectiveStart = start,
            effectiveEnd = end,
            title = "Smoke band",
            body = "",
        )
        return DayBand(instance = inst, priority = 500, laneIndex = 0, totalLanes = 1)
    }

    @Test fun event_detail_renders_when_pending_band_set() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                CompositionLocalProvider(
                    LocalWindowWidthSizeClass provides WindowWidthSizeClass.Compact,
                ) {
                    val pending = remember { mutableStateOf<DayBand?>(makeBand()) }
                    Box(modifier = Modifier.fillMaxSize().testTag("FakeShellRoot")) {
                        // Simulate the rail being present in a sibling subtree.
                        Box(modifier = Modifier.testTag("FakeRail"))
                        pending.value?.let { band ->
                            EventDetailScreen(
                                band = band,
                                onBack = { pending.value = null },
                            )
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag(TestTagEventDetailScreen).assertExists()
        composeRule.onNodeWithTag(TestTagEventDetailBack).assertExists()
        // The detail screen is mounted at the same Box level as the rail,
        // so the rail tag is still semantically present — the visual
        // coverage comes from EventDetailScreen's full-size Surface, not
        // from removing the rail from the tree.
        composeRule.onNodeWithTag("FakeRail").assertExists()
    }

    @Test fun event_detail_absent_when_pending_band_null() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                CompositionLocalProvider(
                    LocalWindowWidthSizeClass provides WindowWidthSizeClass.Compact,
                ) {
                    val pending = remember { mutableStateOf<DayBand?>(null) }
                    Box(modifier = Modifier.fillMaxSize()) {
                        pending.value?.let { band ->
                            EventDetailScreen(
                                band = band,
                                onBack = { pending.value = null },
                            )
                        }
                    }
                }
            }
        }
        composeRule.onNodeWithTag(TestTagEventDetailScreen).assertDoesNotExist()
    }
}
