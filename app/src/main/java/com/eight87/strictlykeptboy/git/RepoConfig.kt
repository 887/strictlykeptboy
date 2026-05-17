package com.eight87.strictlykeptboy.git

import android.util.Log
import com.eight87.strictlykeptboy.BuildConfig
import java.io.File
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
    /**
     * Phase K.3 / D.88 — species the user chose in the wizard for this repo.
     * Drives the per-repo top-bar avatar via `RepoIconKind.Sticker(species)`
     * (see UI rendering in `IdentityAvatar` + `RepoIcon`). Null for repos
     * created before D.88 wiring or where the user opted into Photo/Emoji/
     * AutoInitials icons via Settings → Repos.
     */
    val iconSpecies: String? = null,
    val lastSyncedAt: Long? = null,
    val commitsAhead: Int = 0,
    val commitsBehind: Int = 0,
    /**
     * Phase O.2 / O.4 — true if this repo was added via a `strictlykeptboy://share`
     * deep-link as read-only. Source events render with a foreign-event chip
     * and de-saturated background. Always paired with [sourceRepoLabel] when set.
     */
    val readOnlyViaShare: Boolean = false,
    /** Display label for the source repo when [readOnlyViaShare] is true. */
    val sourceRepoLabel: String? = null,
    /**
     * Optional deep-link URL pointing back at the source app/repo for the
     * "Open in source app" CTA in O.4. May be null if no return-link was provided.
     */
    val sourceRepoBackLink: String? = null,
    /**
     * Round 2.5.A — per-repo overlay flag for the main schedule. When
     * `true`, this repo's bands appear in the unified schedule; when
     * `false`, they are filtered out entirely (one level above the
     * per-calendar `CalendarVisibilityPrefs`). Replaces the binary
     * `ReposViewState.unifiedView` toggle from Round 2.1.B.7.
     */
    val showOnSchedule: Boolean = true,
    /**
     * Round 2.5.A — per-repo overlay flag for the tasks view. When
     * `true`, tasks from this repo enter the Combined / Today views;
     * when `false`, this repo's todolists are filtered out from
     * `TasksUiState.activeTodolistIds`. Defaults to `false` for
     * foreign repos so the dom-repo's todos don't leak into the
     * user's task list by default (per the three-repo acceptance
     * scenario in `docs/plans/round-2-5.md`).
     */
    val drawTasksFrom: Boolean = false,
    /**
     * Round 2.8 — per-repo opt-in: when `true`, the active sticker pack's
     * image files are copied into `<repoRoot>/stickers/<species>/` and
     * committed (so users can edit them / hand to AI image gen). When
     * `false` (default), the repo only carries `stickers/README.md`; the
     * app renders from bundled assets. Off by default because image bytes
     * inflate the repo and most users won't customize.
     */
    val importStickersToRepo: Boolean = false,
    /**
     * Round 2.15 — repo is a generated demo. Treat as read-only; not eligible
     * for share / push / Access enumeration. Wiped + regenerated whenever the
     * user re-enables demo mode for the same perspective.
     */
    val isDemo: Boolean = false,
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

/**
 * Round 2.17.C.4 — debug-only invariant check. Any new repo's [RepoConfig.rootDir]
 * should land under the currently-configured parent folder. We only log a
 * warning here (no enforcement); proper enforcement + a move-job for repos
 * that violate the invariant arrive with Phase E.5. Call this at every
 * site that constructs a brand-new [RepoConfig] before it goes into
 * [RepoStore.add] (wizard scaffolding, AddRepoNavHost finish, demo
 * seeder — demos intentionally violate this and pass a `parentAbsPath`
 * that matches their `filesDir/demo-repos/...` root to silence the warning).
 *
 * No-op on release builds (gated by [BuildConfig.DEBUG]) to keep the
 * `Log.w` call out of the production binary path.
 */
fun warnIfRepoOutsideParent(config: RepoConfig, parentAbsPath: String?) {
    if (!BuildConfig.DEBUG) return
    if (parentAbsPath.isNullOrBlank()) return
    val normalizedParent = File(parentAbsPath).absolutePath
    val normalizedRoot = File(config.rootDir).absolutePath
    if (!normalizedRoot.startsWith(normalizedParent)) {
        Log.w(
            "RepoConfig",
            "repo ${config.repoId} rootDir=$normalizedRoot is outside the " +
                "configured parent=$normalizedParent — Phase E.5 will gain a " +
                "move-job; for now this is just a warning.",
        )
    }
}
