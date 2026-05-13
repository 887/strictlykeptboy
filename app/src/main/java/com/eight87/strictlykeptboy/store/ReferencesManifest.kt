package com.eight87.strictlykeptboy.store

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase NN — `references.toml` manifest reader/writer.
 *
 * Schema (per `data-model.md` DM-Q + Phase YY.H + Phase ZZ.B):
 *
 * ```toml
 * +++
 * schema_version = 1
 * +++
 *
 * [[reference]]
 * repo_id = "01900000-0000-7000-8000-000000000001"
 * display_name = "Master's schedule"
 * url = "git@host:o/r.git"          # OR
 * # remotes = ["git@host:o/r.git", "https://host/o/r.git"]
 * required = true
 * write_back_target = "abcdef0123456789"
 *
 * [reference.credential_hint]
 * kind = "ssh-key"
 * fingerprint = "abc123"
 * ```
 *
 * - NN.1: schema with `[[reference]]` entries.
 * - NN.2: this file owns reader + writer.
 * - NN.3: auto-dedup by `repo_id` (later entries win).
 * - NN.4: circular-reference detection — see [validateNoCycle].
 * - NN.5: `required = true` powers the missing-ref banner — UI consumes
 *   [Entry.required].
 * - NN.6: optional `[reference.credential_hint]` block.
 *
 * Round-2 SOLID — this object is the single owner of parse + serialize
 * (S), open for extension via new optional fields (O), reader handles
 * both `url = "..."` and `remotes = [...]` (L w.r.t. ZZ.B), narrow API
 * surface (I), depends only on `java.nio.file` (D).
 *
 * Hand-rolled TOML to match the rest of `store/` (per CLAUDE.md "ktoml
 * is wired for v2").
 */
object ReferencesManifest {

    data class Entry(
        val repoId: String,
        val displayName: String,
        val urls: List<String>,
        val required: Boolean = false,
        val writeBackTarget: String? = null,
        val credentialHint: CredentialHint? = null,
    )

    /** Hint only — never a credential per SH-C.6. */
    data class CredentialHint(
        val kind: String,
        val fingerprint: String? = null,
        val host: String? = null,
    )

    data class Manifest(
        val schemaVersion: Int = 1,
        val entries: List<Entry> = emptyList(),
    )

    sealed interface AddResult {
        data class Added(val manifest: Manifest) : AddResult
        data class Replaced(val manifest: Manifest, val previous: Entry) : AddResult
        data class CyclicRejected(val reason: String) : AddResult
    }

    /** Empty manifest if the file is missing (DM-Q "graceful empty"). */
    fun read(repoRoot: Path): Manifest {
        val file = repoRoot.resolve("references.toml")
        if (!Files.isRegularFile(file)) return Manifest()
        return parse(String(Files.readAllBytes(file), StandardCharsets.UTF_8))
    }

    fun write(repoRoot: Path, manifest: Manifest) {
        val file = repoRoot.resolve("references.toml")
        Files.createDirectories(file.parent ?: repoRoot)
        Files.write(file, serialize(manifest).toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * NN.3 — adds [entry] to [manifest]. If an existing entry has the
     * same `repo_id`, the new entry replaces it ("newer entry wins").
     *
     * NN.4 — refuses additions that would close a cycle in the supplied
     * [referenceGraph]. `referenceGraph[repoId]` is the set of repo-ids
     * directly referenced by the repo identified by `repoId`. The
     * receiver-side resolver injects the current device-wide graph;
     * the manifest layer stays pure.
     */
    fun addOrReplace(
        manifest: Manifest,
        entry: Entry,
        thisRepoId: String,
        referenceGraph: Map<String, Set<String>> = emptyMap(),
    ): AddResult {
        if (entry.repoId == thisRepoId) {
            return AddResult.CyclicRejected("self-reference: cannot add this repo to its own references.toml")
        }
        val cycle = wouldCycle(thisRepoId, entry.repoId, referenceGraph)
        if (cycle != null) return AddResult.CyclicRejected(cycle)
        val previous = manifest.entries.firstOrNull { it.repoId == entry.repoId }
        val deduped = manifest.entries.filterNot { it.repoId == entry.repoId } + entry
        val newManifest = manifest.copy(entries = deduped)
        return if (previous == null) AddResult.Added(newManifest)
        else AddResult.Replaced(newManifest, previous)
    }

    fun remove(manifest: Manifest, repoId: String): Manifest =
        manifest.copy(entries = manifest.entries.filterNot { it.repoId == repoId })

    /**
     * NN.4 cycle check, BFS-style. Returns a human-readable reason on
     * cycle, null on clean.
     *
     * If adding edge `thisRepoId -> targetRepoId`, we look for a path
     * back from `targetRepoId` to `thisRepoId` in the existing graph.
     */
    fun validateNoCycle(
        thisRepoId: String,
        targetRepoId: String,
        referenceGraph: Map<String, Set<String>>,
    ): String? = wouldCycle(thisRepoId, targetRepoId, referenceGraph)

    private fun wouldCycle(
        thisRepoId: String,
        targetRepoId: String,
        graph: Map<String, Set<String>>,
    ): String? {
        if (targetRepoId == thisRepoId) return "cycle: self-reference $thisRepoId"
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(targetRepoId)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!visited.add(node)) continue
            if (node == thisRepoId) {
                return "cycle: $thisRepoId -> $targetRepoId -> ... -> $thisRepoId"
            }
            graph[node].orEmpty().forEach { next -> queue.add(next) }
        }
        return null
    }

    // ---------- TOML codec ----------

    internal fun parse(text: String): Manifest {
        var schemaVersion = 1
        val entries = mutableListOf<Entry>()
        var currentEntry: MutableEntry? = null
        var currentHint: MutableHint? = null
        var inSubTable = false

        fun commit() {
            currentEntry?.let { e ->
                currentHint?.let { h ->
                    e.credentialHint = CredentialHint(h.kind ?: "", h.fingerprint, h.host)
                }
                entries.add(e.build())
            }
            currentEntry = null
            currentHint = null
            inSubTable = false
        }

        for (raw in text.lines()) {
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty() || line == "+++") continue
            when {
                line == "[[reference]]" -> {
                    commit()
                    currentEntry = MutableEntry()
                    inSubTable = false
                }
                line == "[reference.credential_hint]" -> {
                    currentHint = MutableHint()
                    inSubTable = true
                }
                line.startsWith("schema_version") && currentEntry == null -> {
                    schemaVersion = line.substringAfter('=').trim().toIntOrNull() ?: 1
                }
                else -> {
                    val eq = line.indexOf('=')
                    if (eq <= 0) continue
                    val key = line.substring(0, eq).trim()
                    val value = line.substring(eq + 1).trim()
                    if (inSubTable && currentHint != null) {
                        val s = unquote(value)
                        when (key) {
                            "kind" -> currentHint!!.kind = s
                            "fingerprint" -> currentHint!!.fingerprint = s
                            "host" -> currentHint!!.host = s
                        }
                    } else if (currentEntry != null) {
                        val e = currentEntry!!
                        when (key) {
                            "repo_id" -> e.repoId = unquote(value)
                            "display_name" -> e.displayName = unquote(value)
                            "url" -> e.urls = listOf(unquote(value))
                            "remotes" -> e.urls = parseStringArray(value)
                            "required" -> e.required = value == "true"
                            "write_back_target" -> e.writeBackTarget = unquote(value)
                        }
                    }
                }
            }
        }
        commit()
        return Manifest(schemaVersion, entries)
    }

    internal fun serialize(m: Manifest): String {
        val sb = StringBuilder()
        sb.append("+++\n")
        sb.append("schema_version = ").append(m.schemaVersion).append('\n')
        sb.append("+++\n")
        for (e in m.entries) {
            sb.append('\n')
            sb.append("[[reference]]\n")
            sb.append("repo_id = ").append(quote(e.repoId)).append('\n')
            sb.append("display_name = ").append(quote(e.displayName)).append('\n')
            if (e.urls.size == 1) {
                sb.append("url = ").append(quote(e.urls.single())).append('\n')
            } else if (e.urls.isNotEmpty()) {
                sb.append("remotes = [")
                    .append(e.urls.joinToString(", ") { quote(it) })
                    .append("]\n")
            }
            if (e.required) sb.append("required = true\n")
            e.writeBackTarget?.let { sb.append("write_back_target = ").append(quote(it)).append('\n') }
            e.credentialHint?.let { h ->
                sb.append("\n[reference.credential_hint]\n")
                sb.append("kind = ").append(quote(h.kind)).append('\n')
                h.fingerprint?.let { sb.append("fingerprint = ").append(quote(it)).append('\n') }
                h.host?.let { sb.append("host = ").append(quote(it)).append('\n') }
            }
        }
        return sb.toString()
    }

    private fun unquote(s: String): String {
        val t = s.trim()
        return if (t.length >= 2 && t.startsWith('"') && t.endsWith('"')) t.substring(1, t.length - 1)
        else t
    }
    private fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun parseStringArray(value: String): List<String> {
        val t = value.trim().removePrefix("[").removeSuffix("]")
        if (t.isBlank()) return emptyList()
        return t.split(',').map { unquote(it.trim()) }.filter { it.isNotEmpty() }
    }

    private class MutableEntry(
        var repoId: String = "",
        var displayName: String = "",
        var urls: List<String> = emptyList(),
        var required: Boolean = false,
        var writeBackTarget: String? = null,
        var credentialHint: CredentialHint? = null,
    ) {
        fun build() = Entry(repoId, displayName, urls, required, writeBackTarget, credentialHint)
    }
    private class MutableHint(
        var kind: String? = null,
        var fingerprint: String? = null,
        var host: String? = null,
    )
}
