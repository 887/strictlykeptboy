package com.eight87.strictlykeptboy.notif

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Reminder
import com.eight87.strictlykeptboy.store.RepoScanner
import com.eight87.strictlykeptboy.store.ParseResult
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Phase 2.1.F.8 — re-arm AlarmManager alarms for the sliding horizon.
 *
 * Invoked by [BootCompletedReceiver] post-boot/package-replace and by
 * its own periodic schedule once every 24h to keep the 7-day horizon
 * sliding forward. For each known repo we re-scan event files in the
 * working tree, map their reminders through [EventReminderMapping],
 * and re-arm pre-event alarms that fall within `[now, now + horizon]`.
 *
 * SOLID-S: pure re-arming — no UI, no body building, no DB writes.
 * Failures are best-effort; the worker returns `success()` so
 * WorkManager doesn't backoff a transient I/O issue.
 */
class AlarmHorizonExtenderWorker(
    ctx: Context,
    params: WorkerParameters,
) : Worker(ctx, params) {

    override fun doWork(): Result {
        return runCatching { rearm(applicationContext) }
            .map { Result.success() }
            .getOrElse { Result.success() }
    }

    companion object {
        /** Phase 2.1.F.8 — 7 day sliding horizon per the audit. */
        val HORIZON: Duration = Duration.ofDays(7L)

        /**
         * Test-visible re-arm. Walks every known repo's events directory,
         * parses each frontmatter, and arms alarms for events whose
         * `start` lies within the horizon and which carry reminders.
         */
        fun rearm(context: Context): Int {
            val sched = EventReminderScheduler(context.applicationContext)
            val repos = RepoStore.open(context.applicationContext).list()
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val horizonEnd = now.plus(HORIZON)
            var armed = 0
            for (cfg in repos) {
                val root = java.io.File(cfg.rootDir)
                if (!root.isDirectory) continue
                val results = runCatching { runBlocking { RepoScanner.scanAll(root) } }
                    .getOrNull() ?: continue
                val inputs = mutableListOf<EventReminderScheduler.ReminderInput>()
                for (r in results) {
                    if (r !is ParseResult.Success) continue
                    val entity = r.entity as? Event ?: continue
                    val start = runCatching { OffsetDateTime.parse(entity.start) }.getOrNull()
                        ?: continue
                    if (start.isBefore(now) || start.isAfter(horizonEnd)) continue
                    val reminders: List<Reminder> = runCatching {
                        Reminder.readArray(parseFrontmatter(r.sourcePath.toFile()))
                    }.getOrDefault(emptyList<Reminder>())
                    val leads = EventReminderMapping.leadTimesFor(entity, reminders)
                    if (leads.isEmpty()) continue
                    inputs += EventReminderScheduler.ReminderInput(
                        repoId = cfg.repoId,
                        eventId = entity.id,
                        calendarId = entity.calendarId,
                        title = entity.title,
                        startIso = entity.start,
                        leadTimes = leads,
                        privateEvent = entity.private,
                    )
                }
                armed += sched.scheduleAll(inputs).size
            }
            return armed
        }

        private fun parseFrontmatter(file: java.io.File): com.eight87.strictlykeptboy.store.TomlTable {
            val text = file.readText(Charsets.UTF_8)
            val doc = com.eight87.strictlykeptboy.store.FrontmatterReader.parse(text)
            return doc.frontmatter
        }
    }
}
