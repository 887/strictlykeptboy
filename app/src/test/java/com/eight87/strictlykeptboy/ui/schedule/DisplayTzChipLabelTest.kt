package com.eight87.strictlykeptboy.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Round 2.24 Phase C.4 — unit test the pure label formatter
 * [displayTzChipLabel]. The composable itself is verified by AVD
 * smoke; this pure function pins the three-branch contract.
 */
class DisplayTzChipLabelTest {

    @Test fun null_displayTz_renders_system_label() {
        assertEquals(
            "System (Europe/Berlin)",
            displayTzChipLabel(
                displayTzId = null,
                repoDefaultTzId = "America/New_York",
                systemTzId = "Europe/Berlin",
            ),
        )
    }

    @Test fun displayTz_equal_to_repo_default_renders_repo_default_label() {
        assertEquals(
            "Repo default (America/New_York)",
            displayTzChipLabel(
                displayTzId = "America/New_York",
                repoDefaultTzId = "America/New_York",
                systemTzId = "Europe/Berlin",
            ),
        )
    }

    @Test fun displayTz_different_from_repo_default_renders_custom_label() {
        assertEquals(
            "Custom: Asia/Tokyo",
            displayTzChipLabel(
                displayTzId = "Asia/Tokyo",
                repoDefaultTzId = "America/New_York",
                systemTzId = "Europe/Berlin",
            ),
        )
    }

    @Test fun displayTz_with_no_repo_default_falls_back_to_custom() {
        assertEquals(
            "Custom: Asia/Tokyo",
            displayTzChipLabel(
                displayTzId = "Asia/Tokyo",
                repoDefaultTzId = null,
                systemTzId = "Europe/Berlin",
            ),
        )
    }

    @Test fun null_displayTz_with_no_repo_default_still_says_system() {
        assertEquals(
            "System (UTC)",
            displayTzChipLabel(
                displayTzId = null,
                repoDefaultTzId = null,
                systemTzId = "UTC",
            ),
        )
    }
}
