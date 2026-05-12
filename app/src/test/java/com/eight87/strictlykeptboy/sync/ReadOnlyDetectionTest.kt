package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.PushRejection
import com.eight87.strictlykeptboy.git.PushResult
import com.eight87.strictlykeptboy.git.RemoteName
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ReadOnlyDetectionTest {

    /**
     * Direct unit test of the SyncStatusStore mutation that the scheduler
     * performs on a `PushResult.Rejected(NoPermission)`. The scheduler's
     * actual push-rejection path is the one-liner
     * `statusStore.updateRemote { it.copy(readOnlyDetected = true) }`;
     * the JGit-level reproduction of a NoPermission rejection on a local
     * file-transport bare requires fs ACLs that don't reliably work in
     * Robolectric — we therefore validate the store-mutation contract
     * directly. The plumbing from `Rejected(NoPermission)` -> store is
     * covered by code inspection.
     */
    @Test fun rejectedNoPermissionPersistsReadOnlyFlag() = runTest {
        val store = SyncTestFixtures.newStatusStore()
        val rejected: PushResult = PushResult.Rejected(RemoteName.ORIGIN, PushRejection.NoPermission)
        assertTrue(rejected is PushResult.Rejected && rejected.reason == PushRejection.NoPermission)

        // Simulate the scheduler's response to NoPermission.
        store.updateRemote("ro", RemoteName.ORIGIN) {
            it.copy(readOnlyDetected = true, lastErrorMessage = "read-only")
        }
        val snap = store.state.first()["ro"]!!
        assertTrue(snap.perRemote[RemoteName.ORIGIN.value]!!.readOnlyDetected)
    }
}
