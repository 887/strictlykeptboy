package com.eight87.skb.cli.feedback

import com.eight87.skb.cli.core.AtomicWriter
import com.eight87.skb.cli.core.MiniToml
import com.eight87.skb.cli.core.TomlTable
import java.nio.file.Files
import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Phase YY.2 / FB-B — device-local repo registry.
 *
 * Path: `<app-data>/repo-registry.toml`. NEVER in any git repo, never
 * synced. Records `(fingerprint → local-path + display-name +
 * isolate_from)` for every repo opened on this device.
 *
 * Asymmetric per-direction isolation (FB-B locked): each repo controls
 * only what *it* refuses to see. `isolate_from = [X]` in repo A means
 * "A's view does not see X"; X may or may not isolate against A.
 *
 * SOLID.S: registry IO + filtering only. Auto-registration on repo-open
 * is the caller's responsibility (Phase B / QQ / SS).
 *
 * SOLID.I: the only public read-side is [list] / [byFingerprint] /
 * [allVisibleTo] — the chokepoint required by FB-F.1.
 *
 * FB-F.1 LINT: this file is the ONLY place that reads
 * `repo-registry.toml`. Any other consumer routes through this object;
 * a future Detekt rule will enforce that.
 */
class RepoRegistry private constructor(val storePath: Path) {

  data class RegisteredRepo(
    val fingerprint: String,
    val localPath: Path,
    val displayName: String,
    val lastSeen: String,
    /**
     * Asymmetric: fingerprints this repo refuses to see. [allVisibleTo]
     * filters these out of the viewer's result set. NOT consulted for
     * the *target* repo's view of the viewer — each side controls only
     * its own visibility.
     */
    val isolateFrom: List<String> = emptyList(),
  )

  /** Snapshot of every registered repo. Empty list on first read. */
  fun list(): List<RegisteredRepo> = read()

  fun byFingerprint(fp: String): RegisteredRepo? = list().firstOrNull { it.fingerprint == fp }

  /**
   * Returns every registered repo EXCEPT those that the [viewerFp] has
   * isolated. The viewer itself is included in the result (the viewer's
   * own feedback is visible from its own repo).
   *
   * FB-F.2 LOCK: when the viewer's `isolate_from` excludes targets the
   * UI MUST show no count masking — the isolated source is structurally
   * invisible. This method is the chokepoint that delivers that
   * structural invisibility.
   */
  fun allVisibleTo(viewerFp: String): List<RegisteredRepo> {
    val all = list()
    val viewer = all.firstOrNull { it.fingerprint == viewerFp } ?: return all
    val hide = viewer.isolateFrom.toSet()
    return all.filter { it.fingerprint == viewerFp || it.fingerprint !in hide }
  }

  /** Upsert a repo entry. Idempotent — re-registering bumps last_seen. */
  fun register(fingerprint: String, localPath: Path, displayName: String, now: String = isoNow()): RegisteredRepo {
    require(GlobalId.isValidFingerprint(fingerprint)) { "invalid fingerprint: $fingerprint" }
    val existing = list().toMutableList()
    val idx = existing.indexOfFirst { it.fingerprint == fingerprint }
    val replacement = if (idx >= 0) {
      existing[idx].copy(localPath = localPath, displayName = displayName, lastSeen = now)
    } else {
      RegisteredRepo(fingerprint, localPath, displayName, now)
    }
    if (idx >= 0) existing[idx] = replacement else existing.add(replacement)
    write(existing)
    return replacement
  }

  /** Drop the repo entry on remove-repo (Phase I.5). */
  fun unregister(fingerprint: String) {
    val existing = list()
    val next = existing.filterNot { it.fingerprint == fingerprint }
    if (next.size != existing.size) write(next)
  }

  /** Append [hidden] to [viewerFp]'s isolate_from list. Idempotent. */
  fun isolate(viewerFp: String, hidden: String) {
    require(GlobalId.isValidFingerprint(hidden)) { "invalid fingerprint: $hidden" }
    require(viewerFp != hidden) { "cannot isolate from self" }
    val existing = list().toMutableList()
    val idx = existing.indexOfFirst { it.fingerprint == viewerFp }
    require(idx >= 0) { "viewer repo $viewerFp not registered" }
    val current = existing[idx]
    if (hidden in current.isolateFrom) return
    existing[idx] = current.copy(isolateFrom = current.isolateFrom + hidden)
    write(existing)
  }

  /** Remove [hidden] from [viewerFp]'s isolate_from list. Idempotent. */
  fun unisolate(viewerFp: String, hidden: String) {
    val existing = list().toMutableList()
    val idx = existing.indexOfFirst { it.fingerprint == viewerFp }
    if (idx < 0) return
    val current = existing[idx]
    if (hidden !in current.isolateFrom) return
    existing[idx] = current.copy(isolateFrom = current.isolateFrom - hidden)
    write(existing)
  }

