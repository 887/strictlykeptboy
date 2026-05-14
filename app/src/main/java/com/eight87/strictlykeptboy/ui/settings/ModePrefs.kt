package com.eight87.strictlykeptboy.ui.settings

import android.content.Context
import android.content.SharedPreferences
import com.eight87.strictlykeptboy.git.CommitResult
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.store.DomCadenceWire
import com.eight87.strictlykeptboy.store.IdentityTomlCodec
import com.eight87.strictlykeptboy.store.IdentityTomlData
import com.eight87.strictlykeptboy.store.ModeTomlCodec
import com.eight87.strictlykeptboy.store.ModeTomlData
import com.eight87.strictlykeptboy.store.RepoMode
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Phase S.11 / 2.1.K — Mode (HV-Q.1 / DDD.1 / D.86).
 *
 * Phase 2.1.K.1 — `ModePrefs` is now a thin cache over the active
 * repo's `mode.toml`. Settings edits round-trip to disk:
 *
 *   1. UI calls [update] / [setMode] / [setCadence] / [setPersonaId] →
 *      in-memory [state] flips immediately, SharedPreferences mirror
 *      updates for cold-start.
 *   2. A debounced (500ms) write fires on a coroutine, serializing the
 *      current snapshot through [ModeTomlCodec.write] to
 *      `<activeRepoRoot>/mode.toml`.
 *   3. Once the file lands, `GitRepoRegistry.get(repoId).commitAll(...)`
 *      records the change.
 *
 * Per D.86, the 24h cooling-off gate for leaving strictly-kept lives
 * here: [requestTransitionToFree] sets [ModeState.transitionRequestAtMs],
 * and [canConfirmFreeTransition] reports whether 24h have elapsed.
 *
 * Phase 2.1.K.2 — [AppMode] now mirrors the on-disk [RepoMode]
 * (`Free` / `StrictlyKept` / `SelfKeep`).
 *
 * Phase 2.1.K.6 — `DomPersonaStore` is now the single source of truth
 * for personas; this cache only holds the active `personaId` string and
 * `writeBackTarget` URI. Custom-prompt edits go through
 * [com.eight87.strictlykeptboy.store.DomPersonaStore.writeCustom].
 */
enum class AppMode { Free, StrictlyKept, SelfKeep }
enum class DomCadence { Realtime, EndOfDay, Weekly }

/**
 * Phase 2.1.K.3 — derived "kept-by" classification surfaced in the
 * Mode settings category.
 */
enum class KeptBy { Ai, Human, SelfKeep }

@Serializable
data class ModeState(
    val mode: AppMode = AppMode.Free,
    val cadence: DomCadence = DomCadence.EndOfDay,
    val personaId: String? = null,
    val writeBackTarget: String? = null,
    /**
     * 2.1.K.4 — epoch-ms timestamp of the first "switch to free" tap
     * for the D.86 24h cooling-off gate. Cleared on confirm or on a
     * fresh strictly-kept entry.
     */
    val transitionRequestAtMs: Long? = null,
) {
    /**
     * 2.1.K.3 — derive the kept-by classification from the on-disk
     * fields. `KeptBy.Ai` iff `dom_persona ∈ DomPersonaStore.BUILTINS`,
     * `KeptBy.Human` iff `write_back_target != null && dom_persona == null`,
     * `KeptBy.SelfKeep` iff `mode == self-keep`. Default Ai when
     * StrictlyKept but neither persona nor target are populated yet.
     */
    val keptBy: KeptBy?
        get() = when (mode) {
            AppMode.Free -> null
            AppMode.SelfKeep -> KeptBy.SelfKeep
            AppMode.StrictlyKept -> {
                val isHuman = writeBackTarget != null && personaId == null
                if (isHuman) KeptBy.Human else KeptBy.Ai
            }
        }
}

