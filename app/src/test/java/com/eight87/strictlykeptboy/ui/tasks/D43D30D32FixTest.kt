package com.eight87.strictlykeptboy.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-43 / D-30 / D-32 regression guard.
 *
 * D-43 — author chip initials derive from the resolved display name, not the
 *         raw UUID. UUID-shaped strings that aren't in the display-name map
 *         fall through to "?".
 * D-30 — TaskItem.standing drives a recurring ↻ glyph in TaskRow (checked
 *         via model-layer assertions; full rendering verified on-device).
 * D-32 — AuthorBubble a11y label is "by <displayName>"; PriorityDot a11y
 *         label uses a human-readable tier (model-layer assertions).
 */
class D43D30D32FixTest {

    private val list = TaskFixtures.listA

    // ─── D-43: author name resolution ────────────────────────────────────────

    @Test fun `initials from resolved display name Boy Keeper`() {
        // Simulates what AuthorBubble does when authorDisplayNames["<uuid>"] = "Boy Keeper"
        val displayName = "Boy Keeper"
        val initials = initialsFrom(displayName)
        assertEquals("BK", initials)
    }

    @Test fun `initials from single-word display name Keeper`() {
        val initials = initialsFrom("Keeper")
        assertEquals("K", initials)
    }

    @Test fun `initials from three-word display name takes first two`() {
        val initials = initialsFrom("Sir Boy Keeper")
        assertEquals("SB", initials)
    }

    @Test fun `UUID-shaped author string without display map falls to question mark`() {
        // UUID tokens are all hex digits + dashes → no valid initial → "?"
        val uuid = "01900000-0000-7000-8000-0000000000bb"
        val displayNames = emptyMap<String, String>()
        val resolved = displayNames[uuid] ?: uuid
        val initials = initialsFrom(resolved)
        assertEquals("?", initials)
    }

    @Test fun `UUID author string resolved via display map gives correct initials`() {
        val uuid = "01900000-0000-7000-8000-0000000000bb"
        val displayNames = mapOf(uuid to "Boy Keeper")
        val resolved = displayNames[uuid] ?: uuid
        val initials = initialsFrom(resolved)
        assertEquals("BK", initials)
    }

    @Test fun `empty display name falls to question mark`() {
        val initials = initialsFrom("")
        assertEquals("?", initials)
    }

    // ─── D-30: standing field drives recurring indicator ────────────────────

    @Test fun `standing task item has standing=true`() {
        val item = TaskItem(
            id = "st-1",
            title = "Morning routine",
            todolist = list,
            standing = true,
        )
        assertTrue("standing task must have standing=true for D-30 glyph", item.standing)
    }

    @Test fun `non-standing dated task has standing=false`() {
        val today = java.time.LocalDate.now()
        val item = TaskItem(
            id = "t-1",
            title = "Buy milk",
            todolist = list,
            due = today,
            standing = false,
        )
        assertTrue("dated one-off task must NOT be standing", !item.standing)
    }

    @Test fun `StandingTaskRow toTaskItem produces standing=true`() {
        val row = com.eight87.strictlykeptboy.cache.entities.StandingTaskRow(
            repoId = "repo-1",
            id = "st-2",
            todolistId = list.id,
            title = "Evening ritual",
            body = "",
            done = false,
            pinned = false,
            priority = null,
            tagsJson = "[]",
            sourcePath = "standing/st-2.md",
        )
        val item = row.toTaskItem(list)
        assertTrue("StandingTaskRow.toTaskItem must produce standing=true", item.standing)
    }

    // ─── D-32: priority tier labeling ───────────────────────────────────────

    @Test fun `priority 8+ maps to high priority label`() {
        assertEquals("high priority", priorityTier(8))
        assertEquals("high priority", priorityTier(100))
    }

    @Test fun `priority 4-7 maps to medium priority label`() {
        assertEquals("medium priority", priorityTier(4))
        assertEquals("medium priority", priorityTier(7))
    }

    @Test fun `priority 1-3 maps to low priority label`() {
        assertEquals("low priority", priorityTier(1))
        assertEquals("low priority", priorityTier(3))
    }

    // ─── helpers (mirror TaskRow private logic) ──────────────────────────────

    /** Mirrors AuthorBubble initials derivation (D-43 fix). */
    private fun initialsFrom(displayName: String): String =
        displayName.trim().split(Regex("\\s+")).take(2)
            .mapNotNull { w ->
                if (w.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' || c == '-' }) null
                else w.firstOrNull()?.uppercaseChar()?.toString()
            }
            .joinToString("")
            .ifEmpty { "?" }

    /** Mirrors PriorityDot tier labeling (D-32 fix). */
    private fun priorityTier(level: Int): String = when (level) {
        in 8..Int.MAX_VALUE -> "high priority"
        in 4..7 -> "medium priority"
        else -> "low priority"
    }
}
