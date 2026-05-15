package com.eight87.strictlykeptboy.system

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.EventInput
import com.eight87.strictlykeptboy.resolver.EventRef
import com.eight87.strictlykeptboy.resolver.ExternalSource
import com.eight87.strictlykeptboy.resolver.RepoRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Round 2.18.A.6 — read-side adapter from
 * `CalendarContract.Instances` to the resolver's [EventInput] shape.
 *
 * **No write-back.** Phase D owns two-way edit. Phase A only feeds the
 * resolver.
 *
 * Round 2.18.B.7 — quiet-hours / dom-persona / mode register sourcing
 * for external events: system repos have no `identity.toml`, so any
 * notification / register / quiet-hours decision for an external event
 * inherits the **active write-target repo's** identity (the same
 * identity the user is currently editing in), not the synthetic
 * `system/<accountType>/<accountName>` repo. This bridge only emits
 * resolver-facing inputs; the actual identity inheritance happens in
 * the notif scheduler when it walks `RepoStore.activeRepoId` rather
 * than the event's `repo` field for register lookup.
 *
 * Window contract (A.9): the resolver renders for a per-view date range
 * — typically a few days for Week view, ~30 days for Month, etc. We
 * accept a `[fromMs, toMs)` epoch-millis pair and materialize the
 * Instances table for exactly that window. The
 * `CONTENT_BY_DAY_URI`-style begin/end-appended URI hands recurrence
 * expansion off to the provider — we never see RRULEs here, only
 * pre-expanded instances. (`RRULE` is still surfaced for the future
 * "edit-the-rule" path; today it's informational.)
 *
 * Permission contract (A.13): empty flow on missing `READ_CALENDAR`,
 * re-checked per query.
 *
 * Declined-event filter: rows with `STATUS = STATUS_CANCELED` are
 * dropped. `SELF_ATTENDEE_STATUS = ATTENDEE_STATUS_DECLINED` is
 * surfaced via `isBusy = false` — the user still sees the slot, but
 * the resolver doesn't treat it as time-blocking.
 */
class SystemEventsBridge(
    private val context: Context,
) {

    /**
     * Cold flow of events whose instances overlap `[fromMs, toMs)`.
     *
     * Re-emits when `Events.CONTENT_URI` notifies (covers
     * inserts/updates/deletes on any account's events).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun events(
        fromMs: Long,
        toMs: Long,
        knownCalendars: List<SystemCalendar>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<EventInput>> {
        require(toMs >= fromMs) { "SystemEventsBridge window inverted: from=$fromMs to=$toMs" }
        return ticker().flatMapLatest { _ ->
            kotlinx.coroutines.flow.flowOf(readOnce(fromMs, toMs, knownCalendars, zone))
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Single read of the window. Returns `emptyList()` on missing permission.
     *
     * [knownCalendars] is consulted to resolve `(accountType, accountName)`
     * for each row — CalendarContract.Instances itself does not surface
     * account columns, so we join via `CALENDAR_ID`. Unknown calendar IDs
     * (race: calendar deleted between cal-query and event-query) are dropped.
     */
    fun readOnce(
        fromMs: Long,
        toMs: Long,
        knownCalendars: List<SystemCalendar>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<EventInput> {
        if (!hasReadPermission()) return emptyList()
        return try {
            queryInstances(fromMs, toMs, knownCalendars.associateBy { it.id }, zone)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun ticker(): Flow<Unit> = callbackFlow {
        trySend(Unit)
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val resolver = context.contentResolver
        runCatching {
            resolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                /* notifyForDescendants = */ true,
                observer,
            )
        }
        awaitClose { runCatching { resolver.unregisterContentObserver(observer) } }
    }

    private fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun queryInstances(
        fromMs: Long,
        toMs: Long,
        calendarsById: Map<Long, SystemCalendar>,
        zone: ZoneId,
    ): List<EventInput> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, fromMs)
            ContentUris.appendId(this, toMs)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.EVENT_TIMEZONE,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.ACCESS_LEVEL,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Instances.OWNER_ACCOUNT,
        )
        val out = mutableListOf<EventInput>()
        context.contentResolver.query(
            uri,
            projection,
            /* selection = */ null,
            /* selectionArgs = */ null,
            /* sortOrder = */ "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val bgIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val enIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val adIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val tiIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val deIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
            val loIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            val tzIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_TIMEZONE)
            val ciIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
            val stIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.STATUS)
            val saIdx = c.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS)
            val alIdx = c.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ACCESS_LEVEL)
            val oaIdx = c.getColumnIndex(CalendarContract.Instances.OWNER_ACCOUNT)
            while (c.moveToNext()) {
                val status = if (!c.isNull(stIdx)) c.getInt(stIdx) else CalendarContract.Events.STATUS_CONFIRMED
                if (status == CalendarContract.Events.STATUS_CANCELED) continue
                val attendeeStatus = if (saIdx >= 0 && !c.isNull(saIdx)) c.getInt(saIdx) else CalendarContract.Attendees.ATTENDEE_STATUS_NONE
                val declined = attendeeStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
                val begin = c.getLong(bgIdx)
                val end = c.getLong(enIdx)
                val allDay = c.getInt(adIdx) != 0
                val eventTz = c.getString(tzIdx)?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
                val startZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(begin), if (allDay) ZoneId.of("UTC") else eventTz)
                val endZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(end), if (allDay) ZoneId.of("UTC") else eventTz)
                val eventId = c.getLong(idIdx)
                val calendarId = c.getLong(ciIdx)
                val cal = calendarsById[calendarId] ?: continue
                val accountType = cal.accountType
                val accountName = cal.accountName
                val title = c.getString(tiIdx).orEmpty()
                val body = c.getString(deIdx).orEmpty()
                val location = c.getString(loIdx)
                val accessLevel = c.getInt(alIdx)
                val ownerAccount = if (oaIdx >= 0) c.getString(oaIdx) else null
                // Stable per-instance ref: (eventId, beginMs) — recurring
                // instances all share `eventId`, so the begin disambiguates.
                val instanceRef = if (begin == 0L) "$eventId" else "$eventId@$begin"
                out += EventInput(
                    ref = EventRef("ext-$instanceRef"),
                    calendar = CalendarRef(calendarId.toString()),
                    repo = RepoRef("system/$accountType/$accountName"),
                    title = title,
                    start = startZdt,
                    end = endZdt,
                    isAllDay = allDay,
                    body = body,
                    location = location,
                    isBusy = !declined,
                    externalUid = null,
                    external = ExternalSource(
                        accountType = accountType,
                        accountName = accountName,
                        eventId = eventId,
                        accessLevel = accessLevel,
                        ownerAccount = ownerAccount,
                    ),
                )
            }
        }
        return out
    }
}
