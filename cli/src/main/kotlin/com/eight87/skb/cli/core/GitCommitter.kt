package com.eight87.skb.cli.core

import java.nio.file.Path

/**
 * Auto-commit hook per CLI-B.3 / DM-E. Shells out to `git` (already on
 * PATH; required by the broader app's git-backed model). JGit could be
 * a fat-jar dep, but it doubles the jar size and re-implements what
 * the system `git` already does correctly. Decision: shell out, parity
 * with the GUI's commit-message format.
 *
 * SOLID.S: this object commits files to git. It does not write files
 * (AtomicWriter) or compose messages (callers do that — see
 * the commands/ package for message templates).
 *
 * SOLID.D: callers depend on [GitOps] (interface). [SystemGitOps] is
 * the production impl. Tests substitute a fake.
 */
interface GitOps {
  /** Add the given relative paths and commit with [message]. Returns short SHA, or null when nothing was staged. */
  fun addAndCommit(repoRoot: Path, files: List<String>, message: String, authorName: String, authorEmail: String): String?
  /** Initialise a git repository at [path]. No-op if already initialised. */
  fun init(path: Path)
  /** Returns the short SHA of HEAD, or null if no commits yet. */
  fun headShortSha(repoRoot: Path): String?
}

object SystemGitOps : GitOps {

  override fun init(path: Path) {
    if (path.resolve(".git").toFile().exists()) return
    run(path, listOf("git", "init", "-q", "-b", "main"))
  }

  override fun addAndCommit(
    repoRoot: Path,
    files: List<String>,
    message: String,
    authorName: String,
    authorEmail: String,
  ): String? {
    if (files.isEmpty()) return null
    run(repoRoot, listOf("git", "add", "--") + files)
    // Bail out cleanly if there's nothing staged (e.g. no-op edit).
    val status = run(repoRoot, listOf("git", "status", "--porcelain")).trim()
    if (status.isEmpty()) return null
    val env = mapOf(
      "GIT_AUTHOR_NAME" to authorName,
      "GIT_AUTHOR_EMAIL" to authorEmail,
      "GIT_COMMITTER_NAME" to authorName,
      "GIT_COMMITTER_EMAIL" to authorEmail,
    )
    run(repoRoot, listOf("git", "commit", "-q", "-m", message), env)
    return headShortSha(repoRoot)
  }

  override fun headShortSha(repoRoot: Path): String? {
    return try {
      run(repoRoot, listOf("git", "rev-parse", "--short", "HEAD")).trim().ifEmpty { null }
    } catch (_: GitInvocationFailed) {
      null
    }
  }

  private fun run(cwd: Path, cmd: List<String>, env: Map<String, String> = emptyMap()): String {
    val pb = ProcessBuilder(cmd)
      .directory(cwd.toFile())
      .redirectErrorStream(true)
    pb.environment().putAll(env)
    val proc = pb.start()
    val out = proc.inputStream.bufferedReader().readText()
    val rc = proc.waitFor()
    if (rc != 0) throw GitInvocationFailed(cmd, rc, out)
    return out
  }
}

class GitInvocationFailed(
  val cmd: List<String>,
  val rc: Int,
  val output: String,
) : RuntimeException("git ${cmd.drop(1).joinToString(" ")} failed (rc=$rc):\n$output")
