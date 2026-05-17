package com.eight87.strictlykeptboy.store

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Round 2.27 / Phase D.2 — writes a prompt-response file at the
 * D-2.27.g canonical path:
 *
 *     <repoRoot>/calendars/<calId>/cage-check-responses/<ruleId>/<yyyy-MM-dd>.md
 *
 * Frontmatter shape (per D-2.27.g):
 *   schema_version, id, kind = "prompt_response", prompt_id,
 *   prompt_instance_date, responder, created_at, [attachment].
 *
 * Idempotent: a second write for the same `(ruleId, instanceDate)`
 * overwrites the existing file. The generated `id` is a deterministic
 * UUIDv7-shaped value derived from `instanceDate + ruleId` so the
 * second write doesn't churn it.
 *
 * NO git logic here — the JGit auto-commit path runs elsewhere in the
 * commit-hook plumbing. This file only writes bytes.
 *
 * SOLID:
 *  - **S:** Only emits the prompt-response file; no projector reads,
 *    no cache mutation, no commit.
 *  - **D:** Pure over [Path] + value-typed inputs. Caller injects time
 *    via [nowIso] for deterministic tests.
 */
object PromptResponseWriter {

    fun write(
        repoRoot: Path,
        calId: String,
        ruleId: String,
        instanceDate: LocalDate,
        responderId: String,
        body: String,
        attachment: String? = null,
        nowIso: String = OffsetDateTime.now().withNano(0).toString(),
    ): Path {
        require(calId.isNotBlank()) { "calId required" }
        require(ruleId.isNotBlank()) { "ruleId required" }
        require(responderId.isNotBlank()) { "responderId required" }

        val dir = repoRoot
            .resolve("calendars")
            .resolve(calId)
            .resolve("cage-check-responses")
            .resolve(ruleId)
        Files.createDirectories(dir)
        val target = dir.resolve("$instanceDate.md")

        val id = stableUuid(instanceDate, ruleId)
        val fm = TomlTable().apply {
            putInt("schema_version", 1)
            putString("id", id)
            putString("kind", "prompt_response")
            putString("prompt_id", ruleId)
            putLocalDate("prompt_instance_date", instanceDate.toString())
            putString("responder", responderId)
            // RepoScanner's RawEntity decode requires `author` (EntityHeader
            // .readFrom); responder == author for prompt-response files.
            putString("author", responderId)
            putOffsetDateTime("created_at", nowIso)
            // RepoScanner's RawEntity decode requires `updated_at` on every
            // .md entity (EntityHeader.readFrom). Mirror `created_at` here
            // so prompt-response files round-trip through the scanner.
            putOffsetDateTime("updated_at", nowIso)
            if (!attachment.isNullOrBlank()) putString("attachment", attachment)
        }
        val normalisedBody = buildString {
            if (body.isNotEmpty()) {
                append(body)
                if (!body.endsWith("\n")) append('\n')
            }
        }
        val doc = FrontmatterDoc(fm, normalisedBody)
        // Idempotent overwrite: write to a sibling temp file then move
        // atomically so an interrupted write never leaves a partial.
        val tmp = dir.resolve("$instanceDate.md.tmp")
        Files.write(tmp, FrontmatterWriter.serialize(doc).toByteArray(StandardCharsets.UTF_8))
        runCatching {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.getOrElse {
            // Filesystems without ATOMIC_MOVE (some test FS) — fall back
            // to a plain replace.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    /**
     * Deterministic UUIDv7-shaped id derived from `instanceDate + ruleId`.
     * Not a real time-ordered UUIDv7 — we explicitly do NOT use the wall
     * clock so repeat-writes of the same `(date, rule)` produce the same
     * id (idempotence). The shape (`xxxxxxxx-xxxx-7xxx-8xxx-xxxxxxxxxxxx`,
     * version=7, variant=10) is preserved so downstream UUID parsers
     * accept it.
     */
    internal fun stableUuid(instanceDate: LocalDate, ruleId: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$instanceDate|$ruleId".toByteArray(StandardCharsets.UTF_8))
        val hex = digest.joinToString(separator = "") { b -> "%02x".format(b.toInt() and 0xff) }
        // Force version = 7 and variant = 10 (RFC 4122 §4.1.1).
        val versioned = hex.substring(0, 12) + "7" + hex.substring(13, 16) +
            "8" + hex.substring(17, 20) + hex.substring(20, 32)
        return buildString {
            append(versioned, 0, 8); append('-')
            append(versioned, 8, 12); append('-')
            append(versioned, 12, 16); append('-')
            append(versioned, 16, 20); append('-')
            append(versioned, 20, 32)
        }
    }
}