  // --------- IO (private) ---------

  private fun read(): List<RegisteredRepo> {
    if (!Files.isRegularFile(storePath)) return emptyList()
    val text = Files.readString(storePath)
    return parseRegistry(text)
  }

  private fun write(entries: List<RegisteredRepo>) {
    Files.createDirectories(storePath.parent)
    AtomicWriter.writeUtf8(storePath, emitRegistry(entries))
  }

  companion object {
    /** SOLID.I — open or create the registry at an explicit path. Tests pass tmpdir paths. */
    fun openAt(storePath: Path): RepoRegistry = RepoRegistry(storePath)

    /**
     * Default device-local store path. Phase YY.2: lives at
     * `<XDG_CONFIG_HOME or ~/.config>/skb/repo-registry.toml`. The
     * Android `:app` mirror lands at `<app-data>/repo-registry.toml`.
     */
    fun defaultStorePath(env: Map<String, String> = System.getenv()): Path {
      val xdg = env["XDG_CONFIG_HOME"]?.takeIf { it.isNotBlank() }
      val home = env["HOME"] ?: System.getProperty("user.home") ?: "/tmp"
      val baseStr = xdg ?: "$home/.config"
      return Path.of(baseStr, "skb", "repo-registry.toml")
    }

    private const val SCHEMA_VERSION = 1
    private const val ENTRY_DELIM = "# ---"

    internal fun isoNow(): String =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now(ZoneOffset.UTC))

    /**
     * Hand-rolled emit: top-level `schema_version`, then one `[[repo]]`
     * block per entry. MiniToml doesn't model nested tables / array-of-
     * tables, so we emit literal strings and parse them back by block.
     */
    internal fun emitRegistry(entries: List<RegisteredRepo>): String = buildString {
      append("schema_version = ").append(SCHEMA_VERSION).append('\n')
      for (e in entries) {
        append('\n').append(ENTRY_DELIM).append('\n')
        append("[[repo]]\n")
        append("fingerprint = \"").append(e.fingerprint).append("\"\n")
        append("local_path = \"").append(escape(e.localPath.toString())).append("\"\n")
        append("display_name = \"").append(escape(e.displayName)).append("\"\n")
        append("last_seen = ").append(e.lastSeen).append('\n')
        append("isolate_from = ")
          .append(e.isolateFrom.joinToString(prefix = "[", postfix = "]", separator = ", ") { "\"" + escape(it) + "\"" })
          .append('\n')
      }
    }

    internal fun parseRegistry(text: String): List<RegisteredRepo> {
      val out = mutableListOf<RegisteredRepo>()
      var current: MutableMap<String, String>? = null
      var currentArr: List<String>? = null
      fun flush() {
        val c = current ?: return
        val fp = c["fingerprint"] ?: return
        val lp = c["local_path"] ?: return
        val dn = c["display_name"] ?: return
        val ls = c["last_seen"] ?: ""
        out.add(RegisteredRepo(fp, Path.of(lp), dn, ls, currentArr ?: emptyList()))
        current = null
        currentArr = null
      }
      for (rawLine in text.lines()) {
        val line = rawLine.trim()
        if (line.startsWith("[[repo]]")) { flush(); current = mutableMapOf(); currentArr = emptyList(); continue }
        if (current == null) continue
        if (line.isEmpty() || line.startsWith("#")) continue
        val eq = line.indexOf('=')
        if (eq < 0) continue
        val k = line.substring(0, eq).trim()
        val v = line.substring(eq + 1).trim()
        if (k == "isolate_from") {
          currentArr = parseStringArray(v)
        } else {
          current!![k] = unquote(v)
        }
      }
      flush()
      return out
    }

    private fun escape(s: String): String =
      s.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun unquote(v: String): String {
      if (v.startsWith("\"") && v.endsWith("\"") && v.length >= 2) {
        return v.substring(1, v.length - 1)
          .replace("\\\"", "\"").replace("\\\\", "\\")
      }
      return v
    }

    private fun parseStringArray(rhs: String): List<String> {
      val body = rhs.trim().removePrefix("[").removeSuffix("]").trim()
      if (body.isEmpty()) return emptyList()
      val out = mutableListOf<String>()
      var i = 0
      while (i < body.length) {
        while (i < body.length && body[i] != '"') i++
        if (i >= body.length) break
        val start = ++i
        val sb = StringBuilder()
        while (i < body.length && body[i] != '"') {
          if (body[i] == '\\' && i + 1 < body.length) { sb.append(body[i + 1]); i += 2 } else { sb.append(body[i]); i++ }
        }
        out.add(sb.toString())
        i++
      }
      return out
    }

    @Suppress("unused")
    private fun unused() { MiniToml; TomlTable() } // keep import use stable across refactors
  }
}
