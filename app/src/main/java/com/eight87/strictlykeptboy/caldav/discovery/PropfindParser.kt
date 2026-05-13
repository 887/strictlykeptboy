package com.eight87.strictlykeptboy.caldav.discovery

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Phase Y.7 — PROPFIND multistatus parser. Returns one entry per
 * `<D:response>` element, surfacing the fields we care about for
 * discovery (`displayname`, `resourcetype/calendar`, `getctag`,
 * `sync-token`, `getetag`) without pulling in a full WebDAV stack.
 *
 * Namespaces handled (case-insensitive local-name match, namespace
 * checked when present):
 *  - `DAV:` — `response`, `href`, `propstat`, `prop`, `displayname`,
 *    `resourcetype`, `collection`, `getctag`, `sync-token`, `getetag`,
 *    `current-user-principal`.
 *  - `urn:ietf:params:xml:ns:caldav` — `calendar`, `calendar-home-set`.
 *  - `http://calendarserver.org/ns/` — `getctag`.
 */
data class PropfindEntry(
    val href: String,
    val displayName: String? = null,
    val isCalendar: Boolean = false,
    val isPrincipal: Boolean = false,
    val ctag: String? = null,
    val syncToken: String? = null,
    val etag: String? = null,
    val calendarHomeSet: String? = null,
    val currentUserPrincipal: String? = null,
)

object PropfindParser {
    private const val NS_DAV = "DAV:"
    private const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"

    fun parse(xml: String): List<PropfindEntry> {
        if (xml.isBlank()) return emptyList()
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newPullParser().apply { setInput(StringReader(xml)) }
        val entries = mutableListOf<PropfindEntry>()
        var current: MutableMap<String, Any?>? = null
        var depth = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                depth++
                val local = parser.name
                val ns = parser.namespace
                when {
                    local == "response" && ns == NS_DAV -> current = mutableMapOf()
                    current != null -> readField(parser, local, ns, current)
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (parser.name == "response" && parser.namespace == NS_DAV && current != null) {
                    entries += build(current)
                    current = null
                }
                depth--
            }
            event = parser.next()
        }
        return entries
    }

    private fun readField(p: XmlPullParser, local: String, ns: String?, into: MutableMap<String, Any?>) {
        when {
            local == "href" && ns == NS_DAV && !into.containsKey("href") -> into["href"] = textOf(p)
            local == "displayname" && ns == NS_DAV -> into["displayName"] = textOf(p)
            local == "calendar" && ns == NS_CALDAV -> into["isCalendar"] = true
            local == "principal" && ns == NS_DAV -> into["isPrincipal"] = true
            local == "getctag" -> into["ctag"] = textOf(p)
            local == "sync-token" -> into["syncToken"] = textOf(p)
            local == "getetag" && ns == NS_DAV -> into["etag"] = textOf(p)
            local == "calendar-home-set" && ns == NS_CALDAV -> into["calendarHomeSet"] = firstHref(p)
            local == "current-user-principal" && ns == NS_DAV -> into["currentUserPrincipal"] = firstHref(p)
        }
    }

    private fun textOf(p: XmlPullParser): String {
        val sb = StringBuilder()
        val startDepth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.TEXT) sb.append(p.text)
            else if (e == XmlPullParser.END_TAG && p.depth == startDepth) break
            else if (e == XmlPullParser.END_DOCUMENT) break
        }
        return sb.toString().trim()
    }

    private fun firstHref(p: XmlPullParser): String? {
        val startDepth = p.depth
        var found: String? = null
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.START_TAG && p.name == "href" && p.namespace == NS_DAV) {
                if (found == null) found = textOf(p)
            } else if (e == XmlPullParser.END_TAG && p.depth == startDepth) break
            else if (e == XmlPullParser.END_DOCUMENT) break
        }
        return found?.takeIf { it.isNotBlank() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun build(m: Map<String, Any?>): PropfindEntry = PropfindEntry(
        href = m["href"] as? String ?: "",
        displayName = m["displayName"] as? String,
        isCalendar = m["isCalendar"] as? Boolean ?: false,
        isPrincipal = m["isPrincipal"] as? Boolean ?: false,
        ctag = m["ctag"] as? String,
        syncToken = m["syncToken"] as? String,
        etag = m["etag"] as? String,
        calendarHomeSet = m["calendarHomeSet"] as? String,
        currentUserPrincipal = m["currentUserPrincipal"] as? String,
    )
}
