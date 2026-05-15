package com.eight87.strictlykeptboy.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

/**
 * Round 2.18.A.1 — read-side wrapper around `CalendarContract.Calendars`.
 *
 * Single Responsibility: surface the OS's list of *visible + syncing*
 * calendars across every Android Account the device knows about. Emits
 * once on subscribe, then re-emits on every `ContentObserver`
 * notification against `Calendars.CONTENT_URI`.
 *
 * Permission contract (A.13):
 * - If `READ_CALENDAR` is not granted, every emission is `emptyList()`.
 *   We never let a `SecurityException` bubble out.
 * - Permission is re-checked on **every** query, not cached — the user
 *   may grant the permission in OS Settings while skb is still
 *   subscribed, and we want the next observer tick to pick that up.
 *
 * Concurrency:
 * - The `ContentObserver` is registered on a `Handler(Looper.getMainLooper())`
 *   because `ContentResolver.registerContentObserver` requires a Looper;
 *   the actual ContentResolver query runs on whatever dispatcher the
 *   collector is on — we `flowOn(Dispatchers.IO)` at the boundary.
 *
 * Not in this phase:
 * - Permission request UX (Phase B).
 * - Writeback (Phase D).
 * - Per-calendar visibility/priority overrides (those live in
 *   [SystemCalendarPrefsStore] + are applied in
 *   [SystemCalendarsRepository]).
 */
class CalendarContractBridge(
    private val context: Context,
) {

    /**
     * Cold flow of the device's currently-visible system calendars.
     *
     * Filter: `VISIBLE = 1 AND SYNC_EVENTS = 1` — same filter Etar uses
     * for its account picker. Per A.1.
     */
    fun calendars(): Flow<List<SystemCalendar>> = callbackFlow {
        // First emission: read or empty if no permission.
        trySend(queryOrEmpty())

        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(queryOrEmpty())
            }
        }
        val resolver = context.contentResolver
        runCatching {
            resolver.registerContentObserver(
                CalendarContract.Calendars.CONTENT_URI,
                /* notifyForDescendants = */ true,
                observer,
            )
        }
        awaitClose {
            runCatching { resolver.unregisterContentObserver(observer) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Synchronous one-shot read. Returns `emptyList()` on missing
     * permission or any I/O error.
     */
    fun readOnce(): List<SystemCalendar> = queryOrEmpty()

    private fun queryOrEmpty(): List<SystemCalendar> {
        if (!hasReadPermission()) return emptyList()
        return try {
            queryCalendars()
        } catch (_: SecurityException) {
            // Race: permission revoked between check and query. Treat as empty.
            emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun queryCalendars(): List<SystemCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.SYNC_EVENTS,
            CalendarContract.Calendars.VISIBLE,
        )
        // Filter to visible + syncing — same surface Etar exposes.
        val selection =
            "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
                "${CalendarContract.Calendars.SYNC_EVENTS} = 1"
        val out = mutableListOf<SystemCalendar>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            /* selectionArgs = */ null,
            /* sortOrder = */ "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, " +
                "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
        )?.use { c ->
            val idIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val anIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val atIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            val dnIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val coIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
            val alIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            val oaIdx = c.getColumnIndexOrThrow(CalendarContract.Calendars.OWNER_ACCOUNT)
            val ipIdx = c.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            while (c.moveToNext()) {
                out += SystemCalendar(
                    id = c.getLong(idIdx),
                    accountName = c.getString(anIdx).orEmpty(),
                    accountType = c.getString(atIdx).orEmpty(),
                    displayName = c.getString(dnIdx) ?: c.getString(anIdx).orEmpty(),
                    color = c.getInt(coIdx),
                    accessLevel = c.getInt(alIdx),
                    ownerAccount = c.getString(oaIdx),
                    isPrimary = if (ipIdx >= 0 && !c.isNull(ipIdx)) c.getInt(ipIdx) != 0 else false,
                )
            }
        }
        return out
    }
}
