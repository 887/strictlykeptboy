package com.eight87.strictlykeptboy.caldav.sync

import com.eight87.strictlykeptboy.caldav.CalDavMirror
import com.eight87.strictlykeptboy.caldav.store.CalDavMirrorStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Phase Y.6 — schedule + rate-limit CalDAV mirror workers, peering the
 * existing `sync/SyncScheduler` shape. Each mirror gets its own job;
 * the dispatcher honours `mirror.syncIntervalMinutes` and the
 * per-server rate-limit.
 *
 * Rate-limit policy: at most one in-flight request per `(host)` at a
 * time, plus a minimum gap of [minServerGapMs] between successive
 * passes against the same host. Surfaces `RateLimited` when a tick
 * arrives early.
 *
 * SOLID.S — only orchestration. The worker owns the protocol work;
 * the store owns persistence.
 */
class CalDavScheduler(
    private val mirrorStore: CalDavMirrorStore,
    private val workerFactory: (CalDavMirror) -> CalDavMirrorWorker,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val minServerGapMs: Long = 2_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val periodicJobs = mutableMapOf<String, Job>()
    private val hostMutexes = mutableMapOf<String, Mutex>()
    private val lastHitByHost = mutableMapOf<String, Long>()
    private val mapMutex = Mutex()

    suspend fun runOnce(mirrorId: String): CalDavSyncResult {
        val mirror = mirrorStore.get(mirrorId) ?: return CalDavSyncResult.Failed("unknown mirror $mirrorId")
        return runRateLimited(mirror)
    }

    fun startPeriodicTicks() {
        scope.launch {
            mirrorStore.state.collectLatest { mirrors ->
                val live = mirrors.map { it.mirrorId }.toSet()
                periodicJobs.keys.filterNot { it in live }.forEach {
                    periodicJobs.remove(it)?.cancel()
                }
                for (m in mirrors) {
                    if (m.syncIntervalMinutes <= 0) continue
                    if (periodicJobs[m.mirrorId]?.isActive == true) continue
                    periodicJobs[m.mirrorId] = scope.launch {
                        while (true) {
                            delay(m.syncIntervalMinutes.toLong() * 60_000L)
                            runRateLimited(m)
                        }
                    }
                }
            }
        }
    }

    suspend fun close() {
        periodicJobs.values.forEach { it.cancel() }
        periodicJobs.clear()
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    private suspend fun runRateLimited(mirror: CalDavMirror): CalDavSyncResult {
        val host = hostOf(mirror.serverUrl)
        val mutex = mapMutex.withLock { hostMutexes.getOrPut(host) { Mutex() } }
        return mutex.withLock {
            val now = clock()
            val last = lastHitByHost[host] ?: 0L
            val wait = last + minServerGapMs - now
            if (wait > 0) return@withLock CalDavSyncResult.RateLimited
            val result = workerFactory(mirror).runOnce()
            lastHitByHost[host] = clock()
            // Persist last-sync metadata.
            mirrorStore.upsert(mirror.copy(
                lastSyncedAt = clock(),
                lastError = (result as? CalDavSyncResult.Failed)?.reason,
            ))
            result
        }
    }

    private fun hostOf(url: String): String = runCatching {
        java.net.URI(url).host ?: url
    }.getOrDefault(url)
}
