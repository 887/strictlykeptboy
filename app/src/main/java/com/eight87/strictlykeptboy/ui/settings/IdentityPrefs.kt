package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.eight87.strictlykeptboy.git.CommitResult
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.store.IdentityPronouns
import com.eight87.strictlykeptboy.store.IdentityTomlCodec
import com.eight87.strictlykeptboy.store.IdentityTomlData
import com.eight87.strictlykeptboy.store.ReviewFeedWriter
import java.nio.file.Path
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Phase S.8b — Identity (HV-R.3 / DDD.9).
 *
 * Phase 2.1.J.1 — `IdentityPrefs` is now a thin cache over the active
 * repo's `identity.toml`. Settings edits round-trip to disk:
 *
 *   1. UI calls [update] → in-memory [state] flips immediately (instant
 *      preview), and the SharedPreferences mirror gets updated.
 *   2. A debounced (500ms) write fires on a coroutine, serializing the
 *      current snapshot through [IdentityTomlCodec.write] to
 *      `<activeRepoRoot>/identity.toml`.
 *   3. Once the file lands, `GitRepoRegistry.get(repoId).commitAll(...)`
 *      records the change.
 *   4. In strictly-kept mode (per [ModePrefs.state] when bound), the
 *      writer also fires [ReviewFeedWriter.writeReviewableChange] so the
 *      dom sees the identity edit on the review feed (DM-Z.3).
 *
 * Multiple field edits inside the 500ms window coalesce into one commit.
 * Per-repo write serialization is provided by [GitRepo]'s mutex.
 *
 * SOLID:
 *  - **S:** UI-side cache + write-back orchestration. The codec writes
 *    bytes, GitRepo commits, ReviewFeedWriter emits review entries.
 *  - **D:** Bound to the active repo via [bindActiveRepo]; tests +
 *    surfaces without a repo path stay in-memory only.
 */
enum class ToneRegister { Soft, Neutral, Formal, Stern, Playful }
enum class EmojiDensity { None, Sparse, Standard, Lush }

@Serializable
data class IdentityState(
    val praise: String = "good boy",
    val altTerms: List<String> = emptyList(),
    val pronouns: String = "he/him",
    val pronounsExtra: List<String> = emptyList(),
    val honorific: String = "",
    val tone: ToneRegister = ToneRegister.Neutral,
    val emoji: EmojiDensity = EmojiDensity.Standard,
)

