package com.eight87.skb.cli.core

import java.nio.file.Path

/**
 * Shared per-invocation state passed from the root command down to each
 * subcommand. Resolved lazily — [repoRoot] only triggers discovery on
 * first access, so commands that don't need a repo (e.g. `skb --version`)
 * never throw.
 */
class CliContext(
  val json: Boolean,
  val dryRun: Boolean,
  val quiet: Boolean,
  val verbose: Boolean,
  val noColor: Boolean,
  private val explicitRepo: String?,
  private val env: Map<String, String> = System.getenv(),
) {
  private var resolvedRepo: Path? = null

  /** Lazily resolved repo root. Throws [CliError] if discovery fails. */
  fun repoRoot(): Path {
    resolvedRepo?.let { return it }
    val r = RepoDiscovery.discover(explicitRepo, env)
    resolvedRepo = r
    return r
  }

  /** True iff the [SKB_JSON] env var is truthy. Used by Main to fold it into the `json` flag. */
  companion object {
    fun envJson(env: Map<String, String> = System.getenv()): Boolean =
      env["SKB_JSON"]?.lowercase()?.let { it == "1" || it == "true" || it == "yes" } ?: false
  }
}
