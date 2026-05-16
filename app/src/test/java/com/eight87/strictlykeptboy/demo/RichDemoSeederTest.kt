package com.eight87.strictlykeptboy.demo

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Round 2.20 Phase B — covers the seeder happy path, idempotency, and
 * the `resetSeededFlag` re-seed hook.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RichDemoSeederTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var seeder: RichDemoSeeder
    private lateinit var parent: File

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences("test_rich_demo_${System.nanoTime()}",
            android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        seeder = RichDemoSeeder(ctx, prefs)
        parent = tmp.newFolder("seed-parent")
    }

    @Test fun `first call extracts every manifest entry`() = runTest {
        assertFalse(seeder.isSeeded())
        val result = seeder.seedIfNeeded(parent)
        val repoRoot = result.getOrThrow()
        assertEquals(File(parent, "rich-demo"), repoRoot)
        assertTrue(seeder.isSeeded())

        // Spot-check known files from the manifest.
        assertTrue(File(repoRoot, "AGENTS.md").isFile)
        assertTrue(File(repoRoot, "identity.toml").isFile)
        assertTrue(File(repoRoot, "mode.toml").isFile)
        assertTrue(File(repoRoot, "README.md").isFile)
        assertTrue(File(repoRoot, "calendars/cat-care/calendar.toml").isFile)

        // Marker must be consumed (deleted after symlink reconstruction).
        assertFalse(File(repoRoot, RichDemoSeeder.SYMLINK_MARKER).exists())
        // _manifest.txt itself is NOT extracted — it's the index, not content.
        assertFalse(File(repoRoot, "_manifest.txt").exists())

        // CLAUDE.md should be present (either symlink or copy fallback).
        val claudeMd = File(repoRoot, "CLAUDE.md")
        assertTrue("CLAUDE.md should exist", claudeMd.exists())
    }

    @Test fun `second call no-ops when already seeded`() = runTest {
        seeder.seedIfNeeded(parent).getOrThrow()
        val repoRoot = File(parent, "rich-demo")
        val agentsMd = File(repoRoot, "AGENTS.md")
        // Overwrite content; idempotent re-seed must NOT clobber.
        agentsMd.writeText("LOCAL-EDIT")

        val secondResult = seeder.seedIfNeeded(parent)
        assertNotNull(secondResult.getOrThrow())
        assertEquals("LOCAL-EDIT", agentsMd.readText())
    }

    @Test fun `resetSeededFlag re-enables re-seed`() = runTest {
        seeder.seedIfNeeded(parent).getOrThrow()
        assertTrue(seeder.isSeeded())
        seeder.resetSeededFlag()
        assertFalse(seeder.isSeeded())

        // Mutate a file, re-seed, mutation should be gone.
        val agentsMd = File(parent, "rich-demo/AGENTS.md")
        agentsMd.writeText("STALE")
        seeder.seedIfNeeded(parent).getOrThrow()
        assertTrue(seeder.isSeeded())
        assertFalse(agentsMd.readText() == "STALE")
    }
}
