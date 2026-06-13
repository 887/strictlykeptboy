package com.eight87.strictlykeptboy.ui.schedule

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger

/**
 * D-8 fix regression test — verifies that [EventCreateController.onEventWritten]
 * is invoked after [EventCreateController.confirmFreeForm] successfully writes
 * and commits a new event.
 *
 * Root cause of D-8: after `commitAll` the Room cache was never re-indexed,
 * so the new event never reached [SourcesPublisher] → the schedule renderer.
 * The fix wires [EventCreateController.onEventWritten] in MainActivity to call
 * [com.eight87.strictlykeptboy.cache.Indexer.incrementalScan].
 *
 * This test verifies the hook fires by seeding a real local git repo (so
 * `openRepo` succeeds and the commit path runs), then asserting the lambda
 * was called. [GitRepoRegistry] is populated with the seeded repo so
 * `EventCreateController.openRepo` finds it on the first try.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EventCreateControllerOnEventWrittenTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val now: OffsetDateTime = OffsetDateTime.parse("2026-06-13T12:00:00+02:00")
    private val repoId = "hook-test-repo"
    private val authorIdentity = AuthorIdentity("Test Author", "test@local")

    private fun makePrefs(): EventCreatePrefs {
        val sp = ctx.getSharedPreferences("ecc_hook_test_v2", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        return EventCreatePrefs.openForTest(sp)
    }

    @After
    fun tearDown() {
        // Remove the seeded repo from the global registry so other tests
        // are not affected by a stale handle.
        GitRepoRegistry.clear()
    }

    /**
     * D-8 regression: [EventCreateController.onEventWritten] must be
     * invoked after a successful free-form event write + commit so the
     * caller (MainActivity) can trigger the Room cache re-index.
     */
    @Test
    fun `onEventWritten fires after confirmFreeForm writes an event`() = runBlocking {
        val root = tmp.newFolder()

        // Seed a local git repo so commitAll can land a real commit.
        val gitRepo = GitRepo.initLocalOnly(
            rootDir = root,
            repoId = repoId,
            authorIdentity = authorIdentity,
        )
        GitRepoRegistry.put(gitRepo)

        val cfg = RepoConfig(
            repoId = repoId,
            displayName = "Hook test",
            rootDir = root.absolutePath,
            defaultCalendarId = "cal-hook-test",
            authorIdentity = authorIdentity,
        )

        val hookCallCount = AtomicInteger(0)

        val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller = EventCreateController(
            scope = scope,
            prefs = makePrefs(),
            context = ctx,
            activeRepoProvider = { cfg },
            calendarOptionsProvider = {
                listOf(
                    CalendarOption(
                        id = "cal-hook-test",
                        displayName = "Hook test cal",
                        repoDisplayName = null,
                    ),
                )
            },
        )
        controller.onEventWritten = { _, _ -> hookCallCount.incrementAndGet() }

        // Open the sheet and set a valid draft.
        controller.openSheet(defaultStart = now, defaultDurationMinutes = 30)
        controller.setDraft(
            EventDraft(
                title = "Hook smoke event",
                start = now,
                end = now.plusMinutes(30),
                calendarId = "cal-hook-test",
            ),
        )

        // confirmFreeForm dispatches onto Dispatchers.IO via scope.launch;
        // poll until the hook fires (or timeout after 10s).
        controller.confirmFreeForm()
        withTimeout(10_000L) {
            while (hookCallCount.get() == 0) {
                delay(50)
            }
        }

        assertEquals(
            "onEventWritten must be called exactly once after a successful write",
            1,
            hookCallCount.get(),
        )
    }
}
