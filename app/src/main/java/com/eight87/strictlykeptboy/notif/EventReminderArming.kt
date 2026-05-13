package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.cache.EventCommit
import com.eight87.strictlykeptboy.store.FrontmatterReader
import com.eight87.strictlykeptboy.store.Reminder
import com.eight87.strictlykeptboy.store.TomlTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.nio.file.Files

/**
 * Phase 2.2.E.1 — incremental reminder arming.
 *
 * Bridges [com.eight87.strictlykeptboy.cache.Indexer.commits] into
 * [EventReminderScheduler.scheduleAll]. Without this, only the
 * boot/horizon-slide [AlarmHorizonExtenderWorker] would arm alarms —
 * foreground event commits (create / edit / template materialization)
 * would land in the cache without their AlarmManager alarms being
 * touched, leaving a stale (or missing) reminder for the just-modified
 * event until the next 24h horizon-extender wake-up.
 *
 * The bridge re-reads the event file's frontmatter to recover the
 * `[[reminder]]` array (DM-X) because the Room schema doesn't project
 * those fields — NS-C.3 keeps reminder offsets snapshot-at-schedule-time
 * rather than persisted, so the indexer's narrow [EventCommit] only
 * carries the [com.eight87.strictlykeptboy.store.Event] header + source
 * path.
 *
 * SOLID:
 *  - **S:** does exactly the indexer-to-scheduler hop; the mapping +
 *    schedule arithmetic stay in [EventReminderMapping] and
 *    [EventReminderScheduler] respectively.
 *  - **D:** consumes a [Flow] (the indexer's narrow surface) and an
 *    `arm` lambda; tests substitute both without touching Android.
 */
object EventReminderArming {

    /**
     * Per-commit reminder armer. Pure-ish — the only side effects are the
     * caller-supplied [arm] lambda and the local frontmatter read.
     * Returns the list of armed alarm ids (one per lead-time) when the
     * arm completes; an empty list when the event has no reminders, the
     * file is missing, or [arm] returns empty.
     */
    fun armForCommit(
        commit: EventCommit,
        arm: (EventReminderScheduler.ReminderInput) -> List<String>,
        readFrontmatter: (java.nio.file.Path) -> TomlTable? = ::defaultReadFrontmatter,
    ): List<String> {
        val front = readFrontmatter(commit.sourcePath) ?: TomlTable()
        val reminders: List<Reminder> = runCatching { Reminder.readArray(front) }
            .getOrDefault(emptyList())
        val leads = EventReminderMapping.leadTimesFor(commit.event, reminders)
        if (leads.isEmpty()) return emptyList()
        val input = EventReminderScheduler.ReminderInput(
            repoId = commit.repoId,
            eventId = commit.event.id,
            calendarId = commit.event.calendarId,
            title = commit.event.title,
            startIso = commit.event.start,
            leadTimes = leads,
            privateEvent = commit.event.private,
        )
        return arm(input)
    }

    /**
     * Long-running collector. Wires [commits] into [scheduler] on
     * [scope]. Returns the [Job] so callers can cancel; idempotent at
     * the indexer level (re-arming the same `(repoId, eventId, leadTime)`
     * replaces in place in [EventReminderScheduler]).
     */
    fun install(
        scope: CoroutineScope,
        commits: Flow<EventCommit>,
        scheduler: EventReminderScheduler,
    ): Job = scope.launch {
        commits.onEach { commit ->
            armForCommit(commit, arm = { scheduler.scheduleAll(listOf(it)) })
        }.collect()
    }

    private fun defaultReadFrontmatter(path: java.nio.file.Path): TomlTable? = runCatching {
        if (!Files.isRegularFile(path)) return@runCatching null
        val bytes = Files.readAllBytes(path)
        FrontmatterReader.parse(String(bytes, Charsets.UTF_8)).frontmatter
    }.getOrNull()
}