class IdentityPrefs internal constructor(
    private val prefs: SharedPreferences,
    private val json: Json = DefaultJson,
    private val scope: CoroutineScope = GlobalScope,
    /** Test seam: skipped commit step for unit tests without a live GitRepo. */
    private val commitFn: suspend (GitRepo, String) -> CommitResult = { repo, msg -> repo.commitAll(msg) },
) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<IdentityState> = _state.asStateFlow()

    @Volatile private var activeRepoRoot: Path? = null
    @Volatile private var activeRepoId: String? = null
    @Volatile private var strictlyKeptProvider: () -> Boolean = { false }

    private val writeMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile private var pendingWrite: Job? = null

    /**
     * Phase 2.1.J.1 — bind the prefs cache to the active repo. Subsequent
     * [update] calls write back to `<rootDir>/identity.toml` (debounced)
     * and commit via [GitRepoRegistry]. Pass [repoId] = null to unbind
     * (e.g. when no repo is active — cache stays in-memory only).
     *
     * On bind, the cache reloads from disk via [IdentityTomlCodec.readOrDefault]
     * so the UI reflects the on-disk truth of the new active repo.
     *
     * @param strictlyKept lambda the writer consults to decide whether to
     *   fire [ReviewFeedWriter.writeReviewableChange] after each commit.
     */
    fun bindActiveRepo(
        rootDir: Path?,
        repoId: String?,
        strictlyKept: () -> Boolean = { false },
    ) {
        activeRepoRoot = rootDir
        activeRepoId = repoId
        strictlyKeptProvider = strictlyKept
        if (rootDir != null) {
            // Reload from disk so the cache mirrors the bound repo. Runs
            // synchronously on the caller's thread — typically the
            // composition-root path, which is fine for a single TOML read.
            runCatching { IdentityTomlCodec.readOrDefault(rootDir) }
                .onSuccess { data ->
                    val merged = data.toIdentityState()
                    _state.value = merged
                    prefs.edit().putString(KEY_STATE, json.encodeToString(merged)).apply()
                }
        }
    }

    fun update(transform: (IdentityState) -> IdentityState) {
        val next = transform(_state.value)
        prefs.edit().putString(KEY_STATE, json.encodeToString(next)).apply()
        _state.value = next
        scheduleWriteBack()
    }

    fun resetToDefaults() {
        prefs.edit().remove(KEY_STATE).apply()
        _state.value = IdentityState()
        scheduleWriteBack()
    }

    /**
     * Phase 2.1.J.1 — coalesce a write to identity.toml + a commit, with a
     * 500ms debounce so a flurry of UI keystrokes lands as one commit.
     * Each call cancels the prior pending job.
     */
    private fun scheduleWriteBack() {
        val root = activeRepoRoot ?: return // unbound: in-memory only
        val repoId = activeRepoId ?: return
        pendingWrite?.cancel()
        pendingWrite = scope.launch(Dispatchers.IO) {
            delay(DEBOUNCE_MS)
            writeMutex.lock()
            try {
                writeOnce(root, repoId, _state.value)
            } finally {
                writeMutex.unlock()
            }
        }
    }

    /**
     * Test hook: flush any pending debounced write-back synchronously.
     * Returns whether a write was performed. Intended for unit tests; UI
     * code should rely on the debounce-then-commit flow.
     */
    internal suspend fun flushWriteBack(): Boolean {
        val root = activeRepoRoot ?: return false
        val repoId = activeRepoId ?: return false
        pendingWrite?.cancel()
        pendingWrite = null
        writeMutex.lock()
        try {
            writeOnce(root, repoId, _state.value)
        } finally {
            writeMutex.unlock()
        }
        return true
    }

    private suspend fun writeOnce(root: Path, repoId: String, snapshot: IdentityState) {
        // Read existing data so we don't clobber fields the UI doesn't
        // edit (e.g. alignment/lifestyle from the wizard, schema_version).
        val existing = runCatching { IdentityTomlCodec.readOrDefault(root) }
            .getOrDefault(IdentityTomlData.LockedDefaults)
        val merged = existing.copyFromState(snapshot)
        withContext(Dispatchers.IO) {
            IdentityTomlCodec.write(root, merged)
        }
        val repo = GitRepoRegistry.get(repoId) ?: return // no live handle → write only
        val result = commitFn(repo, "identity: update")
        if (result is CommitResult.Success && strictlyKeptProvider()) {
            // 2.1.J.4 — strictly-kept review-feed hook. Only the
            // IdentityEdit family applies since we only ever touch
            // identity.toml here.
            runCatching {
                val sha = result.newHead.name
                val entry = ReviewFeedWriter.Entry(
                    commitSha = sha,
                    author = merged.praiseTerm,
                    timestamp = OffsetDateTime.now().withNano(0).toString(),
                    changedPaths = listOf(
                        ReviewFeedWriter.ChangedPath(
                            path = IdentityTomlData.FILE_NAME,
                            family = ReviewFeedWriter.PathFamily.IdentityEdit,
                        ),
                    ),
                    diffHunks = "",
                )
                ReviewFeedWriter.writeReviewableChange(root, entry, merged)
                // Commit the review entry on top of the identity edit.
                repo.commitAll("review: log identity edit ${sha.take(7)}")
            }
        }
    }

    private fun load(): IdentityState {
        val raw = prefs.getString(KEY_STATE, null) ?: return IdentityState()
        return runCatching { json.decodeFromString<IdentityState>(raw) }.getOrDefault(IdentityState())
    }

    companion object {
        private const val PREFS_FILE = "identity_v1"
        private const val KEY_STATE = "state"
        private const val DEBOUNCE_MS: Long = 500L
        private val DefaultJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun open(context: Context) = IdentityPrefs(
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
        )

        internal fun openForTest(prefs: SharedPreferences) = IdentityPrefs(prefs)

        /**
         * Test-only constructor that lets the caller supply a fixed scope +
         * a stubbed commit function (so unit tests don't need a JGit repo
         * on disk to verify the write-back path). The commit lambda runs
         * for every successful write attempt; return a [CommitResult].
         */
        internal fun openForTest(
            prefs: SharedPreferences,
            scope: CoroutineScope,
            commitFn: suspend (GitRepo, String) -> CommitResult,
        ) = IdentityPrefs(prefs, scope = scope, commitFn = commitFn)
    }
}

