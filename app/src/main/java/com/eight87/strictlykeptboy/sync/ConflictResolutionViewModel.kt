package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.PullResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase J.6 — drives the conflict-resolution screen. Stateless across
 * configuration changes: pulls fresh content from disk on demand via
 * [readConflictedFile] so we never go stale relative to the actual rebase
 * working tree.
 */
class ConflictResolutionViewModel(
    private val conflictKey: String,
    private val registry: ConflictRegistry = ConflictRegistry,
) {
    private val entry = registry.get(conflictKey)
        ?: error("conflict key $conflictKey not registered")

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    val remoteLabel: String = bindRemoteForLabel(entry.remote)

    private fun initialState(): UiState = UiState(
        files = entry.paths.map { readConflictedFile(entry.repo.rootDir, it) },
        currentIndex = 0,
        resolved = false,
    )

    fun selectFile(index: Int) {
        val s = _state.value
        if (index in s.files.indices) _state.value = s.copy(currentIndex = index)
    }

    suspend fun keepMineAll(): PullResult {
        val s = _state.value
        val result = resolveAllAndContinue(entry.repo, entry.handle, s.files.map { it.path }, ConflictChoice.KeepMine)
        finalize(result)
        return result
    }

    suspend fun keepTheirsAll(): PullResult {
        val s = _state.value
        val result = resolveAllAndContinue(entry.repo, entry.handle, s.files.map { it.path }, ConflictChoice.KeepTheirs)
        finalize(result)
        return result
    }

    suspend fun resolveCurrent(choice: ConflictChoice, manualContent: String? = null) {
        val s = _state.value
        val file = s.files[s.currentIndex]
        resolveAs(entry.repo, file, choice, manualContent)
    }

    suspend fun continueRebase(): PullResult {
        val result = entry.repo.continueRebase(entry.handle)
        finalize(result)
        return result
    }

    suspend fun abort(): PullResult {
        val result = entry.repo.abortRebase(entry.handle)
        finalize(result)
        return result
    }

    private fun finalize(result: PullResult) {
        val cleanlyResolved = result !is PullResult.Conflicted
        _state.value = _state.value.copy(resolved = cleanlyResolved)
        if (cleanlyResolved) registry.release(conflictKey)
    }

    data class UiState(
        val files: List<ConflictedFile>,
        val currentIndex: Int,
        val resolved: Boolean,
    )
}
