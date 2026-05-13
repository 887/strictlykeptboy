package com.eight87.strictlykeptboy.caldav.sync

import com.eight87.strictlykeptboy.caldav.CalDavHttp
import com.eight87.strictlykeptboy.caldav.CalDavMirror
import com.eight87.strictlykeptboy.caldav.CalDavMode
import com.eight87.strictlykeptboy.caldav.discovery.PropfindParser
import com.eight87.strictlykeptboy.caldav.ical.IcalCodec
import com.eight87.strictlykeptboy.caldav.ical.IcalRepoMapper
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.nio.file.Path

/**
 * Phase Y.4/Y.5/Y.7 — one sync pass for one [CalDavMirror].
 *
 * Pull path (Y.4):
 *  1. PROPFIND depth 1 for `getetag` over the collection.
 *  2. For each href whose etag changed (or is new), GET → decode →
 *     materialize as repo file via [IcalRepoMapper].
 *  3. Any href missing from the new PROPFIND that was in the journal
 *     is a server-side delete → drop the repo file.
 *  4. One commit per pass via [MirrorFileSink.commit] under the
 *     "CalDAV mirror <server-host>" identity.
 *
 * Push path (Y.5 — used when mode == BIDI):
 *  - For every repo file whose content hash differs from the version
 *    last serialized to CalDAV, PUT with `If-Match: <last-etag>`.
 *  - 412 Precondition Failed = stale ETag = conflict → return
 *    `Conflicted` so the shared UI (Phase SE-J) can resolve.
 *  - For repo files deleted locally, DELETE with their last-known
 *    ETag.
 *
 * The worker is stateless across calls; per-mirror state lives in
 * [EtagJournal] inside the repo's `.skb/` dir.
 *
 * SOLID.S — only owns one sync pass. SOLID.D — talks to [MirrorFileSink]
 * + [CalDavHttp] abstractions, no Room / JGit references here.
 */
