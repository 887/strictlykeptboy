package com.eight87.strictlykeptboy.system

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.eight87.strictlykeptboy.resolver.EventInput
import com.eight87.strictlykeptboy.resolver.RecurrenceInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Round 2.18.D.1 — write surface for Android's `CalendarContract`.
 *
 * Single Responsibility: insert / update / delete `Events` rows, plus
 * RSVP writes on `Attendees`. Pure side-effecting wrapper around
 * `ContentResolver`; no skb-repo / Git plumbing whatsoever — the source
 * adapter (DAVx5, Google sync, Exchange) handles upstream push.
 *
 * **Permission contract (D.10):** every entry point re-checks
 * `WRITE_CALENDAR` at call-time. If missing we return a clean
 * [Result.failure] / `false` rather than throwing — the UI layer is the
 * one that owns the launcher + inline cue.
 *
 * **Thread contract:** every public function suspends and runs the
 * `ContentResolver` call on `Dispatchers.IO`.
 *
 * **CalendarContract writes never touch the skb repo filesystem.** No
 * `EntityWriter`, no Git, no `RepoStore.activeRepoId`. Phase D is purely
 * Android-system-DB-side.
 */
class CalendarContractWriter(
    private val context: Context,
) {

    /**
     * Insert a fresh event row. Returns the new event `_ID`, or
     * [Result.failure] when `WRITE_CALENDAR` is denied / the
     * resolver returns a null Uri (provider rejected the row).
     */
    suspend fun insertEvent(input: EventInput, calendarId: Long): Result<Long> =
        guardWrite {
            withContext(Dispatchers.IO) {
                val values = EventInputMapper.toContentValues(input, calendarId)
                val uri: Uri? = context.contentResolver.insert(
                    CalendarContract.Events.CONTENT_URI,
                    values,
                )
                val id = uri?.lastPathSegment?.toLongOrNull()
                if (id == null) {
                    Result.failure(IllegalStateException("insertEvent: provider returned no _ID"))
                } else {
                    Result.success(id)
                }
            }
        }

    /**
     * Update an existing event by _ID. Returns rows-affected.
     */
    suspend fun updateEvent(eventId: Long, input: EventInput, calendarId: Long): Result<Int> =
        guardWrite {
            withContext(Dispatchers.IO) {
                val uri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI, eventId,
                )
                val values = EventInputMapper.toContentValues(input, calendarId)
                Result.success(
                    context.contentResolver.update(uri, values, null, null),
                )
            }
        }

    /**
     * Delete an event by _ID. For recurring events the [scope] selects
     * the tri-choice behaviour (D.6, mirroring D.5).
     */
    suspend fun deleteEvent(
        eventId: Long,
        scope: RecurringEditScope = RecurringEditScope.All,
        instanceStartMs: Long? = null,
        parentRrule: String? = null,
        parentDtStartMs: Long? = null,
    ): Result<Int> = guardWrite {
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            when (scope) {
                RecurringEditScope.All -> {
                    val uri = ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_URI, eventId,
                    )
                    Result.success(resolver.delete(uri, null, null))
                }
                RecurringEditScope.ThisOnly -> {
                    // Cancel-this-instance: insert an override row with
                    // STATUS_CANCELED + ORIGINAL_ID + ORIGINAL_INSTANCE_TIME.
                    require(instanceStartMs != null) {
                        "ThisOnly delete needs instanceStartMs"
                    }
                    val cv = ContentValues().apply {
                        put(CalendarContract.Events.ORIGINAL_ID, eventId)
                        put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceStartMs)
                        put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                    }
                    val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, cv)
                    Result.success(if (uri == null) 0 else 1)
                }
                RecurringEditScope.ThisAndFollowing -> {
                    require(instanceStartMs != null && parentRrule != null) {
                        "ThisAndFollowing delete needs instanceStartMs + parentRrule"
                    }
                    val updated = capRruleUntil(eventId, parentRrule, instanceStartMs)
                    Result.success(updated)
                }
            }
        }
    }

    /**
     * Set the current user's `ATTENDEE_STATUS` for [eventId]. Finds the
     * matching attendee row by [userEmail] (typically the calendar's
     * `ownerAccount`).
     */
    suspend fun respondToInvite(
        eventId: Long,
        userEmail: String,
        response: InviteResponse,
    ): Result<Boolean> = guardWrite {
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(CalendarContract.Attendees.ATTENDEE_STATUS, response.statusCode)
            }
            val rows = resolver.update(
                CalendarContract.Attendees.CONTENT_URI,
                values,
                "${CalendarContract.Attendees.EVENT_ID} = ? AND " +
                    "${CalendarContract.Attendees.ATTENDEE_EMAIL} = ?",
                arrayOf(eventId.toString(), userEmail),
            )
            // Also bump the event's SELF_ATTENDEE_STATUS for UI snappiness;
            // the sync adapter will reconcile when it next runs (D.9).
            if (rows > 0) {
                val evUri = ContentUris.withAppendedId(
                    CalendarContract.Events.CONTENT_URI, eventId,
                )
                val evVals = ContentValues().apply {
                    put(CalendarContract.Events.SELF_ATTENDEE_STATUS, response.statusCode)
                }
                runCatching { resolver.update(evUri, evVals, null, null) }
            }
            Result.success(rows > 0)
        }
    }

    /**
     * D.5 "this and following" branch helper. Caps the parent's `RRULE`
     * with `UNTIL=<instanceStart - 1s>` and writes it back. The caller is
     * expected to follow up by inserting a new recurring event from the
     * instance with the user's edits applied.
     */
    suspend fun capRecurrenceUntil(
        parentEventId: Long,
        currentRrule: String,
        instanceStartMs: Long,
    ): Result<Int> = guardWrite {
        withContext(Dispatchers.IO) {
            Result.success(capRruleUntil(parentEventId, currentRrule, instanceStartMs))
        }
    }

    /**
     * D.5 "this only" override row. Inserts a child event row pinned to
     * [parentEventId] + [instanceStartMs] with overrides from [input].
     * Returns the new override event _ID.
     */
    suspend fun insertRecurrenceOverride(
        parentEventId: Long,
        instanceStartMs: Long,
        input: EventInput,
        calendarId: Long,
    ): Result<Long> = guardWrite {
        withContext(Dispatchers.IO) {
            val cv = EventInputMapper.toContentValues(input, calendarId).apply {
                put(CalendarContract.Events.ORIGINAL_ID, parentEventId)
                put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceStartMs)
                // Single-instance override: clear any RRULE we copied through.
                remove(CalendarContract.Events.RRULE)
            }
            val uri = context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI, cv,
            )
            val id = uri?.lastPathSegment?.toLongOrNull()
            if (id == null) {
                Result.failure(IllegalStateException("insertRecurrenceOverride: null Uri"))
            } else {
                Result.success(id)
            }
        }
    }

    /**
     * Inserts a new recurring event starting at [recurrence] — the D.5
     * "this and following" right-hand side. Caller already capped the
     * parent series with [capRecurrenceUntil].
     */
    suspend fun insertRecurrence(
        recurrence: RecurrenceInput,
        calendarId: Long,
        title: String,
        body: String,
        location: String?,
    ): Result<Long> = guardWrite {
        withContext(Dispatchers.IO) {
            val cv = RecurrenceInputMapper.toContentValues(
                recurrence, calendarId, title, body, location,
            )
            val uri = context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI, cv,
            )
            val id = uri?.lastPathSegment?.toLongOrNull()
            if (id == null) {
                Result.failure(IllegalStateException("insertRecurrence: null Uri"))
            } else {
                Result.success(id)
            }
        }
    }

    // -------------------------------------------------------------------
    // internals
    // -------------------------------------------------------------------

    private fun capRruleUntil(
        eventId: Long,
        currentRrule: String,
        instanceStartMs: Long,
    ): Int {
        val until = RecurrenceRuleSerializer.formatUntilUtc(instanceStartMs)
        val capped = RecurrenceRuleSerializer.withUntil(currentRrule, until)
        val uri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI, eventId,
        )
        val cv = ContentValues().apply {
            put(CalendarContract.Events.RRULE, capped)
        }
        return context.contentResolver.update(uri, cv, null, null)
    }

    private inline fun <T> guardWrite(block: () -> Result<T>): Result<T> {
        if (!hasWritePermission()) {
            return Result.failure(SecurityException("WRITE_CALENDAR not granted"))
        }
        return try {
            block()
        } catch (t: SecurityException) {
            Result.failure(t)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Round 2.18.D.5/D.6 — tri-choice for recurring-event edit + delete.
 * Mandatory prompt before any save / delete on a recurring instance;
 * no silent default.
 */
enum class RecurringEditScope {
    /** Override this instance only (override row pinned to parent). */
    ThisOnly,

    /** Cap parent UNTIL=instanceStart and start a new series. */
    ThisAndFollowing,

    /** Update the parent event row (mutates the whole series). */
    All,
}

/**
 * Round 2.18.D.7 — RSVP response that maps onto
 * `Attendees.ATTENDEE_STATUS_*`.
 */
enum class InviteResponse(val statusCode: Int) {
    Accepted(CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED),
    Tentative(CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE),
    Declined(CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED),
}

/**
 * Round 2.18.D.3 — `EventInput` → `ContentValues` mapper.
 *
 * Skb's frontmatter (tags, isPrivate, supersedes, neutralSafe, etc.) has
 * no CalendarContract analog: tags / supersedence are skb-only
 * structural metadata. Emoji becomes a title prefix, body becomes
 * `DESCRIPTION`. Anything else is dropped at the contract boundary —
 * round-trip fidelity for those fields requires skb-side storage.
 */
object EventInputMapper {

    /**
     * @param calendarId target `Calendars._ID` row (we never let the
     *   caller move an event between calendars by accident — picked
     *   explicitly).
     */
    fun toContentValues(input: EventInput, calendarId: Long): ContentValues {
        val cv = ContentValues()
        cv.put(CalendarContract.Events.CALENDAR_ID, calendarId)

        // Title: emoji prefix if present, otherwise raw title.
        val title = input.emoji?.takeIf { it.isNotBlank() }
            ?.let { "$it ${input.title}" }
            ?: input.title
        cv.put(CalendarContract.Events.TITLE, title)
        cv.put(CalendarContract.Events.DESCRIPTION, input.body)
        cv.put(CalendarContract.Events.EVENT_LOCATION, input.location.orEmpty())

        val tz = input.start.zone
        if (input.isAllDay) {
            // CalendarContract docs: all-day events MUST use UTC tz and
            // start-of-day epoch ms; provider will reject otherwise.
            cv.put(CalendarContract.Events.ALL_DAY, 1)
            cv.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            val startUtc = input.start
                .toLocalDate()
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
            val endUtc = input.end
                .toLocalDate()
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
            cv.put(CalendarContract.Events.DTSTART, startUtc)
            // For all-day we may also use DURATION P<n>D, but DTEND is
            // accepted for non-recurring all-day events.
            cv.put(CalendarContract.Events.DTEND, endUtc)
        } else {
            cv.put(CalendarContract.Events.ALL_DAY, 0)
            cv.put(CalendarContract.Events.EVENT_TIMEZONE, tz.id)
            cv.put(CalendarContract.Events.DTSTART, input.start.toInstant().toEpochMilli())
            cv.put(CalendarContract.Events.DTEND, input.end.toInstant().toEpochMilli())
        }

        cv.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        cv.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT)
        cv.put(
            CalendarContract.Events.AVAILABILITY,
            if (input.isBusy) CalendarContract.Events.AVAILABILITY_BUSY
            else CalendarContract.Events.AVAILABILITY_FREE,
        )
        return cv
    }
}

/**
 * Round 2.18.D.4 — `RecurrenceInput` → `ContentValues` mapper.
 *
 * The recurrence rule itself is serialized by
 * [RecurrenceRuleSerializer]. EXDATE is rendered into the CalendarContract
 * `EXDATE` column.
 */
object RecurrenceInputMapper {

    fun toContentValues(
        input: RecurrenceInput,
        calendarId: Long,
        title: String,
        body: String,
        location: String?,
    ): ContentValues {
        val cv = ContentValues()
        cv.put(CalendarContract.Events.CALENDAR_ID, calendarId)
        cv.put(CalendarContract.Events.TITLE, title)
        cv.put(CalendarContract.Events.DESCRIPTION, body)
        cv.put(CalendarContract.Events.EVENT_LOCATION, location.orEmpty())
        cv.put(CalendarContract.Events.EVENT_TIMEZONE, input.tzId.id)
        cv.put(CalendarContract.Events.DTSTART, input.dtstart.toInstant().toEpochMilli())
        // Recurring events use DURATION (ISO-8601), NOT DTEND. CalendarContract
        // docs say a recurring event with DTEND is rejected.
        cv.put(CalendarContract.Events.DURATION, input.duration.toString())
        cv.put(CalendarContract.Events.RRULE, input.rrule)
        if (input.exdates.isNotEmpty()) {
            cv.put(
                CalendarContract.Events.EXDATE,
                RecurrenceRuleSerializer.formatExdate(input.exdates, input.tzId),
            )
        }
        cv.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        cv.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT)
        cv.put(
            CalendarContract.Events.AVAILABILITY,
            if (input.isBusy) CalendarContract.Events.AVAILABILITY_BUSY
            else CalendarContract.Events.AVAILABILITY_FREE,
        )
        return cv
    }
}
