package com.eight87.skb.cli.repo

import com.eight87.skb.cli.commands.emitHuman
import com.eight87.skb.cli.commands.emitJson
import com.eight87.skb.cli.core.AtomicWriter
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.JsonEnvelope
import com.eight87.skb.cli.core.RepoDiscovery
import com.eight87.skb.cli.core.Uuid7
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Phase SS.4 — `skb repo fork --source <repo-id> <destination>`.
 *
 * Local-only fork mechanic mirroring the Android-side [RepoForker]:
 *
 *   1. Copy the source repo tree to <destination>.
 *   2. Reset `.strictlykeptboy/repo-id` to a fresh UUIDv7.
 *   3. Rewrite the `id` field in `.strictlykeptboy/repo.toml`.
 *   4. Drop the cached `.strictlykeptboy/repo-fingerprint` (next read
 *      re-derives from the new root commit if/when one exists).
 *   5. Append a `read_only` back-reference entry to the forked repo's
 *      `references.toml` pointing at the source.
 *
 * The CLI does NOT touch `.git/`: the user is expected to drop the
 * existing remote (or `git remote rename origin upstream`) before
 * publishing their fork — keeps the mechanic minimal and reversible.
 *
 * SOLID.S: one job — local-fork mutations. SOLID.D: filesystem-only, no
 * external git or JGit dep. SOLID.I: narrow CLI surface — exactly two
 * arguments (`--source` + destination path) plus standard flags.
 *
 * Output: human + JSON. JSON envelope `repo.fork` payload carries the
 * new repo-id, destination path, and the back-reference summary.
 */
class ForkCommand(private val ctxOf: () -> CliContext) : CliktCommand(name = "fork") {

    private val source by option(
        "--source",
        help = "source repo path (defaults to current --repo / SKB_REPO / discovered repo)",
    )
    private val destinationArg by argument(
        "destination",
        help = "path for the forked repo (must not already be a strictlykeptboy repo)",
    )
    private val displayName by option(
        "--name",
        help = "display name used in the back-reference entry (defaults to source root folder name)",
    )

    override fun run() {
        val ctx = ctxOf()
        val sourceRoot: Path = source?.let { Paths.get(it).toAbsolutePath().normalize() }
            ?: ctx.repoRoot()
        if (!Files.isDirectory(sourceRoot.resolve(RepoDiscovery.MARKER_DIR))) {
            throw CliError(
                ExitCode.NOT_FOUND,
                "source is not a strictlykeptboy repo: $sourceRoot",
                hint = "expected ${sourceRoot.resolve(RepoDiscovery.MARKER_DIR)} to exist",
            )
        }
        val destination: Path = Paths.get(destinationArg).let {
            if (it.isAbsolute) it else Paths.get("").toAbsolutePath().resolve(it)
        }.normalize()
        if (Files.exists(destination.resolve(RepoDiscovery.MARKER_DIR))) {
            throw CliError(
                ExitCode.CONFLICT,
                "destination is already a strictlykeptboy repo: $destination",
            )
        }
        val newRepoId = Uuid7.generate()
        val sourceLabel = displayName ?: sourceRoot.fileName.toString()
        val sourceId = readSourceRepoId(sourceRoot)

        if (ctx.dryRun) {
            emitHuman(
                "DRY RUN: would fork $sourceRoot → $destination (new repo-id $newRepoId)",
                ctx,
            )
            emitJson(
                JsonEnvelope.success(
                    "repo.fork",
                    buildJsonObject {
                        put("source", JsonPrimitive(sourceRoot.toString()))
                        put("destination", JsonPrimitive(destination.toString()))
                        put("new_repo_id", JsonPrimitive(newRepoId))
                        put("source_repo_id", JsonPrimitive(sourceId.orEmpty()))
                        put("dry_run", JsonPrimitive(true))
                    },
                ),
                ctx,
            )
            return
        }

        Files.createDirectories(destination)
        copyTree(sourceRoot, destination)
        resetRepoId(destination, newRepoId)
        invalidateFingerprintCache(destination)
        appendBackReference(destination, sourceId ?: newRepoId, sourceLabel, sourceRoot.toUri().toString())

        emitHuman(
            buildString {
                appendLine("✓ forked $sourceRoot → $destination")
                appendLine("  new repo id:    $newRepoId")
                appendLine("  source repo id: ${sourceId ?: "<unknown>"}")
                append("  back-reference: $sourceLabel written to references.toml (read-only)")
            },
            ctx,
        )
        emitJson(
            JsonEnvelope.success(
                "repo.fork",
                buildJsonObject {
                    put("source", JsonPrimitive(sourceRoot.toString()))
                    put("destination", JsonPrimitive(destination.toString()))
                    put("new_repo_id", JsonPrimitive(newRepoId))
                    put("source_repo_id", JsonPrimitive(sourceId.orEmpty()))
                    put("back_reference_label", JsonPrimitive(sourceLabel))
                },
            ),
            ctx,
        )
    }

    private fun readSourceRepoId(root: Path): String? {
        val idFile = root.resolve(".strictlykeptboy/repo-id")
        if (!Files.isRegularFile(idFile)) return null
        return Files.readString(idFile, StandardCharsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }

    private fun copyTree(src: Path, dst: Path) {
        Files.walk(src).use { stream ->
            stream.forEach { srcPath ->
                val rel = src.relativize(srcPath)
                val out = dst.resolve(rel.toString())
                if (Files.isDirectory(srcPath)) {
                    Files.createDirectories(out)
                } else {
                    out.parent?.let { Files.createDirectories(it) }
                    Files.copy(srcPath, out, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun resetRepoId(dst: Path, newRepoId: String) {
        val repoIdFile = dst.resolve(".strictlykeptboy/repo-id")
        Files.createDirectories(repoIdFile.parent)
        AtomicWriter.writeUtf8(repoIdFile, newRepoId + "\n")
        val repoToml = dst.resolve(".strictlykeptboy/repo.toml")
        if (Files.isRegularFile(repoToml)) {
            val text = Files.readString(repoToml, StandardCharsets.UTF_8)
            val rewritten = text.lineSequence().joinToString("\n") { line ->
                if (line.trimStart().startsWith("id = ")) {
                    val indent = line.takeWhile { it.isWhitespace() }
                    "${indent}id = \"$newRepoId\""
                } else line
            }
            AtomicWriter.writeUtf8(repoToml, rewritten)
        }
    }

    private fun invalidateFingerprintCache(dst: Path) {
        val cache = dst.resolve(".strictlykeptboy/repo-fingerprint")
        if (Files.exists(cache)) Files.delete(cache)
    }

    private fun appendBackReference(dst: Path, sourceRepoId: String, label: String, url: String) {
        val file = dst.resolve("references.toml")
        val existing = if (Files.isRegularFile(file)) Files.readString(file, StandardCharsets.UTF_8) else ""
        val sb = StringBuilder()
        if (existing.isBlank()) {
            sb.append("+++\n").append("schema_version = 1\n").append("+++\n")
        } else {
            sb.append(existing)
            if (!existing.endsWith("\n")) sb.append('\n')
        }
        sb.append('\n')
        sb.append("[[reference]]\n")
        sb.append("repo_id = \"").append(escape(sourceRepoId)).append("\"\n")
        sb.append("display_name = \"").append(escape(label)).append("\"\n")
        sb.append("url = \"").append(escape(url)).append("\"\n")
        sb.append("read_only = true\n")
        AtomicWriter.writeUtf8(file, sb.toString())
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
