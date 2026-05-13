package com.eight87.skb.cli.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Implements X.11 / CLI-E.1..E.2 repo discovery:
 *
 *   1. `--repo <path>` flag (passed via [explicit]).
 *   2. `SKB_REPO` environment variable.
 *   3. Walk up from `$PWD` looking for a `.strictlykeptboy/` directory.
 *      Halts at filesystem root or at `$HOME` (per CLI-E.2 — avoids
 *      surprising matches above the user's home).
 *
 * Returns the repo root [Path] (the directory CONTAINING
 * `.strictlykeptboy/`), or throws [CliError] with [ExitCode.NOT_FOUND]
 * if nothing resolves.
 *
 * The CLI-E precedence rules around `~/.skb/config.toml`'s `active_repo`
 * (item 4 in CLI-E.1) are deferred — that config file ships in a later
 * CLI-E phase.
 */
object RepoDiscovery {
  const val MARKER_DIR = ".strictlykeptboy"

  fun discover(
    explicit: String? = null,
    env: Map<String, String> = System.getenv(),
    cwd: Path = Paths.get("").toAbsolutePath(),
    home: Path? = System.getProperty("user.home")?.let { Paths.get(it) },
  ): Path {
    explicit?.let { return resolveExplicit(it, cwd) }
    env["SKB_REPO"]?.takeIf { it.isNotBlank() }?.let { return resolveExplicit(it, cwd) }
    walkUp(cwd, home)?.let { return it }
    throw CliError(
      ExitCode.NOT_FOUND,
      "no strictlykeptboy repo found",
      hint = "pass --repo <path>, set SKB_REPO, or run from within a repo (cwd: $cwd)",
      details = mapOf("kind" to "repo", "cwd" to cwd.toString()),
    )
  }

  private fun resolveExplicit(raw: String, cwd: Path): Path {
    val p = Paths.get(raw).let { if (it.isAbsolute) it else cwd.resolve(it).normalize() }
    if (!Files.isDirectory(p)) {
      throw CliError(
        ExitCode.NOT_FOUND,
        "repo path does not exist: $p",
        hint = "create one with: skb repo init <path>",
        details = mapOf("kind" to "repo", "path" to p.toString()),
      )
    }
    val marker = p.resolve(MARKER_DIR)
    if (!Files.isDirectory(marker)) {
      throw CliError(
        ExitCode.CORRUPT,
        "directory is not a strictlykeptboy repo (missing $MARKER_DIR/): $p",
        hint = "initialize with: skb repo init $p",
        details = mapOf("kind" to "repo", "path" to p.toString()),
      )
    }
    return p
  }

  private fun walkUp(start: Path, home: Path?): Path? {
    var cur: Path? = start.toAbsolutePath().normalize()
    while (cur != null) {
      val marker = cur.resolve(MARKER_DIR)
      if (Files.isDirectory(marker)) return cur
      if (home != null && cur == home) return null
      cur = cur.parent
    }
    return null
  }
}
