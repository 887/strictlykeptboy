package com.eight87.strictlykeptboy.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/**
 * Round 2.18.C.9 — read-side adapter from `CalendarContract.Reminders`
 * to a Kotlin-facing list.
 *
 * Method codes mirror `Reminders.METHOD_*`:
 *   - `METHOD_DEFAULT = 0`
 *   - `METHOD_ALERT = 1`
 *   - `METHOD_EMAIL = 2`
 *   - `METHOD_SMS = 3`
 *   - `METHOD_ALARM = 4`
 */
open class SystemRemindersReader(private val context: Context) {

    open fun readForEvent(eventId: Long): List<ExternalReminder> {
        if (!hasReadPermission()) return emptyList()
        return try {
            queryReminders(eventId)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Round 2.18.F.1 — window-scale batched read. Issues one
     * `CalendarContract.Reminders` query with `EVENT_ID IN (...)` so we
     * don't pay the IPC cost per-event when the visible window has many
     * external events. Missing permission ⇒ empty map.
     */
    open fun readForEvents(eventIds: List<Long>): Map<Long, List<ExternalReminder>> {
        if (eventIds.isEmpty()) return emptyMap()
        if (!hasReadPermission()) return emptyMap()
        return try {
            queryRemindersBatch(eventIds.distinct())
        } catch (_: SecurityException) {
            emptyMap()
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun queryReminders(eventId: Long): List<ExternalReminder> {
        val projection = arrayOf(
            CalendarContract.Reminders.MINUTES,
            CalendarContract.Reminders.METHOD,
        )
        val out = mutableListOf<ExternalReminder>()
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            projection,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            CalendarContract.Reminders.MINUTES + " ASC",
        )?.use { c ->
            val mIdx = c.getColumnIndex(CalendarContract.Reminders.MINUTES)
            val xIdx = c.getColumnIndex(CalendarContract.Reminders.METHOD)
            while (c.moveToNext()) {
                out += ExternalReminder(
                    minutes = if (mIdx >= 0 && !c.isNull(mIdx)) c.getInt(mIdx) else 0,
                    method = if (xIdx >= 0 && !c.isNull(xIdx)) c.getInt(xIdx) else 0,
                )
            }
        }
        return out
    }

    private fun queryRemindersBatch(eventIds: List<Long>): Map<Long, List<ExternalReminder>> {
        val projection = arrayOf(
            CalendarContract.Reminders.EVENT_ID,
            CalendarContract.Reminders.MINUTES,
            CalendarContract.Reminders.METHOD,
        )
        val placeholders = eventIds.joinToString(",") { "?" }
        val args = eventIds.map { it.toString() }.toTypedArray()
        val out = HashMap<Long, MutableList<ExternalReminder>>()
        for (id in eventIds) out[id] = mutableListOf()
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            projection,
            "${CalendarContract.Reminders.EVENT_ID} IN ($placeholders)",
            args,
            CalendarContract.Reminders.MINUTES + " ASC",
        )?.use { c ->
            val eIdx = c.getColumnIndex(CalendarContract.Reminders.EVENT_ID)
            val mIdx = c.getColumnIndex(CalendarContract.Reminders.MINUTES)
            val xIdx = c.getColumnIndex(CalendarContract.Reminders.METHOD)
            while (c.moveToNext()) {
                if (eIdx < 0 || c.isNull(eIdx)) continue
                val evId = c.getLong(eIdx)
                val rem = ExternalReminder(
                    minutes = if (mIdx >= 0 && !c.isNull(mIdx)) c.getInt(mIdx) else 0,
                    method = if (xIdx >= 0 && !c.isNull(xIdx)) c.getInt(xIdx) else 0,
                )
                out.getOrPut(evId) { mutableListOf() }.add(rem)
            }
        }
        return out.mapValues { it.value.toList() }
    }
}

data class ExternalReminder(
    /** Lead time in minutes before the event start. */
    val minutes: Int,
    /** `Reminders.METHOD_*` constant. */
    val method: Int,
)
