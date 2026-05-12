package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.SyncError

/**
 * Phase J — events emitted by [SyncScheduler] for the UI to consume
 * (toasts, banners, conflict notifications, top-bar status). Pure
 * data; the scheduler exposes them via a `SharedFlow<SyncEvent>`.
 */
sealed interface SyncEvent {
    val repoId: String

    /** Sync pass started for [repoId]. */
    data class Started(override val repoId: String) : SyncEvent

    /** A single remote within a repo's sync pass finished (fetch / pull / push). */
    data class RemoteResult(
        override val repoId: String,
        val remote: RemoteName,
        val outcome: RemoteOutcome,
    ) : SyncEvent

    /** Repo sync finished. [durationMs] is wall time for the whole pass. */
    data class Finished(
        override val repoId: String,
        val durationMs: Long,
        val perRemote: Map<RemoteName, RemoteOutcome>,
    ) : SyncEvent

    /**
     * pullRebase encountered a conflict. The conflict-resolution UI listens
     * on these to surface the in-app screen + a notification. The
     * [conflictKey] is a stable handle into [ConflictRegistry].
     */
    data class ConflictNotification(
        override val repoId: String,
        val remote: RemoteName,
        val paths: List<String>,
        val conflictKey: String,
    ) : SyncEvent

    /**
     * A non-primary remote reports a tip different from primary. Surfaces
     * as a yellow banner in RepoSettings; never an error.
     */
    data class MirrorDivergence(
        override val repoId: String,
        val mirror: RemoteName,
        val primary: RemoteName,
    ) : SyncEvent
}

sealed interface RemoteOutcome {
    data object UpToDate : RemoteOutcome
    data object Fetched : RemoteOutcome
    data class Pulled(val newCommits: Int) : RemoteOutcome
    data object Pushed : RemoteOutcome
    data object NothingToPush : RemoteOutcome
    data class Conflicted(val paths: List<String>) : RemoteOutcome
    data object ReadOnly : RemoteOutcome
    data class Failed(val error: SyncError) : RemoteOutcome
}
