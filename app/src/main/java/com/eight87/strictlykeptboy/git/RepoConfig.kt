package com.eight87.strictlykeptboy.git

import com.eight87.strictlykeptboy.ui.theming.RepoIconKind
import com.eight87.strictlykeptboy.ui.theming.initialsFromName
import com.eight87.strictlykeptboy.ui.theming.seedColorFromName
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

    /**
     * D.88 / F48 — resolve this repo's avatar to a [RepoIconKind] for the
     * top-bar leading slot + repo-list rows. Order of preference:
     * `iconSpecies` (Phase K-wizard or T.2 picker) → `Sticker(species)`;
     * `iconEmoji` → `Emoji`; else `AutoInitials` derived from
     * `displayName` with a hash-stable seed colour.
     */
    fun toIconKind(): RepoIconKind = when {
        iconSpecies != null -> RepoIconKind.Sticker(iconSpecies.lowercase())
        iconEmoji != null -> RepoIconKind.Emoji(iconEmoji)
        else -> RepoIconKind.AutoInitials(
            initials = initialsFromName(displayName),
            seedColor = seedColorFromName(displayName),
        )
    }
}
