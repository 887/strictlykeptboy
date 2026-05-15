package com.eight87.strictlykeptboy.ui.import_export

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eight87.strictlykeptboy.system.ParsedIcs
import com.eight87.strictlykeptboy.system.ParsedIcsEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Round 2.18.E.8 — IcsImportScreen renders the preview, lists both
 * skb-repo and external-calendar destinations, and surfaces the
 * picked destination via the Save callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IcsImportScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val parsed = ParsedIcs(
        events = listOf(
            ParsedIcsEvent(
                uid = "u-1",
                summary = "Test event from .ics",
                description = "",
                location = "Studio",
                start = ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneOffset.UTC),
                end = ZonedDateTime.of(2026, 6, 1, 13, 0, 0, 0, ZoneOffset.UTC),
                allDay = false,
            ),
        ),
    )

    private val destinations = listOf(
        IcsImportDestination.Repo(
            repoId = "repo-1",
            displayName = "my calendar",
            calendarId = "cal-1",
        ),
        IcsImportDestination.External(
            calendarId = 7L,
            displayName = "Personal",
            accountName = "me@example.com",
            isPrimary = true,
        ),
    )

    @Test fun rendersPreviewAndDestinations() {
        var saved: IcsImportDestination? = null
        composeRule.setContent {
            IcsImportScreen(
                parsed = parsed,
                destinations = destinations,
                onSave = { saved = it },
                onCancel = {},
            )
        }
        composeRule.onNodeWithTag(TestTagIcsImportScreen).assertIsDisplayed()
        composeRule.onNodeWithText("Test event from .ics").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTagIcsImportDestinationDropdown).assertIsDisplayed()

        // Default destination is the primary external calendar.
        composeRule.onNodeWithText("Personal · me@example.com").assertIsDisplayed()

        composeRule.onNodeWithText("Save").performClick()
        assertNotNull(saved)
        assertTrue(saved is IcsImportDestination.External)
        assertEquals(7L, (saved as IcsImportDestination.External).calendarId)
    }

    @Test fun defaultsToFirstWhenNoPrimary() {
        var saved: IcsImportDestination? = null
        val destNoPrimary = listOf(
            IcsImportDestination.Repo(
                repoId = "r-only",
                displayName = "skb",
                calendarId = "c",
            ),
        )
        composeRule.setContent {
            IcsImportScreen(
                parsed = parsed,
                destinations = destNoPrimary,
                onSave = { saved = it },
                onCancel = {},
            )
        }
        composeRule.onNodeWithText("Save").performClick()
        assertEquals(destNoPrimary[0], saved)
    }
}
