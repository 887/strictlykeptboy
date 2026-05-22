package com.eight87.strictlykeptboy.notif

import android.content.Context
import com.eight87.strictlykeptboy.cache.CacheDatabase
import com.eight87.strictlykeptboy.cache.entities.EventInstanceStateRow
import com.eight87.strictlykeptboy.resolver.CompletionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Phase XX.3 / AT-C.3 + AT-B.4(c) — flip the Room cache state for an
 * event instance from `in-progress` to `completed-by-schedule` when the
 * end-alarm fires.
 *
 * The flip is unconditional: by the time the end-alarm fires, the
 * passive habit model says the event is `completed-by-schedule`
 * unless a deviation file has been written. The indexer will erase this
 * cache row if a deviation appears later (per [com.eight87.strictlykeptboy.cache.dao.EventInstanceStateDao.invalidate]),
 * so we don't need to re-check here.
 *
 * Runs on a SupervisorJob + Dispatchers.IO scope outside the broadcast
 * receiver's runtime budget. The receiver fires and forgets; the next
 * resolver pass picks up the freshly-cached value.
 *
 * Injection-friendly: [databaseProvider] is open for tests to swap in
 * an in-memory database.
 */
object CompletionStateCacheWriter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Pluggable for tests. */
    var databaseProvider: (Context) -> CacheDatabase = { CacheDatabase.open(it) }

    fun flipToCompletedBySchedule(
        context: Context,
        repoId: String,
        targetId: String,
        occurrenceDate: String,
        onComplete: () -> Unit = {},
    ) {
        scope.launch {
            runCatching {
                val db = databaseProvider(context.applicationContext)
                db.eventInstanceState().upsert(
                    EventInstanceStateRow(
                        repoId = repoId,
                        targetId = targetId,
                        occurrenceDate = occurrenceDate,
                        completionState = CompletionState.CompletedBySchedule.name,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
            }
            onComplete()
        }
    }
}
