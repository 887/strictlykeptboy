package com.eight87.strictlykeptboy.system

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.SyncResult
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import com.eight87.strictlykeptboy.cache.entities.EventRow
import com.eight87.strictlykeptboy.git.RepoConfig
import kotlinx.coroutines.runBlocking
import java.util.TimeZone

/**
 * Round 2.18.G.3 — the sync adapter that mirrors skb's repo events into
 * `CalendarContract`.
 *
 * Contract:
 *  - One Android [Account] per skb repo, name = `<repoId>@local`,
 *    type = [SkbAccountAuthenticator.SKB_ACCOUNT_TYPE].
 *  - One `Calendars` row per `(repoId, skbCalendarId)`. The row's
 *    `_ID` is cached in [SystemCalendarPrefsStore.calendarRowId] so
 *    every subsequent sync reuses it (G.8 idempotency).
 *  - One `Events` row per skb event. `_SYNC_ID = <skbEventId>` so we
 *    can match for update / delete without ambiguity (G.9).
 *  - Every write uses `CALLER_IS_SYNCADAPTER=true` so the provider
 *    accepts it without `WRITE_CALENDAR` and without the usual
 *    skb-app-side per-event ACL.
 *
 * One-way: skb → OS. We never read events back via this adapter — the
 * file-on-disk repo is the source of truth.
 */
