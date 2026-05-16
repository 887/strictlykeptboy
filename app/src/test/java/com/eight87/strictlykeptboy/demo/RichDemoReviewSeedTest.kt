package com.eight87.strictlykeptboy.demo

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.ui.reviews.ReviewFeedReader
import kotlinx.coroutines.test.runTest
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
 * Round 2.23.1 Fix 2 — the bundled rich-demo repo must contain enough
 * `reviews/<sha>/reviewable_change.md` entries that the Reviews
 * destination feels populated when the user picks "Kept Life" from
 * the wizard. The earlier rich-demo shipped a single review entry
 * (per the original Phase DDD.2 schema demo); this test pins the
 * follow-up that grows the set to a meaningful timeline.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RichDemoReviewSeedTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var seeder: RichDemoSeeder
    private lateinit var parent: File

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences(
            "test_rich_demo_reviews_${System.nanoTime()}",
            android.content.Context.MODE_PRIVATE,
        )
        prefs.edit().clear().commit()
        seeder = RichDemoSeeder(ctx, prefs)
        parent = tmp.newFolder("seed-parent")
    }

    @Test fun `rich demo seeds at least twelve review entries`() = runTest {
        val repoRoot = seeder.seedIfNeeded(parent).getOrThrow()
        val entries = ReviewFeedReader.scan(listOf(repoRoot.toPath()))
        val reviewsDir = File(repoRoot, "reviews")
        val onDisk = reviewsDir.listFiles()?.toList().orEmpty()
        val msg = "entries=${entries.size} onDisk=${onDisk.size} dirs=${onDisk.map { it.name }} " +
            "blankTimestamps=${entries.count { it.timestamp.isBlank() }} " +
            "blankSummaries=${entries.count { it.autoSummary.isBlank() }}"
        org.junit.Assert.assertTrue(msg, entries.size >= 12)
        // At least 12 entries must carry a non-blank timestamp / summary;
        // the pre-existing legacy demo review (`0190d4ff5d40673abc`)
        // predates the timestamp field and we don't migrate it here.
        org.junit.Assert.assertTrue(msg, entries.count { it.timestamp.isNotBlank() } >= 12)
        org.junit.Assert.assertTrue(msg, entries.all { it.autoSummary.isNotBlank() })
    }
}
