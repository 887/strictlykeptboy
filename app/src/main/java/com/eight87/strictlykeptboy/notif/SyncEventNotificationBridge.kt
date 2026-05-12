package com.eight87.strictlykeptboy.notif

import android.content.Context
import com.eight87.strictlykeptboy.git.SyncError
import com.eight87.strictlykeptboy.sync.RemoteOutcome
import com.eight87.strictlykeptboy.sync.SyncEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Phase M.4 — translate scheduler events into sync-channel notifications.
 *
 * R.X.1: this bridge consumes ONLY the [Flow] of [SyncEvent] — not the
 * whole scheduler handle — and writes to the (also narrow)
 * [SyncResultNotifier] surface. Composition root in MainActivity
 * provides both ends.
 */
object SyncEventNotificationBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Install a long-lived collector on [events]. Process-scoped — the
     * supervisor job is parented to the singleton object so it lives as
     * long as the app process. Idempotency is the caller's job (call
     * exactly once from MainActivity.onCreate).
     */
    fun install(context: Context, events: Flow<SyncEvent>) {
        scope.launch {
            var inflight = 0
            var startedAt = 0L
            events.collectLatest { ev ->
                when (ev) {
                    is SyncEvent.Started -> {
                        if (inflight == 0) startedAt = System.currentTimeMillis()
                        inflight++
                    }
                    is SyncEvent.Finished -> {
                        for ((_, outcome) in ev.perRemote) {
                            when (outcome) {
                                is RemoteOutcome.Conflicted ->
                                    SyncResultNotifier.postConflict(context, ev.repoId, outcome.paths.size)
                                is RemoteOutcome.Failed -> {
                                    val err = outcome.error
                                    if (err is SyncError.Auth) {
                                        SyncResultNotifier.postAuthExpired(context, ev.repoId, ev.repoId)
                                    }
                                }
                                else -> Unit
                            }
                        }
                        inflight = (inflight - 1).coerceAtLeast(0)
                        if (inflight == 0) {
                            val duration = System.currentTimeMillis() - startedAt
                            SyncResultNotifier.postSuccess(context, reposSynced = 1, durationMs = duration)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}
