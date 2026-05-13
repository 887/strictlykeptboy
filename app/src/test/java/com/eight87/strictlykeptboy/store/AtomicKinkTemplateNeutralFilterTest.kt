package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase XX.11 / AT-K.8 — neutral-mode filter on the kink template.
 *
 * Per AT-E LOCKED: the `atomic-kink-self-care` template has
 * `neutral_safe = false` and every entry carries `tags = ["kink"]`.
 * Applying the template under neutral mode must yield zero entries.
 *
 * The filter rule (agent 5 + wizard scaffold-time): skip the whole
 * template if `neutral_safe = false`; OR (defensive) drop entries
 * whose `tags` contains "kink".
 */
class AtomicKinkTemplateNeutralFilterTest {

    private val kinkSample = """
        schema_version = 1
        template_id = "atomic-kink-self-care"
        display_name = "Atomic kink self-care"
        category = "self-care"
        neutral_safe = false

        [[entry]]
        id = "cage-check"
        title = "Cage check"
        duration_minutes = 2
        tags = ["kink"]

        [[entry]]
        id = "kegels"
        title = "Kegels"
        duration_minutes = 5
        tags = ["kink"]
    """.trimIndent()

    @Test fun templateHeaderMarksNonNeutral() {
        val t = AtomicTemplateLoader.parse(kinkSample)
        assertFalse("template must be neutral_safe = false", t.neutralSafe)
    }

    @Test fun everyEntryCarriesKinkTag() {
        val t = AtomicTemplateLoader.parse(kinkSample)
        assertTrue(t.entries.isNotEmpty())
        for (e in t.entries) {
            assertTrue("entry ${e.id} missing kink tag", e.tags.contains("kink"))
        }
    }

    @Test fun neutralModeApplyYieldsZeroEntries() {
        val t = AtomicTemplateLoader.parse(kinkSample)
        val neutralMode = true
        val filtered = applyUnderMode(t, neutralMode)
        assertEquals(0, filtered.size)
    }

    @Test fun nonNeutralModeKeepsAllEntries() {
        val t = AtomicTemplateLoader.parse(kinkSample)
        val filtered = applyUnderMode(t, neutralMode = false)
        assertEquals(2, filtered.size)
    }

    /**
     * Locked filter rule (matches the wizard scaffolder behaviour
     * specified in TW-I): in neutral mode, drop the template entirely
     * if `neutral_safe = false`, and additionally drop any entry tagged
     * "kink" as a defensive belt-and-braces filter.
     */
    private fun applyUnderMode(t: AtomicTemplate, neutralMode: Boolean): List<AtomicTemplateEntry> {
        if (neutralMode && !t.neutralSafe) return emptyList()
        return if (neutralMode) t.entries.filterNot { it.tags.contains("kink") } else t.entries
    }
}
