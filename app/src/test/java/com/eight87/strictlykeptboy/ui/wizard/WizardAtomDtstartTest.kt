package com.eight87.strictlykeptboy.ui.wizard

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.store.EntityKind
import com.eight87.strictlykeptboy.store.FrontmatterReader
import com.eight87.strictlykeptboy.store.RecurrenceRule
// runBlocking instead of kotlinx.coroutines.test.runTest — see comment in
// TripScaffolderTest. Migratory `UncaughtExceptionsBeforeTest` canary.
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

/**
 * Phase 2.1.I.5 — atoms get spread across morning/midday/evening buckets
 * instead of stacking at 09:00.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WizardAtomDtstartTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `brush-teeth bucket is 07-00`() {
        assertEquals("07:00", TemplateRegistry.dtstartHmFor("brush-teeth"))
    }

    @Test fun `meds-pm bucket is 21-00`() {
        assertEquals("21:00", TemplateRegistry.dtstartHmFor("meds-pm"))
    }

    @Test fun `cardio-30min bucket is 17-00`() {
        assertEquals("17:00", TemplateRegistry.dtstartHmFor("cardio-30min"))
    }

    @Test fun `feed-am and feed-pm spread across day`() {
        assertEquals("07:00", TemplateRegistry.dtstartHmFor("feed-am"))
        assertEquals("18:00", TemplateRegistry.dtstartHmFor("feed-pm"))
    }

    @Test fun `unknown atom falls back to default 09-00`() {
        assertEquals("09:00", TemplateRegistry.dtstartHmFor("not-a-real-atom"))
    }

    @Test fun `scaffolded rules carry spread dtstarts`() = runBlocking {
        val draft = WizardDraft(
            alignment = Alignment.Submissive,
            lifestyle = Lifestyle.SingleStrict,
            roles = setOf(RoleId.SelfCare, RoleId.Health, RoleId.PetCare),
            displayName = "spread test",
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent"),
            draft = draft,
            author = AuthorIdentity("tester", "tester@example.com"),
            tzId = "Europe/Berlin",
        )
        val root = outcome.rootDir.toPath()
        // Walk every recurrence file; collect dtstart hours.
        val hours = mutableSetOf<String>()
        for ((_, calId) in outcome.calendarIds) {
            val dir = root.resolve("calendars/$calId/recurrences")
            if (!Files.isDirectory(dir)) continue
            Files.list(dir).use { stream ->
                stream.forEach { p ->
                    val text = String(Files.readAllBytes(p), Charsets.UTF_8)
                    val doc = FrontmatterReader.parse(text)
                    val kind = doc.frontmatter.getString("kind")
                    if (kind == EntityKind.Recurrence.tomlValue) {
                        val rule = RecurrenceRule.fromDoc(doc)
                        // dtstart is "yyyy-MM-ddTHH:mm:ss"
                        hours += rule.dtstart.substringAfter('T').substring(0, 5)
                    }
                }
            }
        }
        // At least three distinct HH:mm values across our 3 picked roles.
        assertTrue(
            "expected ≥3 distinct dtstart times across rules, got $hours",
            hours.size >= 3,
        )
        // brush-teeth must be at 07:00 specifically.
        assertTrue(
            "brush-teeth bucket landed on disk (07:00)",
            "07:00" in hours,
        )
    }
}
