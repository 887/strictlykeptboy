package com.eight87.strictlykeptboy.caldav.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase Y.7 — per-mirror ETag journal. Records `(href → etag + eventId)`
 * so a subsequent pull of the same VEVENT lands in the same repo file
 * (`existingEventId` to [com.eight87.strictlykeptboy.caldav.ical.IcalRepoMapper.toRepoFile]).
 *
 * Persisted as a small JSON file under
 * `<rootDir>/.skb/caldav/<mirrorId>.json`. The file lives inside the
 * user's git repo working tree but under a `.skb/` dir that the
 * RepoBootstrap (Phase A) already excludes from commits.
 */
class EtagJournal(private val rootDir: Path, private val mirrorId: String) {

    @Serializable data class Entry(val etag: String, val eventId: String)
    @Serializable private data class Snapshot(val v: Int = 1, val e: Map<String, Entry> = emptyMap())

    private val entries: MutableMap<String, Entry> = load().toMutableMap()

    fun get(href: String): Entry? = entries[href]
    fun put(href: String, entry: Entry) { entries[href] = entry; save() }
    fun remove(href: String) { entries.remove(href); save() }
    fun hrefs(): Set<String> = entries.keys.toSet()
    fun all(): Map<String, Entry> = entries.toMap()

    private fun file(): Path = rootDir.resolve(".skb/caldav/$mirrorId.json")

    private fun load(): Map<String, Entry> {
        val f = file()
        if (!Files.exists(f)) return emptyMap()
        val text = Files.readAllBytes(f).toString(Charsets.UTF_8)
        return runCatching { JSON.decodeFromString<Snapshot>(text).e }.getOrDefault(emptyMap())
    }

    private fun save() {
        val f = file()
        Files.createDirectories(f.parent)
        val text = JSON.encodeToString(Snapshot(e = entries.toMap()))
        Files.write(f, text.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private val JSON = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}
