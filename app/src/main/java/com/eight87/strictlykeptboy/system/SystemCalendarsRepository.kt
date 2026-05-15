package com.eight87.strictlykeptboy.system

import com.eight87.strictlykeptboy.resolver.CalendarKind
import com.eight87.strictlykeptboy.resolver.CalendarMeta
import com.eight87.strictlykeptboy.resolver.CalendarRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Round 2.18.A.5 — translates [CalendarContractBridge] output into
 * resolver-facing [CalendarMeta] values, applying the
 * [SystemCalendarPrefsStore] overlay (A.15).
 *
 * Behaviour:
 *  - One [CalendarMeta] per (visible+syncing) system calendar.
 *  - `kind = External` (A.3).
 *  - `repo = RepoRef("system/<accountType>/<accountName>")` (A.4 / D-2.18.f).
 *  - `colorSeed` derived from `CalendarContract.CALENDAR_COLOR` (A.10).
 *  - `externalAccessLevel` populated with the raw `CAL_ACCESS_*` int (A.11).
 *  - User overrides (priority, activeToggle, supersedes) layered on top (A.15).
 *
 * Concurrency:
 *  - The flow lives on whichever `CoroutineScope` is passed in
 *    (typically the AppGraph's appScope per A.12). It is **never**
 *    owned by a ViewModel.
 *  - `stateIn(Eagerly)` so the registry/AppGraph can read the latest
 *    list synchronously.
 */
class SystemCalendarsRepository(
    private val bridge: CalendarContractBridge,
    private val prefs: SystemCalendarPrefsStore,
    private val scope: CoroutineScope,
) {

    /**
     * Emits the current list of merged [CalendarMeta] for system
     * calendars.
     *
     * Round 2.18.B.2 — gated on the global `showSystemCalendars` toggle
     * (off by default, the resolver sees an empty list until the user
     * opts in via Settings → External calendars).
     *
     * Round 2.18.B.5 — calendars whose `SystemCalendarOverride.visible`
     * is `false` are filtered out before emission.
     */
    val state: StateFlow<List<CalendarMeta>> =
        combine(bridge.calendars(), prefs.state, prefs.globalState) { sysCals, overrides, global ->
            if (!global.showSystemCalendars) emptyList()
            else sysCals
                .filter { cal ->
                    val ov = overrides[SystemCalendarPrefsStore.keyFor(
                        cal.accountType, cal.accountName, cal.id,
                    )]
                    ov?.visible ?: true
                }
                .map { cal -> toMeta(cal, overrides) }
        }.stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptyList())

    /**
     * Per-source view of `SystemCalendar` rows. Useful for the Phase B
     * Settings screen that lists every calendar grouped by account.
     */
    fun systemCalendars(): Flow<List<SystemCalendar>> = bridge.calendars()

    /** Synchronous one-shot used by tests / one-off readers. */
    fun readOnce(): List<CalendarMeta> {
        if (!prefs.globalState.value.showSystemCalendars) return emptyList()
        val sysCals = bridge.readOnce()
        val overrides = prefs.state.value
        return sysCals
            .filter { cal ->
                val ov = overrides[SystemCalendarPrefsStore.keyFor(
                    cal.accountType, cal.accountName, cal.id,
                )]
                ov?.visible ?: true
            }
            .map { toMeta(it, overrides) }
    }

    private fun toMeta(
        cal: SystemCalendar,
        overrides: Map<String, SystemCalendarOverride>,
    ): CalendarMeta {
        val overlayKey = SystemCalendarPrefsStore.keyFor(
            accountType = cal.accountType,
            accountName = cal.accountName,
            calendarId = cal.id,
        )
        val override = overrides[overlayKey]
        val basePriority = if (cal.isPrimary) PRIORITY_PRIMARY else PRIORITY_SECONDARY
        return CalendarMeta(
            ref = CalendarRef(cal.id.toString()),
            repo = RepoRef(cal.syntheticRepoId),
            displayName = cal.displayName,
            priority = override?.priority ?: basePriority,
            activeToggle = override?.activeToggle ?: true,
            kind = CalendarKind.External,
            supersedes = override?.supersedes.orEmpty().map { CalendarRef(it) },
            // CALENDAR_COLOR is a raw 0xAARRGGBB int; treating its hash
            // as a seed is fine — the M3E tint pipeline only needs
            // determinism, not the original int (Material You harmonization
            // re-derives the chip tone from it).
            colorSeed = cal.color.takeIf { it != 0 },
            externalAccessLevel = cal.accessLevel,
        )
    }

    companion object {
        // Same priority space as file-backed calendars (1..1000). Primary
        // calendars sit slightly above secondary, matching Etar's
        // "default-first" ordering.
        private const val PRIORITY_PRIMARY = 510
        private const val PRIORITY_SECONDARY = 500

    }
}
