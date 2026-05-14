package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.CommitResult
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.store.IdentityTomlCodec
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 2.1.J — IdentityPrefs round-trips edits through identity.toml
 * on the active repo and commits via GitRepoRegistry.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdentityTomlRoundTripTest {

    @get:Rule val tmp = TemporaryFolder()

    @After fun cleanup() {
        GitRepoRegistry.clear()
    }

    @Test fun update_praise_lands_on_disk_and_commits() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("id_roundtrip_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }

        // Spin up a real GitRepo via the local-only init path so the
        // commit step actually exercises JGit.
        val repoDir = tmp.newFolder("repo")
        val repo = GitRepo.initLocalOnly(
            rootDir = repoDir,
            repoId = "repo-roundtrip",
            authorIdentity = AuthorIdentity("tester", "tester@example.com"),
        )
        GitRepoRegistry.put(repo)
        // Seed an initial identity.toml so the bind path has something
        // to read.
        IdentityTomlCodec.write(repoDir.toPath(), com.eight87.strictlykeptboy.store.IdentityTomlData.LockedDefaults)
        repo.commitAll("initial: seed identity.toml")

        val identityPrefs = IdentityPrefs.openForTest(
            prefs = prefs,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            commitFn = { r, msg -> r.commitAll(msg) },
        )
        identityPrefs.bindActiveRepo(
            rootDir = repoDir.toPath(),
            repoId = "repo-roundtrip",
            strictlyKept = { false },
        )

        identityPrefs.update { it.copy(praise = "good kit") }
        // Flush the debounce window deterministically.
        identityPrefs.flushWriteBack()

        // identity.toml on disk reflects the edit.
        val onDisk = IdentityTomlCodec.readOrDefault(repoDir.toPath())
        assertEquals("good kit", onDisk.praiseTerm)

        // git log shows the identity commit.
        val log = repo.log(maxCount = 5)
        assertTrue(
            "expected an 'identity: update' commit in log",
            log.any { it.message.startsWith("identity: update") },
        )
        repo.close()
    }

    @Test fun update_without_bind_keeps_inmemory_only() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("id_unbound_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        val identityPrefs = IdentityPrefs.openForTest(prefs)
        identityPrefs.update { it.copy(praise = "darling") }
        assertEquals("darling", identityPrefs.state.value.praise)
        // No active repo bound → flushWriteBack is a no-op.
        assertTrue(!identityPrefs.flushWriteBack())
    }

    @Test fun bind_reloads_from_disk() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("id_bind_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        val repoDir = tmp.newFolder("repo2")
        IdentityTomlCodec.write(
            repoDir.toPath(),
            com.eight87.strictlykeptboy.store.IdentityTomlData.LockedDefaults.copy(praiseTerm = "pup"),
        )

        val identityPrefs = IdentityPrefs.openForTest(prefs)
        identityPrefs.bindActiveRepo(
            rootDir = repoDir.toPath(),
            repoId = "repo2",
            strictlyKept = { false },
        )

        assertEquals("pup", identityPrefs.state.value.praise)
    }

    @Test fun strictlykept_mode_writes_review_feed_entry() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("id_strictly_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        val repoDir = tmp.newFolder("repo3")
        val repo = GitRepo.initLocalOnly(
            rootDir = repoDir,
            repoId = "repo-strict",
            authorIdentity = AuthorIdentity("tester", "tester@example.com"),
        )
        GitRepoRegistry.put(repo)
        IdentityTomlCodec.write(repoDir.toPath(), com.eight87.strictlykeptboy.store.IdentityTomlData.LockedDefaults)
        repo.commitAll("initial: seed")

        val identityPrefs = IdentityPrefs.openForTest(
            prefs = prefs,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            commitFn = { r, msg -> r.commitAll(msg) },
        )
        identityPrefs.bindActiveRepo(
            rootDir = repoDir.toPath(),
            repoId = "repo-strict",
            strictlyKept = { true }, // simulate strictly-kept mode
        )

        identityPrefs.update { it.copy(praise = "good kit") }
        identityPrefs.flushWriteBack()

        val reviewsDir = File(repoDir, "reviews")
        assertTrue("reviews/ should be created in strictly-kept mode", reviewsDir.isDirectory)
        val anyReviewEntry = reviewsDir.walk()
            .any { it.isFile && it.name == "reviewable_change.md" }
        assertTrue("expected at least one reviewable_change.md", anyReviewEntry)
        repo.close()
    }
}
