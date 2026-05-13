package com.eight87.strictlykeptboy.caldav.ui

import com.eight87.strictlykeptboy.caldav.CalDavHttp
import com.eight87.strictlykeptboy.caldav.CalDavMirror
import com.eight87.strictlykeptboy.caldav.CalDavMode
import com.eight87.strictlykeptboy.caldav.CalDavProvider
import com.eight87.strictlykeptboy.caldav.auth.CalDavCredential
import com.eight87.strictlykeptboy.caldav.auth.CalDavSecretsStore
import com.eight87.strictlykeptboy.caldav.discovery.CalDavDiscovery
import com.eight87.strictlykeptboy.caldav.discovery.DiscoveryResult
import com.eight87.strictlykeptboy.caldav.mirrorIdOf
import com.eight87.strictlykeptboy.caldav.store.CalDavMirrorStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase Y.2 — orchestrates the AddCalDavMirror wizard. The composable
 * shell observes [state] and dispatches user actions. Network I/O is
 * delegated to a caller-supplied [discoveryFactory] so this VM is
 * unit-testable without a live server (a `MockWebServer` is plugged in
 * via the factory in tests).
 *
 * SOLID.D — VM depends on factory function types, not concrete classes.
 */
class AddMirrorViewModel(
    private val repoId: String,
    private val mirrorStore: CalDavMirrorStore,
    private val secrets: CalDavSecretsStore,
    private val discoveryFactory: (CalDavCredential) -> CalDavDiscovery = { cred ->
        CalDavDiscovery(CalDavHttp(credential = cred))
    },
) {
    private val _state = MutableStateFlow(AddMirrorState())
    val state: StateFlow<AddMirrorState> = _state.asStateFlow()

    fun pickProvider(p: CalDavProvider) {
        _state.value = _state.value.copy(
            provider = p,
            serverUrl = p.wellKnown ?: _state.value.serverUrl,
            step = AddMirrorState.Step.EnterCredentials,
            error = null,
        )
    }

    fun enterCredentials(serverUrl: String, username: String, password: String) {
        _state.value = _state.value.copy(serverUrl = serverUrl, username = username, password = password, inFlight = true, error = null)
        val cred = buildCredential() ?: run {
            _state.value = _state.value.copy(inFlight = false, error = "missing credentials")
            return
        }
        val discovery = discoveryFactory(cred)
        when (val result = discovery.discover(serverUrl)) {
            is DiscoveryResult.Success -> _state.value = _state.value.copy(
                discoveredCalendars = result.calendars,
                step = AddMirrorState.Step.PickCalendar,
                inFlight = false,
            )
            is DiscoveryResult.Failed -> _state.value = _state.value.copy(error = result.reason, inFlight = false)
        }
    }

    fun pickCalendar(href: String, targetCalendarId: String) {
        _state.value = _state.value.copy(
            selectedCalendarHref = href,
            targetCalendarId = targetCalendarId,
            step = AddMirrorState.Step.PickMode,
        )
    }

    fun pickMode(mode: CalDavMode) {
        _state.value = _state.value.copy(mode = mode, step = AddMirrorState.Step.Confirm)
    }

    fun confirm(): CalDavMirror? {
        val s = _state.value
        val href = s.selectedCalendarHref ?: return null
        val calendar = s.discoveredCalendars.firstOrNull { it.href == href } ?: return null
        val cred = buildCredential() ?: return null
        val mirrorId = mirrorIdOf(repoId, s.serverUrl, calendar.href)
        when (cred) {
            is CalDavCredential.BasicAuth -> secrets.storeBasic(mirrorId, cred)
            is CalDavCredential.AppPassword -> secrets.storeAppPassword(mirrorId, cred)
            is CalDavCredential.Bearer -> secrets.storeBearer(mirrorId, cred)
        }
        val mirror = CalDavMirror(
            mirrorId = mirrorId,
            repoId = repoId,
            displayName = calendar.displayName,
            serverUrl = s.serverUrl,
            calendarHomePath = s.serverUrl,
            calendarPath = calendar.href,
            targetCalendarId = s.targetCalendarId,
            mode = s.mode,
            provider = s.provider,
            credentialBindingId = mirrorId,
            lastSyncedCtag = calendar.ctag,
            lastSyncedSyncToken = calendar.syncToken,
        )
        mirrorStore.upsert(mirror)
        _state.value = s.copy(step = AddMirrorState.Step.Done)
        return mirror
    }

    private fun buildCredential(): CalDavCredential? {
        val s = _state.value
        if (s.username.isBlank() || s.password.isBlank()) return null
        return when (s.provider) {
            CalDavProvider.APPLE_ICLOUD -> CalDavCredential.AppPassword(s.username, s.password)
            CalDavProvider.GOOGLE, CalDavProvider.MICROSOFT_365 ->
                // OAuth flow integration is wired by the host UI; for the
                // wizard's offline test path we accept the access token in
                // the `password` slot and treat it as a Bearer.
                CalDavCredential.Bearer(accessToken = s.password)
            CalDavProvider.NEXTCLOUD, CalDavProvider.CUSTOM -> CalDavCredential.BasicAuth(s.username, s.password)
        }
    }
}
