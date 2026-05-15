package com.eight87.strictlykeptboy.system

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.G.7 — every successful `GitRepo.commitAll` fires the
 * notifier hook with the affected repo's id, which `AppGraph.parkRuntimes`
 * wires to `SkbAccountManager.requestSyncFor`. We test the seam itself
 * here (a full GitRepo round-trip would need JGit + a temp repo on disk
 * and adds no signal vs. directly probing the hook).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CommitTriggersSyncTest {

    @After fun teardown() {
        SkbCommitNotifier.hook = null
    }

    @Test fun hookFiresWithRepoId() {
        var seen: String? = null
        SkbCommitNotifier.hook = { repoId -> seen = repoId }
        SkbCommitNotifier.notifyCommit("repo-xyz")
        assertEquals("repo-xyz", seen)
    }

    @Test fun missingHookIsNoOp() {
        SkbCommitNotifier.hook = null
        // Must not throw.
        SkbCommitNotifier.notifyCommit("repo-xyz")
        assertNull(SkbCommitNotifier.hook)
    }

    @Test fun hookExceptionDoesNotEscape() {
        SkbCommitNotifier.hook = { error("boom") }
        // notifyCommit wraps the call in runCatching; any thrown
        // exception inside a third-party hook must not break the
        // commit path.
        SkbCommitNotifier.notifyCommit("repo-xyz")
    }
}
