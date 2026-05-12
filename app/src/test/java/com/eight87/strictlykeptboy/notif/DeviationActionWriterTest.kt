package com.eight87.strictlykeptboy.notif

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class DeviationActionWriterTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun writesSkippedDeviationFile() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val root = tmp.newFolder("repo")
        val latch = CountDownLatch(1)
        DeviationActionWriter.writeAsync(
            context = ctx,
            repoId = "r1",
            eventId = "ev1",
            kind = ReminderBroadcastReceiver.DEVIATION_SKIPPED,
            targetRoot = root,
        ) { latch.countDown() }
        assertTrue(latch.await(5, TimeUnit.SECONDS))

        // Walk root to find any .md file under deviations/.
        val found = Files.walk(root.toPath()).use { stream ->
            stream.filter { it.toString().contains("deviations") && it.toString().endsWith(".md") }
                .findFirst().orElse(null)
        }
        assertTrue("no deviation file written under $root", found != null)
        val content = String(Files.readAllBytes(found))
        assertTrue("missing deviation_kind = skipped", content.contains("deviation_kind = \"skipped\""))
        assertTrue("missing target_id", content.contains("target_id = \"ev1\""))
    }
}
