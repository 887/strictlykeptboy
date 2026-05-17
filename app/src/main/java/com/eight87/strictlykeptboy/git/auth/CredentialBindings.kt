package com.eight87.strictlykeptboy.git.auth

import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.CredentialBinding
import com.eight87.strictlykeptboy.git.CredentialResolver
import com.eight87.strictlykeptboy.git.RemoteBinding
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

/**
 * Concrete [CredentialBinding] implementations for the auth modes supported
 * in Phase B / ZZ.C. They consult [SecretsStore] at transport time so token
 * rotations are picked up without restart.
 *
 * SSH transport plumbing (SshdSessionFactory + TOFU host-key verifier + thread-
 * local per-remote identity injection) is deferred to Phase I — the UI doesn't
 * let users add an SSH remote until then. Calling [forSsh] before Phase I lands
 * the JGit-SSH wiring will throw a clear error rather than misbehave silently.
 */
object CredentialBindings {

    fun forOAuthGitHub(repoId: String, remote: RemoteBinding, secrets: SecretsStore): CredentialBinding =
        forOAuth(repoId, remote, secrets, GitHubAuth.GIT_USERNAME)

    fun forOAuthForgejo(repoId: String, remote: RemoteBinding, secrets: SecretsStore): CredentialBinding =
        forOAuth(repoId, remote, secrets, ForgejoAuth.GIT_USERNAME)

    /**
     * Internal OAuth binding factory. The username is supplied by the
     * per-variant entry points above — there is no longer a `when` chain
     * (and no `error("non-OAuth … reached forOAuth")` dead branch); the
     * sealed-type dispatch in [ProductionCredentialResolver] picks the
     * right factory per variant.
     */
    private fun forOAuth(
        repoId: String,
        remote: RemoteBinding,
        secrets: SecretsStore,
        username: String,
    ): CredentialBinding {
        return CredentialBinding { command ->
            val token = secrets.getOAuthToken(repoId, remote.name)
                ?: throw IllegalStateException(
                    "no OAuth token stored for repoId=$repoId remote=${remote.name.value} — " +
                        "user needs to (re-)authenticate via Device Flow",
                )
            command.setCredentialsProvider(
                UsernamePasswordCredentialsProvider(username, token.accessToken),
            )
        }
    }

    fun forPat(repoId: String, remote: RemoteBinding, secrets: SecretsStore): CredentialBinding {
        return CredentialBinding { command ->
            val pat = secrets.getPat(repoId, remote.name)
                ?: throw IllegalStateException(
                    "no PAT stored for repoId=$repoId remote=${remote.name.value} — user needs to enter one",
                )
            command.setCredentialsProvider(
                UsernamePasswordCredentialsProvider(pat.username, pat.token),
            )
        }
    }

    /**
     * SSH binding stub. The full implementation requires JGit's SshdSessionFactory
     * + ServerKeyDatabase TOFU verifier + thread-local per-remote identity
     * injection, which lives in Phase I once the repo-add UI can configure an
     * SSH remote. For now this is a clear-error stub so calls don't silently
     * misbehave.
     */
    fun forSsh(repoId: String, remote: RemoteBinding, @Suppress("UNUSED_PARAMETER") secrets: SecretsStore): CredentialBinding {
        return CredentialBinding {
            throw NotImplementedError(
                "SSH transport plumbing is deferred to Phase I (B.4 remainder). " +
                    "repoId=$repoId remote=${remote.name.value}. " +
                    "Use HTTPS+OAuth or HTTPS+PAT in the meantime.",
            )
        }
    }
}

/**
 * Production [CredentialResolver] for the running app. Dispatches on
 * [RemoteBinding.authMethod] to the appropriate concrete binding.
 *
 * Tests should construct [CredentialResolver.NoopResolver] or wire a
 * fake SecretsStore.
 */
class ProductionCredentialResolver(
    private val secrets: SecretsStore,
) : CredentialResolver {
    override fun resolve(repoId: String, remote: RemoteBinding): CredentialBinding =
        when (remote.authMethod) {
            AuthMethod.OAuthGitHub -> CredentialBindings.forOAuthGitHub(repoId, remote, secrets)
            AuthMethod.OAuthForgejo -> CredentialBindings.forOAuthForgejo(repoId, remote, secrets)
            AuthMethod.ManualPat -> CredentialBindings.forPat(repoId, remote, secrets)
            AuthMethod.Ssh -> CredentialBindings.forSsh(repoId, remote, secrets)
            AuthMethod.None -> CredentialBinding.None
        }
}
