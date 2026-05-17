package com.eight87.strictlykeptboy.cache

import com.eight87.strictlykeptboy.cache.entities.IndexErrorRow
import com.eight87.strictlykeptboy.cache.entities.RepoStateRow
import com.eight87.strictlykeptboy.git.ChangeKind
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.store.Deviation
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Exception as StoreException
import com.eight87.strictlykeptboy.store.Identity
import com.eight87.strictlykeptboy.store.JournalEntry
import com.eight87.strictlykeptboy.store.Override
import com.eight87.strictlykeptboy.store.ParseResult
import com.eight87.strictlykeptboy.store.RawEntity
import com.eight87.strictlykeptboy.store.RecurrenceRule
import com.eight87.strictlykeptboy.store.RepoScanner
import com.eight87.strictlykeptboy.store.StandingTask
import com.eight87.strictlykeptboy.store.Task
import com.eight87.strictlykeptboy.store.TypedEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.eclipse.jgit.lib.ObjectId
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase D.2 / D.3 / D.4 — full and incremental indexer.
 *
 * The indexer is the only writer to the cache DB. Every fan-out (full
 * scan, incremental, schema-mismatch rebuild) flows through one of
 * the two entry points on this class. Callers do not insert rows
 * directly.
 *
 * `CURRENT_SCHEMA_VERSION` is the cache-payload version, distinct from
 * the Room DB schema version. Bump it when the indexer's mapping
 * logic changes in a way that should retroactively rebuild rows
 * even from an existing on-disk DB at the same Room version.
 */
