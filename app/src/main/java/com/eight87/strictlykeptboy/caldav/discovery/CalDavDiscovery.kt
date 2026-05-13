package com.eight87.strictlykeptboy.caldav.discovery

import com.eight87.strictlykeptboy.caldav.CalDavHttp
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Phase Y.2 — CalDAV discovery state machine. Given a server URL and an
 * authenticated [CalDavHttp], walks:
 *  1. `OPTIONS` to confirm `DAV: calendar-access`.
 *  2. `.well-known/caldav` GET → expect 301/302 to the user-principal,
 *     falling back to a `PROPFIND` on the supplied URL for
 *     `{DAV:}current-user-principal` if `.well-known` is absent.
 *  3. PROPFIND on principal for `{C:}calendar-home-set`.
 *  4. PROPFIND on calendar-home depth 1 for `displayname`,
 *     `resourcetype`, `getctag`, `sync-token`.
 *
 * Returns a [DiscoveryResult] containing the discovered calendars so
 * the wizard (Y.2) can render them for the user to pick.
 *
 * SOLID.S — discovery only. Credential acquisition is the caller's job;
 * file materialization is the worker's job.
 */
class CalDavDiscovery(private val http: CalDavHttp) {

    fun discover(serverUrl: String): DiscoveryResult {
        val opts = http.options(serverUrl)
        if (opts.code !in 200..299) return DiscoveryResult.Failed("server returned ${opts.code} on OPTIONS")
        if (!opts.supportsCalendar) return DiscoveryResult.Failed("server does not advertise calendar-access")

        val principalUrl = findPrincipal(serverUrl)
            ?: return DiscoveryResult.Failed("could not locate user-principal")

        val home = findCalendarHome(principalUrl)
            ?: return DiscoveryResult.Failed("could not locate calendar-home-set")

        val homeAbs = absolutize(serverUrl, home)
        val calendars = enumerateCalendars(homeAbs)
        return DiscoveryResult.Success(
            principalUrl = principalUrl,
            calendarHomeUrl = homeAbs,
            calendars = calendars,
            supportsSyncCollection = opts.supportsSyncCollection,
        )
    }

    private fun findPrincipal(serverUrl: String): String? {
        // .well-known route first — many servers (Google, Apple) honour the redirect.
        val wellKnown = serverUrl.trimEnd('/') + "/.well-known/caldav"
        val resp = runCatching { http.propfind(wellKnown, depth = "0", body = PROPFIND_PRINCIPAL) }.getOrNull()
        if (resp != null && resp.code in 200..299) {
            val parsed = PropfindParser.parse(resp.xml)
            parsed.firstOrNull { it.currentUserPrincipal != null }?.currentUserPrincipal?.let { return absolutize(serverUrl, it) }
            parsed.firstOrNull { it.isPrincipal }?.href?.let { return absolutize(serverUrl, it) }
        }
        // Fall back to PROPFIND on the supplied URL.
        val direct = runCatching { http.propfind(serverUrl, depth = "0", body = PROPFIND_PRINCIPAL) }.getOrNull()
            ?: return null
        if (direct.code !in 200..299) return null
        val parsed = PropfindParser.parse(direct.xml)
        return parsed.firstOrNull { it.currentUserPrincipal != null }?.currentUserPrincipal?.let { absolutize(serverUrl, it) }
            ?: parsed.firstOrNull { it.isPrincipal }?.href?.let { absolutize(serverUrl, it) }
    }

    private fun findCalendarHome(principalUrl: String): String? {
        val resp = http.propfind(principalUrl, depth = "0", body = PROPFIND_HOME_SET)
        if (resp.code !in 200..299) return null
        return PropfindParser.parse(resp.xml).firstOrNull { it.calendarHomeSet != null }?.calendarHomeSet
    }

    private fun enumerateCalendars(homeUrl: String): List<DiscoveredCalendar> {
        val resp = http.propfind(homeUrl, depth = "1", body = PROPFIND_CALENDARS)
        if (resp.code !in 200..299) return emptyList()
        return PropfindParser.parse(resp.xml)
            .filter { it.isCalendar && it.href.isNotBlank() }
            .map {
                DiscoveredCalendar(
                    href = absolutize(homeUrl, it.href),
                    displayName = it.displayName ?: it.href.trimEnd('/').substringAfterLast('/'),
                    ctag = it.ctag,
                    syncToken = it.syncToken,
                )
            }
    }

    private fun absolutize(base: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return runCatching { base.toHttpUrl().resolve(href).toString() }.getOrDefault(href)
    }

    companion object {
        private const val PROPFIND_PRINCIPAL = """<?xml version="1.0"?>
<propfind xmlns="DAV:"><prop><current-user-principal/><resourcetype/></prop></propfind>"""

        private const val PROPFIND_HOME_SET = """<?xml version="1.0"?>
<propfind xmlns="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav"><prop><c:calendar-home-set/></prop></propfind>"""

        private const val PROPFIND_CALENDARS = """<?xml version="1.0"?>
<propfind xmlns="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/"><prop><displayname/><resourcetype/><cs:getctag/><sync-token/></prop></propfind>"""
    }
}

data class DiscoveredCalendar(
    val href: String,
    val displayName: String,
    val ctag: String? = null,
    val syncToken: String? = null,
)

sealed interface DiscoveryResult {
    data class Success(
        val principalUrl: String,
        val calendarHomeUrl: String,
        val calendars: List<DiscoveredCalendar>,
        val supportsSyncCollection: Boolean,
    ) : DiscoveryResult
    data class Failed(val reason: String) : DiscoveryResult
}
