package com.eight87.strictlykeptboy.port.ics

import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.store.Event
import com.eight87.strictlykeptboy.store.Exception
import com.eight87.strictlykeptboy.store.ParseResult
import com.eight87.strictlykeptboy.store.RecurrenceRule
import com.eight87.strictlykeptboy.store.RepoScanner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Round 2.28 / SOLID #8b — extracted from MainActivity.
 *
 * Phase P.4 — assemble an .ics serialisation by scanning the repo's
 * working tree, narrowing to event-shaped entities, and handing the
 * triple to [IcsExporter].
 */
object ExportContentBuilder {
    suspend fun build(repo: RepoConfig): String =
        withContext(Dispatchers.IO) {
            val results = RepoScanner.scanAll(File(repo.rootDir))
            val entities = results
                .filterIsInstance<ParseResult.Success>()
                .map { it.entity }
            val events = entities.filterIsInstance<Event>()
            val rules = entities.filterIsInstance<RecurrenceRule>()
            val exceptions = entities.filterIsInstance<Exception>()
            IcsExporter.export(events, rules, exceptions)
        }
}
