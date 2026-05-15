package com.eight87.strictlykeptboy.system

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/**
 * Round 2.18.C.8 — read-side adapter from `CalendarContract.Attendees`
 * to a Kotlin-facing list.
 *
 * Pure read-only; Phase D adds the corresponding writer. Permission
 * re-checked at query time.
 *
 * Status code mapping (mirrors `Attendees.ATTENDEE_STATUS_*`):
 *   - `STATUS_NONE = 0`
 *   - `STATUS_ACCEPTED = 1`
 *   - `STATUS_DECLINED = 2`
 *   - `STATUS_INVITED = 3`
 *   - `STATUS_TENTATIVE = 4`
 */
class SystemAttendeesReader(private val context: Context) {

    /**
     * One-shot read of the attendees table for a given event id. Returns
     * an empty list when the permission isn't granted or the cursor is
     * null. Safe to call from a background dispatcher.
     */
    fun readForEvent(eventId: Long): List<ExternalAttendee> {
        if (!hasReadPermission()) return emptyList()
        return try {
            queryAttendees(eventId)
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun queryAttendees(eventId: Long): List<ExternalAttendee> {
        val projection = arrayOf(
            CalendarContract.Attendees.ATTENDEE_NAME,
            CalendarContract.Attendees.ATTENDEE_EMAIL,
            CalendarContract.Attendees.ATTENDEE_STATUS,
            CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
        )
        val out = mutableListOf<ExternalAttendee>()
        context.contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            projection,
            "${CalendarContract.Attendees.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { c ->
            val nIdx = c.getColumnIndex(CalendarContract.Attendees.ATTENDEE_NAME)
            val eIdx = c.getColumnIndex(CalendarContract.Attendees.ATTENDEE_EMAIL)
            val sIdx = c.getColumnIndex(CalendarContract.Attendees.ATTENDEE_STATUS)
            val rIdx = c.getColumnIndex(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP)
            while (c.moveToNext()) {
                out += ExternalAttendee(
                    displayName = if (nIdx >= 0) c.getString(nIdx).orEmpty() else "",
                    email = if (eIdx >= 0) c.getString(eIdx).orEmpty() else "",
                    statusCode = if (sIdx >= 0 && !c.isNull(sIdx)) c.getInt(sIdx) else 0,
                    relationship = if (rIdx >= 0 && !c.isNull(rIdx)) c.getInt(rIdx) else 0,
                )
            }
        }
        return out
    }

    @Suppress("unused")
    private fun eventUri(eventId: Long) =
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
}

/**
 * Pure value type. `statusCode` mirrors `Attendees.ATTENDEE_STATUS_*` —
 * we don't enum it here so the UI can render an "unknown" fallback for
 * future provider values without a code change.
 */
data class ExternalAttendee(
    val displayName: String,
    val email: String,
    val statusCode: Int,
    val relationship: Int,
)
