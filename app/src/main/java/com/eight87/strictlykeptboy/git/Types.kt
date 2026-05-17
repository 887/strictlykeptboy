package com.eight87.strictlykeptboy.git

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
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
 * Per-remote auth method discriminator. Promoted from an enum to a sealed
 * interface in the 2026-05-17 SOLID audit (#17 Liskov / OCP): per-variant
 * dispatch in `git/auth/CredentialBindings.kt` is now exhaustive `when`, the
 * soft `error("non-OAuth … reached forOAuth")` dead branch is gone, and the
 * SSH `NotImplementedError` is narrowed to `is AuthMethod.Ssh`.
 *
 * Wire format unchanged: each variant declares its legacy enum-name as its
 * [SerialName], so JSON-encoded `RepoConfig` blobs on disk continue to
 * parse round-trip (`"Ssh" | "OAuthGitHub" | "OAuthForgejo" | "ManualPat" |
 * "None"`). Concrete credentials are still looked up by
 * `(repoId, remoteName)` from SecretsStore (Phase ZZ.C); the data objects
 * are pure discriminators today, with room to grow per-variant config
 * (e.g. SSH key paths) later without breaking the wire.
 */
@Serializable(with = AuthMethodSerializer::class)
sealed interface AuthMethod {
    data object Ssh : AuthMethod
    data object OAuthGitHub : AuthMethod
    data object OAuthForgejo : AuthMethod
    data object ManualPat : AuthMethod
    data object None : AuthMethod

    /** Legacy enum-name string used as JSON wire value + toString. */
    val wireValue: String
        get() = when (this) {
            Ssh -> "Ssh"
            OAuthGitHub -> "OAuthGitHub"
            OAuthForgejo -> "OAuthForgejo"
            ManualPat -> "ManualPat"
            None -> "None"
        }

    companion object {
        /** Map a legacy wire-format string to its [AuthMethod] variant. */
        fun fromWire(value: String): AuthMethod = when (value) {
            "Ssh" -> Ssh
            "OAuthGitHub" -> OAuthGitHub
            "OAuthForgejo" -> OAuthForgejo
            "ManualPat" -> ManualPat
            "None" -> None
            else -> error("unknown AuthMethod wire value '$value'")
        }
    }
}

/**
 * Bare-string serializer for [AuthMethod]. Preserves byte-for-byte JSON
 * compatibility with the pre-sealed enum representation
 * (`"authMethod": "OAuthGitHub"` rather than the polymorphic
 * `{"type": "OAuthGitHub"}` default).
 */
internal object AuthMethodSerializer : KSerializer<AuthMethod> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.eight87.strictlykeptboy.git.AuthMethod", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AuthMethod) {
        encoder.encodeString(value.wireValue)
    }

    override fun deserialize(decoder: Decoder): AuthMethod =
        AuthMethod.fromWire(decoder.decodeString())
}

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
    /**
     * User override: when true, the user has explicitly marked this remote
     * read-only via Repo Settings (Phase O.3). Push attempts get queued + a
     * banner shows in the UI. Independent of [readOnlyDetected], which is
     * the auto-detected flag set when push-rejected has been observed.
     */
    val treatAsReadOnly: Boolean = false,
    val displayName: String? = null,
) {
    /** True if either auto-detected or user-marked read-only (O.3). */
    val effectiveReadOnly: Boolean get() = readOnlyDetected || treatAsReadOnly
}

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
