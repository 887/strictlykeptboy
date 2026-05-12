package com.eight87.strictlykeptboy.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/**
 * Schema versioning + migration scaffold per DM-F.
 *
 * v1 ships with no migrations (it IS the floor — DM-F.5). The runner
 * exists so v2 can land without touching call sites.
 */
object SchemaMigrationRunner {

    const val LATEST_SCHEMA = 1
    const val MIN_READABLE_SCHEMA = 1

    sealed interface MigrationResult {
        data object NoOp : MigrationResult
        data class Migrated(val from: Int, val to: Int) : MigrationResult
        data class TooNew(val repoVersion: Int) : MigrationResult
        data class NotManaged(val message: String) : MigrationResult
    }

    /**
     * Inspects `<root>/.strictlykeptboy/schema.toml`. Per DM-F.2:
     *  - missing → NotManaged (caller can offer to adopt)
     *  - > LATEST → TooNew (refuse to write)
     *  - < LATEST → run registered migrations sequentially (none in v1)
     *  - == LATEST → NoOp
     */
    suspend fun migrateIfNeeded(rootDir: File): MigrationResult = withContext(Dispatchers.IO) {
        val schemaPath = rootDir.toPath().resolve(".strictlykeptboy/schema.toml")
        if (!Files.exists(schemaPath)) return@withContext MigrationResult.NotManaged(
            "no .strictlykeptboy/schema.toml — not a strictlykeptboy-managed repo"
        )
        val text = String(Files.readAllBytes(schemaPath), Charsets.UTF_8)
        val table = TomlReader.parse(text)
        val v = table.getInt("schema_version") ?: 1
        when {
            v > LATEST_SCHEMA -> MigrationResult.TooNew(v)
            v == LATEST_SCHEMA -> MigrationResult.NoOp
            else -> {
                // v1 ships with no migrations; this branch exists for v2+.
                MigrationResult.NoOp
            }
        }
    }
}