class ModePrefs internal constructor(
    private val prefs: SharedPreferences,
    private val json: Json = DefaultJson,
    private val scope: CoroutineScope = GlobalScope,
    /** Test seam: skipped commit step for unit tests without a live GitRepo. */
    private val commitFn: suspend (GitRepo, String) -> CommitResult = { repo, msg -> repo.commitAll(msg) },
    /** Test seam: clock for the cooling-off check. */
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ModeState> = _state.asStateFlow()

    @Volatile private var activeRepoRoot: Path? = null
    @Volatile private var activeRepoId: String? = null
    @Volatile private var strictlyKeptProvider: () -> Boolean = { _state.value.mode == AppMode.StrictlyKept }

    private val writeMutex = kotlinx.coroutines.sync.Mutex()
    @Volatile private var pendingWrite: Job? = null

    /**
     * Phase 2.1.K.1 — bind the prefs cache to the active repo. Subsequent
     * [update] calls write back to `<rootDir>/mode.toml` (debounced)
     * and commit via [GitRepoRegistry]. Pass [repoId] = null to unbind.
     *
     * On bind, the cache reloads from disk via [ModeTomlCodec.readOrDefault]
     * so the UI reflects the on-disk truth of the new active repo.
     */
    fun bindActiveRepo(rootDir: Path?, repoId: String?) {
        activeRepoRoot = rootDir
        activeRepoId = repoId
        if (rootDir != null) {
            runCatching { ModeTomlCodec.readOrDefault(rootDir) }
                .onSuccess { data ->
                    val merged = data.toModeState(_state.value)
                    _state.value = merged
                    prefs.edit().putString(KEY_STATE, json.encodeToString(merged)).apply()
                }
        }
    }

    fun setMode(mode: AppMode) = update { it.copy(mode = mode) }
    fun setCadence(cadence: DomCadence) = update { it.copy(cadence = cadence) }
    fun setPersonaId(id: String?) = update { it.copy(personaId = id) }
    fun setWriteBackTarget(target: String?) = update { it.copy(writeBackTarget = target) }

    /**
     * 2.1.K.5 — Atomic migration to kept-by-AI: mode = StrictlyKept,
     * personaId set to a builtin, writeBackTarget cleared.
     */
    fun migrateToKeptByAi(personaId: String) = update {
        it.copy(
            mode = AppMode.StrictlyKept,
            personaId = personaId,
            writeBackTarget = null,
            transitionRequestAtMs = null,
        )
    }

    /**
     * 2.1.K.5 — Atomic migration to kept-by-human: mode = StrictlyKept,
     * personaId cleared, writeBackTarget set (caller passes the target URI;
     * pass `"pending"` if a share link is still to be generated).
     */
    fun migrateToKeptByHuman(writeBackTarget: String) = update {
        it.copy(
            mode = AppMode.StrictlyKept,
            personaId = null,
            writeBackTarget = writeBackTarget,
            transitionRequestAtMs = null,
        )
    }

    /**
     * 2.1.K.5 — Single-button self-keep ramp.
     */
    fun migrateToSelfKeep() = update {
        it.copy(
            mode = AppMode.SelfKeep,
            personaId = null,
            writeBackTarget = null,
            transitionRequestAtMs = null,
        )
    }

    /**
     * 2.1.K.4 — D.86 24h cooling-off. First tap of "switch to free"
     * records the timestamp; second tap (after 24h elapsed + typed
     * phrase) actually flips the mode.
     */
    fun requestTransitionToFree() = update {
        if (it.transitionRequestAtMs == null) it.copy(transitionRequestAtMs = nowMs()) else it
    }

    fun cancelTransitionRequest() = update { it.copy(transitionRequestAtMs = null) }

    /** Returns true once 24h have elapsed since [requestTransitionToFree]. */
    fun canConfirmFreeTransition(): Boolean {
        val at = _state.value.transitionRequestAtMs ?: return false
        return nowMs() - at >= COOLING_OFF_MS
    }

    /** Remaining ms in the cooling-off window; 0 if elapsed; null if no request. */
    fun coolingOffRemainingMs(): Long? {
        val at = _state.value.transitionRequestAtMs ?: return null
        val remaining = COOLING_OFF_MS - (nowMs() - at)
        return if (remaining <= 0) 0L else remaining
    }

    /** Confirm + flip to free. Clears the timestamp. Caller checks the gate first. */
    fun confirmTransitionToFree() = update {
        it.copy(mode = AppMode.Free, transitionRequestAtMs = null)
    }

    internal fun update(transform: (ModeState) -> ModeState) {
        val next = transform(_state.value)
        prefs.edit().putString(KEY_STATE, json.encodeToString(next)).apply()
        _state.value = next
        scheduleWriteBack()
    }

    /**
     * Phase 2.1.K.1 — coalesce a write to mode.toml + a commit, with a
     * 500ms debounce so a flurry of UI taps lands as one commit.
     */
    private fun scheduleWriteBack() {
        val root = activeRepoRoot ?: return
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

    private suspend fun writeOnce(root: Path, repoId: String, snapshot: ModeState) {
        // Preserve fields the UI doesn't edit (schemaVersion, keptSince,
        // calendarOverrides).
        val existing = runCatching { ModeTomlCodec.readOrDefault(root) }
            .getOrDefault(ModeTomlData.Default)
        val merged = existing.copyFromState(snapshot)
        withContext(Dispatchers.IO) {
            ModeTomlCodec.write(root, merged)
        }
        val repo = GitRepoRegistry.get(repoId) ?: return
        val result = commitFn(repo, "mode: update")
        if (result is CommitResult.Success && strictlyKeptProvider()) {
            // Strictly-kept review-feed hook for mode edits.
            runCatching {
                val sha = result.newHead.name
                // Read identity for tone-aware summary.
                val identity = runCatching { IdentityTomlCodec.readOrDefault(root) }
                    .getOrDefault(IdentityTomlData.LockedDefaults)
                val entry = ReviewFeedWriter.Entry(
                    commitSha = sha,
                    author = identity.praiseTerm,
                    timestamp = OffsetDateTime.now().withNano(0).toString(),
                    changedPaths = listOf(
                        ReviewFeedWriter.ChangedPath(
                            path = ModeTomlData.FILE_NAME,
                            family = ReviewFeedWriter.PathFamily.ModeFlip,
                        ),
                    ),
                    diffHunks = "",
                )
                ReviewFeedWriter.writeReviewableChange(root, entry, identity)
                repo.commitAll("review: log mode edit ${sha.take(7)}")
            }
        }
    }

    private fun load(): ModeState {
        val raw = prefs.getString(KEY_STATE, null) ?: return ModeState()
        return runCatching { json.decodeFromString<ModeState>(raw) }.getOrDefault(ModeState())
    }

    companion object {
        private const val PREFS_FILE = "mode_v1"
        private const val KEY_STATE = "state"
        private const val DEBOUNCE_MS: Long = 500L

        /** D.86 — 24h cooling-off. */
        const val COOLING_OFF_MS: Long = 24L * 60L * 60L * 1000L

        /** D.86 — boy types this exact phrase to confirm leaving strictly-kept. */
        const val FREE_CONFIRMATION_PHRASE: String = "yes I want to leave"

        private val DefaultJson = Json {
            ignoreUnknownKeys = true; encodeDefaults = true
        }

        fun open(context: Context) = ModePrefs(
            context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE),
        )

        internal fun openForTest(prefs: SharedPreferences) = ModePrefs(prefs)

        internal fun openForTest(
            prefs: SharedPreferences,
            scope: CoroutineScope,
            commitFn: suspend (GitRepo, String) -> CommitResult,
            nowMs: () -> Long = { System.currentTimeMillis() },
        ) = ModePrefs(prefs, scope = scope, commitFn = commitFn, nowMs = nowMs)
    }
}

