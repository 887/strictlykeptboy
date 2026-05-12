package com.eight87.strictlykeptboy.ui.repos

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase I — top-level state holder for Repo-management UI (I.1, I.6).
 *
 * Wraps a [RepoStore] reactive snapshot, plus a small in-process state for
 * the active repo ID and the "all repos unified view" master toggle (I.6,
 * persisted to plain SharedPreferences).
 *
 * Per-repo sync status (synced / syncing / error / local-only) is exposed
 * separately so the dropdown can render badges without coupling to the
 * Phase J sync orchestrator (which doesn't exist yet). Default is
 * [SyncStatus.LocalOnly] for no-origin repos, [SyncStatus.Synced] otherwise
 * — caller can override with [setSyncStatus].
 */
class ReposViewState(
    val store: RepoStore,
    private val prefs: SharedPreferences? = null,
    initialActiveRepoId: String? = null,
) {
    private val _activeRepoId = MutableStateFlow(
        initialActiveRepoId
            ?: prefs?.getString(KEY_ACTIVE_REPO, null)
            ?: store.list().firstOrNull()?.repoId,
    )
    val activeRepoId: StateFlow<String?> = _activeRepoId.asStateFlow()

    private val _unifiedView = MutableStateFlow(
        prefs?.getBoolean(KEY_UNIFIED, false) ?: false,
    )
    val unifiedView: StateFlow<Boolean> = _unifiedView.asStateFlow()

    private val _syncStatuses = MutableStateFlow<Map<String, SyncStatus>>(emptyMap())
    val syncStatuses: StateFlow<Map<String, SyncStatus>> = _syncStatuses.asStateFlow()

    val repos: StateFlow<List<RepoConfig>> = store.state

    fun setActive(repoId: String) {
        _activeRepoId.value = repoId
        prefs?.edit()?.putString(KEY_ACTIVE_REPO, repoId)?.apply()
    }

    fun setUnifiedView(enabled: Boolean) {
        _unifiedView.value = enabled
        prefs?.edit()?.putBoolean(KEY_UNIFIED, enabled)?.apply()
    }

    fun setSyncStatus(repoId: String, status: SyncStatus) {
        _syncStatuses.value = _syncStatuses.value + (repoId to status)
    }

    /** Resolved per-repo status: explicit override, else local-only, else synced. */
    fun statusFor(config: RepoConfig): SyncStatus =
        _syncStatuses.value[config.repoId]
            ?: if (config.remotes.isEmpty()) SyncStatus.LocalOnly else SyncStatus.Synced

    companion object {
        const val PREFS_FILE = "repos_view_v1"
        const val KEY_ACTIVE_REPO = "active_repo_id"
        const val KEY_UNIFIED = "unified_view"

        fun open(context: Context, store: RepoStore): ReposViewState {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            return ReposViewState(store, prefs)
        }
    }
}

@Immutable
enum class SyncStatus { Synced, Syncing, Error, LocalOnly }
