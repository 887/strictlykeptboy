package com.eight87.strictlykeptboy.ui.share

import com.eight87.strictlykeptboy.store.ReferencesManifest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Phase SS.1 / SS.2 — fork a shared (read-only) repo into the recipient's
 * own writable copy.
 *
 * The mechanic is intentionally local-only (no JGit push): copy the on-
 * disk repo tree, reset `repo-id` to a fresh UUIDv7, invalidate the cached
 * `repo-fingerprint` (deleted; will be re-derived on next CLI/app open),
 * and add the original as a `read_only` entry in the forked repo's
 * `references.toml` so the link back to the source survives migration.
 *
 * The caller wires actual git operations (re-init `.git/`, drop the
 * existing remote so the user can `remote add origin` for their own
 * publishing target) — the forker only owns the strictlykeptboy-side
 * mutations.
 *
 * SOLID.S: one job — apply the local fork transforms. SOLID.D: pure
 * filesystem; no Android, no JGit; everything goes through `java.nio.file`.
 */
object RepoForker {

    /** Result of a successful fork. */
    data class ForkResult(
        val newRepoRoot: Path,
        val newRepoId: String,
        val backReference: ReferencesManifest.Entry,
    )

    sealed interface ForkOutcome {
        data class Ok(val result: ForkResult) : ForkOutcome
        data class Failed(val reason: String) : ForkOutcome
    }

    /**
     * Perform the fork.
     *
     * @param sourceRepoRoot the original (read-only / shared) repo on disk.
     * @param destinationRepoRoot the new path that should receive the copy.
     *                            Must not exist or must be empty.
     * @param newRepoId fresh UUIDv7 supplied by the caller (we don't
     *                  generate UUIDs here — keeps the forker pure and
     *                  deterministic in tests).
     * @param sourceLabel display label used in the back-reference entry.
     * @param sourceRepoId the original repo's UUIDv7 (used as the
     *                     `repo_id` field of the back-reference entry —
     *                     consistent with the recipient-side write that
     *                     pointed at the shared repo in the first place).
     * @param sourceUrls remote URLs the source was known by, embedded in
     *                   the back-reference for cross-device sync.
     * @param sourceFingerprint optional `write_back_target` value — when
     *                          set, signals "I can still write feedback
     *                          back to the original" per YY.8.
     */
    fun fork(
        sourceRepoRoot: Path,
        destinationRepoRoot: Path,
        newRepoId: String,
        sourceRepoId: String,
        sourceLabel: String,
        sourceUrls: List<String>,
        sourceFingerprint: String? = null,
    ): ForkOutcome {
        if (!Files.isDirectory(sourceRepoRoot)) {
            return ForkOutcome.Failed("source repo root does not exist: $sourceRepoRoot")
        }
        if (Files.exists(destinationRepoRoot)) {
            val nonEmpty = Files.list(destinationRepoRoot).use { it.findAny().isPresent }
            if (nonEmpty) return ForkOutcome.Failed("destination $destinationRepoRoot is not empty")
        } else {
            Files.createDirectories(destinationRepoRoot)
        }
        copyTree(sourceRepoRoot, destinationRepoRoot)
        resetRepoId(destinationRepoRoot, newRepoId)
        invalidateFingerprintCache(destinationRepoRoot)
        val entry = ReferencesManifest.Entry(
            repoId = sourceRepoId,
            displayName = sourceLabel,
            urls = sourceUrls,
            required = false,
            writeBackTarget = sourceFingerprint,
        )
        val current = ReferencesManifest.read(destinationRepoRoot)
        // The forked repo's own id is the *new* id, so cycle detection
        // against `newRepoId` is what matters here.
        val result = ReferencesManifest.addOrReplace(
            current,
            entry,
            thisRepoId = newRepoId,
        )
        val merged = when (result) {
            is ReferencesManifest.AddResult.Added -> result.manifest
            is ReferencesManifest.AddResult.Replaced -> result.manifest
            is ReferencesManifest.AddResult.CyclicRejected ->
                return ForkOutcome.Failed("references.toml back-link rejected: ${result.reason}")
        }
        ReferencesManifest.write(destinationRepoRoot, merged)
        return ForkOutcome.Ok(
            ForkResult(
                newRepoRoot = destinationRepoRoot,
                newRepoId = newRepoId,
                backReference = entry,
            ),
        )
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
        Files.write(repoIdFile, (newRepoId + "\n").toByteArray(StandardCharsets.UTF_8))
        // Also rewrite the `id` field in repo.toml if present (best-effort
        // line-level rewrite — the file is hand-rolled TOML).
        val repoToml = dst.resolve(".strictlykeptboy/repo.toml")
        if (Files.isRegularFile(repoToml)) {
            val text = String(Files.readAllBytes(repoToml), StandardCharsets.UTF_8)
            val rewritten = text.lineSequence().joinToString("\n") { line: String ->
                if (line.trimStart().startsWith("id = ")) {
                    val indent = line.takeWhile { it.isWhitespace() }
                    "${indent}id = \"$newRepoId\""
                } else line
            }
            Files.write(repoToml, rewritten.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun invalidateFingerprintCache(dst: Path) {
        val cache = dst.resolve(".strictlykeptboy/repo-fingerprint")
        if (Files.exists(cache)) Files.delete(cache)
    }
}
