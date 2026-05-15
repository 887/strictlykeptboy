package com.eight87.strictlykeptboy.composition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.DateRange
import com.eight87.strictlykeptboy.resolver.EventInput
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.ExternalSource
import com.eight87.strictlykeptboy.resolver.RepoRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.18.C.0 — assert that external (CalendarContract-backed) events
 * produced by `SystemEventsBridge` are folded into [SourcesPublisher.state]
 * alongside file-backed events.
 *
 * Drives the publisher with an injected `externalEventsProvider` so the
 * test doesn't need a Robolectric content provider — those code paths
 * are exercised by `SystemEventsBridgeTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SystemEventsConsumptionTest {

    private lateinit var ctx: Context
    private lateinit var db: CacheDatabase
    private lateinit var repoStore: RepoStore

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = CacheDatabase.openInMemoryWithDriver(
            ctx, androidx.sqlite.driver.bundled.BundledSQLiteDriver(),
        )
        val prefs = ctx.getSharedPreferences("sys_evt_consume_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        repoStore = RepoStore.openForTest(prefs)
    }

    @After fun tearDown() { db.close() }

    @Test fun externalEventsAcrossTwoCalendarsAreFoldedIntoSources() = runBlocking {
        repoStore.add(
            RepoConfig(
                repoId = "repo-a", displayName = "A", rootDir = "/tmp/a",
                authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
            ),
        )
        val zone = ZoneId.systemDefault()
        val ev = { id: Long, calId: Long, title: String, day: String ->
            EventInput(
                ref = EventRef("ext-$id"),
                calendar = CalendarRef(calId.toString()),
                repo = RepoRef("system/com.google/alice@gmail.com"),
                title = title,
                start = ZonedDateTime.of(LocalDate.parse(day).atTime(10, 0), zone),
                end = ZonedDateTime.of(LocalDate.parse(day).atTime(11, 0), zone),
                external = ExternalSource(
                    accountType = "com.google",
                    accountName = "alice@gmail.com",
                    eventId = id,
                    accessLevel = 700,
                ),
            )
        }
        val externals = listOf(
            ev(1L, 100L, "Cal1-EvA", "2026-05-10"),
            ev(2L, 100L, "Cal1-EvB", "2026-05-12"),
            ev(3L, 101L, "Cal2-EvC", "2026-05-14"),
        )

        val range = MutableStateFlow(
            DateRange(start = LocalDate.parse("2026-05-01"), endInclusive = LocalDate.parse("2026-05-31")),
        )
        val pubScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val publisher = SourcesPublisher(
            db = db, repoStore = repoStore, visibleRange = range, scope = pubScope,
            externalEventsProvider = { _ -> flowOf(externals) },
        )

        val sources = withTimeout(5_000) {
            publisher.state.first { it.events.size >= 3 }
        }
        // All 3 external instances surface, each with the external sidecar.
        val externalEvents = sources.events.filter { it.external != null }
        assertEquals(3, externalEvents.size)
        assertEquals(
            setOf("Cal1-EvA", "Cal1-EvB", "Cal2-EvC"),
            externalEvents.map { it.title }.toSet(),
        )
        assertTrue(externalEvents.all { it.external!!.accountType == "com.google" })
        assertTrue(externalEvents.all { it.repo.id == "system/com.google/alice@gmail.com" })
        pubScope.cancel()
    }
}
