package com.eight87.strictlykeptboy.ui.wizard

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.store.EntityKind
import com.eight87.strictlykeptboy.store.FrontmatterReader
import com.eight87.strictlykeptboy.store.RecurrenceRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

/**
 * Phase 2.1.I.6 — inverted-default atoms (brush-teeth, meds-am, shower,
 * feed-am, etc.) get tagged with `inverted = true` in the emitted
 * RecurrenceRule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WizardInvertedAtomTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `brush-teeth is inverted, deep-work-am is not`() {
        assertTrue(TemplateRegistry.isInvertedAtom("brush-teeth"))
        assertTrue(TemplateRegistry.isInvertedAtom("meds-am"))
        assertTrue(TemplateRegistry.isInvertedAtom("meds-pm"))
        assertTrue(TemplateRegistry.isInvertedAtom("shower"))
        assertTrue(TemplateRegistry.isInvertedAtom("feed-am"))
        assertFalse(TemplateRegistry.isInvertedAtom("deep-work-am"))
        assertFalse(TemplateRegistry.isInvertedAtom("cardio-30min"))
    }

    @Test fun `emitted RecurrenceRule for brush-teeth carries inverted=true`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.Submissive,
            lifestyle = Lifestyle.SingleStrict,
            roles = setOf(RoleId.SelfCare),
            displayName = "inverted test",
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent"),
            draft = draft,
            author = AuthorIdentity("tester", "tester@example.com"),
            tzId = "Europe/Berlin",
        )
        val root = outcome.rootDir.toPath()
        val selfCareCalId = outcome.calendarIds[RoleId.SelfCare]!!
        val dir = root.resolve("calendars/$selfCareCalId/recurrences")
        var foundBrushTeeth = false
        Files.list(dir).use { stream ->
            stream.forEach { p ->
                val text = String(Files.readAllBytes(p), Charsets.UTF_8)
                val doc = FrontmatterReader.parse(text)
                if (doc.frontmatter.getString("kind") != EntityKind.Recurrence.tomlValue) return@forEach
                val rule = RecurrenceRule.fromDoc(doc)
                if (rule.title.equals("Brush teeth", ignoreCase = true)) {
                    foundBrushTeeth = true
                    assertTrue("brush-teeth should have inverted=true", rule.inverted)
                }
            }
        }
        assertTrue("brush-teeth recurrence file was emitted", foundBrushTeeth)
    }
}
