package com.eight87.strictlykeptboy.git.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.RemoteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase B.4 — SecretsStore CRUD + per-`(repoId, remoteName)` isolation.
 * Plain SharedPreferences under the hood (Robolectric doesn't simulate the
 * Keystore-backed wrapper); the encryption layer is verified in instrumented
 * tests later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SecretsStoreTest {

    private lateinit var store: SecretsStore

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ctx.getSharedPreferences("secrets_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        store = SecretsStore.openForTest(prefs)
    }

    @Test fun sshKeypairRoundTrip() {
        val kp = Ed25519KeyGen.generate("test")
        store.storeSshKeypair("repo-a", RemoteName.ORIGIN, kp)
        val got = store.getSshKeypair("repo-a", RemoteName.ORIGIN)
        assertEquals(kp.publicOpenSsh, got?.publicOpenSsh)
        assertEquals(kp.privateOpenSshPem, got?.privateOpenSshPem)
    }

    @Test fun keypairsAreIsolatedPerRemote() {
        val kp1 = Ed25519KeyGen.generate("origin")
        val kp2 = Ed25519KeyGen.generate("mirror-1")
        store.storeSshKeypair("repo-a", RemoteName.ORIGIN, kp1)
        store.storeSshKeypair("repo-a", RemoteName("mirror-1"), kp2)

        assertEquals(kp1.publicOpenSsh, store.getSshKeypair("repo-a", RemoteName.ORIGIN)?.publicOpenSsh)
        assertEquals(kp2.publicOpenSsh, store.getSshKeypair("repo-a", RemoteName("mirror-1"))?.publicOpenSsh)
    }

    @Test fun keypairsAreIsolatedPerRepo() {
        val kp1 = Ed25519KeyGen.generate("repo-a")
        val kp2 = Ed25519KeyGen.generate("repo-b")
        store.storeSshKeypair("repo-a", RemoteName.ORIGIN, kp1)
        store.storeSshKeypair("repo-b", RemoteName.ORIGIN, kp2)
        assertEquals(kp1.publicOpenSsh, store.getSshKeypair("repo-a", RemoteName.ORIGIN)?.publicOpenSsh)
        assertEquals(kp2.publicOpenSsh, store.getSshKeypair("repo-b", RemoteName.ORIGIN)?.publicOpenSsh)
    }

    @Test fun oauthTokenRoundTripWithExpiry() {
        val now = System.currentTimeMillis()
        val token = OAuthToken("ya29.abc", refreshToken = "r-xyz", expiryEpochMs = now + 3_600_000)
        store.storeOAuthToken("repo-a", RemoteName.ORIGIN, token)
        val got = store.getOAuthToken("repo-a", RemoteName.ORIGIN)
        assertEquals(token, got)
        // Not yet expired
        assertEquals(false, got!!.isExpired(now))
        // Expired one second past expiry
        assertEquals(true, got.isExpired(now + 3_600_001))
    }

    @Test fun oauthTokenWithoutRefreshNorExpiry() {
        store.storeOAuthToken("repo-a", RemoteName.ORIGIN, OAuthToken("gho_abc"))
        val got = store.getOAuthToken("repo-a", RemoteName.ORIGIN)
        assertEquals("gho_abc", got?.accessToken)
        assertNull(got?.refreshToken)
        assertNull(got?.expiryEpochMs)
    }

    @Test fun patRoundTrip() {
        store.storePat("repo-a", RemoteName.ORIGIN, PatCredential("the-bat", "pat-token-1234"))
        val got = store.getPat("repo-a", RemoteName.ORIGIN)
        assertEquals("the-bat", got?.username)
        assertEquals("pat-token-1234", got?.token)
    }

    @Test fun clearForRemoteRemovesOnlyThatRemote() {
        val kp1 = Ed25519KeyGen.generate("origin")
        val kp2 = Ed25519KeyGen.generate("mirror-1")
        store.storeSshKeypair("repo-a", RemoteName.ORIGIN, kp1)
        store.storeSshKeypair("repo-a", RemoteName("mirror-1"), kp2)
        store.storeOAuthToken("repo-a", RemoteName.ORIGIN, OAuthToken("ya29.abc"))

        store.clearForRemote("repo-a", RemoteName.ORIGIN)

        assertNull(store.getSshKeypair("repo-a", RemoteName.ORIGIN))
        assertNull(store.getOAuthToken("repo-a", RemoteName.ORIGIN))
        assertNotNull(store.getSshKeypair("repo-a", RemoteName("mirror-1")))
    }

    @Test fun clearForRepoRemovesAllRemotes() {
        store.storeSshKeypair("repo-a", RemoteName.ORIGIN, Ed25519KeyGen.generate("o"))
        store.storeSshKeypair("repo-a", RemoteName("mirror-1"), Ed25519KeyGen.generate("m"))
        store.storeSshKeypair("repo-b", RemoteName.ORIGIN, Ed25519KeyGen.generate("b"))

        store.clearForRepo("repo-a")

        assertNull(store.getSshKeypair("repo-a", RemoteName.ORIGIN))
        assertNull(store.getSshKeypair("repo-a", RemoteName("mirror-1")))
        assertNotNull(store.getSshKeypair("repo-b", RemoteName.ORIGIN))
    }
}
