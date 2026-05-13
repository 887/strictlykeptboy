package com.eight87.strictlykeptboy.caldav.sync

import com.eight87.strictlykeptboy.caldav.CalDavHttp
import com.eight87.strictlykeptboy.caldav.CalDavMirror
import com.eight87.strictlykeptboy.caldav.CalDavMode
import com.eight87.strictlykeptboy.caldav.mirrorIdOf
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files

/**
 * Phase Y.7 — full PROPFIND → GET → repo-file materialize and PUT with
 * stale-ETag conflict detection, all over MockWebServer.
 *
 * Robolectric is required so the XML pull-parser used by
 * `PropfindParser` resolves to a real `kxml2` factory rather than the
 * stubbed Android `XmlPullParserFactory`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class CalDavMirrorWorkerTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var rootDir: java.nio.file.Path
    private lateinit var sink: FilesystemMirrorFileSink

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        rootDir = tmp.newFolder("repo").toPath()
        sink = FilesystemMirrorFileSink(rootDir)
    }

    @After fun tearDown() { server.shutdown() }

    private fun mirrorFor(mode: CalDavMode): CalDavMirror {
        val calPath = server.url("/dav/alice/work/").toString()
        val srv = server.url("/").toString()
        return CalDavMirror(
            mirrorId = mirrorIdOf("repoA", srv, calPath),
            repoId = "repoA",
            displayName = "Work",
            serverUrl = srv,
            calendarHomePath = srv,
            calendarPath = calPath,
            targetCalendarId = "work",
            mode = mode,
            credentialBindingId = "binding",
        )
    }

    @Test fun pull_writes_repo_files_and_records_etag() {
        val mirror = mirrorFor(CalDavMode.PULL_ONLY)
        val href = "/dav/alice/work/event-1.ics"
        // PROPFIND response: one VEVENT href + etag.
        server.enqueue(MockResponse().setResponseCode(207).setBody("""<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>$href</d:href>
    <d:propstat><d:prop><d:getetag>"v1"</d:getetag></d:prop></d:propstat>
  </d:response>
</d:multistatus>"""))
        // GET response: VEVENT body.
        server.enqueue(MockResponse().setBody("""BEGIN:VCALENDAR
BEGIN:VEVENT
UID:event-1
SUMMARY:Standup
DTSTART:20260413T090000Z
DTEND:20260413T093000Z
END:VEVENT
END:VCALENDAR""").setHeader("ETag", "\"v1\""))

        val worker = CalDavMirrorWorker(mirror, CalDavHttp(), sink, rootDir)
        val result = worker.runOnce()

        assertTrue("got $result", result is CalDavSyncResult.Pulled)
        val pulled = result as CalDavSyncResult.Pulled
        assertEquals(1, pulled.added)
        val written = sink.listEventFiles("work")
        assertEquals(1, written.size)
        val body = Files.readAllBytes(rootDir.resolve(written.first())).toString(Charsets.UTF_8)
        assertTrue(body.contains("title = \"Standup\""))
        assertTrue(body.contains("external_uid = \"event-1\""))
    }

    @Test fun pull_second_pass_is_up_to_date_when_etag_unchanged() {
        val mirror = mirrorFor(CalDavMode.PULL_ONLY)
        val href = server.url("/dav/alice/work/event-1.ics").encodedPath
        val pf = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>$href</d:href>
    <d:propstat><d:prop><d:getetag>"v1"</d:getetag></d:prop></d:propstat>
  </d:response>
</d:multistatus>"""
        server.enqueue(MockResponse().setResponseCode(207).setBody(pf))
        server.enqueue(MockResponse().setBody("""BEGIN:VCALENDAR
BEGIN:VEVENT
UID:event-1
SUMMARY:S
DTSTART:20260413T090000Z
DTEND:20260413T093000Z
END:VEVENT
END:VCALENDAR""").setHeader("ETag", "\"v1\""))
        server.enqueue(MockResponse().setResponseCode(207).setBody(pf)) // second PROPFIND, same etag

        val worker = { CalDavMirrorWorker(mirror, CalDavHttp(), sink, rootDir) }
        val first = worker().runOnce()
        assertTrue(first is CalDavSyncResult.Pulled)
        val second = worker().runOnce()
        assertEquals(CalDavSyncResult.UpToDate, second)
    }

    @Test fun push_with_stale_etag_emits_conflict() {
        val pullMirror = mirrorFor(CalDavMode.PULL_ONLY)
        val bidiMirror = mirrorFor(CalDavMode.BIDI)
        val href = "/dav/alice/work/event-1.ics"
        // First, seed the journal with a PULL_ONLY pass (no push attempted).
        server.enqueue(MockResponse().setResponseCode(207).setBody("""<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>$href</d:href>
    <d:propstat><d:prop><d:getetag>"v1"</d:getetag></d:prop></d:propstat>
  </d:response>
</d:multistatus>"""))
        server.enqueue(MockResponse().setBody("""BEGIN:VCALENDAR
BEGIN:VEVENT
UID:event-1
SUMMARY:Standup
DTSTART:20260413T090000Z
DTEND:20260413T093000Z
END:VEVENT
END:VCALENDAR""").setHeader("ETag", "\"v1\""))
        val first = CalDavMirrorWorker(pullMirror, CalDavHttp(), sink, rootDir).runOnce()
        assertTrue("first: $first", first is CalDavSyncResult.Pulled)

        // Edit the local file so the upcoming BIDI push observes a change.
        val rels = sink.listEventFiles("work")
        val rel = rels.single()
        val current = sink.readFile(rel)!!
        sink.writeFile(rel, current.replace("title = \"Standup\"", "title = \"Standup (edited)\""))

        // BIDI pass: PROPFIND reports the same ETag (no remote change) →
        // pull short-circuits to UpToDate, then push PUTs with If-Match and
        // hits 412.
        server.enqueue(MockResponse().setResponseCode(207).setBody("""<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>$href</d:href>
    <d:propstat><d:prop><d:getetag>"v1"</d:getetag></d:prop></d:propstat>
  </d:response>
</d:multistatus>"""))
        server.enqueue(MockResponse().setResponseCode(412))

        val second = CalDavMirrorWorker(bidiMirror, CalDavHttp(), sink, rootDir).runOnce()
        assertTrue("got $second", second is CalDavSyncResult.Conflicted)
    }

    @Test fun server_deletes_remove_local_file() {
        val mirror = mirrorFor(CalDavMode.PULL_ONLY)
        val href = "/dav/alice/work/event-1.ics"
        // First pass: present.
        server.enqueue(MockResponse().setResponseCode(207).setBody("""<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>$href</d:href>
    <d:propstat><d:prop><d:getetag>"v1"</d:getetag></d:prop></d:propstat>
  </d:response>
</d:multistatus>"""))
        server.enqueue(MockResponse().setBody("""BEGIN:VCALENDAR
BEGIN:VEVENT
UID:event-1
SUMMARY:S
DTSTART:20260413T090000Z
DTEND:20260413T093000Z
END:VEVENT
END:VCALENDAR""").setHeader("ETag", "\"v1\""))
        // Second pass: empty multistatus → server deleted.
        server.enqueue(MockResponse().setResponseCode(207).setBody("""<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:"></d:multistatus>"""))

        val w1 = CalDavMirrorWorker(mirror, CalDavHttp(), sink, rootDir).runOnce()
        assertTrue(w1 is CalDavSyncResult.Pulled)
        assertEquals(1, sink.listEventFiles("work").size)
        val w2 = CalDavMirrorWorker(mirror, CalDavHttp(), sink, rootDir).runOnce()
        val pulled = w2 as CalDavSyncResult.Pulled
        assertEquals(1, pulled.deleted)
        assertEquals(0, sink.listEventFiles("work").size)
    }
}
