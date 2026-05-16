package com.eight87.strictlykeptboy.demo

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/**
 * Round 2.20 Phase B — verifies `CLAUDE.md → AGENTS.md` symlink
 * reconstruction. On the host JVM (Robolectric tmp dir on a normal
 * Linux filesystem) symlinks ARE supported, so we assert the link
 * exists. The copy-fallback path is covered structurally: same
 * content should be readable through CLAUDE.md regardless of which
 * branch fires, so we also assert content equality.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RichDemoSeederSymlinkTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun `CLAUDE_md exists and matches AGENTS_md after seed`() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences("test_symlink_${System.nanoTime()}",
            android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val seeder = RichDemoSeeder(ctx, prefs)
        val parent = tmp.newFolder("seed-parent")

        val repoRoot = seeder.seedIfNeeded(parent).getOrThrow().toPath()
        val claudeMd = repoRoot.resolve("CLAUDE.md")
        val agentsMd = repoRoot.resolve("AGENTS.md")

        assertTrue("CLAUDE.md must exist", Files.exists(claudeMd))
        assertTrue("AGENTS.md must exist", Files.exists(agentsMd))

        // Either it's a symlink or a copy; either way readContent
        // through CLAUDE.md should equal AGENTS.md's content.
        assertEquals(
            String(Files.readAllBytes(agentsMd)),
            String(Files.readAllBytes(claudeMd)),
        )

        // On normal POSIX filesystems Robolectric's tmp dir supports
        // symlinks, so the link branch should have won. Assert that
        // here — if it ever fails on a CI runner with a weird FS,
        // we'll learn about it and can relax this assertion.
        assertTrue(
            "CLAUDE.md should be a symbolic link on this filesystem",
            Files.isSymbolicLink(claudeMd),
        )
        assertEquals(File("AGENTS.md").toPath(), Files.readSymbolicLink(claudeMd))
    }
}
