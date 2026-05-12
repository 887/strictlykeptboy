package com.eight87.strictlykeptboy.git

import java.lang.ref.WeakReference
import java.util.Collections

/**
 * Process-wide cache of opened [GitRepo] instances, keyed by repoId. Held via
 * WeakReference so that JGit's repository handles get closed by the JVM if
 * nothing else is holding them. The cache exists so that listing N configured
 * repos doesn't reopen each one on every screen-mount.
 *
 * Phase V (perf pass) — registry is now bounded at [MAX_ENTRIES] with
 * insertion-order LRU eviction (the oldest entry is dropped when capacity
 * is exceeded). Previously unbounded which would leak weak refs as repos
 * scaled past the realistic working-set (the standing finding from the
 * perf-discipline checklist).
 *
 * Phase B.10 / SE-B.10 — single per-repo Mutex inside GitRepo handles
 * write-serialization; this registry handles instance reuse only.
 */
object GitRepoRegistry {
    const val MAX_ENTRIES: Int = 50

    /**
     * Insertion-order LRU. `LinkedHashMap.removeEldestEntry` evicts the
     * oldest entry on every `put` once we cross [MAX_ENTRIES]. Wrapped in
     * `synchronizedMap` because all access is mutating (`put` re-orders).
     */
    private val cache: MutableMap<String, WeakReference<GitRepo>> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, WeakReference<GitRepo>>(
                /* initialCapacity = */ 16,
                /* loadFactor = */ 0.75f,
                /* accessOrder = */ true,
            ) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, WeakReference<GitRepo>>,
                ): Boolean = size > MAX_ENTRIES
            },
        )

    fun get(repoId: String): GitRepo? = synchronized(cache) { cache[repoId]?.get() }

    fun put(repo: GitRepo) {
        synchronized(cache) { cache[repo.repoId] = WeakReference(repo) }
    }

    suspend fun evict(repoId: String) {
        val ref = synchronized(cache) { cache.remove(repoId) }
        ref?.get()?.close()
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    /** For tests — current live entry count (excluding GC'd weak refs). */
    internal fun size(): Int = synchronized(cache) { cache.size }
}
