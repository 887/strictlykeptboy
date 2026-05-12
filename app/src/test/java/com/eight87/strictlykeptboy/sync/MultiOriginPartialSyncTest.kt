package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.PushResult
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.Transport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class MultiOriginPartialSyncTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun mirrorFailureSurfacesPartialSuccess() = runTest {
        // Two reachable bares + one missing bare.
        val origin = SyncTestFixtures.initBare(tmp.root, "origin.git")
        val mirror1 = SyncTestFixtures.initBare(tmp.root, "mirror1.git")
        val missing = File(tmp.root, "missing.git")

        val workDir = tmp.newFolder("work")
        val mirror1Binding = RemoteBinding(
            name = RemoteName("mirror-1"),
            url = mirror1.toURI().toString(),
            transport = Transport.File,
            authMethod = AuthMethod.None,
        )
        val deadBinding = RemoteBinding(
            name = RemoteName("mirror-dead"),
            url = missing.toURI().toString(),
            transport = Transport.File,
            authMethod = AuthMethod.None,
        )
        val repo = SyncTestFixtures.cloneFrom(
            bare = origin,
            workDir = workDir,
            repoId = "multi",
            additional = listOf(mirror1Binding, deadBinding),
        )
        File(workDir, "x.md").writeText("x\n")
        repo.commitAll("x")

        val push = repo.push(null)
        assertTrue("expected PartiallySuccess, got $push", push is PushResult.PartiallySuccess)
        val partial = push as PushResult.PartiallySuccess
        assertTrue(RemoteName.ORIGIN in partial.pushed)
        assertTrue(RemoteName("mirror-1") in partial.pushed)
        assertTrue(RemoteName("mirror-dead") in partial.failed)
    }
}
