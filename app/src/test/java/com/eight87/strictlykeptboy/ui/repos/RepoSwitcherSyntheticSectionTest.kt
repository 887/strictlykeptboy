package com.eight87.strictlykeptboy.ui.repos

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 2.18.C.3 — assert the synthetic-row data type's contract.
 *
 * The composable defends "non-clickable in the active-repo picker but
 * visible" at the Compose level (no `clickable` modifier on the row); the
 * data-type asserts the id format that drives `RepoRef` lookup —
 * `system/<accountType>/<accountName>`.
 */
class RepoSwitcherSyntheticSectionTest {

    @Test fun systemRepoRowCarriesSyntheticIdAndLabels() {
        val row = SystemRepoRow(
            repoId = "system/com.google/alice@gmail.com",
            displayLabel = "alice@gmail.com",
            secondaryLabel = "com.google",
        )
        // The id matches the resolver's `RepoRef.id` for events that came
        // in via SystemEventsBridge — exercised in SystemEventsBridgeTest.
        assertEquals("system/com.google/alice@gmail.com", row.repoId)
        assertEquals("alice@gmail.com", row.displayLabel)
        assertEquals("com.google", row.secondaryLabel)
    }

    @Test fun missingSecondaryLabelIsAllowed() {
        // Some bare accounts (e.g. local-only providers) have no useful
        // secondary label — the row still renders without it.
        val row = SystemRepoRow(
            repoId = "system/LOCAL/My Calendar",
            displayLabel = "My Calendar",
            secondaryLabel = null,
        )
        assertEquals(null, row.secondaryLabel)
    }
}
