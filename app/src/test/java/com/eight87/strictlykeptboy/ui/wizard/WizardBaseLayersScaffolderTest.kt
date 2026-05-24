package com.eight87.strictlykeptboy.ui.wizard

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.store.CalendarActivityConfig
import com.eight87.strictlykeptboy.store.SupersedenceConfig
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Walkthrough-2 — verify the wizard's first-paint scaffold meets the
 * "demo-comparable density" bar (≥ 1 base calendar, ≥ 10 recurrences,
 * ≥ 3 sample tasks, ≥ 1 sample event) and that the additive base /
 * holidays / vacation calendars round-trip through the existing TOML
 * + supersedence codecs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WizardBaseLayersScaffolderTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `default wizard scaffold lands demo-comparable density`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.Submissive,
            lifestyle = Lifestyle.SingleStrict,
            roles = setOf(RoleId.SelfCare, RoleId.Work, RoleId.Kink),
            displayName = "density-test",
        ).normalize()

        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent"),
            draft = draft,
            author = AuthorIdentity("tester", "tester@example.com"),
            tzId = "Europe/London",
        )

        val enriched = outcome.baseLayers
        assertNotNull("base-layers outcome present", enriched)
        enriched!!

        // Density bar (per walkthrough-2 spec).
        assertTrue(
            "≥ 10 recurrences total — got ${outcome.recurrencesWritten}",
            outcome.recurrencesWritten >= 10,
        )
        assertTrue(
            "≥ 3 sample tasks added — got ${enriched.sampleTasksWritten}",
            enriched.sampleTasksWritten >= 3,
        )
        assertTrue(
            "≥ 1 sample event added — got ${enriched.sampleEventsWritten}",
            enriched.sampleEventsWritten >= 1,
        )

        // Base + holidays + vacation calendars all materialized on disk.
        val root = outcome.rootDir.toPath()
        assertTrue(
            "base daily-scaffold calendar.toml exists",
            Files.exists(root.resolve("calendars/${enriched.baseCalendarId}/calendar.toml")),
        )
        assertTrue(
            "public-holidays calendar.toml exists",
            Files.exists(root.resolve("calendars/${enriched.holidaysCalendarId}/calendar.toml")),
        )
        assertTrue(
            "vacation calendar.toml exists",
            Files.exists(root.resolve("calendars/${enriched.vacationCalendarId}/calendar.toml")),
        )
    }

    @Test fun `base calendar is kind=base and non_superseable`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.UnalignedPrivate,
            roles = setOf(RoleId.SelfCare, RoleId.Workout),
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent2"),
            draft = draft,
        )
        val root = outcome.rootDir.toPath()
        val baseId = outcome.baseLayers!!.baseCalendarId
        val baseToml = String(
            Files.readAllBytes(root.resolve("calendars/$baseId/calendar.toml")),
            StandardCharsets.UTF_8,
        )
        assertTrue("kind = base", baseToml.contains("kind = \"base\""))
        assertTrue(
            "non_superseable true on base layer",
            baseToml.contains("non_superseable = true"),
        )
        // Round-trip via the supersedence codec.
        val cfg = SupersedenceConfig.readFrom(
            root.resolve("calendars/$baseId/calendar.toml"),
        )
        assertTrue(cfg.nonSuperseable)
    }

    @Test fun `holidays calendar supersedes every wizard role calendar`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.Submissive,
            lifestyle = Lifestyle.SingleStrict,
            roles = setOf(RoleId.SelfCare, RoleId.Work, RoleId.Kink, RoleId.Workout),
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent3"),
            draft = draft,
        )
        val root = outcome.rootDir.toPath()
        val holidaysId = outcome.baseLayers!!.holidaysCalendarId
        val cfg = SupersedenceConfig.readFrom(
            root.resolve("calendars/$holidaysId/calendar.toml"),
        )
        // Every role calendar id is in the supersedes list.
        val roleIds = outcome.calendarIds.values.toSet()
        assertEquals(roleIds, cfg.supersedes.toSet())
        assertTrue("non_superseable on holidays", cfg.nonSuperseable)
    }

    @Test fun `vacation calendar is kind=base with priority 900 and supersedes roles`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.Submissive,
            roles = setOf(RoleId.SelfCare, RoleId.Work),
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent4"),
            draft = draft,
        )
        val root = outcome.rootDir.toPath()
        val vacationId = outcome.baseLayers!!.vacationCalendarId
        val vacationTomlPath = root.resolve("calendars/$vacationId/calendar.toml")
        val text = String(Files.readAllBytes(vacationTomlPath), StandardCharsets.UTF_8)
        assertTrue("kind = base", text.contains("kind = \"base\""))
        assertTrue("priority = 900", text.contains("priority = 900"))
        val cfg = SupersedenceConfig.readFrom(vacationTomlPath)
        val roleIds = outcome.calendarIds.values.toSet()
        assertEquals(roleIds, cfg.supersedes.toSet())
        // CalendarActivityConfig should also parse cleanly (color_seed).
        val activity = CalendarActivityConfig.readFrom(vacationTomlPath)
        assertNotNull("color_seed parsed", activity)
    }

    @Test fun `base layer recurrences include sleep block`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.UnalignedPrivate,
            roles = setOf(RoleId.SelfCare),
        ).normalize()
        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent5"),
            draft = draft,
        )
        val root = outcome.rootDir.toPath()
        val baseId = outcome.baseLayers!!.baseCalendarId
        val recurDir = root.resolve("calendars/$baseId/recurrences")
        val recurFiles = Files.list(recurDir).use { it.toList() }
        assertTrue("at least 3 base recurrences (got ${recurFiles.size})", recurFiles.size >= 3)
        // One of them must be the sleep block.
        val anySleep = recurFiles.any { p ->
            val txt = String(Files.readAllBytes(p), StandardCharsets.UTF_8)
            txt.contains("title = \"Sleep\"")
        }
        assertTrue("sleep block present", anySleep)
    }

    @Test fun `re-running materialize is idempotent`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.Submissive,
            roles = setOf(RoleId.SelfCare, RoleId.Workout),
        ).normalize()
        val parent = tmp.newFolder("parent6")
        val first = WizardScaffolder.materialize(parentDir = parent, draft = draft)
        // Manual re-run of the enrichment against the same root must not
        // double-write anything.
        val secondEnrichment = WizardBaseLayersScaffolder.materialize(
            rootDir = first.rootDir,
            identityId = first.authorIdentity.email, // arbitrary; second run uses string only on new files
            tzId = "UTC",
            roleCalendarIds = first.calendarIds.values,
            onboardingTodolistId = first.todolistId,
        )
        assertEquals(0, secondEnrichment.recurrencesWritten)
        assertEquals(0, secondEnrichment.sampleTasksWritten)
        assertEquals(0, secondEnrichment.sampleEventsWritten)
    }
}
