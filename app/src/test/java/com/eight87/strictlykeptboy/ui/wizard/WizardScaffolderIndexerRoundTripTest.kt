package com.eight87.strictlykeptboy.ui.wizard

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.cache.Indexer
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Walkthrough-2 / W2-B-1 + W2-U-1 regression — after the wizard scaffolds
 * a fresh repo, re-opening the GitRepo and running `Indexer.fullScan`
 * MUST populate Room with the on-disk standing tasks, dated tasks and
 * recurrence rules. Before the MainActivity fix, the Tasks pane +
 * Schedule rendered empty because nothing kicked an index after the
 * scaffolder's initial commit.
 *
 * This guard simulates the exact post-wizard flow MainActivity now runs
 * inside `onWizardScaffold` (see the `// W2-B-1 / W2-U-1` block).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WizardScaffolderIndexerRoundTripTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: CacheDatabase

    @Before fun setUp() {
        db = CacheDatabase.openInMemoryWithDriver(
            ApplicationProvider.getApplicationContext(),
            androidx.sqlite.driver.bundled.BundledSQLiteDriver(),
        )
    }

    @After fun tearDown() { db.close() }

    @Test fun `wizard scaffold round-trips standing tasks and recurrences into Room`() = runTest {
        val draft = WizardDraft(
            alignment = Alignment.Submissive,
            lifestyle = Lifestyle.SingleStrict,
            roles = setOf(RoleId.SelfCare, RoleId.Work),
            displayName = "round-trip",
        ).normalize()

        val outcome = WizardScaffolder.materialize(
            parentDir = tmp.newFolder("parent"),
            draft = draft,
            author = AuthorIdentity("tester", "tester@example.com"),
            tzId = "Europe/London",
        )

        // Mirror MainActivity onWizardScaffold: re-open the freshly-
        // committed repo and run a full index. The scaffolder closed its
        // own GitRepo handle after the initial commit.
        val gitRepo = GitRepo.open(
            rootDir = outcome.rootDir,
            repoId = outcome.repoId,
            remotes = emptyList(),
            primaryRemote = null,
            authorIdentity = outcome.authorIdentity,
        )
        val stats = Indexer(db).fullScan(outcome.repoId, gitRepo)

        // Indexer touched at least the entities the wizard wrote
        // (5 onboarding standing + 5 sample standing + 1 dated task +
        // per-role recurrences + 3 base recurrences + 4 holidays +
        // 2 briefings + 1 sample event ⇒ comfortably ≥ 15).
        assertTrue("indexer touched something — got ${stats.touched}", stats.touched >= 15)

        // The full Tasks pane source — `cacheDatabase.tasks().listAll` +
        // `standingTasks().listAll` — is what MainActivity reads. If
        // either of these returns empty after a wizard finish, the
        // Tasks → All pane stays blank (W2-B-1).
        val standingRows = db.standingTasks().listAll(outcome.repoId)
        assertTrue(
            "standing tasks landed in Room — got ${standingRows.size}",
            standingRows.size >= 5,
        )
        val datedTaskRows = db.tasks().listAll(outcome.repoId)
        assertTrue(
            "dated tasks landed in Room — got ${datedTaskRows.size}",
            datedTaskRows.isNotEmpty(),
        )

        // SourcesPublisher reads `recurrenceRules().listAll` — empty
        // here = Schedule Day view shows the empty state despite 16
        // rules on disk (W2-U-1).
        val ruleRows = db.recurrenceRules().listAll(outcome.repoId)
        assertTrue(
            "recurrence rules landed in Room — got ${ruleRows.size}",
            ruleRows.size >= 10,
        )

        // Sanity: at least the sample one-off event is present.
        val eventRows = db.events().listAll(outcome.repoId)
        assertTrue(
            "sample event landed in Room — got ${eventRows.size}",
            eventRows.isNotEmpty(),
        )

        gitRepo.close()
    }
}
