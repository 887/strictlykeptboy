package com.eight87.strictlykeptboy.system

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Round 2.18.E.6 — classify incoming OS calendar intents into a
 * skb-internal routing decision.
 *
 * Pure Kotlin + Android `Intent` / `Uri` reading only — no Compose,
 * no side effects, no I/O. Caller (MainActivity) consumes the result
 * and dispatches into the existing Compose surfaces (Schedule pinning,
 * EventDetailSheet, event editor, IcsImportScreen).
 *
 * The OS contracts this layer handles (see Android docs
 * `CalendarContract` + `Intent` + the standard calendar app skills):
 *
 *   VIEW content://com.android.calendar/time/<epoch-seconds>
 *     → jump the calendar UI to that date.
 *
 *   VIEW content://<authority>/events/<id>
 *     with MIME `vnd.android.cursor.item/event`
 *     → open the detail sheet for that event row.
 *
 *   EDIT / INSERT vnd.android.cursor.item/event
 *     with optional extras EXTRA_EVENT_BEGIN_TIME / _END_TIME /
 *     _ALL_DAY / TITLE / DESCRIPTION / EVENT_LOCATION
 *     → open the event editor prefilled.
 *
 *   VIEW text/calendar (file:// or content://)
 *     → open the .ics import flow on that payload.
 *
 *   VIEW https://&lt;host&gt;/&lt;path&gt;.ics (or http)
 *     → fetch + open the .ics import flow.
 *
 * Phase MM `strictlykeptboy://...` deep links and Phase O share links
 * deliberately fall through to [RoutedIntent.Unhandled] — those
 * surfaces own their own routers.
 */
object CalendarIntentRouter {

    /**
     * Classify an incoming intent. Returns [RoutedIntent.Unhandled]
     * when no calendar-contract pattern matches; the caller then
     * forwards the intent to other handlers (share link, MM deep
     * link, etc.) without further mutation.
     */
    fun classify(intent: Intent): RoutedIntent {
        val action = intent.action ?: return RoutedIntent.Unhandled
        val data: Uri? = intent.data
        val mime: String? = intent.type

        when (action) {
            Intent.ACTION_VIEW -> {
                // Time / epoch → jump to date.
                if (data != null && isTimeEpoch(data)) {
                    val epochSeconds = data.lastPathSegment?.toLongOrNull()
                    val date = epochSeconds?.let { secondsToLocalDate(it) }
                        ?: return RoutedIntent.Unhandled
                    return RoutedIntent.GoToDate(date)
                }
                // Event detail row.
                if (mime == "vnd.android.cursor.item/event" ||
                    (data != null && isEventsRow(data))
                ) {
                    val rowId = data?.lastPathSegment?.toLongOrNull()
                    return if (rowId != null) {
                        RoutedIntent.ShowEvent(eventId = rowId)
                    } else {
                        RoutedIntent.Unhandled
                    }
                }
                // .ics import — local file/content.
                if (mime == "text/calendar" && data != null) {
                    return RoutedIntent.ImportIcs(source = IcsSource.LocalUri(data))
                }
                // .ics import — http(s) URL to an .ics file. Match by
                // path suffix; this is the BROWSABLE filter.
                if (data != null && isIcsUrl(data)) {
                    return RoutedIntent.ImportIcs(source = IcsSource.RemoteUrl(data))
                }
            }
            Intent.ACTION_EDIT, Intent.ACTION_INSERT -> {
                val isEventMime = mime == "vnd.android.cursor.item/event" ||
                    mime == "vnd.android.cursor.dir/event"
                if (isEventMime || action == Intent.ACTION_INSERT) {
                    val draft = readEventEditExtras(intent)
                    val rowId = if (action == Intent.ACTION_EDIT && data != null) {
                        data.lastPathSegment?.toLongOrNull()
                    } else null
                    return RoutedIntent.EditEvent(eventId = rowId, prefill = draft)
                }
            }
        }
        return RoutedIntent.Unhandled
    }

    /**
     * Matches the OS contract `content://com.android.calendar/time/<n>`.
     * Authority can vary by ROM — we accept any `content://&lt;auth&gt;/time/&lt;n&gt;`
     * to be tolerant. The trailing path segment is the epoch in seconds.
     */
    internal fun isTimeEpoch(uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        val segments = uri.pathSegments
        if (segments.isEmpty()) return false
        if (segments[0] != "time") return false
        return segments.size >= 2 && segments[1].toLongOrNull() != null
    }

    /** Matches `content://<authority>/events/<id>`. */
    internal fun isEventsRow(uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        val segments = uri.pathSegments
        if (segments.size < 2) return false
        if (segments[0] != "events") return false
        return segments[1].toLongOrNull() != null
    }

    /** Matches `http(s)://host/.../something.ics`. */
    internal fun isIcsUrl(uri: Uri): Boolean {
        val scheme = uri.scheme ?: return false
        if (scheme != "http" && scheme != "https") return false
        val path = uri.path ?: return false
        return path.endsWith(".ics", ignoreCase = true)
    }

    private fun secondsToLocalDate(epochSeconds: Long): LocalDate =
        Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()

    /**
     * Read the CalendarContract extras that the OS / share-sheets
     * deliver alongside an `EDIT`/`INSERT vnd.android.cursor.item/event`
     * intent. All extras are optional; the editor fills in defaults
     * where missing.
     */
    private fun readEventEditExtras(intent: Intent): EventEditDraft {
        val begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L)
            .takeIf { it > 0L }
        val end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L)
            .takeIf { it > 0L }
        // EXTRA_EVENT_ALL_DAY is documented as Boolean.
        val allDayExtra = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
        val title = intent.getStringExtra(CalendarContract.Events.TITLE)
        val description = intent.getStringExtra(CalendarContract.Events.DESCRIPTION)
        val location = intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION)
        return EventEditDraft(
            beginMs = begin,
            endMs = end,
            allDay = allDayExtra,
            title = title,
            description = description,
            location = location,
        )
    }
}

/**
 * Sealed result of [CalendarIntentRouter.classify]. Each variant maps
 * to one Compose destination; `Unhandled` means "fall through to the
 * next router".
 */
sealed interface RoutedIntent {
    data class GoToDate(val date: LocalDate) : RoutedIntent

    data class ShowEvent(val eventId: Long) : RoutedIntent

    data class EditEvent(
        /** null for INSERT, set for EDIT of an existing row. */
        val eventId: Long?,
        val prefill: EventEditDraft,
    ) : RoutedIntent

    data class ImportIcs(val source: IcsSource) : RoutedIntent

    data object Unhandled : RoutedIntent
}

/** Where an `.ics` payload lives. */
sealed interface IcsSource {
    /** `file://` or `content://` URI on the local device. */
    data class LocalUri(val uri: Uri) : IcsSource

    /** `https://.../foo.ics` URL — caller fetches via OkHttp. */
    data class RemoteUrl(val uri: Uri) : IcsSource
}

/**
 * Prefill draft for `EDIT/INSERT vnd.android.cursor.item/event`. Every
 * field is optional; the event editor fills sensible defaults when
 * absent.
 */
data class EventEditDraft(
    val beginMs: Long? = null,
    val endMs: Long? = null,
    val allDay: Boolean = false,
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
)
