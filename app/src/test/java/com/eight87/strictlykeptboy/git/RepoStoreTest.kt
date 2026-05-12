package com.eight87.strictlykeptboy.git

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase B.3 / SE-F.5 — RepoStore round-trip + CRUD.
 *
 * Uses plain SharedPreferences via `RepoStore.openForTest` because Robolectric
 * doesn't simulate the Keystore-backed EncryptedSharedPreferences MasterKey.
 * The encryption layer is exercised separately via instrumented tests later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RepoStoreTest {

    private lateinit var store: RepoStore

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("repos_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        store = RepoStore.openForTest(prefs)
    }

    private fun cfg(repoId: String, remoteCount: Int = 0): RepoConfig {
        val remotes = (1..remoteCount).map { i ->
            RemoteBinding(
                name = if (i == 1) RemoteName.ORIGIN else RemoteName("mirror-$i"),
                url = "https://example.com/repo$i.git",
                transport = Transport.HttpsOAuth,
                authMethod = AuthMethod.OAuthGitHub,
            )
        }
        return RepoConfig(
            repoId = repoId,
            displayName = "test repo $repoId",
            rootDir = "/tmp/$repoId",
            remotes = remotes,
            primaryRemote = remotes.firstOrNull()?.name,
            authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
        )
    }

    @Test fun emptyStoreReturnsEmptyList() {
        assertTrue(store.list().isEmpty())
    }

    @Test fun addAndListRoundtrip() = runTest {
        store.add(cfg("repo-a"))
        store.add(cfg("repo-b", remoteCount = 2))

        assertEquals(2, store.list().size)
        assertEquals("repo-a", store.list()[0].repoId)
        assertEquals("repo-b", store.list()[1].repoId)
        assertEquals(2, store.get("repo-b")?.remotes?.size)
    }

    @Test fun persistsAcrossReopen() = runTest {
        store.add(cfg("repo-a", remoteCount = 1))
        store.add(cfg("repo-b", remoteCount = 0))   // no-origin
        store.add(cfg("repo-c", remoteCount = 3))

        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("repos_test", Context.MODE_PRIVATE)
        val reopened = RepoStore.openForTest(prefs)

        assertEquals(3, reopened.list().size)
        assertEquals(0, reopened.get("repo-b")?.remotes?.size)
        assertNull(reopened.get("repo-b")?.primaryRemote)
        assertEquals(3, reopened.get("repo-c")?.remotes?.size)
        assertEquals(RemoteName.ORIGIN, reopened.get("repo-c")?.primaryRemote)
    }

    @Test fun addRemoteUpdatesPrimaryWhenFirst() = runTest {
        store.add(cfg("repo-a"))   // no-origin
        assertNull(store.get("repo-a")?.primaryRemote)

        store.addRemote(
            "repo-a",
            RemoteBinding(
                name = RemoteName.ORIGIN,
                url = "git@example.com:repo.git",
                transport = Transport.Ssh,
                authMethod = AuthMethod.Ssh,
            ),
        )
        assertEquals(RemoteName.ORIGIN, store.get("repo-a")?.primaryRemote)
    }

    @Test fun removeRemoteRebindsPrimary() = runTest {
        store.add(cfg("repo-a", remoteCount = 2))
        assertEquals(RemoteName.ORIGIN, store.get("repo-a")?.primaryRemote)

        store.removeRemote("repo-a", RemoteName.ORIGIN)
        assertEquals(RemoteName("mirror-2"), store.get("repo-a")?.primaryRemote)
    }

    @Test fun removeAllRemotesClearsPrimary() = runTest {
        store.add(cfg("repo-a", remoteCount = 1))
        store.removeRemote("repo-a", RemoteName.ORIGIN)
        assertNull(store.get("repo-a")?.primaryRemote)
        assertTrue(store.get("repo-a")?.remotes!!.isEmpty())
    }

    @Test fun removeRepo() = runTest {
        store.add(cfg("repo-a"))
        store.add(cfg("repo-b"))
        store.remove("repo-a")
        assertEquals(1, store.list().size)
        assertNull(store.get("repo-a"))
    }
}
