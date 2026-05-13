package com.eight87.strictlykeptboy.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.ui.share.ShareLink
import com.eight87.strictlykeptboy.ui.share.ShareMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.2.D.6 — AccessAggregator builds rows across all configured
 * repos via a pluggable [ShareLinkSource]. Asserts: 2 repos × 3
 * share-links across them → 3 aggregated rows; empty source → empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class AccessAggregatorTest {

    private fun freshStore(): RepoStore {
        val prefs = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("access-test-${System.nanoTime()}", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        return RepoStore.openForTest(prefs)
    }

    private fun repo(id: String, name: String) = RepoConfig(
        repoId = id,
        displayName = name,
        rootDir = "/tmp/$id",
        authorIdentity = AuthorIdentity("me", "me@example.com"),
    )

    @Test fun emptySourceReturnsEmpty() = runTest {
        val store = freshStore()
        store.add(repo("r1", "Repo One"))
        val agg = AccessAggregator(store)
        assertTrue(agg.aggregate().isEmpty())
    }

    @Test fun aggregatesAcrossRepos() = runTest {
        val store = freshStore()
        store.add(repo("r1", "Repo One"))
        store.add(repo("r2", "Repo Two"))
        val links = mapOf(
            "r1" to listOf(
                ShareLink(
                    urls = listOf("https://example.com/alice/calendar.git"),
                    mode = ShareMode.ReadOnly,
                    sourceLabel = "alice",
                ),
                ShareLink(
                    urls = listOf("https://bob/calendar.git"),
                    mode = ShareMode.ReadWrite,
                    singleUseToken = true,
                    expiryIso = "2030-01-01T00:00:00Z",
                ),
            ),
            "r2" to listOf(
                ShareLink(
                    urls = listOf("https://example.com/charlie/calendar.git"),
                    mode = ShareMode.ReadOnly,
                ),
            ),
        )
        val src = ShareLinkSource { repoId -> links[repoId].orEmpty() }
        val rows = AccessAggregator(store, src).aggregate()
        assertEquals(3, rows.size)
        val byRepo = rows.groupBy { it.repoId }
        assertEquals(2, byRepo.getValue("r1").size)
        assertEquals(1, byRepo.getValue("r2").size)
        // recipientLabel comes from sourceLabel when set, otherwise derived from url.
        val alice = byRepo.getValue("r1").first { it.mode == ShareMode.ReadOnly }
        assertEquals("alice", alice.recipientLabel)
        val bob = byRepo.getValue("r1").first { it.mode == ShareMode.ReadWrite }
        assertEquals("bob/calendar", bob.recipientLabel)
        assertTrue(bob.singleUse)
        assertEquals(
            java.time.Instant.parse("2030-01-01T00:00:00Z").toEpochMilli(),
            bob.expiresAtMs,
        )
        val charlie = byRepo.getValue("r2").single()
        assertEquals("charlie/calendar", charlie.recipientLabel)
        assertEquals(null, charlie.expiresAtMs)
    }
}
