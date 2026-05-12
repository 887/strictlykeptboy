package com.eight87.strictlykeptboy.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.git.RemoteName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SyncStatusPersistenceTest {

    @Test fun statusSurvivesReopen() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val prefsName = "test-status-${System.nanoTime()}"
        val prefs = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val store1 = SyncStatusStore.openForTest(prefs)
        store1.updateRemote("r1", RemoteName.ORIGIN) {
            it.copy(lastSyncedAt = 12345L, readOnlyDetected = true)
        }
        store1.updateRepo("r1") { it.copy(lastSyncedAt = 99999L) }

        // Re-open with a fresh instance over the same prefs.
        val store2 = SyncStatusStore.openForTest(prefs)
        val snap = store2.state.first()["r1"]!!
        assertEquals(99999L, snap.lastSyncedAt)
        val remote = snap.perRemote[RemoteName.ORIGIN.value]!!
        assertEquals(12345L, remote.lastSyncedAt)
        assertTrue(remote.readOnlyDetected)
    }
}
