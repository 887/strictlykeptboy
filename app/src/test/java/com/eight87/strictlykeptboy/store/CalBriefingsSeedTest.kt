package com.eight87.strictlykeptboy.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CalBriefingsSeedTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun seedCreatesCalendarAndTwoRules() {
        val root = tmp.newFolder("repo")
        val outcome = CalBriefingsSeed.seed(rootDir = root)
        assertTrue(outcome.wasNewlyCreated)
        assertEquals("cal-briefings", outcome.calendarId)

        val calToml = root.toPath().resolve("calendars/cal-briefings/calendar.toml")
        assertTrue(java.nio.file.Files.isRegularFile(calToml))

        val text = java.nio.file.Files.readAllBytes(calToml).toString(Charsets.UTF_8)
        assertTrue("calendar.toml must mark auto_generated", text.contains("auto_generated"))
        assertTrue(text.contains("Briefings"))

        // Two recurrence-rule files exist under recurrences/.
        val rulesDir = root.toPath().resolve("calendars/cal-briefings/recurrences")
        val rules = java.nio.file.Files.walk(rulesDir).use { stream ->
            stream.filter { java.nio.file.Files.isRegularFile(it) && it.toString().endsWith(".md") }
                .toList()
        }
        assertEquals(2, rules.size)
    }

    @Test fun seedIsIdempotent() {
        val root = tmp.newFolder("repo")
        val first = CalBriefingsSeed.seed(rootDir = root)
        assertTrue(first.wasNewlyCreated)

        val second = CalBriefingsSeed.seed(rootDir = root)
        assertFalse(second.wasNewlyCreated)
        assertEquals(first.calendarId, second.calendarId)

        // Still only 2 events; the second run did not stomp.
        val rulesDir = root.toPath().resolve("calendars/cal-briefings/recurrences")
        val rules = java.nio.file.Files.walk(rulesDir).use { stream ->
            stream.filter { java.nio.file.Files.isRegularFile(it) && it.toString().endsWith(".md") }
                .toList()
        }
        assertEquals(2, rules.size)
    }
}
