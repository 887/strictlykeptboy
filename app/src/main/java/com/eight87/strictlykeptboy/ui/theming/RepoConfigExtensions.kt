package com.eight87.strictlykeptboy.ui.theming

import com.eight87.strictlykeptboy.git.RepoConfig

/**
 * D.88 / F48 — resolve this repo's avatar to a [RepoIconKind] for the
 * top-bar leading slot + repo-list rows. Order of preference:
 * `iconSpecies` (Phase K-wizard or T.2 picker) → `Sticker(species)`;
 * `iconEmoji` → `Emoji`; else `AutoInitials` derived from
 * `displayName` with a hash-stable seed colour.
 *
 * Lives in `ui/theming/` (not `git/`) because the result type
 * [RepoIconKind] is UI-layer; `git/` → `ui/` imports are forbidden
 * by R.X.6.
 */
fun RepoConfig.toIconKind(): RepoIconKind = when {
    iconSpecies != null -> RepoIconKind.Sticker(iconSpecies.lowercase())
    iconEmoji != null -> RepoIconKind.Emoji(iconEmoji)
    else -> RepoIconKind.AutoInitials(
        initials = initialsFromName(displayName),
        seedColor = seedColorFromName(displayName),
    )
}
