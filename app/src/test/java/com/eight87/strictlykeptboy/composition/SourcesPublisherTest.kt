package com.eight87.strictlykeptboy.composition

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.cache.entities.EventRow
import com.eight87.strictlykeptboy.cache.entities.RecurrenceRuleRow
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.resolver.DateRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SourcesPublisherTest {

    private lateinit var ctx: Context
    private lateinit var db: CacheDatabase
    private lateinit var repoStore: RepoStore

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = CacheDatabase.openInMemoryWithDriver(
            ctx,
            androidx.sqlite.driver.bundled.BundledSQLiteDriver(),
        )
        val prefs = ctx.getSharedPreferences("src_pub_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        repoStore = RepoStore.openForTest(prefs)
    }

    @After fun tearDown() { db.close() }

    private fun cfg(id: String) = RepoConfig(
        repoId = id, displayName = id, rootDir = "/tmp/$id",
        authorIdentity = AuthorIdentity("Bat", "bat@example.com"),
    )

    private fun event(repoId: String, id: String, calendarId: String, dayIso: String) =
        EventRow(
            repoId = repoId, id = id, calendarId = calendarId,
            startEpochMs = ZonedDateTime.of(LocalDate.parse(dayIso).atTime(10, 0), ZoneId.systemDefault())
                .toInstant().toEpochMilli(),
            endEpochMs = ZonedDateTime.of(LocalDate.parse(dayIso).atTime(11, 0), ZoneId.systemDefault())
                .toInstant().toEpochMilli(),
            allDay = false, title = id, body = "", tagsJson = "[]",
            location = null, emoji = null, busy = true, priorityOverride = null,
            externalUid = null, privateFlag = false,
            sourcePath = "calendars/$calendarId/events/$id.md",
        )

    private fun rule(repoId: String, id: String, calendarId: String) = RecurrenceRuleRow(
        repoId = repoId, id = id, calendarId = calendarId,
        title = id, rrule = "FREQ=DAILY",
        dtstart = "2026-05-01T09:00:00", duration = "PT1H", tzId = "Europe/Berlin",
        location = null, emoji = null, busy = true, active = true,
        tagsJson = "[]", body = "",
        sourcePath = "calendars/$calendarId/recurrences/$id.md",
    )

    @Test fun eventsUnionAcrossTwoReposInsideRange() = runBlocking {
        repoStore.add(cfg("repo-a"))
        repoStore.add(cfg("repo-b"))

        db.events().upsertAll(listOf(
            event("repo-a", "ev-a1", "cal-a", "2026-05-10"),
            event("repo-a", "ev-out", "cal-a", "2026-01-15"), // outside window
            event("repo-b", "ev-b1", "cal-b", "2026-05-12"),
        ))
        db.recurrenceRules().upsertAll(listOf(
            rule("repo-a", "rr-a1", "cal-a"),
            rule("repo-b", "rr-b1", "cal-b"),
        ))

        val range = MutableStateFlow(
            DateRange(start = LocalDate.parse("2026-05-01"), endInclusive = LocalDate.parse("2026-05-31")),
        )
        val pubScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val publisher = SourcesPublisher(
            db = db, repoStore = repoStore, visibleRange = range, scope = pubScope,
        )

        val sources = withTimeout(5_000) {
            publisher.state.first { it.events.isNotEmpty() }
        }
        val eventIds = sources.events.map { it.ref.id }.toSet()
        assertEquals(setOf("ev-a1", "ev-b1"), eventIds)
        assertTrue(eventIds.none { it == "ev-out" })

        val ruleIds = sources.rules.map { it.rule.id }.toSet()
        assertEquals(setOf("rr-a1", "rr-b1"), ruleIds)

        val ruleA = sources.rules.first { it.rule.id == "rr-a1" }
        assertEquals(java.time.Duration.ofHours(1), ruleA.duration)
        assertEquals(ZoneId.of("Europe/Berlin"), ruleA.tzId)
        pubScope.cancel()
    }

    @Test fun emptyRepoSetEmitsEmptySources() = runBlocking {
        val range = MutableStateFlow(
            DateRange(start = LocalDate.parse("2026-05-01"), endInclusive = LocalDate.parse("2026-05-31")),
        )
        val pubScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val publisher = SourcesPublisher(
            db = db, repoStore = repoStore, visibleRange = range, scope = pubScope,
        )
        val sources = withTimeout(2_000) { publisher.state.first() }
        assertEquals(0, sources.events.size)
        assertEquals(0, sources.rules.size)
        pubScope.cancel()
    }
}
