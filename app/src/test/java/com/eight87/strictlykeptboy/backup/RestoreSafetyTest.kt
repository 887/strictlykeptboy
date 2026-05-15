package com.eight87.strictlykeptboy.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Round 2.17 Phase G.8 — wipe-safety per D-2.17.g.
 *
 *  - A non-empty parent without a `.skb-root` marker MUST NOT be
 *    wiped. [BackupRestorer.restoreFromArchive] surfaces this as a
 *    `Result.failure` carrying [BackupRestorer.UnsafeWipeException].
 *  - The pre-existing unrelated files MUST still be on disk after
 *    the refusal (the failure is raised BEFORE any deletion).
 *  - An empty parent IS allowed (nothing to nuke).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class RestoreSafetyTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun refusesToWipeUnrelatedFolder() = runTest {
        val parent = tmp.newFolder("scary-user-folder")
        val unrelated1 = File(parent, "important-novel.txt").apply {
            writeText("the cat sat on the mat")
        }
        val unrelated2 = File(parent, "Pictures").apply { mkdirs() }
        File(unrelated2, "cat.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))

        val result = runCatching {
            BackupRestorer.restoreFromArchive(
                input = ByteArrayInputStream(ByteArray(0)),
                parent = parent,
            )
        }
        assertTrue("restore must fail", result.isFailure)
        val err = result.exceptionOrNull()
        assertTrue(
            "expected UnsafeWipeException but got $err",
            err is BackupRestorer.UnsafeWipeException,
        )
        assertEquals(parent, (err as BackupRestorer.UnsafeWipeException).parent)

        // Unrelated files survived the refusal.
        assertTrue("unrelated file still there", unrelated1.isFile)
        assertEquals("the cat sat on the mat", unrelated1.readText())
        assertTrue("unrelated dir still there", unrelated2.isDirectory)
        assertTrue(File(unrelated2, "cat.jpg").isFile)
    }

    @Test
    fun emptyParentIsSafeToWipe() {
        val parent = tmp.newFolder("empty")
        // assertSafeToWipe is the gate; no exception means OK.
        BackupRestorer.assertSafeToWipe(parent)
        assertFalse(
            "empty parent should not carry a marker",
            File(parent, ".skb-root").exists(),
        )
    }
}
