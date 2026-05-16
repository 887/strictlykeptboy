package com.eight87.strictlykeptboy.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import com.eight87.strictlykeptboy.cache.dao.DeviationDao
import com.eight87.strictlykeptboy.cache.dao.EventDao
import com.eight87.strictlykeptboy.cache.dao.EventInstanceStateDao
import com.eight87.strictlykeptboy.cache.dao.ExceptionDao
import com.eight87.strictlykeptboy.cache.dao.FtsDao
import com.eight87.strictlykeptboy.cache.dao.IdentityDao
import com.eight87.strictlykeptboy.cache.dao.IndexErrorDao
import com.eight87.strictlykeptboy.cache.dao.JournalDao
import com.eight87.strictlykeptboy.cache.dao.OverrideDao
import com.eight87.strictlykeptboy.cache.dao.RecurrenceRuleDao
import com.eight87.strictlykeptboy.cache.dao.RepoStateDao
import com.eight87.strictlykeptboy.cache.dao.StandingTaskDao
import com.eight87.strictlykeptboy.cache.dao.TaskDao
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

/**
 * Phase D — Room read-through cache (D.8).
 *
 * Per the architecture: files are source of truth. This DB is wiped
 * + rebuilt without ceremony whenever `(git-HEAD, schemaVersion)`
 * disagree with what's on disk. `fallbackToDestructiveMigration` is
 * the policy because the indexer can repopulate from the working
 * tree.
 *
 * Bump [Indexer.CURRENT_SCHEMA_VERSION] to force rebuild on every
 * open even if the Room schema itself hasn't changed (e.g. a parser
 * bug fix that retroactively changes how files map to rows).
 */
@Database(
    entities = [
        EventRow::class,
        TaskRow::class,
        StandingTaskRow::class,
        RecurrenceRuleRow::class,
        ExceptionRow::class,
        DeviationRow::class,
        OverrideRow::class,
        JournalEntryRow::class,
        IdentityRow::class,
        RepoStateRow::class,
        IndexErrorRow::class,
        EventInstanceStateRow::class,
        EventFtsRow::class,
        TaskFtsRow::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun events(): EventDao
    abstract fun tasks(): TaskDao
    abstract fun standingTasks(): StandingTaskDao
    abstract fun recurrenceRules(): RecurrenceRuleDao
    abstract fun exceptions(): ExceptionDao
    abstract fun deviations(): DeviationDao
    abstract fun eventInstanceState(): EventInstanceStateDao
    abstract fun overrides(): OverrideDao
    abstract fun journal(): JournalDao
    abstract fun identities(): IdentityDao
    abstract fun repoState(): RepoStateDao
    abstract fun indexErrors(): IndexErrorDao
    abstract fun fts(): FtsDao

    companion object {
        const val DB_NAME = "skb-cache.db"

        fun open(context: Context): CacheDatabase = Room.databaseBuilder(
            context.applicationContext,
            CacheDatabase::class.java,
            DB_NAME,
        ).fallbackToDestructiveMigration(true).build()

        fun openInMemory(context: Context): CacheDatabase = Room.inMemoryDatabaseBuilder(
            context.applicationContext,
            CacheDatabase::class.java,
        ).allowMainThreadQueries().build()

        /**
         * Open with an explicit SQLite driver. Used by the Robolectric
         * test harness to swap in `BundledSQLiteDriver` (which ships its
         * own native SQLite via androidx.sqlite:sqlite-bundled) rather
         * than relying on the Android framework SQLite, which Robolectric
         * only partially shadows in current versions.
         */
        fun openInMemoryWithDriver(context: Context, driver: SQLiteDriver): CacheDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, CacheDatabase::class.java)
                .setDriver(driver)
                .allowMainThreadQueries()
                .build()
    }
}