class Indexer(private val db: CacheDatabase) {

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private const val BATCH = 500
    }

    /**
     * Phase 2.2.E.1 — post-commit pulse. Every event that lands in the
     * cache (full scan or incremental rescan) is emitted here so
     * subscribers — today, the event-reminder arming bridge — can react
     * to *foreground* commits, not just the boot/horizon-slide
     * `AlarmHorizonExtenderWorker` re-arm cycle.
     *
     * Replay = 0 because subscribers wire up at process start; buffer
     * of 64 covers a multi-event batch (template materialization,
     * import) without dropping. Dropping on overflow is acceptable —
     * the next incremental scan will re-emit anyway.
     */
    private val _commits = MutableSharedFlow<EventCommit>(replay = 0, extraBufferCapacity = 64)
    val commits: SharedFlow<EventCommit> = _commits.asSharedFlow()

    /**
     * Wipe + repopulate the cache for [repoId] from the working tree.
     * Records the resulting head SHA + schema version into [RepoStateRow].
     */
    suspend fun fullScan(
        repoId: String,
        gitRepo: GitRepo,
        scanner: RepoScanner = RepoScanner,
    ): IndexStats = withContext(Dispatchers.IO) {
        clearRepo(repoId)
        val results = scanner.scanAll(gitRepo.rootDir)
        val stats = applyResults(repoId, gitRepo.rootDir, results)
        recordHead(repoId, gitRepo.headSha())
        stats
    }

    /**
     * Apply only the changes in `gitRepo.diffSinceLastIndexed(lastSha)`.
     * Falls back to [fullScan] when the recorded head is missing or
     * the diff blows up.
     */
    suspend fun incrementalScan(
        repoId: String,
        gitRepo: GitRepo,
        scanner: RepoScanner = RepoScanner,
    ): IndexStats = withContext(Dispatchers.IO) {
        val state = db.repoState().get(repoId)
        if (state == null || state.schemaVersion != CURRENT_SCHEMA_VERSION) {
            return@withContext fullScan(repoId, gitRepo, scanner)
        }
        val lastSha = state.lastIndexedHeadSha?.let { ObjectId.fromString(it) }
        val changed = try {
            gitRepo.diffSinceLastIndexed(lastSha)
        } catch (_: Throwable) {
            return@withContext fullScan(repoId, gitRepo, scanner)
        }
        if (changed.isEmpty()) {
            recordHead(repoId, gitRepo.headSha())
            return@withContext IndexStats(touched = 0, deleted = 0, failed = 0)
        }
        val root = gitRepo.rootDir.toPath()
        var touched = 0
        var deleted = 0
        var failed = 0
        val toReparse = mutableListOf<Path>()
        for (change in changed) {
            when (change.kind) {
                ChangeKind.Deleted -> {
                    deleteByPath(repoId, change.path)
                    if (change.oldPath != null && change.oldPath != change.path) {
                        deleteByPath(repoId, change.oldPath)
                    }
                    deleted++
                }
                ChangeKind.Renamed -> {
                    if (change.oldPath != null) deleteByPath(repoId, change.oldPath)
                    val abs = root.resolve(change.path)
                    if (Files.isRegularFile(abs)) toReparse.add(abs)
                }
                else -> {
                    val abs = root.resolve(change.path)
                    if (Files.isRegularFile(abs)) {
                        toReparse.add(abs)
                    } else {
                        deleteByPath(repoId, change.path)
                        deleted++
                    }
                }
            }
        }
        if (toReparse.isNotEmpty()) {
            val rescans = toReparse.mapNotNull { scanner.parseSingle(gitRepo.rootDir, it) }
            val stats = applyResults(repoId, gitRepo.rootDir, rescans)
            touched += stats.touched
            failed += stats.failed
        }
        recordHead(repoId, gitRepo.headSha())
        IndexStats(touched = touched, deleted = deleted, failed = failed)
    }

    private suspend fun applyResults(
        repoId: String,
        rootDir: File,
        results: List<ParseResult>,
    ): IndexStats {
        val rootPath = rootDir.toPath().toAbsolutePath().normalize()
        val events = mutableListOf<Pair<Event, String>>()
        val tasks = mutableListOf<Pair<Task, String>>()
        val standing = mutableListOf<Pair<StandingTask, String>>()
        val rules = mutableListOf<Pair<RecurrenceRule, String>>()
        val exceptions = mutableListOf<Pair<StoreException, String>>()
        val deviations = mutableListOf<Pair<Deviation, String>>()
        val overrides = mutableListOf<Pair<Override, String>>()
        val journal = mutableListOf<Pair<JournalEntry, String>>()
        val identities = mutableListOf<Pair<Identity, String>>()
        val errors = mutableListOf<IndexErrorRow>()
        val now = System.currentTimeMillis()

        for (r in results) {
            val rel = relPath(rootPath, r.sourcePath)
            when (r) {
                is ParseResult.Failed -> errors += IndexErrorRow(repoId, rel, r.error, now)
                is ParseResult.Success -> when (val e: TypedEntity = r.entity) {
                    is Event -> events += e to rel
                    is Task -> tasks += e to rel
                    is StandingTask -> standing += e to rel
                    is RecurrenceRule -> rules += e to rel
                    is StoreException -> exceptions += e to rel
                    is Deviation -> deviations += e to rel
                    is Override -> overrides += e to rel
                    is JournalEntry -> journal += e to rel
                    is Identity -> identities += e to rel
                    // SOLID fix #7 — explicit per-variant ignore so the
                    // compiler flags any new TypedEntity addition. Today
                    // only RawEntity falls here (meta / unknown-kind
                    // files round-trip via Phase D scope-trim).
                    is RawEntity -> Unit
                }
            }
        }

        events.chunked(BATCH).forEach { chunk ->
            db.events().upsertAll(chunk.map { EntityMapping.event(repoId, it.first, it.second) })
            // FTS: delete then upsert so re-indexed events don't accumulate duplicates.
            chunk.forEach { db.fts().deleteEvent(repoId, it.first.id) }
            db.fts().upsertEvents(chunk.map { EntityMapping.eventFts(repoId, it.first) })
        }
        tasks.chunked(BATCH).forEach { chunk ->
            db.tasks().upsertAll(chunk.map { EntityMapping.task(repoId, it.first, it.second) })
            chunk.forEach { db.fts().deleteTask(repoId, it.first.id) }
            db.fts().upsertTasks(chunk.map { EntityMapping.taskFts(repoId, it.first) })
        }
        standing.chunked(BATCH).forEach { chunk ->
            db.standingTasks().upsertAll(chunk.map { EntityMapping.standingTask(repoId, it.first, it.second) })
            chunk.forEach { db.fts().deleteTask(repoId, it.first.id) }
            db.fts().upsertTasks(chunk.map { EntityMapping.taskFts(repoId, it.first) })
        }
        rules.chunked(BATCH).forEach { chunk ->
            db.recurrenceRules().upsertAll(chunk.map { EntityMapping.recurrenceRule(repoId, it.first, it.second) })
        }
        exceptions.chunked(BATCH).forEach { chunk ->
            db.exceptions().upsertAll(chunk.map { EntityMapping.exception(repoId, it.first, it.second) })
        }
        deviations.chunked(BATCH).forEach { chunk ->
            db.deviations().upsertAll(chunk.map { EntityMapping.deviation(repoId, it.first, it.second) })
        }
        overrides.chunked(BATCH).forEach { chunk ->
            db.overrides().upsertAll(chunk.map { EntityMapping.override(repoId, it.first, it.second) })
        }
        journal.chunked(BATCH).forEach { chunk ->
            db.journal().upsertAll(chunk.map { EntityMapping.journal(repoId, it.first, it.second) })
        }
        identities.chunked(BATCH).forEach { chunk ->
            db.identities().upsertAll(chunk.map { EntityMapping.identity(repoId, it.first, it.second) })
        }
        if (errors.isNotEmpty()) db.indexErrors().upsertAll(errors)

        // Phase 2.2.E.1 — emit post-commit events so the reminder arming
        // bridge can re-arm AlarmManager alarms for foreground commits.
        // We pass the absolute on-disk path so subscribers can re-read
        // the frontmatter for the `[[reminder]]` array without holding
        // a Room handle. Best-effort emit — dropping a pulse never
        // corrupts state (the next scan re-emits).
        events.forEach { (event, rel) ->
            val absolute = rootPath.resolve(rel)
            _commits.tryEmit(EventCommit(repoId = repoId, event = event, sourcePath = absolute))
        }

        val touched = events.size + tasks.size + standing.size + rules.size +
            exceptions.size + deviations.size + overrides.size + journal.size + identities.size
        return IndexStats(touched = touched, deleted = 0, failed = errors.size)
    }

    private suspend fun clearRepo(repoId: String) {
        db.events().deleteAllForRepo(repoId)
        db.tasks().deleteAllForRepo(repoId)
        db.standingTasks().deleteAllForRepo(repoId)
        db.recurrenceRules().deleteAllForRepo(repoId)
        db.exceptions().deleteAllForRepo(repoId)
        db.deviations().deleteAllForRepo(repoId)
        db.overrides().deleteAllForRepo(repoId)
        db.journal().deleteAllForRepo(repoId)
        db.identities().deleteAllForRepo(repoId)
        db.indexErrors().deleteAllForRepo(repoId)
        db.fts().deleteAllEventsForRepo(repoId)
        db.fts().deleteAllTasksForRepo(repoId)
    }

    private suspend fun deleteByPath(repoId: String, sourcePath: String) {
        // Look up event/task IDs first to keep FTS in sync, then delete the rows.
        db.events().listAll(repoId).firstOrNull { it.sourcePath == sourcePath }?.let {
            db.fts().deleteEvent(repoId, it.id)
        }
        db.tasks().listAll(repoId).firstOrNull { it.sourcePath == sourcePath }?.let {
            db.fts().deleteTask(repoId, it.id)
        }
        db.standingTasks().listAll(repoId).firstOrNull { it.sourcePath == sourcePath }?.let {
            db.fts().deleteTask(repoId, it.id)
        }
        db.events().deleteBySourcePath(repoId, sourcePath)
        db.tasks().deleteBySourcePath(repoId, sourcePath)
        db.standingTasks().deleteBySourcePath(repoId, sourcePath)
        db.recurrenceRules().deleteBySourcePath(repoId, sourcePath)
        db.exceptions().deleteBySourcePath(repoId, sourcePath)
        db.deviations().deleteBySourcePath(repoId, sourcePath)
        db.overrides().deleteBySourcePath(repoId, sourcePath)
        db.journal().deleteBySourcePath(repoId, sourcePath)
        db.identities().deleteBySourcePath(repoId, sourcePath)
        db.indexErrors().deleteBySourcePath(repoId, sourcePath)
    }

    private suspend fun recordHead(repoId: String, head: ObjectId?) {
        db.repoState().upsert(
            RepoStateRow(
                repoId = repoId,
                lastIndexedHeadSha = head?.name,
                schemaVersion = CURRENT_SCHEMA_VERSION,
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private fun relPath(root: Path, abs: Path): String =
        root.relativize(abs.toAbsolutePath().normalize()).toString().replace('\\', '/')
}

data class IndexStats(val touched: Int, val deleted: Int, val failed: Int)

/**
 * Phase 2.2.E.1 — one emission per Event that landed in the cache via
 * [Indexer.fullScan] or [Indexer.incrementalScan]. Carries the absolute
 * on-disk path so subscribers can re-read frontmatter (the `[[reminder]]`
 * array isn't projected into the Room schema today — Phase NS-C.3
 * snapshots-at-schedule-time keeps reminder fields off the cache).
 */
data class EventCommit(
    val repoId: String,
    val event: com.eight87.strictlykeptboy.store.Event,
    val sourcePath: Path,
)
