package com.eight87.strictlykeptboy.notif

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.resolver.CompletionState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CompletionStateCacheWriterTest {

    private lateinit var db: CacheDatabase
    private lateinit var ctx: Context

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = CacheDatabase.openInMemoryWithDriver(
            ctx,
            androidx.sqlite.driver.bundled.BundledSQLiteDriver(),
        )
        // Inject the in-memory DB for the writer.
        CompletionStateCacheWriter.databaseProvider = { db }
    }

    @After fun tearDown() {
        db.close()
        CompletionStateCacheWriter.databaseProvider = { CacheDatabase.open(it) }
    }

    @Test fun endAlarmFlipsRowToCompletedBySchedule() {
        val latch = CountDownLatch(1)
        CompletionStateCacheWriter.flipToCompletedBySchedule(
            ctx,
            repoId = "r1",
            targetId = "ev1",
            occurrenceDate = "2026-05-12",
        ) { latch.countDown() }
        assert(latch.await(5, TimeUnit.SECONDS)) { "writer did not complete" }

        val row = runBlocking { db.eventInstanceState().get("r1", "ev1", "2026-05-12") }
        assertNotNull(row)
        assertEquals(CompletionState.CompletedBySchedule.name, row!!.completionState)
    }
}
