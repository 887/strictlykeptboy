package com.eight87.strictlykeptboy.demo

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.store.CalendarActivityConfig
import com.eight87.strictlykeptboy.store.TomlReader
import kotlinx.coroutines.test.runTest
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
 * Round 2.25 follow-up — the bundled rich-demo repo must give every
 * calendar a non-null `colorSeed` so the day grid renders distinct
 * per-calendar colors instead of all-uniform-green (the user's
 * 2026-05-17 feedback was "all the stuff in the demo calendars is
 * still green and has no color prepicked dumb demo data").
 *
 * The fix lives in two places:
 *  - [CalendarActivityConfig.read] now accepts `color = "#hex"` as a
 *    fallback when `color_seed = <int>` is absent.
 *  - Every demo `calendar.toml` ships an explicit `color_seed` from
 *    the D-2.21.b 12-swatch palette.
 *
 * This test pins both: each demo calendar's parsed [CalendarActivityConfig]
 * has a non-null colorSeed, AND the set of seeds is fully distinct
 * (no accidental palette collisions).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RichDemoColorSeedTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var seeder: RichDemoSeeder
    private lateinit var parent: File

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = ctx.getSharedPreferences(
            "test_rich_demo_colors_${System.nanoTime()}",
            android.content.Context.MODE_PRIVATE,
        )
        prefs.edit().clear().commit()
        seeder = RichDemoSeeder(ctx, prefs)
        parent = tmp.newFolder("seed-parent")
    }

    @Test fun `every demo calendar resolves to a non null colorSeed`() = runTest {
        val repoRoot = seeder.seedIfNeeded(parent).getOrThrow()
        val calendarsDir = File(repoRoot, "calendars")
        val calDirs = calendarsDir.listFiles { f -> f.isDirectory }?.toList().orEmpty()
        assertTrue("expected demo calendars under $calendarsDir", calDirs.size >= 11)

        val seedsByName = mutableMapOf<String, Int>()
        for (dir in calDirs) {
            val tomlFile = File(dir, "calendar.toml")
            assertTrue("missing calendar.toml in ${dir.name}", tomlFile.exists())
            val cfg = CalendarActivityConfig.read(TomlReader.parse(tomlFile.readText()))
            assertNotNull("colorSeed null for ${dir.name}", cfg.colorSeed)
            seedsByName[dir.name] = cfg.colorSeed!!
        }
        // All seeds distinct — no accidental palette collisions.
        val distinct = seedsByName.values.toSet()
        assertTrue(
            "colorSeeds must be distinct across demo calendars: $seedsByName",
            distinct.size == seedsByName.size,
        )
    }
}