class SkbCalendarSyncAdapter(
    context: Context,
    autoInitialize: Boolean,
    allowParallelSyncs: Boolean = false,
) : AbstractThreadedSyncAdapter(context, autoInitialize, allowParallelSyncs) {

    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult,
    ) {
        val repoStore = SkbSyncRuntime.repoStore
        val cache = SkbSyncRuntime.cacheDatabase
        val prefs = SkbSyncRuntime.systemCalendarPrefs
        if (repoStore == null || cache == null || prefs == null) {
            // AppGraph hasn't parked yet (boot race), or the toggle just
            // got flipped on before parkRuntimes ran. Caller will retry
            // via the regular tick + commit-hook path.
            return
        }
        val repoId = SkbAccountAuthenticator.repoIdFor(account.name) ?: return
        val cfg = repoStore.get(repoId) ?: return

        val rows: List<EventRow> = runBlocking { cache.events().listAll(repoId) }

        // Materialize one Calendars row per (repo, skbCalendarId).
        val skbCalendarIds = rows.map { it.calendarId }.distinct()
        val calendarRowIds = skbCalendarIds.associateWith { skbCalId ->
            ensureCalendarRow(provider, account, cfg, skbCalId, prefs, syncResult)
        }

        // Read existing Events rows for this account; key by _SYNC_ID
        // (= skb event UUIDv7) so we can diff.
        val existing: Map<String, Long> = readExistingEvents(provider, account)
        val skbBySyncId: Map<String, EventRow> = rows.associateBy { it.id }

        val toInsert = rows.filter { it.id !in existing }
        val toUpdate = rows.filter { it.id in existing }
        val toDelete = existing.filterKeys { it !in skbBySyncId }

        for (ev in toInsert) {
            val calRowId = calendarRowIds[ev.calendarId] ?: continue
            insertEvent(provider, account, calRowId, ev, syncResult)
        }
        for (ev in toUpdate) {
            val existingId = existing[ev.id] ?: continue
            val calRowId = calendarRowIds[ev.calendarId] ?: continue
            updateEvent(provider, account, existingId, calRowId, ev, syncResult)
        }
        for ((_, eventRowId) in toDelete) {
            deleteEvent(provider, account, eventRowId, syncResult)
        }
    }

    // ----------------------------------------------------------------
    // Calendars
    // ----------------------------------------------------------------

    private fun ensureCalendarRow(
        provider: ContentProviderClient,
        account: Account,
        cfg: RepoConfig,
        skbCalendarId: String,
        prefs: SystemCalendarPrefsStore,
        syncResult: SyncResult,
    ): Long? {
        val cached = prefs.calendarRowId(cfg.repoId, skbCalendarId)
        if (cached != null && calendarRowExists(provider, cached)) return cached

        val tz = TimeZone.getDefault().id
        val displayName = "${cfg.displayName} · $skbCalendarId"
        // G.10 — CAL_ACCESS_OWNER (700) so other apps treat us as full
        // owner. G.11 — CALENDAR_COLOR from repo's colorSeed.
        val cv = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
            put(CalendarContract.Calendars.NAME, "${cfg.repoId}:$skbCalendarId")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(CalendarContract.Calendars.CALENDAR_COLOR, cfg.colorSeed ?: DEFAULT_CALENDAR_COLOR)
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.OWNER_ACCOUNT, account.name)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, tz)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }
        val syncUri = asSyncAdapter(CalendarContract.Calendars.CONTENT_URI, account)
        val uri = runCatching { provider.insert(syncUri, cv) }
            .onFailure { syncResult.databaseError = true }
            .getOrNull()
        val id = uri?.lastPathSegment?.toLongOrNull() ?: return null
        prefs.setCalendarRowId(cfg.repoId, skbCalendarId, id)
        return id
    }

    private fun calendarRowExists(provider: ContentProviderClient, calendarId: Long): Boolean {
        val cursor = runCatching {
            provider.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars._ID} = ?",
                arrayOf(calendarId.toString()),
                null,
            )
        }.getOrNull() ?: return false
        cursor.use { return it.count > 0 }
    }

    // ----------------------------------------------------------------
    // Events — read / insert / update / delete
    // ----------------------------------------------------------------

    private fun readExistingEvents(
        provider: ContentProviderClient,
        account: Account,
    ): Map<String, Long> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events._SYNC_ID,
        )
        val selection =
            "${CalendarContract.Events.ACCOUNT_TYPE} = ? AND " +
                "${CalendarContract.Events.ACCOUNT_NAME} = ?"
        val args = arrayOf(account.type, account.name)
        val out = mutableMapOf<String, Long>()
        val cursor = runCatching {
            provider.query(
                asSyncAdapter(CalendarContract.Events.CONTENT_URI, account),
                projection,
                selection,
                args,
                null,
            )
        }.getOrNull() ?: return emptyMap()
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val syncId = c.getString(1) ?: continue
                out[syncId] = id
            }
        }
        return out
    }

    private fun insertEvent(
        provider: ContentProviderClient,
        account: Account,
        calendarRowId: Long,
        ev: EventRow,
        syncResult: SyncResult,
    ) {
        val cv = toContentValues(ev, calendarRowId, account)
        cv.put(CalendarContract.Events._SYNC_ID, ev.id)
        val syncUri = asSyncAdapter(CalendarContract.Events.CONTENT_URI, account)
        val uri = runCatching { provider.insert(syncUri, cv) }
            .onFailure { syncResult.databaseError = true }
            .getOrNull()
        val eventId = uri?.lastPathSegment?.toLongOrNull()
        if (eventId != null) {
            insertRemindersFor(provider, account, eventId, ev)
        }
    }

    private fun updateEvent(
        provider: ContentProviderClient,
        account: Account,
        existingEventId: Long,
        calendarRowId: Long,
        ev: EventRow,
        syncResult: SyncResult,
    ) {
        val cv = toContentValues(ev, calendarRowId, account)
        val rowUri = android.content.ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            existingEventId,
        )
        val syncUri = asSyncAdapter(rowUri, account)
        runCatching { provider.update(syncUri, cv, null, null) }
            .onFailure { syncResult.databaseError = true }
        // G.12 — refresh reminder mirror (drop + re-insert is simplest).
        runCatching {
            provider.delete(
                asSyncAdapter(CalendarContract.Reminders.CONTENT_URI, account),
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(existingEventId.toString()),
            )
        }
        insertRemindersFor(provider, account, existingEventId, ev)
    }

    private fun deleteEvent(
        provider: ContentProviderClient,
        account: Account,
        existingEventId: Long,
        syncResult: SyncResult,
    ) {
        val rowUri = android.content.ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI,
            existingEventId,
        )
        val syncUri = asSyncAdapter(rowUri, account)
        runCatching { provider.delete(syncUri, null, null) }
            .onFailure { syncResult.databaseError = true }
    }

    // ----------------------------------------------------------------
    // Reminders mirror (G.12)
    // ----------------------------------------------------------------

    private fun insertRemindersFor(
        provider: ContentProviderClient,
        account: Account,
        eventId: Long,
        @Suppress("UNUSED_PARAMETER") ev: EventRow,
    ) {
        // The skb cache `EventRow` doesn't carry reminder offsets in
        // the v1 schema; reminder lead times live alongside the event
        // file as TOML frontmatter and are projected through
        // `EventReminderScheduler`. For the OS mirror we publish a
        // single default 10-minute reminder so other apps (Wear OS)
        // see *some* signal. The full per-event lead-time projection
        // is tracked as a Phase H follow-up.
        val cv = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, DEFAULT_REMINDER_MINUTES)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        runCatching {
            provider.insert(
                asSyncAdapter(CalendarContract.Reminders.CONTENT_URI, account),
                cv,
            )
        }
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private fun toContentValues(ev: EventRow, calendarRowId: Long, account: Account): ContentValues =
        ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarRowId)
            put(CalendarContract.Events.ACCOUNT_NAME, account.name)
            put(CalendarContract.Events.ACCOUNT_TYPE, account.type)
            val title = ev.emoji?.takeIf { it.isNotBlank() }
                ?.let { "$it ${ev.title}" } ?: ev.title
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, ev.body)
            put(CalendarContract.Events.EVENT_LOCATION, ev.location.orEmpty())
            put(CalendarContract.Events.DTSTART, ev.startEpochMs)
            put(CalendarContract.Events.DTEND, ev.endEpochMs)
            put(CalendarContract.Events.ALL_DAY, if (ev.allDay) 1 else 0)
            put(
                CalendarContract.Events.EVENT_TIMEZONE,
                if (ev.allDay) "UTC" else TimeZone.getDefault().id,
            )
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
            put(
                CalendarContract.Events.AVAILABILITY,
                if (ev.busy) CalendarContract.Events.AVAILABILITY_BUSY
                else CalendarContract.Events.AVAILABILITY_FREE,
            )
            put(
                CalendarContract.Events.ACCESS_LEVEL,
                if (ev.privateFlag) CalendarContract.Events.ACCESS_PRIVATE
                else CalendarContract.Events.ACCESS_DEFAULT,
            )
        }

    companion object {
        const val DEFAULT_CALENDAR_COLOR: Int = -0x9b6b00 // M3E primary-ish fallback
        const val DEFAULT_REMINDER_MINUTES: Int = 10

        /**
         * Append `CALLER_IS_SYNCADAPTER=true` + the account info to a
         * CalendarContract Uri so the provider accepts writes that
         * bypass `WRITE_CALENDAR` (G — critical constraint).
         */
        fun asSyncAdapter(uri: Uri, account: Account): Uri =
            uri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
                .build()
    }
}

@Suppress("unused")
private fun ContentResolver.unusedSentinel() = Unit
