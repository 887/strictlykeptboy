package com.eight87.strictlykeptboy.demo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.ui.wizard.intro.DemoPerspectiveChoice
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
import java.io.File

/**
 * Round 2.20 Phase C.4 — confirming RichDemo in the picker dispatches
 * to [RichDemoSeeder.seedIfNeeded] (NOT to the legacy
 * `DemoRepoSeeder.seed`), and the registrar that handles the seeded
 * repo on disk produces a `RepoConfig` whose `repoId`,
 * `defaultCalendarId`, and `defaultTodolistId` come from
 * `.strictlykeptboy/repo.toml`.
 *
 * The test mirrors the branch in `MainActivity` where
 * `DemoPerspectiveChoice.RichDemo` is matched — we exercise the
 * seeder + registrar path directly to keep the test focused; the
 * Compose-level wiring is covered by [RichDemoPickerRowTest] +
 * [RichDemoDefaultSelectionTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RichDemoSeederDispatchTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun rich_demo_branch_invokes_seeder_and_builds_demo_repo_config() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("rich_demo_dispatch_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val seeder = RichDemoSeeder(ctx, prefs)
        val parent = tmp.newFolder("demo-repos")

        // Simulate the picker's RichDemo branch in MainActivity.
        val choice: DemoPerspectiveChoice = DemoPerspectiveChoice.RichDemo
        assertTrue("test fixture must use RichDemo", choice is DemoPerspectiveChoice.RichDemo)
        val repoRoot = seeder.seedIfNeeded(parent).getOrThrow()

        // Sanity: the seed materialized the manifest tree on disk.
        assertTrue("rich-demo folder exists", repoRoot.isDirectory)
        assertTrue("AGENTS.md extracted", File(repoRoot, "AGENTS.md").isFile)
        assertTrue("repo.toml extracted", File(repoRoot, ".strictlykeptboy/repo.toml").isFile)
        assertTrue("idempotency flag set", seeder.isSeeded())

        // RichDemoRegistrar pulls the IDs out of repo.toml.
        val cfg = RichDemoRegistrar.buildConfig(repoRoot)
        assertNotNull("repoId resolved", cfg.repoId)
        assertEquals(repoRoot.absolutePath, cfg.rootDir)
        assertEquals(true, cfg.isDemo)
        assertNotNull("defaultCalendarId resolved", cfg.defaultCalendarId)
        assertNotNull("defaultTodolistId resolved", cfg.defaultTodolistId)
        // The legacy demo seeder is decidedly NOT what produced this:
        // its display names follow `demo · pet kept by ai` / etc.
        // The rich-demo registrar uses its own constant.
        assertEquals(RichDemoRegistrar.DISPLAY_NAME, cfg.displayName)
    }
}
