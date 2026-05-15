package com.eight87.strictlykeptboy.system

/**
 * Round 2.18.G.7 — process-wide hook for [com.eight87.strictlykeptboy.git.GitRepo]
 * commits.
 *
 * [com.eight87.strictlykeptboy.git.GitRepo] doesn't depend on
 * [SkbAccountManager] directly (it lives in the `git/` package, which
 * stays clean of Android-OS surfaces). Instead, `AppGraph.parkRuntimes`
 * sets [hook] to `SkbAccountManager::requestSyncFor`; `commitAll`
 * invokes the parked hook after a successful commit.
 *
 * Null = not yet wired (Robolectric tests + early process-start).
 * Failures are swallowed — sync requests are best-effort.
 */
object SkbCommitNotifier {

    @Volatile var hook: ((String) -> Unit)? = null

    /** Called from `GitRepo.commitAll` post-commit. */
    fun notifyCommit(repoId: String) {
        runCatching { hook?.invoke(repoId) }
    }
}
