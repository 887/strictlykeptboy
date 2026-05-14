package com.eight87.strictlykeptboy.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 2.1.G.1 — title-truncation policy.
 *
 * Per the audit, Android Auto hosts clip overlong row titles aggressively.
 * Pin the 24-char + trailing-ellipsis policy from [AutoRowFormatter]
 * so a future "use full event title" regression fails loudly.
 */
class CarAppRuntimeTitleTruncationTest {

    private val start = ZonedDateTime.of(2026, 5, 13, 16, 0, 0, 0, ZoneId.of("UTC"))

    @Test fun truncate_leaves_short_titles_untouched() {
        assertEquals("Standup", AutoRowFormatter.truncateTitle("Standup"))
        // Exactly at the cap → still no ellipsis.
        val exact = "a".repeat(AutoRowFormatter.MAX_TITLE_LEN)
        assertEquals(exact, AutoRowFormatter.truncateTitle(exact))
    }

    @Test fun truncate_long_title_adds_ellipsis() {
        val long = "this is an absurdly long event title that the host will truncate"
        val out = AutoRowFormatter.truncateTitle(long)
        assertTrue("must end with ellipsis", out.endsWith(AutoRowFormatter.ELLIPSIS))
        assertEquals(AutoRowFormatter.MAX_TITLE_LEN, out.length)
    }

    @Test fun row_title_clips_long_titles_in_plain_template() {
        val long = "biweekly all-hands product strategy review"
        val title = AutoRowFormatter.rowTitle(start, long, identity = null)
        assertTrue("must contain truncated form", title.contains(AutoRowFormatter.ELLIPSIS))
        // Time prefix still present.
        assertTrue("must keep time prefix", title.contains("16:00"))
    }
}
