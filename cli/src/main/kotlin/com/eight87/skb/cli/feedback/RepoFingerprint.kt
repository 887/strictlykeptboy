package com.eight87.skb.cli.feedback

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Phase YY.1 / FB-A — repo fingerprint derivation + cache.
 *
 * Global ID format = `<repo-fingerprint>:<entity-uuid>` per the FB-A LOCK.
 * Fingerprint is `SHA-256(root-commit's tree SHA, hex)` truncated to the
 * first 16 hex chars (8 bytes). Stable across rebases / squashes of
 * non-root commits because the tree-SHA of the root is purely a function
 * of root-commit *content*.
 *
 * The cache file `.strictlykeptboy/repo-fingerprint` is plain text, one
 * line, ALWAYS gitignored, NEVER committed. If the cache is missing the
 * fingerprint is re-derived from `git log --reverse --max-count=1
 * --format=%T`.
 *
 * For empty repos (no commits yet) we return `null` and the caller defers
 * feedback writes until the first commit lands. The wizard makes that
 * commit immediately on scaffold so this is only theoretical.
 *
 * SOLID.S: this file does one thing — derive + cache + load the
 * fingerprint. The git-shell-out path lives behind [GitTreeReader] for
 * SOLID.D + test seams.
 */
object RepoFingerprint {
  const val CACHE_REL_PATH = ".strictlykeptboy/repo-fingerprint"
  const val GITIGNORE_LINE = ".strictlykeptboy/repo-fingerprint"

  /**
   * Read cache if valid (16 lowercase hex chars). Else compute from the
   * root tree SHA and persist the cache. Returns `null` for empty repos.
   *
   * If [verify] is true and the cache exists, recompute and compare —
   * mismatch returns the freshly-computed value WITHOUT rewriting the
   * cache; the caller (typically [detectRootRewrite]) decides what to do.
   */
  fun computeOrLoad(
    repoRoot: Path,
    git: GitTreeReader = SystemGitTreeReader,
    verify: Boolean = false,
  ): String? {
    val cache = repoRoot.resolve(CACHE_REL_PATH)
    if (!verify && Files.isRegularFile(cache)) {
      val text = Files.readString(cache).trim()
      if (isValidFingerprint(text)) return text
    }
    val rootTree = git.rootTreeSha(repoRoot) ?: return null
    val fp = sha256Hex16(rootTree)
    if (!Files.isRegularFile(cache)) {
      Files.createDirectories(cache.parent)
      Files.writeString(cache, fp + "\n")
    }
    return fp
  }

  /**
   * Phase YY.1 / FB-A.4 — root-rewrite detection. Recomputes the
   * fingerprint into a transient and compares against the cached value.
   * Returns [RootRewriteState] so the caller can decide whether to
   * surface the rebind UI.
   */
  fun detectRootRewrite(
    repoRoot: Path,
    git: GitTreeReader = SystemGitTreeReader,
  ): RootRewriteState {
    val cache = repoRoot.resolve(CACHE_REL_PATH)
    val cached = if (Files.isRegularFile(cache)) Files.readString(cache).trim().takeIf { isValidFingerprint(it) } else null
    val rootTree = git.rootTreeSha(repoRoot) ?: return RootRewriteState.NoCommitsYet
    val fresh = sha256Hex16(rootTree)
    return when {
      cached == null -> RootRewriteState.NoCache(fresh)
      cached == fresh -> RootRewriteState.Stable(fresh)
      else -> RootRewriteState.RootRewritten(oldFingerprint = cached, newFingerprint = fresh)
    }
  }

  /** Rewrites the cache file to a new fingerprint after a confirmed rebind. */
  fun replaceCache(repoRoot: Path, newFingerprint: String) {
    require(isValidFingerprint(newFingerprint)) { "invalid fingerprint: $newFingerprint" }
    val cache = repoRoot.resolve(CACHE_REL_PATH)
    Files.createDirectories(cache.parent)
    Files.writeString(cache, newFingerprint + "\n")
  }

  /**
   * Phase YY.1 / FB-A.2 — append the cache filename to a repo's
   * `.gitignore` if it isn't already covered. Idempotent. Safe to call
   * on every repo open.
   */
  fun ensureGitignored(repoRoot: Path) {
    val gi = repoRoot.resolve(".gitignore")
    val existing = if (Files.isRegularFile(gi)) Files.readString(gi) else ""
    val pattern = GITIGNORE_LINE
    val covered = existing.lineSequence().any { it.trim() == pattern || it.trim() == "/$pattern" }
    if (covered) return
    val updated = buildString {
      append(existing)
      if (existing.isNotEmpty() && !existing.endsWith("\n")) append('\n')
      append(pattern).append('\n')
    }
    Files.writeString(gi, updated)
  }

  fun isValidFingerprint(s: String): Boolean = s.length == 16 && s.all { it in '0'..'9' || it in 'a'..'f' }

  internal fun sha256Hex16(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder(64)
    for (b in bytes) {
      val v = b.toInt() and 0xff
      sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
    }
    return sb.substring(0, 16)
  }

  private val HEX = "0123456789abcdef".toCharArray()
}

/**
 * Root-rewrite states surfaced by [RepoFingerprint.detectRootRewrite].
 * FB-A.4 / FB-I.7: the rebind UI walks `feedback/<old-fingerprint>/` and
 * rewrites paths + frontmatter `target` strings to the new fingerprint
 * in one commit. The CLI surface for the rebind itself lives outside
 * this file (see [FeedbackRebinder]).
 */
sealed class RootRewriteState {
  data object NoCommitsYet : RootRewriteState()
  data class NoCache(val current: String) : RootRewriteState()
  data class Stable(val fingerprint: String) : RootRewriteState()
  data class RootRewritten(val oldFingerprint: String, val newFingerprint: String) : RootRewriteState()
}

/**
 * SOLID.D test seam: production reads root-tree SHA via `git log
 * --reverse --max-count=1 --format=%T`. Tests substitute a fake.
 */
interface GitTreeReader {
  /** Returns the tree SHA (40 lowercase hex chars) of the unique root commit, or `null` if no commits. */
  fun rootTreeSha(repoRoot: Path): String?
}

object SystemGitTreeReader : GitTreeReader {
  override fun rootTreeSha(repoRoot: Path): String? {
    val pb = ProcessBuilder("git", "log", "--reverse", "--max-count=1", "--format=%T")
      .directory(repoRoot.toFile())
      .redirectErrorStream(true)
    val proc = pb.start()
    val out = proc.inputStream.bufferedReader().readText().trim()
    val rc = proc.waitFor()
    if (rc != 0) return null
    return out.lineSequence().firstOrNull { it.matches(Regex("^[0-9a-f]{40}$")) }
  }
}
