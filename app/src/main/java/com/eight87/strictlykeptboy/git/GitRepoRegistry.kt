package com.eight87.strictlykeptboy.git

import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide cache of opened [GitRepo] instances, keyed by repoId. Held via
 * WeakReference so that JGit's repository handles get closed by the JVM if
 * nothing else is holding them. The cache exists so that listing N configured
 * repos doesn't reopen each one on every screen-mount.
 *
 * Phase B.10 / SE-B.10 — single per-repo Mutex inside GitRepo handles
 * write-serialization; this registry handles instance reuse only.
 */
object GitRepoRegistry {
    private val cache = ConcurrentHashMap<String, WeakReference<GitRepo>>()

    fun get(repoId: String): GitRepo? = cache[repoId]?.get()

    fun put(repo: GitRepo) {
        cache[repo.repoId] = WeakReference(repo)
    }

    suspend fun evict(repoId: String) {
        val ref = cache.remove(repoId)
        ref?.get()?.close()
    }

    fun clear() {
        cache.clear()
    }
}
