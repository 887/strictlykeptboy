package com.eight87.strictlykeptboy.resolver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.1.B.1 — coverage for the calendars-first registry.
 *
 * Verifies overlay semantics: when `calendars/<id>/calendar.toml` is
 * present, its parsed fields win over the synthesized snapshot
 * defaults. When absent, the synthesized row passes through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarRegistryTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var ctx: Context
    private lateinit var repoStore: RepoStore

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val prefs = ctx.getSharedPreferences("cal_reg_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        repoStore = RepoStore.openForTest(prefs)
    }

    @After fun tearDown() { /* TempFolder auto-cleans. */ }

    private fun writeCalendarToml(root: Path, calId: String, body: String) {
        val dir = root.resolve("calendars/$calId")
        Files.createDirectories(dir)
        Files.write(
            dir.resolve("calendar.toml"),
            body.toByteArray(StandardCharsets.UTF_8),
        )
    }

    @Test fun overlaysDiskFieldsOntoSynthesizedSnapshot() = runBlocking {
        val repoRoot = tmp.newFolder("repo-a").toPath()
        repoStore.add(
            RepoConfig(
                repoId = "repo-a",
                displayName = "repo a",
                rootDir = repoRoot.toString(),
                authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
                colorSeed = 0x44ccaa,
            ),
        )
        writeCalendarToml(
            repoRoot, "cal-routine",
            """
            schema_version = 1
            id = "cal-routine"
            name = "Morning Routine"
            priority = 750
            emoji = "☀"
            routine = true
            routine_can_materialize = true
            active_toggle = false
            supersedes = ["cal-other"]
            """.trimIndent(),
        )

        val synthesizedSnap = MutableStateFlow(
            RepoSnapshot(
                repos = listOf(RepoSnapshot.RepoEntry(RepoRef("repo-a"), null)),
                calendars = listOf(
                    CalendarMeta(
                        ref = CalendarRef("cal-routine"),
                        repo = RepoRef("repo-a"),
                        displayName = "cal-routine", // synthesized fallback
                        priority = 500,
                    ),
                ),
                todolists = emptyList(),
            ),
        )
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val reg = CalendarRegistry(repoStore, synthesizedSnap, scope)
        val cals = withTimeout(5_000) { reg.state.first { it.isNotEmpty() } }
        val cal = cals.first { it.ref.id == "cal-routine" }
        assertEquals("Morning Routine", cal.displayName)
        assertEquals(750, cal.priority)
        assertEquals(false, cal.activeToggle)
        assertEquals(listOf(CalendarRef("cal-other")), cal.supersedes)
        // colorSeed falls back to repo-level seed when calendar.toml lacks it.
        assertEquals(0x44ccaa, cal.colorSeed)
        scope.cancel()
    }

    @Test fun passesThroughSynthesizedCalendarsWithoutDisk() = runBlocking {
        val repoRoot = tmp.newFolder("repo-b").toPath()
        repoStore.add(
            RepoConfig(
                repoId = "repo-b",
                displayName = "repo b",
                rootDir = repoRoot.toString(),
                authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
            ),
        )
        // No calendar.toml on disk.
        val synthesizedSnap = MutableStateFlow(
            RepoSnapshot(
                repos = listOf(RepoSnapshot.RepoEntry(RepoRef("repo-b"), null)),
                calendars = listOf(
                    CalendarMeta(
                        ref = CalendarRef("cal-only-in-cache"),
                        repo = RepoRef("repo-b"),
                        displayName = "cal-only-in-cache",
                        priority = 500,
                    ),
                ),
                todolists = emptyList(),
            ),
        )
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val reg = CalendarRegistry(repoStore, synthesizedSnap, scope)
        val cals = withTimeout(5_000) { reg.state.first { it.isNotEmpty() } }
        val cal = cals.firstOrNull { it.ref.id == "cal-only-in-cache" }
        assertNotNull(cal)
        assertEquals("cal-only-in-cache", cal!!.displayName)
        assertEquals(500, cal.priority)
        scope.cancel()
    }

    @Test fun overlaysActiveWindowsHoursAndColorSeed() = runBlocking {
        val repoRoot = tmp.newFolder("repo-c").toPath()
        repoStore.add(
            RepoConfig(
                repoId = "repo-c",
                displayName = "repo c",
                rootDir = repoRoot.toString(),
                authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
            ),
        )
        writeCalendarToml(
            repoRoot, "cal-work",
            """
            schema_version = 1
            id = "cal-work"
            name = "Work"
            priority = 600
            color_seed = 4900691

            [[active_windows]]
            from = 2026-06-01
            to = 2026-06-30

            [[active_hours]]
            day = "MON"
            from = "09:00"
            to = "17:00"
            [[active_hours]]
            day = "FRI"
            from = "09:00"
            to = "13:00"
            """.trimIndent(),
        )
        val synthesizedSnap = MutableStateFlow(
            RepoSnapshot(
                repos = listOf(RepoSnapshot.RepoEntry(RepoRef("repo-c"), null)),
                calendars = emptyList(),
                todolists = emptyList(),
            ),
        )
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val reg = CalendarRegistry(repoStore, synthesizedSnap, scope)
        val cals = withTimeout(5_000) { reg.state.first { it.isNotEmpty() } }
        val cal = cals.first { it.ref.id == "cal-work" }
        assertEquals(4900691, cal.colorSeed)
        assertEquals(1, cal.activeWindows.size)
        assertEquals(java.time.LocalDate.of(2026, 6, 1), cal.activeWindows[0].start)
        assertEquals(java.time.LocalDate.of(2026, 6, 30), cal.activeWindows[0].endInclusive)
        assertEquals(2, cal.activeHours.size)
        assertEquals(java.time.DayOfWeek.MONDAY, cal.activeHours[0].day)
        assertEquals(java.time.LocalTime.of(9, 0), cal.activeHours[0].from)
        assertEquals(java.time.LocalTime.of(17, 0), cal.activeHours[0].to)
        scope.cancel()
    }

    @Test fun emptyRepoSetEmitsEmpty() = runBlocking {
        val synthesizedSnap = MutableStateFlow(
            RepoSnapshot(emptyList(), emptyList(), emptyList()),
        )
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val reg = CalendarRegistry(repoStore, synthesizedSnap, scope)
        val cals = withTimeout(2_000) { reg.state.first() }
        assertTrue(cals.isEmpty())
        scope.cancel()
    }
}
