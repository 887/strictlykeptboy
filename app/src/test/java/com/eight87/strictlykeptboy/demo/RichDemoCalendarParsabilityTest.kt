package com.eight87.strictlykeptboy.demo

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.store.ParseResult
import com.eight87.strictlykeptboy.store.RepoScanner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
 * Round 2.20 Phase D.4 — load every `.md`/`.toml` file from the extracted
 * rich-demo repo through [RepoScanner] and assert zero parse failures.
 *
 * This is the load-bearing test for Phase D: if any file fails to parse
 * the resolver pipeline can't surface it. Catches the kind of authoring
 * mistakes that compile (markdown is forgiving) but break downstream
 * (missing required frontmatter fields, malformed RRULEs, etc.).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RichDemoCalendarParsabilityTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var seeder: RichDemoSeeder
    private lateinit var parent: File

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences(
            "test_rich_demo_parsability_${System.nanoTime()}",
            android.content.Context.MODE_PRIVATE,
        )
        prefs.edit().clear().commit()
        seeder = RichDemoSeeder(ctx, prefs)
        parent = tmp.newFolder("parsability-parent")
    }

    @Test fun `every authored rich-demo file parses cleanly`() = runTest {
        val repoRoot = seeder.seedIfNeeded(parent).getOrThrow()
        val results = RepoScanner.scanAll(repoRoot)

        assertTrue(
            "expected RepoScanner to walk a non-empty asset tree (got ${results.size})",
            results.isNotEmpty(),
        )

        val failures = results.filterIsInstance<ParseResult.Failed>()
        val rendered = failures.joinToString("\n") { f ->
            val rel = repoRoot.toPath().relativize(f.sourcePath).toString().replace('\\', '/')
            "$rel — ${f.error}"
        }
        assertEquals(
            "Phase A authoring drift — files failed to parse:\n$rendered",
            0,
            failures.size,
        )
    }
}
