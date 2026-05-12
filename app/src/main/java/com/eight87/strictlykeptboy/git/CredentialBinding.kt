package com.eight87.strictlykeptboy.git

import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.api.TransportConfigCallback

/**
 * Binds a JGit `TransportCommand` (fetch / pull / push / clone) to the right
 * authentication for a given `(repoId, remoteName)` pair. Concrete impls land
 * in Phase B.4 (Ssh) / B.5 / B.6 (OAuth) / B.7 (Pat).
 *
 * For local-only operations (file:// transport, init, commit, log, status,
 * diff) [None] is sufficient and does no work.
 */
fun interface CredentialBinding {

    /**
     * Configure the given transport command for this credential binding. The
     * binding may install a TransportConfigCallback for SSH, a
     * CredentialsProvider for HTTPS, or both (rare). Local/file transports
     * require nothing; [None] is the noop binding.
     */
    fun configure(command: TransportCommand<*, *>)

    companion object {
        /** No-op binding. Use for file:// transport or local-only repos. */
        val None: CredentialBinding = CredentialBinding { /* no-op */ }
    }
}

/**
 * Resolves a `(repoId, remoteName)` pair to a [CredentialBinding]. The real
 * implementation (Phase B.4..B.7) consults SecretsStore + the
 * remote's `AuthMethod`; tests can inject a stub that returns [None] for
 * every input.
 */
fun interface CredentialResolver {
    fun resolve(repoId: String, remote: RemoteBinding): CredentialBinding

    companion object {
        /** Always returns [CredentialBinding.None]. Tests + local-only repos use this. */
        val NoopResolver: CredentialResolver = CredentialResolver { _, _ -> CredentialBinding.None }
    }
}

/**
 * JGit's TransportConfigCallback is the canonical hook for installing per-call
 * transport behavior (SSH session factory, HTTP factory). This helper lets a
 * CredentialBinding install a callback declaratively.
 */
internal fun TransportCommand<*, *>.applyTransportConfig(callback: TransportConfigCallback?) {
    if (callback != null) setTransportConfigCallback(callback)
}
