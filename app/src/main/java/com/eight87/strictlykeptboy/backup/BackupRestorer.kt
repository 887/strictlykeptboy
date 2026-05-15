package com.eight87.strictlykeptboy.backup

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.prefs.SkbRootMarker
import com.eight87.strictlykeptboy.store.TomlReader
import com.eight87.strictlykeptboy.store.TomlValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Round 2.17 Phase G.1 / G.2 — counterpart to [BackupArchiver].
 *
 *  - [restoreFromArchive] wipes the destination parent (after a safety
 *    check) and streams the tar.gz back into place. Throws when the
 *    destination has files but no `.skb-root` — refusing to nuke user
 *    data we didn't put there per D-2.17.g.
 *  - [rescanParent] walks the parent and synthesizes a fresh
 *    [RepoConfig] for each repo directory it finds. Used by the
 *    "Restore from current folder" flow when the user edits repo dirs
 *    out-of-band (rename, drop in via SAF, etc.).
 *
 * Streaming: the tar is consumed entry-by-entry; we never read the
 * whole archive into memory.
 */
object BackupRestorer {

    /**
     * Marker exception for the wipe-safety refusal. Surfaces as a
     * `Result.failure` to UI callers so they can show a distinct
     * "this folder has files we don't recognise" toast.
     */
    class UnsafeWipeException(val parent: File) :
        IOException("refusing to wipe $parent: contains files but no ${SkbRootMarker.FILE_NAME}")

    /**
     * Wipe [parent] and extract [input] (a `.tar.gz` produced by
     * [BackupArchiver.export]) into it.
     *
     * Wipe-safety: if [parent] currently contains any files / dirs but
     * NO valid `.skb-root` marker, throws [UnsafeWipeException] BEFORE
     * touching anything. An empty / nonexistent parent is fine.
     *
     * Marker preservation: an archive produced by Phase F skips the
     * `.skb-root` file (it's regenerated on restore). After extract,
     * if the marker is missing we re-create one so the next boot
     * recognises the parent.
     *
     * Caller owns [input] (typically a SAF
     * `ContentResolver.openInputStream`); this function does not
     * close it.
     */
    suspend fun restoreFromArchive(
        input: InputStream,
        parent: File,
        deviceName: String = "",
    ): BackupArchiver.Manifest = withContext(Dispatchers.IO) {
        assertSafeToWipe(parent)
        wipeContents(parent)
        parent.mkdirs()

        var manifest: BackupArchiver.Manifest? = null
        // NOTE: do NOT use .use {} on either stream — caller owns the
        // outer InputStream. The TarArchiveInputStream / Gzip pair are
        // internal wrappers; closing them would also close `input`,
        // which is the caller's prerogative. We explicitly leak them
        // (GC closes on collection) — same pattern Commons Compress
        // recommends when the underlying stream is borrowed.
        val gz = GzipCompressorInputStream(input)
        val tar = TarArchiveInputStream(gz)
        var entry: TarArchiveEntry? = tar.nextEntry
        while (entry != null) {
            val rel = entry.name
            if (rel == "manifest.toml") {
                val bytes = tar.readAllBytes()
                manifest = decodeManifest(bytes.toString(Charsets.UTF_8))
            } else {
                writeEntry(parent.toPath(), entry, tar)
            }
            entry = tar.nextEntry
        }

        // G.6 / marker-preservation: archive skipped `.skb-root`, so
        // regenerate it so the next boot recognises the folder.
        if (!File(parent, SkbRootMarker.FILE_NAME).isFile) {
            SkbRootMarker.write(parent, deviceName)
        }

        manifest
            ?: error("archive carried no manifest.toml as its first entry")
    }

    /**
     * Walk `<parent>/<dir>/.git` and produce a [RepoConfig] for every
     * discovered repo. `repo.toml` (at `<dir>/.strictlykeptboy/repo.toml`)
     * supplies `displayName` / `defaultCalendarId` when present;
     * otherwise we fall back to the directory name + nulls.
     */
    suspend fun rescanParent(parent: File): List<RepoConfig> = withContext(Dispatchers.IO) {
        if (!parent.isDirectory) return@withContext emptyList()
        parent.listFiles().orEmpty()
            .asSequence()
            .filter { it.isDirectory }
            .filter { !it.name.startsWith(".") }
            .filter { File(it, ".git").exists() }
            .map { repoConfigFromDir(it) }
            .toList()
            .sortedBy { it.displayName.lowercase() }
    }

