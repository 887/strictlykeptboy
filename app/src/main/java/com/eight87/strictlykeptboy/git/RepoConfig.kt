package com.eight87.strictlykeptboy.git

import kotlinx.serialization.Serializable

/**
 * Persistent per-repo configuration. Lives in EncryptedSharedPreferences,
 * keyed by [repoId]. Per Phase B.3 / SE-F.1 / ZZ.A.1.
 *
 * - `remotes` may be empty (no-origin repo).
 * - `primaryRemote` is `null` iff `remotes` is empty.
 * - Transient sync state (lastError, lastSyncedAt) is OK to persist but
 *   will be refreshed on the next sync — it's a UI display optimization,
 *   not authoritative state.
 */
@Serializable
data class RepoConfig(
    val repoId: String,
    val displayName: String,
    val rootDir: String,
    val remotes: List<RemoteBinding> = emptyList(),
    val primaryRemote: RemoteName? = null,
    val authorIdentity: AuthorIdentity,
    val defaultBranch: String = "main",
    val activeIdentityPersonId: String? = null,
    val autoSyncEnabled: Boolean = true,
    val syncIntervalMinutes: Int = 15,
    val wifiOnly: Boolean = false,
    val defaultCalendarId: String? = null,
    val defaultTodolistId: String? = null,
    val colorSeed: Int? = null,
    val iconEmoji: String? = null,
    val lastSyncedAt: Long? = null,
    val commitsAhead: Int = 0,
    val commitsBehind: Int = 0,
) {
    init {
        require(remotes.isEmpty() == (primaryRemote == null)) {
            "primaryRemote must be null iff remotes is empty"
        }
        if (primaryRemote != null) {
            require(remotes.any { it.name == primaryRemote }) {
                "primaryRemote $primaryRemote is not in remotes"
            }
        }
    }
}
