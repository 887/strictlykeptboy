package com.eight87.strictlykeptboy.ui.repos

/**
 * Sub-screen navigation within the Repos pane. Used by both the compact
 * (single-pane) branch and the tablet (master-detail) branch.
 */
internal sealed interface Mode {
    object List : Mode
    object Add : Mode
    data class Settings(val repoId: String) : Mode
    data class Identities(val repoId: String) : Mode
    data class StickerPacks(val repoId: String) : Mode
}
