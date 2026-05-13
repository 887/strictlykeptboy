package com.eight87.strictlykeptboy.caldav.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase Y.7 — PROPFIND multistatus parsing covers the realistic shapes
 *  emitted by Google / Apple / Nextcloud / Radicale.
 *
 *  Robolectric runner is required so `XmlPullParserFactory.newInstance()`
 *  resolves to a real `kxml2` parser instead of the stubbed Android API. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class PropfindParserTest {

    @Test fun parses_calendar_home_set() {
        val xml = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav">
  <d:response>
    <d:href>/principals/users/alice/</d:href>
    <d:propstat>
      <d:prop>
        <c:calendar-home-set>
          <d:href>/caldav/alice/</d:href>
        </c:calendar-home-set>
      </d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
</d:multistatus>"""
        val entries = PropfindParser.parse(xml)
        assertEquals(1, entries.size)
        assertEquals("/caldav/alice/", entries[0].calendarHomeSet)
    }

    @Test fun parses_calendar_collection_with_ctag() {
        val xml = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:" xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:cs="http://calendarserver.org/ns/">
  <d:response>
    <d:href>/caldav/alice/work/</d:href>
    <d:propstat>
      <d:prop>
        <d:displayname>Work</d:displayname>
        <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
        <cs:getctag>"ctag-123"</cs:getctag>
      </d:prop>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/caldav/alice/personal/</d:href>
    <d:propstat>
      <d:prop>
        <d:displayname>Personal</d:displayname>
        <d:resourcetype><d:collection/><c:calendar/></d:resourcetype>
      </d:prop>
    </d:propstat>
  </d:response>
</d:multistatus>"""
        val entries = PropfindParser.parse(xml)
        assertEquals(2, entries.size)
        val work = entries.first { it.href == "/caldav/alice/work/" }
        assertEquals("Work", work.displayName)
        assertTrue(work.isCalendar)
        assertNotNull(work.ctag)
    }

    @Test fun parses_etag_listing() {
        val xml = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>/caldav/alice/work/event-1.ics</d:href>
    <d:propstat><d:prop><d:getetag>"abc"</d:getetag></d:prop></d:propstat>
  </d:response>
  <d:response>
    <d:href>/caldav/alice/work/event-2.ics</d:href>
    <d:propstat><d:prop><d:getetag>"def"</d:getetag></d:prop></d:propstat>
  </d:response>
</d:multistatus>"""
        val entries = PropfindParser.parse(xml)
        assertEquals(2, entries.size)
        assertEquals(""""abc"""", entries[0].etag)
        assertEquals(""""def"""", entries[1].etag)
    }

    @Test fun empty_returns_empty() {
        assertTrue(PropfindParser.parse("").isEmpty())
    }
}
