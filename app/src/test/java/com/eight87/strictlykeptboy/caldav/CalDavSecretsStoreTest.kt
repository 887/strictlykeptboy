package com.eight87.strictlykeptboy.caldav

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.caldav.auth.CalDavCredential
import com.eight87.strictlykeptboy.caldav.auth.CalDavSecretsStore
import com.eight87.strictlykeptboy.caldav.store.CalDavMirrorStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase Y.3 / Y.4 — Robolectric smoke for CalDAV credential and
 *  mirror persistence (the EncryptedSharedPreferences wrapper is
 *  simulated by Robolectric with plain SharedPreferences, identical
 *  to the existing `SecretsStoreTest` pattern). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CalDavSecretsStoreTest {

    private lateinit var creds: CalDavSecretsStore
    private lateinit var mirrors: CalDavMirrorStore

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val secretPrefs = ctx.getSharedPreferences("caldav_creds_test", Context.MODE_PRIVATE).also { it.edit().clear().apply() }
        val mirrorPrefs = ctx.getSharedPreferences("caldav_mirrors_test", Context.MODE_PRIVATE).also { it.edit().clear().apply() }
        creds = CalDavSecretsStore.openForTest(secretPrefs)
        mirrors = CalDavMirrorStore.openForTest(mirrorPrefs)
    }

    @Test fun basicAuthRoundTrip() {
        creds.storeBasic("binding-1", CalDavCredential.BasicAuth("alice", "secret"))
        val out = creds.get("binding-1") as CalDavCredential.BasicAuth
        assertEquals("alice", out.username)
        assertEquals("secret", out.password)
    }

    @Test fun appPasswordRoundTrip() {
        creds.storeAppPassword("b2", CalDavCredential.AppPassword("alice@me.com", "abcd-efgh-ijkl-mnop"))
        val out = creds.get("b2") as CalDavCredential.AppPassword
        assertEquals("alice@me.com", out.appleId)
    }

    @Test fun bearerRoundTripIncludesExpiry() {
        creds.storeBearer("b3", CalDavCredential.Bearer("at", refreshToken = "rt", expiryEpochMs = 12345L))
        val out = creds.get("b3") as CalDavCredential.Bearer
        assertEquals("at", out.accessToken)
        assertEquals("rt", out.refreshToken)
        assertEquals(12345L, out.expiryEpochMs)
    }

    @Test fun clearRemovesCredential() {
        creds.storeBasic("b4", CalDavCredential.BasicAuth("u", "p"))
        creds.clear("b4")
        assertNull(creds.get("b4"))
    }

    @Test fun mirrorStoreUpsertAndList() {
        val mirror = CalDavMirror(
            mirrorId = mirrorIdOf("r1", "https://h", "/c/"),
            repoId = "r1",
            displayName = "Work",
            serverUrl = "https://h",
            calendarHomePath = "https://h",
            calendarPath = "/c/",
            targetCalendarId = "work",
            mode = CalDavMode.PULL_ONLY,
            credentialBindingId = "b",
        )
        mirrors.upsert(mirror)
        assertEquals(1, mirrors.listForRepo("r1").size)
        assertNotNull(mirrors.get(mirror.mirrorId))
        mirrors.remove(mirror.mirrorId)
        assertTrue(mirrors.listForRepo("r1").isEmpty())
    }
}
