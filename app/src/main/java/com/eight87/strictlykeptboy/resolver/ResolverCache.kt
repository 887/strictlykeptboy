package com.eight87.strictlykeptboy.resolver

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Phase E.6 / RV-F — memoization cache.
 *
 * LRU over `(snapshot.contentHash, viewMode, range)`. Bounded at 20
 * entries by default (~1MB worst case at ~50KB/RenderedSchedule).
 * The cache is read-mostly; a single [Mutex] guards write paths.
 *
 * Cache invalidation comes for free with the key: when any repo's HEAD
 * changes, `RepoSnapshot.contentHash` changes and the new query misses.
 * Calendar metadata edits (priority, toggle, windows) are not yet folded
 * into the key — that's a follow-up tied to settings-event wiring per
 * RV-F.3. v1 callers should `clear()` after any settings edit.
 */
class ResolverCache(
    private val capacity: Int = 20,
) {
    data class Key(
        val snapshotHash: String,
        val viewMode: ViewMode,
        val range: DateRange,
    )

    private val mutex = Mutex()
    private val store = object : LinkedHashMap<Key, RenderedSchedule>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, RenderedSchedule>): Boolean =
            size > capacity
    }

    suspend fun get(key: Key): RenderedSchedule? = mutex.withLock { store[key] }

    suspend fun put(key: Key, value: RenderedSchedule) = mutex.withLock { store[key] = value }

    /**
     * Compute-if-absent. If `compute` throws, nothing is stored.
     */
    suspend fun getOrPut(key: Key, compute: suspend () -> RenderedSchedule): RenderedSchedule {
        get(key)?.let { return it }
        val v = compute()
        put(key, v)
        return v
    }

    suspend fun clear() = mutex.withLock { store.clear() }

    suspend fun size(): Int = mutex.withLock { store.size }

    /** Drop entries whose key contains a repo whose HEAD changed (RV-F.3). */
    suspend fun invalidateRepo(repo: RepoRef, oldSnapshotHash: String) = mutex.withLock {
        // Snapshot hashes are opaque so we approximate by dropping any entry
        // that matches the old hash. Caller passes the previous snapshot's
        // hash; new snapshot hashes are written under the new key.
        val keysToDrop = store.keys.filter { it.snapshotHash == oldSnapshotHash }
        keysToDrop.forEach { store.remove(it) }
    }
}
