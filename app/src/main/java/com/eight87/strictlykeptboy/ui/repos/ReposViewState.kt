package com.eight87.strictlykeptboy.ui.repos

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase I — top-level state holder for Repo-management UI (I.1, I.6).
 *
 * Round 2.5.A.2 — the legacy `unifiedView` flow is dropped. Each repo
 * now carries its own `showOnSchedule` + `drawTasksFrom` flags on
 * [RepoConfig]; per-repo chip toggles in [RepoSwitcherDropdown] write
 * back through [RepoStore.setShowOnSchedule] / [setDrawTasksFrom].
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

    private val _syncStatuses = MutableStateFlow<Map<String, SyncStatus>>(emptyMap())
    val syncStatuses: StateFlow<Map<String, SyncStatus>> = _syncStatuses.asStateFlow()

    val repos: StateFlow<List<RepoConfig>> = store.state

    fun setActive(repoId: String) {
        _activeRepoId.value = repoId
        prefs?.edit()?.putString(KEY_ACTIVE_REPO, repoId)?.apply()
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
        /** Legacy key — read once during Round 2.5.A.1 migration, never written. */
        internal const val LEGACY_KEY_UNIFIED = "unified_view"

        fun open(context: Context, store: RepoStore): ReposViewState {
            val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            val state = ReposViewState(store, prefs)
            // Round 2.5.A.1 — one-time migration of the legacy unified-view
            // boolean into per-repo `showOnSchedule` + `drawTasksFrom` flags.
            val legacyUnified = prefs.getBoolean(LEGACY_KEY_UNIFIED, false)
            val activeId = prefs.getString(KEY_ACTIVE_REPO, null)
            val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            migrationScope.launch {
                store.migrateUnifiedViewV25(legacyUnified, activeId)
            }
            return state
        }
    }
}

@Immutable
enum class SyncStatus { Synced, Syncing, Error, LocalOnly }
