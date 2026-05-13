package com.eight87.skb.cli.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * Active identity binding per CLI-E.5. Phase X ships only the
 * `repo.toml`'s `default_identity` path. `--author` flag and the
 * `~/.skb/config.toml` override land in CLI-E later.
 */
data class ActiveIdentity(val id: String, val displayName: String, val email: String)

object IdentityResolver {
  fun resolve(repoRoot: Path): ActiveIdentity {
    val repoMeta = repoRoot.resolve(".strictlykeptboy/repo.toml")
    if (!Files.isRegularFile(repoMeta)) throw CliError(
      ExitCode.CORRUPT,
      "missing .strictlykeptboy/repo.toml in $repoRoot",
      hint = "this directory's marker exists but the meta file is missing; re-run skb repo init",
    )
    val table = MiniToml.parse(Files.readString(repoMeta))
    val defaultId = table.getString("default_identity") ?: throw CliError(
      ExitCode.CORRUPT,
      "repo.toml missing default_identity",
    )
    val idFile = repoRoot.resolve("identities/$defaultId.md")
    if (!Files.isRegularFile(idFile)) {
      // Tolerate a missing identity file in Phase X — fall back to id stub.
      return ActiveIdentity(defaultId, displayName = "skb-cli", email = "$defaultId@strictlykeptboy.local")
    }
    val (idTable, _) = Frontmatter.parse(Files.readString(idFile))
    val name = idTable.getString("display_name") ?: "skb-cli"
    val email = idTable.getString("email") ?: "$defaultId@strictlykeptboy.local"
    return ActiveIdentity(defaultId, name, email)
  }
}
