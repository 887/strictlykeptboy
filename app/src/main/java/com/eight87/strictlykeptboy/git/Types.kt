package com.eight87.strictlykeptboy.git

import kotlinx.serialization.Serializable
import org.eclipse.jgit.lib.ObjectId

/**
 * Git layer types — Phase B.2 / ZZ.A.
 *
 * RemoteName is a JVM-level value class around the string identifier we hand
 * to JGit (`origin`, `mirror-1`, …). Policy lives in [RemoteBinding] fields,
 * NOT in the name (per D.74).
 */
@Serializable
@JvmInline
value class RemoteName(val value: String) {
    init {
        require(value.isNotBlank()) { "remote name cannot be blank" }
        require(value != "HEAD") { "'HEAD' is a reserved ref name" }
        require(value.none { it.isWhitespace() || it == '/' }) {
            "remote name '$value' contains illegal characters"
        }
    }

    override fun toString(): String = value

    companion object {
        val ORIGIN = RemoteName("origin")
    }
}

enum class Transport { Ssh, HttpsOAuth, HttpsPat, HttpsPublic, File }

/**
 * Per-remote push policy. PUSH = push every sync; PUSH_LAZY = manual + post-commit
 * only; NEVER = fetch-only mirror. Per Phase ZZ.E.1.
 */
enum class PushPolicy { Push, PushLazy, Never }

/**
 * Per-remote auth method discriminator. Concrete credentials are looked up
 * by `(repoId, remoteName)` from SecretsStore (Phase ZZ.C).
 */
enum class AuthMethod { Ssh, OAuthGitHub, OAuthForgejo, ManualPat, None }

/**
 * One configured remote on a repo. A repo may have zero, one, or many.
 * Per Phase ZZ.A.1 / D.74.
 */
@Serializable
data class RemoteBinding(
    val name: RemoteName,
    val url: String,
    val transport: Transport,
    val authMethod: AuthMethod,
    val fetchEnabled: Boolean = true,
    val pushPolicy: PushPolicy = PushPolicy.Push,
    val readOnlyDetected: Boolean = false,
    val displayName: String? = null,
)

@Serializable
data class AuthorIdentity(val name: String, val email: String)

data class GitStatus(
    val untracked: Set<String>,
    val modified: Set<String>,
    val added: Set<String>,
    val removed: Set<String>,
    val missing: Set<String>,
    val conflicting: Set<String>,
    val headSha: ObjectId?,
    val localOnlyCommits: Int,
) {
    val isClean: Boolean
        get() = untracked.isEmpty() && modified.isEmpty() && added.isEmpty() &&
            removed.isEmpty() && missing.isEmpty() && conflicting.isEmpty()
}

enum class ChangeKind { Added, Modified, Deleted, Renamed, Copied }

data class ChangedPath(
    val path: String,
    val oldPath: String?,
    val kind: ChangeKind,
)

data class LogEntry(
    val sha: ObjectId,
    val authorName: String,
    val authorEmail: String,
    val whenEpochSec: Long,
    val message: String,
)

/**
 * Error taxonomy. Concrete subtypes are populated by callers; the conflict
 * UI / re-auth UI / banner system (Phase J / SE-L) consumes these.
 */
sealed interface SyncError {
    val cause: Throwable?

    data class Network(override val cause: Throwable? = null) : SyncError
    data class Auth(val provider: AuthMethod, val remote: RemoteName, override val cause: Throwable? = null) : SyncError
    data class HostKey(val host: String, override val cause: Throwable? = null) : SyncError
    data class RemoteRejected(val reason: PushRejection, override val cause: Throwable? = null) : SyncError
    data class Conflict(val paths: List<String>, override val cause: Throwable? = null) : SyncError
    data class Unknown(override val cause: Throwable? = null) : SyncError
}

enum class PushRejection { NonFastForward, NoPermission, BranchProtected, Unknown }
