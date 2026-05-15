package com.eight87.strictlykeptboy.system

import android.accounts.Account
import android.app.Application
import android.content.ContentProvider
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SyncResult
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.cache.entities.EventRow
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.G.3 — sync adapter diff behaviour against a shadowed
 * CalendarContract provider.
 *
 * Scenarios:
 *  - skb event present, OS empty → INSERT.
 *  - skb event matches existing OS row by `_SYNC_ID` → UPDATE.
 *  - OS row present whose `_SYNC_ID` is not in skb → DELETE.
 *  - First sync inserts a Calendars row; second sync reuses the same
 *    `_ID` (idempotency via [SystemCalendarPrefsStore.calendarRowId]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SkbCalendarSyncAdapterTest {

    private lateinit var ctx: Context
    private lateinit var repoStore: RepoStore
    private lateinit var cache: CacheDatabase
    private lateinit var prefs: SystemCalendarPrefsStore
    private lateinit var adapter: SkbCalendarSyncAdapter
    private lateinit var account: Account

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        val info = ProviderInfo().apply { authority = CalendarContract.AUTHORITY }
        Robolectric.buildContentProvider(FakeSyncProvider::class.java).create(info).get()
        FakeSyncProvider.reset()

        val rsPrefs = ctx.getSharedPreferences("repos_sync_${System.nanoTime()}", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        repoStore = RepoStore.openForTest(rsPrefs)
        runBlocking {
            repoStore.add(
                RepoConfig(
                    repoId = "repo-1",
                    displayName = "Repo One",
                    rootDir = ctx.cacheDir.resolve("repo-1").also { it.mkdirs() }.absolutePath,
                    authorIdentity = AuthorIdentity("t", "t@example.com"),
                    colorSeed = 0xFF0000.toInt(),
                ),
            )
        }
        cache = CacheDatabase.openInMemoryWithDriver(
            ctx,
            androidx.sqlite.driver.bundled.BundledSQLiteDriver(),
        )

        val sp = ctx.getSharedPreferences("sync_prefs_${System.nanoTime()}", Context.MODE_PRIVATE)
            .also { it.edit().clear().apply() }
        prefs = SystemCalendarPrefsStore.openForTest(sp)

        SkbSyncRuntime.repoStore = repoStore
        SkbSyncRuntime.cacheDatabase = cache
        SkbSyncRuntime.systemCalendarPrefs = prefs

        adapter = SkbCalendarSyncAdapter(ctx, autoInitialize = false)
        account = Account("repo-1@local", SkbAccountAuthenticator.SKB_ACCOUNT_TYPE)
    }

    @After fun tearDown() {
        SkbSyncRuntime.repoStore = null
        SkbSyncRuntime.cacheDatabase = null
        SkbSyncRuntime.systemCalendarPrefs = null
        cache.close()
    }

    private fun perform(): SyncResult {
        val sr = SyncResult()
        val client: ContentProviderClient =
            ctx.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
        try {
            adapter.onPerformSync(account, Bundle(), CalendarContract.AUTHORITY, client, sr)
        } finally {
            client.close()
        }
        return sr
    }

    private fun seedEvent(id: String, title: String, calId: String = "routines"): EventRow {
        val row = EventRow(
            repoId = "repo-1",
            id = id,
            calendarId = calId,
            startEpochMs = 1_700_000_000_000L,
            endEpochMs = 1_700_003_600_000L,
            allDay = false,
            title = title,
            body = "",
            tagsJson = "[]",
            location = null,
            emoji = null,
            busy = true,
            priorityOverride = null,
            externalUid = null,
            privateFlag = false,
            sourcePath = "calendars/$calId/events/2026/05/$id.md",
        )
        runBlocking { cache.events().upsertAll(listOf(row)) }
        return row
    }

    @Test fun insertsEventWhenAbsentInOs() {
        seedEvent("evt-1", "Hello")
        perform()
        // One Events row created with _SYNC_ID = "evt-1".
        assertEquals(1, FakeSyncProvider.events.size)
        val row = FakeSyncProvider.events.values.first()
        assertEquals("evt-1", row.getAsString(CalendarContract.Events._SYNC_ID))
        assertEquals("Hello", row.getAsString(CalendarContract.Events.TITLE))
        assertEquals(account.name, row.getAsString(CalendarContract.Events.ACCOUNT_NAME))
        assertEquals(account.type, row.getAsString(CalendarContract.Events.ACCOUNT_TYPE))
    }

    @Test fun updatesExistingMatchingSyncId() {
        seedEvent("evt-1", "First")
        perform()
        val osId = FakeSyncProvider.events.keys.first()
        // Mutate skb-side and re-sync.
        seedEvent("evt-1", "Renamed")
        perform()
        assertEquals(1, FakeSyncProvider.events.size)
        assertEquals(osId, FakeSyncProvider.events.keys.first())
        assertEquals(
            "Renamed",
            FakeSyncProvider.events[osId]?.getAsString(CalendarContract.Events.TITLE),
        )
    }

    @Test fun deletesRowMissingFromSkb() {
        seedEvent("evt-1", "First")
        perform()
        assertEquals(1, FakeSyncProvider.events.size)
        // Drop the event from skb. The next sync must DELETE.
        runBlocking { cache.events().deleteAllForRepo("repo-1") }
        perform()
        assertEquals(0, FakeSyncProvider.events.size)
    }

    @Test fun firstSyncInsertsCalendarRowSecondReuses() {
        seedEvent("evt-1", "Hello")
        perform()
        val calIdBefore = FakeSyncProvider.calendars.keys.firstOrNull()
        assertNotNull(calIdBefore)
        val cachedRowId = prefs.calendarRowId("repo-1", "routines")
        assertEquals(calIdBefore, cachedRowId)
        // Second sync — no new Calendars row.
        seedEvent("evt-2", "Another")
        perform()
        assertEquals(1, FakeSyncProvider.calendars.size)
        assertEquals(calIdBefore, FakeSyncProvider.calendars.keys.first())
        // _SYNC_ID identity preserved.
        assertNull(
            FakeSyncProvider.events.values.firstOrNull {
                it.getAsString(CalendarContract.Events._SYNC_ID) == "missing"
            },
        )
    }

    @Test fun missingRuntimeMakesSyncNoOp() {
        SkbSyncRuntime.repoStore = null
        // Should not throw.
        perform()
        assertEquals(0, FakeSyncProvider.events.size)
    }
}

/**
 * Minimal in-memory CalendarContract provider for the sync-adapter
 * test. Mirrors the `FakeWriterProvider` in `CalendarContractWriterTest`
 * but with `Calendars` + `_SYNC_ID` tracking the sync adapter relies on.
 */
internal class FakeSyncProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        values ?: return null
        val stripped = uri.buildUpon().clearQuery().build().toString()
        return when {
            stripped == CalendarContract.Calendars.CONTENT_URI.toString() -> {
                val id = nextCalendarId++
                calendars[id] = ContentValues(values)
                ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id)
            }
            stripped == CalendarContract.Events.CONTENT_URI.toString() -> {
                val id = nextEventId++
                events[id] = ContentValues(values)
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
            }
            stripped == CalendarContract.Reminders.CONTENT_URI.toString() -> {
                val id = nextReminderId++
                reminders[id] = ContentValues(values)
                ContentUris.withAppendedId(CalendarContract.Reminders.CONTENT_URI, id)
            }
            else -> null
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        values ?: return 0
        val stripped = uri.buildUpon().clearQuery().build().toString()
        if (stripped.startsWith(CalendarContract.Events.CONTENT_URI.toString())) {
            val id = uri.lastPathSegment?.toLongOrNull()
            if (id != null && events.containsKey(id)) {
                val merged = ContentValues(events[id]).apply { putAll(values) }
                events[id] = merged
                return 1
            }
        }
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val stripped = uri.buildUpon().clearQuery().build().toString()
        if (stripped.startsWith(CalendarContract.Events.CONTENT_URI.toString())) {
            val id = uri.lastPathSegment?.toLongOrNull() ?: return 0
            return if (events.remove(id) != null) 1 else 0
        }
        if (stripped == CalendarContract.Reminders.CONTENT_URI.toString()) {
            // selection: EVENT_ID = ?
            val eventId = selectionArgs?.firstOrNull()?.toLongOrNull() ?: return 0
            val before = reminders.size
            reminders.values.removeAll { it.getAsLong(CalendarContract.Reminders.EVENT_ID) == eventId }
            return before - reminders.size
        }
        return 0
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cols = projection ?: arrayOf(CalendarContract.Events._ID)
        val cursor = MatrixCursor(cols)
        val stripped = uri.buildUpon().clearQuery().build().toString()
        if (stripped == CalendarContract.Events.CONTENT_URI.toString()) {
            // ACCOUNT_TYPE = ? AND ACCOUNT_NAME = ? path.
            val type = selectionArgs?.getOrNull(0)
            val name = selectionArgs?.getOrNull(1)
            for ((eid, row) in events) {
                if (type != null && row.getAsString(CalendarContract.Events.ACCOUNT_TYPE) != type) continue
                if (name != null && row.getAsString(CalendarContract.Events.ACCOUNT_NAME) != name) continue
                cursor.addRow(
                    cols.map { col ->
                        when (col) {
                            CalendarContract.Events._ID -> eid
                            else -> row.get(col)
                        }
                    },
                )
            }
        } else if (stripped == CalendarContract.Calendars.CONTENT_URI.toString()) {
            // _ID = ? lookup used by calendarRowExists.
            val id = selectionArgs?.firstOrNull()?.toLongOrNull()
            if (id != null && calendars.containsKey(id)) {
                cursor.addRow(cols.map { if (it == CalendarContract.Calendars._ID) id else null })
            }
        }
        return cursor
    }

    companion object {
        val events: MutableMap<Long, ContentValues> = LinkedHashMap()
        val calendars: MutableMap<Long, ContentValues> = LinkedHashMap()
        val reminders: MutableMap<Long, ContentValues> = LinkedHashMap()
        var nextEventId: Long = 1L
        var nextCalendarId: Long = 100L
        var nextReminderId: Long = 1L

        fun reset() {
            events.clear()
            calendars.clear()
            reminders.clear()
            nextEventId = 1L
            nextCalendarId = 100L
            nextReminderId = 1L
        }
    }
}
