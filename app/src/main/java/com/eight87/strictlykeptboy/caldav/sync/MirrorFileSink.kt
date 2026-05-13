package com.eight87.strictlykeptboy.caldav.sync

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Phase Y — narrow file-write abstraction the worker depends on so it
 * doesn't have to know about `GitRepo` (SOLID.D + SOLID.I). Production
 * wires `GitBackedMirrorFileSink` which atomic-writes + commits under
 * the configured "CalDAV mirror <server-host>" identity (Y.4).
 *
 * Tests pass [FilesystemMirrorFileSink] and verify the materialized
 * files directly.
 */
interface MirrorFileSink {
    /** Write or replace a file at [relativePath]. Returns the absolute path. */
    fun writeFile(relativePath: String, content: String): Path
    /** Delete a file at [relativePath] if present. Returns true if a file existed. */
    fun deleteFile(relativePath: String): Boolean
    /** Read the current content at [relativePath] or null if absent. */
    fun readFile(relativePath: String): String?
    /** List existing event files for one calendar. Used to scope full-pull diffs. */
    fun listEventFiles(calendarId: String): List<String>
    /** Commit a batch of changes; called once per sync pass. No-op for non-git sinks. */
    fun commit(message: String)
}

/** Plain-filesystem sink used by tests + the headless `skb` CLI bridge. */
class FilesystemMirrorFileSink(private val rootDir: Path) : MirrorFileSink {
    override fun writeFile(relativePath: String, content: String): Path {
        val target = rootDir.resolve(relativePath)
        Files.createDirectories(target.parent)
        Files.write(target, content.toByteArray(Charsets.UTF_8),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        return target
    }

    override fun deleteFile(relativePath: String): Boolean {
        val target = rootDir.resolve(relativePath)
        return Files.deleteIfExists(target)
    }

    override fun readFile(relativePath: String): String? {
        val target = rootDir.resolve(relativePath)
        return if (Files.exists(target)) Files.readAllBytes(target).toString(Charsets.UTF_8) else null
    }

    override fun listEventFiles(calendarId: String): List<String> {
        val base = rootDir.resolve("calendars/$calendarId/events")
        if (!Files.exists(base)) return emptyList()
        val list = mutableListOf<String>()
        Files.walk(base).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }
                .forEach { list += rootDir.relativize(it).toString().replace('\\', '/') }
        }
        return list
    }

    override fun commit(message: String) {
        // No-op — tests do not exercise git. Production uses the
        // GitBackedMirrorFileSink companion which delegates to GitRepo.
    }
}
