package com.eight87.strictlykeptboy.prefs

import android.content.ContentResolver
import android.net.Uri

/**
 * Round 2.17.A.9 — single source of truth for "do we have a confirmed
 * parent yet?".
 *
 * Used by the wizard's Phase D storage step and the Repos pane's
 * Phase E add-repo gate to short-circuit the "where should we store
 * your repos?" question.
 *
 * `Confirmed` iff:
 *  - [RepoStoragePrefs.location] is non-null, AND
 *  - for `External`, the SAF tree URI permission is still granted
 *    (a user can revoke it via system settings — D-2.17.k), AND the
 *    cached real path resolves on disk + carries the [SkbRootMarker].
 *
 * `External` without a cached real path is treated as NeedsPicking —
 * Phase B's picker rewrite will always populate it; the legacy
 * upgrade-from-2.7 path leaves it null intentionally so the user
 * confirms the new framing.
 */
object ParentLocationGate {

    sealed interface State {
        data object NeedsPicking : State
        data class Confirmed(val location: ParentLocation) : State
    }

    /**
     * @param storagePrefs the prefs surface holding the current parent.
     * @param contentResolver used to check `persistedUriPermissions` for
     *   External parents. Tests can stub via the [Probe] overload.
     */
    fun evaluate(
        storagePrefs: RepoStoragePrefs,
        contentResolver: ContentResolver,
    ): State = evaluate(storagePrefs, defaultProbe(contentResolver))

    /**
     * Test-friendly overload — accepts a [Probe] that decides whether an
     * SAF URI is still held.
     */
    fun evaluate(
        storagePrefs: RepoStoragePrefs,
        probe: Probe,
    ): State {
        val loc = storagePrefs.location ?: return State.NeedsPicking
        return when (loc) {
            is ParentLocation.Internal -> {
                // Marker is created at parent-init; absence means
                // initialization didn't finish — surface the question.
                val dir = loc.workingDir()
                if (SkbRootMarker.isSkbRoot(dir)) State.Confirmed(loc) else State.NeedsPicking
            }
            is ParentLocation.External -> {
                val dir = loc.workingDir() ?: return State.NeedsPicking
                if (!probe.isUriStillGranted(loc.treeUri)) return State.NeedsPicking
                if (!SkbRootMarker.isSkbRoot(dir)) return State.NeedsPicking
                State.Confirmed(loc)
            }
        }
    }

    /** Probe interface so tests can short-circuit ContentResolver. */
    fun interface Probe {
        fun isUriStillGranted(treeUri: String): Boolean
    }

    private fun defaultProbe(cr: ContentResolver): Probe = Probe { treeUri ->
        val target = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return@Probe false
        cr.persistedUriPermissions.any { it.uri == target && it.isReadPermission && it.isWritePermission }
    }
}