// -----------------------------------------------------------------
// Cross-mapping helpers — UI [ModeState] ↔ on-disk [ModeTomlData].
// -----------------------------------------------------------------

internal fun ModeTomlData.toModeState(prior: ModeState): ModeState {
    val uiMode = when (mode) {
        RepoMode.Free -> AppMode.Free
        RepoMode.StrictlyKept -> AppMode.StrictlyKept
        RepoMode.SelfKeep -> AppMode.SelfKeep
    }
    val uiCadence = when (domCadence) {
        DomCadenceWire.Realtime -> DomCadence.Realtime
        DomCadenceWire.EndOfDay, null -> DomCadence.EndOfDay
        DomCadenceWire.Weekly -> DomCadence.Weekly
    }
    return ModeState(
        mode = uiMode,
        cadence = uiCadence,
        personaId = domPersona,
        writeBackTarget = writeBackTarget,
        transitionRequestAtMs = prior.transitionRequestAtMs,
    )
}

internal fun ModeTomlData.copyFromState(s: ModeState): ModeTomlData {
    val wireMode = when (s.mode) {
        AppMode.Free -> RepoMode.Free
        AppMode.StrictlyKept -> RepoMode.StrictlyKept
        AppMode.SelfKeep -> RepoMode.SelfKeep
    }
    val wireCadence = when (s.cadence) {
        DomCadence.Realtime -> DomCadenceWire.Realtime
        DomCadence.EndOfDay -> DomCadenceWire.EndOfDay
        DomCadence.Weekly -> DomCadenceWire.Weekly
    }
    return copy(
        mode = wireMode,
        domPersona = s.personaId,
        writeBackTarget = s.writeBackTarget,
        domCadence = wireCadence,
    )
}
