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
class SystemRemindersReader(private val context: Context) {

    fun readForEvent(eventId: Long): List<ExternalReminder> {
        if (!hasReadPermission()) return emptyList()
        return try {
            queryReminders(eventId)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
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
}

data class ExternalReminder(
    /** Lead time in minutes before the event start. */
    val minutes: Int,
    /** `Reminders.METHOD_*` constant. */
    val method: Int,
)
