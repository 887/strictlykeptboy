package com.eight87.strictlykeptboy.notif

import android.content.Context
import com.eight87.strictlykeptboy.store.Deviation
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.EntityWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Phase M.6 — write a `deviation/<event-id>/<yyyy-mm-dd>.md` when the
 * user taps "I didn't" / "Partial" on an event reminder.
 *
 * Runs on a SupervisorJob + Dispatchers.IO scope outside the broadcast
 * receiver's runtime budget. Receivers should call [writeAsync] then
 * finish via the supplied completion lambda.
 */
object DeviationActionWriter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * @param eventId target event id
     * @param kind one of `"skipped"`, `"partial"`, `"completed-early"`,
     *             `"completed-late"`
     */
    fun writeAsync(
        context: Context,
        repoId: String,
        eventId: String,
        kind: String,
        targetRoot: File = defaultRepoRoot(context, repoId),
        subbeatsCompleted: List<String> = emptyList(),
        onComplete: () -> Unit = {},
    ) {
        scope.launch {
            runCatching {
                val now = OffsetDateTime.now(ZoneOffset.UTC).toString()
                val today = LocalDate.now().toString()
                val deviation = Deviation(
                    header = EntityHeader(
                        id = "$eventId-$today",
                        createdAt = now,
                        updatedAt = now,
                        author = "device",
                    ),
                    targetId = eventId,
                    instanceDate = today,
                    devKind = kind,
                    at = now,
                    note = null,
                    subbeatsCompleted = subbeatsCompleted,
                )
                EntityWriter.write(targetRoot, deviation)
            }
            onComplete()
        }
    }

    private fun defaultRepoRoot(context: Context, repoId: String): File =
        File(context.filesDir, "repos/$repoId")
}
