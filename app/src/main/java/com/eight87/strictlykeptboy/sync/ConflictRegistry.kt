package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.RebaseHandle
import com.eight87.strictlykeptboy.git.RemoteName
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase J.5 — process-wide registry mapping conflict keys to live
 * [RebaseHandle]s and the repo they belong to. The conflict-resolution UI
 * looks up the entry by key (received from a [SyncEvent.ConflictNotification])
 * and drives `continueRebase` / `abortRebase` against the cached entry.
 *
 * The handle is intrinsically tied to the in-progress rebase on disk —
 * dropping it leaves the repo in a STOPPED state. The UI must call
 * [release] after continue or abort completes.
 */
object ConflictRegistry {
    private val seq = AtomicLong(0)
    private val entries = ConcurrentHashMap<String, Entry>()

    data class Entry(
        val repo: GitRepo,
        val remote: RemoteName,
        val handle: RebaseHandle,
        val paths: List<String>,
    )

    fun register(repo: GitRepo, remote: RemoteName, handle: RebaseHandle, paths: List<String>): String {
        val key = "${repo.repoId}#${seq.incrementAndGet()}"
        entries[key] = Entry(repo, remote, handle, paths)
        return key
    }

    fun get(key: String): Entry? = entries[key]

    fun release(key: String) {
        entries.remove(key)
    }

    fun clear() {
        entries.clear()
    }
}
