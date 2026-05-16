package com.eight87.strictlykeptboy.ui.reviews

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eight87.strictlykeptboy.theme.StrictlyKeptBoyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.23.1 / D.118 — Reviews pane no longer renders horizontal
 * `FilterChip`s at the top. Filter labels move to the shell's vertical
 * left rail; this test asserts none of the four filter labels appear
 * inside the `ReviewsPane` semantics tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewsPaneVerticalRailTest {
    @get:Rule val composeRule = createComposeRule()

    private val sampleEntries = listOf(
        ReviewEntry(
            commitSha = "sha1",
            author = "Boy Keeper",
            timestamp = "2026-05-15T10:00:00+01:00",
            autoSummary = "good boy held the morning routine clean",
            unread = true,
        ),
        ReviewEntry(
            commitSha = "sha2",
            author = "Boy Keeper",
            timestamp = "2026-05-16T10:00:00+01:00",
            autoSummary = "good boy — bedtime by 23:30 again",
            unread = false,
        ),
    )

    @Test fun pane_renders_no_horizontal_filter_chips() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ReviewsPane(side = ReviewsSide.Boy, items = sampleEntries)
            }
        }
        composeRule.onNodeWithTag(TestTagReviewsPane).assertIsDisplayed()
        // The four filter labels (All / Unread / Reactions / Threaded)
        // used to render as FilterChip text inside the pane. The
        // vertical rail in the shell owns them now — this pane must
        // not surface any of those texts itself.
        listOf("All", "Unread", "Reactions", "Threaded").forEach { label ->
            composeRule.onAllNodesWithText(label).assertCountEquals(0)
        }
    }

    @Test fun pane_applies_external_filter_unread() {
        composeRule.setContent {
            StrictlyKeptBoyTheme {
                ReviewsPane(
                    side = ReviewsSide.Boy,
                    items = sampleEntries,
                    filter = ReviewsFilter.Unread,
                )
            }
        }
        // Only the unread item (sha1) should render.
        composeRule.onNodeWithTag("${TestTagReviewItemPrefix}sha1").assertIsDisplayed()
        composeRule.onAllNodesWithText(sampleEntries[1].autoSummary).assertCountEquals(0)
    }

    @Test fun filter_label_resolver_covers_every_enum() {
        // Smoke: every ReviewsFilter case maps to a non-zero @StringRes.
        ReviewsFilter.entries.forEach { f ->
            assertEquals(true, reviewsFilterLabelRes(f) != 0)
        }
    }
}
