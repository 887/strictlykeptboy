package com.eight87.strictlykeptboy.sync

import com.eight87.strictlykeptboy.git.FetchResult
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.NetworkState
import com.eight87.strictlykeptboy.git.PullResult
import com.eight87.strictlykeptboy.git.PushPolicy
import com.eight87.strictlykeptboy.git.PushRejection
import com.eight87.strictlykeptboy.git.PushResult
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.git.SyncError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Phase J.2 — sync orchestrator. Singleton, runs on its own dispatcher.
 *
 * Responsibilities:
 *  - Coalesce sync requests for the same repo inside [coalesceWindowMs].
 *  - Fan-out fetch across every `fetchEnabled` remote per repo (ZZ.D).
 *  - Reconcile against `primaryRemote` only; emit `MirrorDivergence`
 *    signals for non-primary tips.
 *  - Skip repos with `remotes.isEmpty()` entirely (ZZ.H).
 *  - Persist results to [SyncStatusStore] + emit [SyncEvent]s.
 *  - Periodic per-repo ticks driven by `syncIntervalMinutes`.
 *
 * Concurrency: [requestSync] is safe to call from any thread. Per-repo
 * work uses [GitRepo]'s own mutex so two scheduler passes on the same
 * repo serialize naturally. We additionally coalesce at the scheduler
 * level to drop redundant calls.
 */
