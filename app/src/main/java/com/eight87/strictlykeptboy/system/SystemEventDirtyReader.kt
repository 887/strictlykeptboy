package com.eight87.strictlykeptboy.system

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/**
 * Round 2.18.D.8 — read `Events.DIRTY` for a given event id.
 *
 * When [CalendarContractWriter.updateEvent] writes through, the provider
 * sets `DIRTY = 1` on the row until the source sync adapter pushes it
 * upstream. UI surfaces a passive "Sync pending" badge while DIRTY = 1;
 * the badge clears on the next ContentObserver tick once the sync
 * adapter finishes.
 *
 * Empty / `false` on missing READ_CALENDAR — never throws.
 */
class SystemEventDirtyReader(private val context: Context) {

    fun isDirty(eventId: Long): Boolean {
        if (!hasReadPermission()) return false
        return try {
            queryDirty(eventId)
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun queryDirty(eventId: Long): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Events.DIRTY),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(CalendarContract.Events.DIRTY)
                if (idx >= 0 && !c.isNull(idx)) return c.getInt(idx) != 0
            }
        }
        return false
    }
}