class CalDavMirrorWorker(
    private val mirror: CalDavMirror,
    private val http: CalDavHttp,
    private val sink: MirrorFileSink,
    private val rootDir: Path,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val journal = EtagJournal(rootDir, mirror.mirrorId)

    fun runOnce(): CalDavSyncResult = try {
        when (mirror.mode) {
            CalDavMode.PULL_ONLY -> doPull()
            CalDavMode.PUSH_ONLY -> doPush()
            CalDavMode.BIDI -> {
                val pull = doPull()
                if (pull is CalDavSyncResult.Failed || pull is CalDavSyncResult.Conflicted) pull
                else {
                    val push = doPush()
                    when {
                        push is CalDavSyncResult.Failed || push is CalDavSyncResult.Conflicted -> push
                        pull is CalDavSyncResult.Pulled && push is CalDavSyncResult.Pushed ->
                            CalDavSyncResult.BiDirectional(pull, push)
                        else -> push
                    }
                }
            }
        }
    } catch (t: Throwable) {
        CalDavSyncResult.Failed(t.message ?: t.javaClass.simpleName)
    }

    private fun doPull(): CalDavSyncResult {
        val list = http.propfind(mirror.calendarPath, depth = "1", body = PROPFIND_ETAGS)
        if (list.code !in 200..299) return CalDavSyncResult.Failed("PROPFIND ${list.code}")
        val server = PropfindParser.parse(list.xml)
            .filter { it.href.isNotBlank() && it.href.endsWith(".ics") }

        var added = 0; var updated = 0; var deleted = 0
        val seenHrefs = HashSet<String>()

        for (entry in server) {
            val href = absolutize(mirror.calendarPath, entry.href)
            seenHrefs += href
            val etag = entry.etag ?: continue
            val known = journal.get(href)
            if (known?.etag == etag) continue // unchanged
            val resource = http.getResource(href)
            if (resource.code !in 200..299) continue
            val events = IcalCodec.decode(resource.body)
            if (events.isEmpty()) continue
            val ev = events.first()
            val file = IcalRepoMapper.toRepoFile(
                calendarId = mirror.targetCalendarId,
                event = ev,
                existingEventId = known?.eventId,
                sourceServerHost = hostOf(mirror.serverUrl),
            )
            sink.writeFile(file.relativePath, file.content)
            journal.put(href, EtagJournal.Entry(etag = resource.etag ?: etag, eventId = file.eventId))
            if (known == null) added++ else updated++
        }

        // Server-side deletes: anything in journal that isn't in the new propfind.
        for (href in journal.hrefs() - seenHrefs) {
            val entry = journal.get(href) ?: continue
            val rel = findRelByEventId(entry.eventId)
            if (rel != null) sink.deleteFile(rel)
            journal.remove(href)
            deleted++
        }

        if (added + updated + deleted > 0) {
            sink.commit("caldav: pull ${mirror.displayName} (+$added ~$updated -$deleted)")
            return CalDavSyncResult.Pulled(added, updated, deleted)
        }
        return CalDavSyncResult.UpToDate
    }

    private fun doPush(): CalDavSyncResult {
        var added = 0; var updated = 0; var deleted = 0
        val conflicts = mutableListOf<String>()

        // Push every repo file that doesn't yet exist on the server OR has changed.
        val repoFiles = sink.listEventFiles(mirror.targetCalendarId)
        val seenUidByHref = mutableMapOf<String, String>()
        for (rel in repoFiles) {
            val content = sink.readFile(rel) ?: continue
            val ev = IcalRepoMapper.fromRepoFile(content) ?: continue
            val href = hrefForUid(ev.uid)
            seenUidByHref[href] = ev.uid
            val known = journal.get(href)
            val ical = IcalCodec.encode(ev)
            val resp = if (known == null) {
                http.putResource(href, ical, etag = "")
            } else {
                http.putResource(href, ical, etag = known.etag)
            }
            when (resp.code) {
                in 200..299 -> {
                    val newEtag = resp.etag ?: known?.etag.orEmpty()
                    val eid = known?.eventId ?: extractEventIdFromRel(rel) ?: ev.uid
                    journal.put(href, EtagJournal.Entry(etag = newEtag, eventId = eid))
                    if (known == null) added++ else updated++
                }
                412 -> conflicts += href
                else -> return CalDavSyncResult.Failed("PUT $href -> ${resp.code}")
            }
        }

        // Locally deleted = journal href whose repo file is missing → DELETE on server.
        for ((href, entry) in journal.all()) {
            if (href in seenUidByHref) continue
            val rel = findRelByEventId(entry.eventId)
            if (rel != null) continue // file still present, not a delete
            val code = http.deleteResource(href, entry.etag)
            if (code in 200..299 || code == 404) {
                journal.remove(href); deleted++
            } else if (code == 412) {
                conflicts += href
            }
        }

        if (conflicts.isNotEmpty()) return CalDavSyncResult.Conflicted(conflicts)
        if (added + updated + deleted > 0) return CalDavSyncResult.Pushed(added, updated, deleted)
        return CalDavSyncResult.UpToDate
    }

    private fun hrefForUid(uid: String): String {
        val base = mirror.calendarPath
        val sep = if (base.endsWith("/")) "" else "/"
        return "$base$sep" + safeFile(uid) + ".ics"
    }

    private fun safeFile(uid: String): String = uid.map {
        if (it.isLetterOrDigit() || it == '-' || it == '_' || it == '.') it else '_'
    }.joinToString("")

    private fun findRelByEventId(eventId: String): String? =
        sink.listEventFiles(mirror.targetCalendarId).firstOrNull { it.endsWith("$eventId.md") }

    private fun extractEventIdFromRel(rel: String): String? =
        rel.substringAfterLast('/').removeSuffix(".md").takeIf { it.isNotBlank() }

    private fun hostOf(serverUrl: String): String =
        runCatching { serverUrl.toHttpUrl().host }.getOrDefault(serverUrl)

    private fun absolutize(base: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        return runCatching { base.toHttpUrl().resolve(href).toString() }.getOrDefault(href)
    }

    companion object {
        private const val PROPFIND_ETAGS = """<?xml version="1.0"?>
<propfind xmlns="DAV:"><prop><getetag/><resourcetype/></prop></propfind>"""
    }
}
