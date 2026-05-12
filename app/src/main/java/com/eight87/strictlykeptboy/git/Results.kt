package com.eight87.strictlykeptboy.git

import org.eclipse.jgit.lib.ObjectId

/**
 * Sealed result types for GitRepo network ops. The `NoRemotes` variants
 * are required by Phase ZZ.A — no-origin repos return these without
 * throwing, so the rest of the app can treat "phone-only" as a peer
 * state rather than a degraded error case.
 */
sealed interface FetchResult {
    object NoRemotes : FetchResult
    data class Success(val remote: RemoteName, val updatedRefs: Set<String>) : FetchResult
    data class Failed(val remote: RemoteName?, val error: SyncError) : FetchResult
}

sealed interface PullResult {
    object NoRemotes : PullResult
    object UpToDate : PullResult
    data class FastForwarded(val fromSha: ObjectId, val toSha: ObjectId, val changedPaths: Set<ChangedPath>) : PullResult
    data class Rebased(val fromSha: ObjectId, val toSha: ObjectId, val changedPaths: Set<ChangedPath>) : PullResult
    data class Conflicted(val conflictedPaths: List<String>, val handle: RebaseHandle) : PullResult
    data class Failed(val error: SyncError) : PullResult
}

sealed interface PushResult {
    object NoRemotes : PushResult
    object Success : PushResult
    object NothingToPush : PushResult
    data class Rejected(val remote: RemoteName, val reason: PushRejection) : PushResult
    data class PartiallySuccess(val pushed: Set<RemoteName>, val failed: Map<RemoteName, SyncError>) : PushResult
    data class Failed(val remote: RemoteName?, val error: SyncError) : PushResult
}

sealed interface CommitResult {
    object NothingToCommit : CommitResult
    data class Success(val newHead: ObjectId, val message: String) : CommitResult
    data class Failed(val error: SyncError) : CommitResult
}

/**
 * Opaque handle to an in-progress rebase. The conflict-resolution UI (Phase J / SE-J)
 * calls [GitRepo.continueRebase] / [GitRepo.abortRebase] with this handle once the
 * user has resolved the working-tree conflicts. The handle carries no state of its
 * own; the in-progress rebase is recorded inside the .git directory by JGit.
 */
class RebaseHandle internal constructor(internal val repo: GitRepo)
