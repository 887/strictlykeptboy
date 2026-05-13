package com.eight87.strictlykeptboy.cache.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.eight87.strictlykeptboy.cache.entities.DeviationRow
import com.eight87.strictlykeptboy.cache.entities.EventFtsRow
import com.eight87.strictlykeptboy.cache.entities.EventInstanceStateRow
import com.eight87.strictlykeptboy.cache.entities.EventRow
import com.eight87.strictlykeptboy.cache.entities.ExceptionRow
import com.eight87.strictlykeptboy.cache.entities.IdentityRow
import com.eight87.strictlykeptboy.cache.entities.IndexErrorRow
import com.eight87.strictlykeptboy.cache.entities.JournalEntryRow
import com.eight87.strictlykeptboy.cache.entities.OverrideRow
import com.eight87.strictlykeptboy.cache.entities.RecurrenceRuleRow
import com.eight87.strictlykeptboy.cache.entities.RepoStateRow
import com.eight87.strictlykeptboy.cache.entities.StandingTaskRow
import com.eight87.strictlykeptboy.cache.entities.TaskFtsRow
import com.eight87.strictlykeptboy.cache.entities.TaskRow
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<EventRow>)

    @Query("DELETE FROM events WHERE repoId = :repoId AND sourcePath = :sourcePath")
    suspend fun deleteBySourcePath(repoId: String, sourcePath: String)

    @Query("DELETE FROM events WHERE repoId = :repoId")
    suspend fun deleteAllForRepo(repoId: String)

    @Query("SELECT * FROM events WHERE repoId = :repoId AND startEpochMs < :toMs AND endEpochMs > :fromMs ORDER BY startEpochMs")
    fun byDateRange(repoId: String, fromMs: Long, toMs: Long): Flow<List<EventRow>>

    @Query("SELECT * FROM events WHERE repoId = :repoId AND calendarId = :calendarId ORDER BY startEpochMs")
    fun byCalendar(repoId: String, calendarId: String): Flow<List<EventRow>>

    @Query("SELECT * FROM events WHERE startEpochMs < :toMs AND endEpochMs > :fromMs ORDER BY startEpochMs")
    fun multiRepoByDateRange(fromMs: Long, toMs: Long): Flow<List<EventRow>>

    @Query("SELECT * FROM events WHERE repoId = :repoId")
    suspend fun listAll(repoId: String): List<EventRow>
}

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<TaskRow>)

    @Query("DELETE FROM tasks WHERE repoId = :repoId AND sourcePath = :sourcePath")
    suspend fun deleteBySourcePath(repoId: String, sourcePath: String)

    @Query("DELETE FROM tasks WHERE repoId = :repoId")
    suspend fun deleteAllForRepo(repoId: String)

    @Query("SELECT * FROM tasks WHERE repoId = :repoId AND todolistId = :todolistId AND (:includeDone OR done = 0) ORDER BY dueEpochMs")
    fun byTodolist(repoId: String, todolistId: String, includeDone: Boolean): Flow<List<TaskRow>>

    @Query("SELECT * FROM tasks WHERE repoId = :repoId AND tagsJson LIKE '%' || :needle || '%'")
    fun byTagsLike(repoId: String, needle: String): Flow<List<TaskRow>>

    @Query("SELECT * FROM tasks WHERE repoId = :repoId")
    suspend fun listAll(repoId: String): List<TaskRow>
}

@Dao
interface StandingTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<StandingTaskRow>)

    @Query("DELETE FROM standing_tasks WHERE repoId = :repoId AND sourcePath = :sourcePath")
    suspend fun deleteBySourcePath(repoId: String, sourcePath: String)

    @Query("DELETE FROM standing_tasks WHERE repoId = :repoId")
    suspend fun deleteAllForRepo(repoId: String)

    @Query("SELECT * FROM standing_tasks WHERE repoId = :repoId AND todolistId = :todolistId AND (:includeDone OR done = 0)")
    fun byTodolist(repoId: String, todolistId: String, includeDone: Boolean): Flow<List<StandingTaskRow>>

    @Query("SELECT * FROM standing_tasks WHERE repoId = :repoId")
    suspend fun listAll(repoId: String): List<StandingTaskRow>
}

