package com.eight87.strictlykeptboy.ui.wizard

import com.eight87.strictlykeptboy.git.AuthorIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WizardScaffolderTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `submissive plus strictly-kept materializes calendars and recurrences`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.Submissive,
            lifestyle = Lifestyle.SingleStrict,
            roles = setOf(RoleId.SelfCare, RoleId.Workout, RoleId.Kink),
            displayName = "test repo",
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent"),
            draft = draft,
            author = AuthorIdentity("tester", "tester@example.com"),
            tzId = "Europe/Berlin",
        )
        assertEquals(3, outcome.calendarIds.size)
        assertTrue(RoleId.Kink in outcome.calendarIds)
        // Files exist on disk
        val root = outcome.rootDir.toPath()
        for ((role, calId) in outcome.calendarIds) {
            assertTrue(
                "calendar.toml exists for ${role.id}",
                Files.exists(root.resolve("calendars/$calId/calendar.toml")),
            )
            val recurDir = root.resolve("calendars/$calId/recurrences")
            assertTrue("recurrences dir for ${role.id}", Files.isDirectory(recurDir))
            val rules = Files.list(recurDir).use { it.toList() }
            assertTrue("at least one recurrence for ${role.id}", rules.isNotEmpty())
        }
        assertEquals(5, outcome.standingTasksWritten)
        // identity.toml has alignment + tone
        val idToml = String(Files.readAllBytes(root.resolve("identity.toml")), Charsets.UTF_8)
        assertTrue(idToml.contains("submissive"))
        assertTrue(idToml.contains("[tone]"))
        assertTrue(idToml.contains("[alignment]"))
    }

    @Test fun `unaligned-private writes no kink calendar`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.UnalignedPrivate,
            roles = setOf(RoleId.SelfCare, RoleId.Kink, RoleId.Workout),
            lifestyle = Lifestyle.SingleFree,
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent2"),
            draft = draft,
            author = AuthorIdentity("tester", "tester@example.com"),
        )
        assertFalse("no kink role", RoleId.Kink in outcome.calendarIds)
        assertTrue("workout still present", RoleId.Workout in outcome.calendarIds)
        assertTrue("self-care always there", RoleId.SelfCare in outcome.calendarIds)
    }

    @Test fun `phone-only materializes a single initial commit`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.UnalignedPrivate,
            roles = setOf(RoleId.SelfCare),
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent3"),
            draft = draft,
            author = AuthorIdentity("tester", "tester@example.com"),
        )
        // .git exists
        assertTrue(outcome.rootDir.resolve(".git").exists())
    }

    @Test fun `praise term lands in identity-toml`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.Submissive,
            praiseTerms = listOf("good kitten", "sweet thing"),
            roles = setOf(RoleId.SelfCare),
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent4"),
            draft = draft,
        )
        val idToml = String(Files.readAllBytes(outcome.rootDir.toPath().resolve("identity.toml")), Charsets.UTF_8)
        assertTrue("primary praise term", idToml.contains("good kitten"))
        // Phase 2.1.J.2 — canonical key is `alt_terms` under `[praise]`,
        // matching IdentityTomlCodec. The pre-2.1.J text-concat appendix
        // wrote `[praise.alternates]` — the codec is now the single
        // writer, so the canonical key is the assertion.
        assertTrue("alt_terms array present", idToml.contains("alt_terms"))
        assertTrue("alternate term present", idToml.contains("sweet thing"))
    }
}
