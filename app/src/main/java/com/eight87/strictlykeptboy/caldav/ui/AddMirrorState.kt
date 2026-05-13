package com.eight87.strictlykeptboy.caldav.ui

import com.eight87.strictlykeptboy.caldav.CalDavMode
import com.eight87.strictlykeptboy.caldav.CalDavProvider
import com.eight87.strictlykeptboy.caldav.discovery.DiscoveredCalendar

/**
 * Phase Y.2 — wizard state for `Settings → Repos → <repo> → + Add
 * CalDAV mirror`. Split out from the composable so the ViewModel can
 * unit-test without Compose.
 */
data class AddMirrorState(
    val step: Step = Step.PickProvider,
    val provider: CalDavProvider = CalDavProvider.CUSTOM,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val mode: CalDavMode = CalDavMode.PULL_ONLY,
    val targetCalendarId: String = "",
    val discoveredCalendars: List<DiscoveredCalendar> = emptyList(),
    val selectedCalendarHref: String? = null,
    val error: String? = null,
    val inFlight: Boolean = false,
) {
    enum class Step { PickProvider, EnterCredentials, PickCalendar, PickMode, Confirm, Done }
}
