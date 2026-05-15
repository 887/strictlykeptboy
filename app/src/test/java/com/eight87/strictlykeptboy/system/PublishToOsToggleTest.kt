package com.eight87.strictlykeptboy.system

import android.accounts.AccountManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.G.6 — toggling publish-to-OS creates / removes one
 * AccountManager account per skb repo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class PublishToOsToggleTest {

    private lateinit var ctx: Context
    private lateinit var prefs: SystemCalendarPrefsStore
    private lateinit var repoStore: RepoStore
    private lateinit var mgr: SkbAccountManager

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        // Fresh AccountManager state per test.
        val am = AccountManager.get(ctx)
        am.getAccountsByType(SkbAccountAuthenticator.SKB_ACCOUNT_TYPE).forEach {
            am.removeAccountExplicitly(it)
        }
        val sp = ctx.getSharedPreferences("publish_test_${System.nanoTime()}", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        prefs = SystemCalendarPrefsStore.openForTest(sp)
        val rsPrefs = ctx.getSharedPreferences("repos_test_${System.nanoTime()}", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        repoStore = RepoStore.openForTest(rsPrefs)
        // Seed two repos.
        runBlocking {
            repoStore.add(
                RepoConfig(
                    repoId = "repo-a",
                    displayName = "Repo A",
                    rootDir = ctx.cacheDir.resolve("repo-a").also { it.mkdirs() }.absolutePath,
                    authorIdentity = AuthorIdentity("test", "t@example.com"),
                ),
            )
            repoStore.add(
                RepoConfig(
                    repoId = "repo-b",
                    displayName = "Repo B",
                    rootDir = ctx.cacheDir.resolve("repo-b").also { it.mkdirs() }.absolutePath,
                    authorIdentity = AuthorIdentity("test", "t@example.com"),
                ),
            )
        }
        mgr = SkbAccountManager(ctx, repoStore, prefs)
    }

    @Test fun enableCreatesOneAccountPerRepo() {
        val added = mgr.enableForAllRepos()
        assertEquals(2, added)
        val am = AccountManager.get(ctx)
        val ours = am.getAccountsByType(SkbAccountAuthenticator.SKB_ACCOUNT_TYPE)
        val names = ours.map { it.name }.toSet()
        assertTrue("expected repo-a@local in $names", "repo-a@local" in names)
        assertTrue("expected repo-b@local in $names", "repo-b@local" in names)
    }

    @Test fun enableIsIdempotent() {
        mgr.enableForAllRepos()
        val secondPass = mgr.enableForAllRepos()
        // Second pass doesn't double-add; account count stays at 2.
        assertEquals(0, secondPass)
        val am = AccountManager.get(ctx)
        assertEquals(
            2,
            am.getAccountsByType(SkbAccountAuthenticator.SKB_ACCOUNT_TYPE).size,
        )
    }

    @Test fun disableRemovesEverything() {
        mgr.enableForAllRepos()
        prefs.setCalendarRowId("repo-a", "routines", 7L)
        mgr.disableForAllRepos()
        val am = AccountManager.get(ctx)
        assertEquals(
            0,
            am.getAccountsByType(SkbAccountAuthenticator.SKB_ACCOUNT_TYPE).size,
        )
        // Calendar-row ID map for that repo is cleared on disable.
        assertEquals(null, prefs.calendarRowId("repo-a", "routines"))
    }

    @Test fun requestSyncForNoOpsWhenToggleOff() {
        // Toggle defaults to off — no exception even without accounts.
        assertFalse(prefs.globalState.value.publishToOs)
        mgr.requestSyncFor("repo-a")
    }
}