@Dao
interface RecurrenceRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<RecurrenceRuleRow>)

    @Query("DELETE FROM recurrence_rules WHERE repoId = :repoId AND sourcePath = :sourcePath")
    suspend fun deleteBySourcePath(repoId: String, sourcePath: String)

    @Query("DELETE FROM recurrence_rules WHERE repoId = :repoId")
    suspend fun deleteAllForRepo(repoId: String)

    @Query("SELECT * FROM recurrence_rules WHERE repoId = :repoId AND calendarId = :calendarId")
    fun byCalendar(repoId: String, calendarId: String): Flow<List<RecurrenceRuleRow>>

    @Query("SELECT * FROM recurrence_rules WHERE repoId = :repoId")
    suspend fun listAll(repoId: String): List<RecurrenceRuleRow>
}

@Dao
interface ExceptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ExceptionRow>)

    @Query("DELETE FROM exceptions WHERE repoId = :repoId AND sourcePath = :sourcePath")
    suspend fun deleteBySourcePath(repoId: String, sourcePath: String)

    @Query("DELETE FROM exceptions WHERE repoId = :repoId")
    suspend fun deleteAllForRepo(repoId: String)

    @Query("SELECT * FROM exceptions WHERE repoId = :repoId AND ruleId = :ruleId")
    fun byRule(repoId: String, ruleId: String): Flow<List<ExceptionRow>>

    @Query("SELECT * FROM exceptions WHERE repoId = :repoId")
    suspend fun listAll(repoId: String): List<ExceptionRow>
}

@Dao
interface DeviationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<DeviationRow>)

    @Query("DELETE FROM deviations WHERE repoId = :repoId AND sourcePath = :sourcePath")
    suspend fun deleteBySourcePath(repoId: String, sourcePath: String)

    @Query("DELETE FROM deviations WHERE repoId = :repoId")
    suspend fun deleteAllForRepo(repoId: String)

    @Query("SELECT * FROM deviations WHERE repoId = :repoId AND targetId = :targetId")
    fun byTarget(repoId: String, targetId: String): Flow<List<DeviationRow>>

    @Query("SELECT * FROM deviations WHERE repoId = :repoId")
    suspend fun listAll(repoId: String): List<DeviationRow>
}

@Dao
interface OverrideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<OverrideRow>)

    @Query("DELETE FROM overrides WHERE repoId = :repoId AND sourcePath = :sourcePath")
    suspend fun deleteBySourcePath(repoId: String, sourcePath: String)

    @Query("DELETE FROM overrides WHERE repoId = :repoId")
    suspend fun deleteAllForRepo(repoId: String)

    @Query("SELECT * FROM overrides WHERE repoId = :repoId")
    suspend fun listAll(repoId: String): List<OverrideRow>
}

@Dao
interface JournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<JournalEntryRow>)

    @Query("DELETE FROM journal_entries WHERE repoId = :repoId AND sourcePath = :sourcePath")
    suspend fun deleteBySourcePath(repoId: String, sourcePath: String)

    @Query("DELETE FROM journal_entries WHERE repoId = :repoId")
    suspend fun deleteAllForRepo(repoId: String)

    @Query("SELECT * FROM journal_entries WHERE repoId = :repoId AND dayEpochMs BETWEEN :fromMs AND :toMs ORDER BY dayEpochMs, sequence")
    fun byDateRange(repoId: String, fromMs: Long, toMs: Long): Flow<List<JournalEntryRow>>

    @Query("SELECT * FROM journal_entries WHERE repoId = :repoId")
    suspend fun listAll(repoId: String): List<JournalEntryRow>
}