class SyncScheduler(
    private val repoStore: RepoStore,
    private val statusStore: SyncStatusStore,
    private val repoProvider: suspend (RepoConfig) -> GitRepo?,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coalesceWindowMs: Long = 5_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val events = MutableSharedFlow<SyncEvent>(replay = 0, extraBufferCapacity = 64)
    val eventsFlow: SharedFlow<SyncEvent> = events.asSharedFlow()

    private val coalesceMutex = Mutex()
    // repoId -> earliest fire time + the in-flight Job that will perform the sync.
    private val pending = mutableMapOf<String, PendingSync>()
    private val periodicJobs = mutableMapOf<String, Job>()

    private data class PendingSync(val fireAt: Long, val job: Job)

    /** Request a sync for a single repo. Coalesces with any pending request. */
    fun requestSync(repoId: String) {
        scope.launch { enqueueSync(repoId) }
    }

    /** Request a sync for every configured repo with at least one remote. */
    fun requestSyncAll() {
        scope.launch {
            for (cfg in repoStore.list()) {
                if (cfg.remotes.isNotEmpty()) enqueueSync(cfg.repoId)
            }
        }
    }

    /** Run a single repo's sync pass inline (test entrypoint). */
    suspend fun runOnce(repoId: String) {
        val cfg = repoStore.get(repoId) ?: return
        if (cfg.remotes.isEmpty()) return
        syncPass(cfg)
    }

    /** Spin up periodic ticks per repo (uses `syncIntervalMinutes`). */
    fun startPeriodicTicks() {
        scope.launch {
            repoStore.state.collectLatest { repos ->
                // Cancel jobs for removed repos.
                val live = repos.map { it.repoId }.toSet()
                periodicJobs.keys.filterNot { it in live }.forEach {
                    periodicJobs.remove(it)?.cancel()
                }
                // Start jobs for new repos.
                for (cfg in repos) {
                    if (cfg.remotes.isEmpty() || !cfg.autoSyncEnabled) continue
                    if (cfg.syncIntervalMinutes <= 0) continue
                    if (periodicJobs[cfg.repoId]?.isActive == true) continue
                    periodicJobs[cfg.repoId] = scope.launch {
                        val periodMs = cfg.syncIntervalMinutes.toLong() * 60_000L
                        while (true) {
                            delay(periodMs)
                            enqueueSync(cfg.repoId)
                        }
                    }
                }
            }
        }
    }

    /** Observe network state; on transition Offline -> Online, sync everything. */
    fun observeNetwork(networkFlow: Flow<NetworkState>) {
        scope.launch {
            var wasOffline = true
            networkFlow.collectLatest { state ->
                val online = state is NetworkState.Online
                if (online && wasOffline) requestSyncAll()
                wasOffline = !online
            }
        }
    }

    suspend fun close() {
        periodicJobs.values.forEach { it.cancel() }
        periodicJobs.clear()
        scope.coroutineContext[Job]?.cancelAndJoin()
    }

    private suspend fun enqueueSync(repoId: String) {
        val cfg = repoStore.get(repoId) ?: return
        if (cfg.remotes.isEmpty()) return

        val fireAt = clock() + coalesceWindowMs
        coalesceMutex.withLock {
            pending[repoId]?.let { return }
            val job = scope.launch {
                val sleep = (fireAt - clock()).coerceAtLeast(0L)
                if (sleep > 0) delay(sleep)
                coalesceMutex.withLock { pending.remove(repoId) }
                repoStore.get(repoId)?.let { fresh ->
                    if (fresh.remotes.isNotEmpty()) syncPass(fresh)
                }
            }
            pending[repoId] = PendingSync(fireAt, job)
        }
    }

    private suspend fun syncPass(cfg: RepoConfig) {
        val started = clock()
        events.emit(SyncEvent.Started(cfg.repoId))
        val repo = repoProvider(cfg) ?: run {
            events.emit(SyncEvent.Finished(cfg.repoId, 0, emptyMap()))
            return
        }
        val outcomes = mutableMapOf<RemoteName, RemoteOutcome>()
        val fetched = mutableMapOf<RemoteName, FetchResult>()

        // Fan-out fetch across every fetch-enabled remote.
        for (binding in repo.remotes.filter { it.fetchEnabled }) {
            val result = repo.fetch(binding.name)
            fetched[binding.name] = result
            val outcome = when (result) {
                is FetchResult.Success -> RemoteOutcome.Fetched
                is FetchResult.Failed -> RemoteOutcome.Failed(result.error)
                FetchResult.NoRemotes -> RemoteOutcome.Failed(SyncError.Unknown())
            }
            outcomes[binding.name] = outcome
            statusStore.updateRemote(cfg.repoId, binding.name) { existing ->
                when (result) {
                    is FetchResult.Success -> existing.copy(lastSyncedAt = clock(), lastErrorMessage = null)
                    is FetchResult.Failed -> existing.copy(lastErrorMessage = errorMessage(result.error))
                    else -> existing
                }
            }
            events.emit(SyncEvent.RemoteResult(cfg.repoId, binding.name, outcome))
        }

        // Reconcile against primary only.
        val primary = repo.primaryRemote
        if (primary != null && fetched[primary] is FetchResult.Success) {
            when (val pull = repo.pullRebase(primary)) {
                is PullResult.Conflicted -> {
                    val key = ConflictRegistry.register(repo, primary, pull.handle, pull.conflictedPaths)
                    outcomes[primary] = RemoteOutcome.Conflicted(pull.conflictedPaths)
                    events.emit(SyncEvent.ConflictNotification(cfg.repoId, primary, pull.conflictedPaths, key))
                }
                is PullResult.Failed -> {
                    outcomes[primary] = RemoteOutcome.Failed(pull.error)
                    statusStore.updateRepo(cfg.repoId) {
                        it.copy(lastErrorMessage = errorMessage(pull.error))
                    }
                }
                PullResult.UpToDate -> {
                    if (outcomes[primary] !is RemoteOutcome.Failed) outcomes[primary] = RemoteOutcome.UpToDate
                }
                is PullResult.FastForwarded -> outcomes[primary] = RemoteOutcome.Pulled(pull.changedPaths.size)
                is PullResult.Rebased -> outcomes[primary] = RemoteOutcome.Pulled(pull.changedPaths.size)
                PullResult.NoRemotes -> Unit
            }
        }

        // Push if there's anything to push (only when no unresolved conflict).
        val conflicted = outcomes.values.any { it is RemoteOutcome.Conflicted }
        if (!conflicted) {
            val pushTargets = repo.remotes.filter { it.pushPolicy == PushPolicy.Push }
            if (pushTargets.isNotEmpty()) {
                when (val push = repo.push(null)) {
                    PushResult.Success -> pushTargets.forEach { outcomes[it.name] = RemoteOutcome.Pushed }
                    PushResult.NothingToPush -> pushTargets.forEach {
                        // Don't downgrade Pulled / Fetched outcomes.
                        if (outcomes[it.name] == null || outcomes[it.name] is RemoteOutcome.Fetched ||
                            outcomes[it.name] is RemoteOutcome.UpToDate
                        ) {
                            outcomes[it.name] = RemoteOutcome.NothingToPush
                        }
                    }
                    is PushResult.Rejected -> {
                        if (push.reason == PushRejection.NoPermission) {
                            outcomes[push.remote] = RemoteOutcome.ReadOnly
                            statusStore.updateRemote(cfg.repoId, push.remote) {
                                it.copy(readOnlyDetected = true, lastErrorMessage = "read-only")
                            }
                        } else {
                            outcomes[push.remote] = RemoteOutcome.Failed(SyncError.RemoteRejected(push.reason))
                        }
                    }
                    is PushResult.PartiallySuccess -> {
                        push.pushed.forEach { outcomes[it] = RemoteOutcome.Pushed }
                        val primaryShipped = primary != null && primary in push.pushed
                        push.failed.forEach { (name, err) ->
                            outcomes[name] = RemoteOutcome.Failed(err)
                            // ZZ.E.3 — primary shipped, mirror failed → partialPushDegraded soft signal.
                            if (primaryShipped && name != primary) {
                                statusStore.updateRemote(cfg.repoId, name) {
                                    it.copy(partialPushDegraded = true, lastErrorMessage = errorMessage(err))
                                }
                            }
                        }
                        // Clear the degraded flag for any mirror that successfully pushed.
                        if (primaryShipped) {
                            push.pushed.forEach { name ->
                                if (name != primary) {
                                    statusStore.updateRemote(cfg.repoId, name) {
                                        it.copy(partialPushDegraded = false)
                                    }
                                }
                            }
                        }
                    }
                    is PushResult.Failed -> {
                        val target = push.remote
                        if (target != null) outcomes[target] = RemoteOutcome.Failed(push.error)
                    }
                    PushResult.NoRemotes -> Unit
                }
            }
        }

        // Mirror divergence: any fetched non-primary remote whose updated refs differ
        // from primary's updated refs gets a soft signal. We can't easily compare
        // SHAs here without another round-trip; treat any successful non-primary
        // fetch as potential divergence — primary always wins reconciliation.
        if (primary != null) {
            for ((remoteName, result) in fetched) {
                if (remoteName == primary) continue
                val diverged = result is FetchResult.Success && result.updatedRefs.isNotEmpty()
                if (diverged) {
                    events.emit(SyncEvent.MirrorDivergence(cfg.repoId, remoteName, primary))
                }
                // Persist divergence flag so the banner can survive process death.
                statusStore.updateRemote(cfg.repoId, remoteName) {
                    it.copy(mirrorDiverged = diverged)
                }
            }
        }

        statusStore.updateRepo(cfg.repoId) {
            it.copy(lastSyncedAt = clock())
        }
        events.emit(SyncEvent.Finished(cfg.repoId, clock() - started, outcomes))
    }

    private fun errorMessage(err: SyncError): String = when (err) {
        is SyncError.Network -> "network"
        is SyncError.Auth -> "auth (${err.provider})"
        is SyncError.HostKey -> "host-key (${err.host})"
        is SyncError.RemoteRejected -> "rejected (${err.reason})"
        is SyncError.Conflict -> "conflict"
        is SyncError.Unknown -> "unknown"
    }
}