// -----------------------------------------------------------------
// Cross-mapping helpers — UI [IdentityState] ↔ on-disk
// [IdentityTomlData]. Kept package-private so the conversions
// stay co-located with the rewire that introduced them.
// -----------------------------------------------------------------

internal fun IdentityTomlData.toIdentityState(): IdentityState {
    // Best-effort: map back from codec to UI flat state. Pronouns flatten
    // to a `<subject>/<obj>` string for the UI text field; pronounsExtra
    // collapses each set to the same `subject/obj` shape.
    val flatPronouns = "${pronouns.subject}/${pronouns.obj}"
    val flatExtras = pronounsExtra.map { "${it.subject}/${it.obj}" }
    val tone = when (toneRegister) {
        "soft-kinky", "soft" -> ToneRegister.Soft
        "warm-neutral", "neutral" -> ToneRegister.Neutral
        "clinical", "strict-clinical", "formal" -> ToneRegister.Formal
        "stern" -> ToneRegister.Stern
        "playful" -> ToneRegister.Playful
        else -> ToneRegister.Neutral
    }
    val emoji = when (emojiDensity) {
        "off", "none" -> EmojiDensity.None
        "light", "sparse" -> EmojiDensity.Sparse
        "medium", "standard" -> EmojiDensity.Standard
        "heavy", "lush" -> EmojiDensity.Lush
        else -> EmojiDensity.Standard
    }
    return IdentityState(
        praise = praiseTerm,
        altTerms = altTerms,
        pronouns = flatPronouns,
        pronounsExtra = flatExtras,
        honorific = if (honorificForDom == "Sir" && altTerms.isEmpty() && pronounsExtra.isEmpty() && praiseTerm == "good boy") "" else honorificForDom,
        tone = tone,
        emoji = emoji,
    )
}

/**
 * Merge UI-edited [IdentityState] back into an existing [IdentityTomlData],
 * preserving fields the UI doesn't edit (schema version, alignment,
 * lifestyle, pronouns reflexive/possessive).
 */
internal fun IdentityTomlData.copyFromState(s: IdentityState): IdentityTomlData {
    val parsed = parsePronounsFlat(s.pronouns) ?: pronouns
    val parsedExtras = s.pronounsExtra.mapNotNull(::parsePronounsFlat)
    val toneStr = when (s.tone) {
        ToneRegister.Soft -> "soft-kinky"
        ToneRegister.Neutral -> "warm-neutral"
        ToneRegister.Formal -> "clinical"
        ToneRegister.Stern -> "strict-clinical"
        ToneRegister.Playful -> "playful"
    }
    val emojiStr = when (s.emoji) {
        EmojiDensity.None -> "off"
        EmojiDensity.Sparse -> "light"
        EmojiDensity.Standard -> "medium"
        EmojiDensity.Lush -> "heavy"
    }
    return copy(
        praiseTerm = s.praise.ifBlank { praiseTerm },
        altTerms = s.altTerms,
        pronouns = parsed,
        pronounsExtra = parsedExtras,
        honorificForDom = s.honorific.ifBlank { "Sir" },
        toneRegister = toneStr,
        emojiDensity = emojiStr,
    )
}

private fun parsePronounsFlat(flat: String): IdentityPronouns? {
    val trimmed = flat.trim()
    if (trimmed.isBlank()) return null
    return when (trimmed.lowercase()) {
        "he/him", "he" -> IdentityPronouns.HeHim
        "she/her", "she" -> IdentityPronouns.SheHer
        "they/them", "they" -> IdentityPronouns.TheyThem
        else -> {
            val parts = trimmed.split('/').map { it.trim() }
            if (parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                IdentityPronouns(
                    subject = parts[0],
                    obj = parts[1],
                    possessive = parts.getOrNull(2) ?: (parts[1] + "s"),
                    reflexive = parts.getOrNull(3) ?: (parts[1] + "self"),
                )
            } else null
        }
    }
}

@Suppress("unused")
private fun runBlockingFallback(block: suspend () -> Unit) = runBlocking { block() }
