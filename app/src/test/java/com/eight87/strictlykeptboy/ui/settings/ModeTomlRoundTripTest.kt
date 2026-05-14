package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.store.ModeTomlCodec
import com.eight87.strictlykeptboy.store.ModeTomlData
import com.eight87.strictlykeptboy.store.RepoMode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/**
 * Phase 2.1.K.1 — ModePrefs round-trips edits through mode.toml on the
 * active repo and commits via GitRepoRegistry. Sibling of
 * [IdentityTomlRoundTripTest].
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModeTomlRoundTripTest {

    @get:Rule val tmp = TemporaryFolder()

    @After fun cleanup() {
        GitRepoRegistry.clear()
    }

    @Test fun update_personaId_lands_on_disk_and_commits() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("mode_roundtrip_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }

        val repoDir = tmp.newFolder("repo")
        val repo = GitRepo.initLocalOnly(
            rootDir = repoDir,
            repoId = "repo-mode-roundtrip",
            authorIdentity = AuthorIdentity("tester", "tester@example.com"),
        )
        GitRepoRegistry.put(repo)
        ModeTomlCodec.write(repoDir.toPath(), ModeTomlData.Default)
        repo.commitAll("initial: seed mode.toml")

        val modePrefs = ModePrefs.openForTest(
            prefs = prefs,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            commitFn = { r, msg -> r.commitAll(msg) },
        )
        modePrefs.bindActiveRepo(repoDir.toPath(), "repo-mode-roundtrip")

        modePrefs.setMode(AppMode.StrictlyKept)
        modePrefs.setPersonaId("stern-but-fair")
        modePrefs.flushWriteBack()

        val onDisk = ModeTomlCodec.readOrDefault(repoDir.toPath())
        assertEquals(RepoMode.StrictlyKept, onDisk.mode)
        assertEquals("stern-but-fair", onDisk.domPersona)

        val log = repo.log(maxCount = 5)
        assertTrue(
            "expected a 'mode: update' commit in log",
            log.any { it.message.startsWith("mode: update") },
        )
        repo.close()
    }

    @Test fun bind_reloads_from_disk_with_self_keep() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("mode_bind_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        val repoDir = tmp.newFolder("repo2")
        ModeTomlCodec.write(
            repoDir.toPath(),
            ModeTomlData(mode = RepoMode.SelfKeep),
        )
        val modePrefs = ModePrefs.openForTest(prefs)
        modePrefs.bindActiveRepo(repoDir.toPath(), "repo2")
        assertEquals(AppMode.SelfKeep, modePrefs.state.value.mode)
    }

    @Test fun unbound_update_stays_inmemory() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("mode_unbound_test", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        val modePrefs = ModePrefs.openForTest(prefs)
        modePrefs.setMode(AppMode.StrictlyKept)
        assertEquals(AppMode.StrictlyKept, modePrefs.state.value.mode)
        // Unbound → flushWriteBack is a no-op.
        assertTrue(!modePrefs.flushWriteBack())
    }

    @Test fun keptBy_derives_from_persona_and_target() {
        val s1 = ModeState(mode = AppMode.StrictlyKept, personaId = "stern-but-fair", writeBackTarget = null)
        assertEquals(KeptBy.Ai, s1.keptBy)
        val s2 = ModeState(mode = AppMode.StrictlyKept, personaId = null, writeBackTarget = "https://example/dom")
        assertEquals(KeptBy.Human, s2.keptBy)
        val s3 = ModeState(mode = AppMode.SelfKeep)
        assertEquals(KeptBy.SelfKeep, s3.keptBy)
        val s4 = ModeState(mode = AppMode.Free)
        assertEquals(null, s4.keptBy)
    }
}
