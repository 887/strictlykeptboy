package com.eight87.strictlykeptboy.composition

import android.content.Context
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.resolver.DateRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * [M] #8d (audit pass 2026-05-17) — cohesive sub-graph holding the
 * Round 2.18 CalendarContract bridges + system-calendar prefs +
 * AccountManager facade extracted out of [AppGraph]. No behavioural
 * change: every `by lazy` retains the same initialisation order, and
 * the [buildExternalEventsFlow] producer continues to drive
 * [SourcesPublisher]'s external-event stream through the resolver
 * pipeline.
 */
class SystemCalendarGraph(
    private val appContext: Context,
    private val appScope: CoroutineScope,
    private val repoStore: RepoStore,
) {

    /**
     * Round 2.18.A.1 — CalendarContract.Calendars wrapper. Cold; reads
     * are gated by `READ_CALENDAR` and return empty when ungranted.
     */
    val calendarContractBridge: com.eight87.strictlykeptboy.system.CalendarContractBridge by lazy {
        com.eight87.strictlykeptboy.system.CalendarContractBridge(appContext)
    }

    /** Round 2.18.A.6 — CalendarContract.Instances wrapper (windowed). */
    val systemEventsBridge: com.eight87.strictlykeptboy.system.SystemEventsBridge by lazy {
        com.eight87.strictlykeptboy.system.SystemEventsBridge(appContext)
    }

    /** Round 2.18.C.8 — CalendarContract.Attendees reader. */
    val systemAttendeesReader: com.eight87.strictlykeptboy.system.SystemAttendeesReader by lazy {
        com.eight87.strictlykeptboy.system.SystemAttendeesReader(appContext)
    }

    /** Round 2.18.C.9 — CalendarContract.Reminders reader. */
    val systemRemindersReader: com.eight87.strictlykeptboy.system.SystemRemindersReader by lazy {
        com.eight87.strictlykeptboy.system.SystemRemindersReader(appContext)
    }

    /** Round 2.18.A.14 — per-system-calendar user overrides. */
    val systemCalendarPrefsStore: com.eight87.strictlykeptboy.system.SystemCalendarPrefsStore by lazy {
        com.eight87.strictlykeptboy.system.SystemCalendarPrefsStore.open(appContext)
    }

    /** Round 2.18.B.6 — first-run nudge when OS-level accounts change. */
    val accountChangeNudge: com.eight87.strictlykeptboy.system.AccountChangeNudge by lazy {
        com.eight87.strictlykeptboy.system.AccountChangeNudge.open(appContext).also { it.start() }
    }

    /** Round 2.18.F.6 — external (CalendarContract) reminder scheduler. */
    val externalReminderScheduler: com.eight87.strictlykeptboy.notif.ExternalReminderScheduler by lazy {
        com.eight87.strictlykeptboy.notif.ExternalReminderScheduler(appContext)
    }

    /**
     * Round 2.18.G.6 / G.7 — AccountManager facade for skb's local-only
     * accounts. Creates / removes the `<repoId>@local` accounts when the
     * Settings toggle flips and fires sync requests on every commit.
     */
    val skbAccountManager: com.eight87.strictlykeptboy.system.SkbAccountManager by lazy {
        com.eight87.strictlykeptboy.system.SkbAccountManager(
            context = appContext,
            repoStore = repoStore,
            systemCalendarPrefs = systemCalendarPrefsStore,
        )
    }

    /** Round 2.18.A.5 / A.15 — synthesized [CalendarMeta] for system calendars. */
    val systemCalendarsRepository: com.eight87.strictlykeptboy.system.SystemCalendarsRepository by lazy {
        com.eight87.strictlykeptboy.system.SystemCalendarsRepository(
            bridge = calendarContractBridge,
            prefs = systemCalendarPrefsStore,
            scope = appScope,
        )
    }

    /**
     * Round 2.18.B.5 — raw `SystemCalendar` list for the External
     * Calendars settings screen (every CalendarContract row, regardless
     * of the global show toggle or per-calendar visibility overrides;
     * the screen needs every row so the user can flip visibility on
     * hidden ones).
     */
    val systemCalendarsRawFlow: StateFlow<List<com.eight87.strictlykeptboy.system.SystemCalendar>> by lazy {
        systemCalendarsRepository.systemCalendars().stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )
    }

    /**
     * Round 2.18.C.0 — build the windowed `Flow<List<EventInput>>` for
     * the resolver's external-event source. Combines the visible meta
     * set (so global-toggle-off + per-calendar-hidden are honored) with
     * the raw system calendar list (needed to map id → accountType for
     * the `external` sidecar), then drives the `SystemEventsBridge` for
     * the given window. Re-checks `READ_CALENDAR` per query (the bridge
     * itself returns empty on missing permission).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun buildExternalEventsFlow(
        range: DateRange,
    ): Flow<List<com.eight87.strictlykeptboy.resolver.EventInput>> {
        val zone = java.time.ZoneId.systemDefault()
        val fromMs = range.start.atStartOfDay(zone).toInstant().toEpochMilli()
        val toMs = (range.endInclusive ?: range.start)
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return systemCalendarsRepository.state.flatMapLatest { metas ->
            if (metas.isEmpty()) {
                flowOf(emptyList())
            } else {
                val visibleIds = metas
                    .mapNotNull { runCatching { it.ref.id.toLong() }.getOrNull() }
                    .toSet()
                systemCalendarsRawFlow
                    .map { all -> all.filter { it.id in visibleIds } }
                    .flatMapLatest { known ->
                        systemEventsBridge.events(fromMs, toMs, known, zone)
                    }
            }
        }
    }
}
