package com.eight87.strictlykeptboy.ui.share

import com.eight87.strictlykeptboy.share.ShareLink
import com.eight87.strictlykeptboy.share.ShareMode

/**
 * Phase O.2 — classifies an incoming `strictlykeptboy://share` URI into an
 * action the composition root should take.
 *
 * Pure: no Android imports. Decides; the caller dispatches.
 *
 * Sealed result so callers can `when` exhaustively (R.X.2).
 */
object ShareLinkReceiver {

    sealed interface Action {
        /** URI didn't parse as a share link. */
        data class Invalid(val uri: String) : Action

        /** Link is well-formed but expired. */
        data class Expired(val link: ShareLink) : Action

        /** Read-only — clone the repo into a special read-only slot. */
        data class CloneReadOnly(val link: ShareLink) : Action

        /** Read-write — pre-fill the Add-Repo flow with the primary URL. */
        data class LaunchAddRepo(val link: ShareLink) : Action
    }

    fun classify(uri: String, nowEpochMs: Long = System.currentTimeMillis()): Action {
        val link = ShareLinkCodec.decode(uri) ?: return Action.Invalid(uri)
        if (ShareLinkExpiry.isExpired(link.expiryIso, nowEpochMs)) return Action.Expired(link)
        return when (link.mode) {
            ShareMode.ReadOnly -> Action.CloneReadOnly(link)
            ShareMode.ReadWrite -> Action.LaunchAddRepo(link)
        }
    }
}