@Dao
interface IdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<IdentityRow>)

    @Query("DELETE FROM identities WHERE repoId = :repoId AND sourcePath = :sourcePath")
    suspend fun deleteBySourcePath(repoId: String, sourcePath: String)

    @Query("DELETE FROM identities WHERE repoId = :repoId")
    suspend fun deleteAllForRepo(repoId: String)

    @Query("SELECT * FROM identities WHERE repoId = :repoId")
    suspend fun listAll(repoId: String): List<IdentityRow>
}

@Dao
interface RepoStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: RepoStateRow)

    @Query("SELECT * FROM repo_state WHERE repoId = :repoId")
    suspend fun get(repoId: String): RepoStateRow?

    @Query("DELETE FROM repo_state WHERE repoId = :repoId")
    suspend fun delete(repoId: String)
}

@Dao
interface IndexErrorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<IndexErrorRow>)

    @Query("DELETE FROM index_errors WHERE repoId = :repoId AND sourcePath = :sourcePath")
    suspend fun deleteBySourcePath(repoId: String, sourcePath: String)

    @Query("DELETE FROM index_errors WHERE repoId = :repoId")
    suspend fun deleteAllForRepo(repoId: String)

    @Query("SELECT * FROM index_errors WHERE repoId = :repoId")
    suspend fun listForRepo(repoId: String): List<IndexErrorRow>
}

@Dao
interface FtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(rows: List<EventFtsRow>)

    @Query("DELETE FROM events_fts WHERE repoId = :repoId AND eventId = :eventId")
    suspend fun deleteEvent(repoId: String, eventId: String)

    @Query("DELETE FROM events_fts WHERE repoId = :repoId")
    suspend fun deleteAllEventsForRepo(repoId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(rows: List<TaskFtsRow>)

    @Query("DELETE FROM tasks_fts WHERE repoId = :repoId AND taskId = :taskId")
    suspend fun deleteTask(repoId: String, taskId: String)

    @Query("DELETE FROM tasks_fts WHERE repoId = :repoId")
    suspend fun deleteAllTasksForRepo(repoId: String)

    @Query("SELECT eventId FROM events_fts WHERE repoId = :repoId AND events_fts MATCH :query")
    suspend fun searchEventIds(repoId: String, query: String): List<String>

    @Query("SELECT taskId FROM tasks_fts WHERE repoId = :repoId AND tasks_fts MATCH :query")
    suspend fun searchTaskIds(repoId: String, query: String): List<String>
}

/**
 * Phase XX.2 / AT-B.4 / RV-R.4 — completion-state cache DAO.
 *
 * Storage layer for `CompletionState` per `(repoId, targetId,
 * occurrenceDate)`. Indexers + the end-alarm receiver upsert here; the
 * resolver / UI reads.
 */
@Dao
interface EventInstanceStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: EventInstanceStateRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<EventInstanceStateRow>)

    @Query(
        "SELECT * FROM event_instance_state WHERE repoId = :repoId " +
            "AND targetId = :targetId AND occurrenceDate = :occurrenceDate",
    )
    suspend fun get(repoId: String, targetId: String, occurrenceDate: String): EventInstanceStateRow?

    /** AT-B.4 (b): drop the cached row when a deviation file is written or removed. */
    @Query(
        "DELETE FROM event_instance_state WHERE repoId = :repoId " +
            "AND targetId = :targetId AND occurrenceDate = :occurrenceDate",
    )
    suspend fun invalidate(repoId: String, targetId: String, occurrenceDate: String)

    /** AT-B.4 (a): drop everything for a target whose underlying file changed. */
    @Query("DELETE FROM event_instance_state WHERE repoId = :repoId AND targetId = :targetId")
    suspend fun invalidateTarget(repoId: String, targetId: String)

    @Query("DELETE FROM event_instance_state WHERE repoId = :repoId")
    suspend fun deleteAllForRepo(repoId: String)
}