    private fun repoConfigFromDir(dir: File): RepoConfig {
        val toml = File(dir, ".strictlykeptboy/repo.toml")
            .takeIf { it.isFile }
            ?.runCatching { TomlReader.parse(readText(Charsets.UTF_8)) }
            ?.getOrNull()
        val repoId = (toml?.scalars?.get("id") as? TomlValue.Str)?.value ?: dir.name
        val displayName = (toml?.scalars?.get("name") as? TomlValue.Str)?.value ?: dir.name
        val defaultCalendarId =
            (toml?.scalars?.get("default_calendar_id") as? TomlValue.Str)?.value
                ?: discoverFirstCalendarId(dir)
        return RepoConfig(
            repoId = repoId,
            displayName = displayName,
            rootDir = dir.absolutePath,
            authorIdentity = AuthorIdentity(name = "", email = ""),
            defaultCalendarId = defaultCalendarId,
        )
    }

    private fun discoverFirstCalendarId(dir: File): String? {
        val calendars = File(dir, "calendars")
        if (!calendars.isDirectory) return null
        return calendars.listFiles().orEmpty()
            .asSequence()
            .filter { it.isDirectory && File(it, "calendar.toml").isFile }
            .map { it.name }
            .sorted()
            .firstOrNull()
    }

    internal fun assertSafeToWipe(parent: File) {
        if (!parent.exists()) return
        val children = parent.listFiles().orEmpty()
        if (children.isEmpty()) return
        if (SkbRootMarker.isSkbRoot(parent)) return
        throw UnsafeWipeException(parent)
    }

    private fun wipeContents(parent: File) {
        if (!parent.isDirectory) return
        parent.listFiles()?.forEach { child ->
            if (Files.isSymbolicLink(child.toPath())) {
                Files.deleteIfExists(child.toPath())
            } else if (child.isDirectory) {
                child.deleteRecursively()
            } else {
                child.delete()
            }
        }
    }

    private fun writeEntry(root: Path, entry: TarArchiveEntry, tar: TarArchiveInputStream) {
        // Reject path traversal — entry names must be relative and not
        // escape the parent root. (Commons Compress already normalises
        // `..`, but be belt-and-braces.)
        val rel = entry.name.trim('/')
        if (rel.isEmpty() || rel.contains("..")) return
        val target = root.resolve(rel).normalize()
        if (!target.startsWith(root)) return

        when {
            entry.isSymbolicLink -> {
                Files.createDirectories(target.parent)
                Files.deleteIfExists(target)
                try {
                    Files.createSymbolicLink(target, Paths.get(entry.linkName))
                } catch (_: UnsupportedOperationException) {
                    // Filesystem doesn't support symlinks — fall back
                    // to a stub file referencing the target so the
                    // user can recreate it.
                    Files.write(
                        target,
                        "See ${entry.linkName}.\n".toByteArray(Charsets.UTF_8),
                    )
                }
            }
            entry.isDirectory -> {
                Files.createDirectories(target)
            }
            else -> {
                Files.createDirectories(target.parent)
                Files.newOutputStream(target).use { out -> tar.copyTo(out) }
            }
        }
    }

    private fun decodeManifest(text: String): BackupArchiver.Manifest {
        val t = TomlReader.parse(text)
        fun s(name: String, default: String = ""): String =
            (t.scalars[name] as? TomlValue.Str)?.value
                ?: (t.scalars[name] as? TomlValue.OffsetDateTime)?.text
                ?: default
        fun i(name: String, default: Long = 0L): Long =
            (t.scalars[name] as? TomlValue.I64)?.value ?: default
        return BackupArchiver.Manifest(
            skbVersion = s("skb_version"),
            schemaVersion = i("schema_version").toInt(),
            createdAt = s("created_at"),
            repoCount = i("repo_count").toInt(),
            parentLabel = s("parent_label"),
            deviceName = s("device_name"),
        )
    }
}
