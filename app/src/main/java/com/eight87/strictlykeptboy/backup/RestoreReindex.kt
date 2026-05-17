package com.eight87.strictlykeptboy.backup

import com.eight87.strictlykeptboy.cache.Indexer
import com.eight87.strictlykeptboy.composition.AppGraph
import com.eight87.strictlykeptboy.git.GitRepo
import com.eight87.strictlykeptboy.git.GitRepoRegistry
import com.eight87.strictlykeptboy.git.RepoConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Round 2.28 / SOLID #8b — extracted from MainActivity.
 *
 * Round 2.17 Phase G.6 — invalidate the cache DB + full re-index
 * the restored repos. Order matters: this MUST run AFTER the
 * on-disk wipe/extract AND `RepoStore.replaceAll`, so the new
 * configs are authoritative and we don't index a stale set.
 *
 * Implementation: clear all Room tables, drop the GitRepo handle
 * cache (rootDirs may have changed), then `fullScan` each repo —
 * which records a fresh `RepoStateRow` head SHA + schema version.
 */
object RestoreReindex {
    suspend fun reindex(graph: AppGraph, configs: List<RepoConfig>) {
        withContext(Dispatchers.IO) {
            runCatching { graph.cacheDatabase.clearAllTables() }
            GitRepoRegistry.clear()
            val indexer = Indexer(graph.cacheDatabase)
            for (cfg in configs) {
                runCatching {
                    val gitRepo = GitRepo.open(
                        rootDir = File(cfg.rootDir),
                        repoId = cfg.repoId,
                        remotes = cfg.remotes,
                        primaryRemote = cfg.primaryRemote,
                        authorIdentity = cfg.authorIdentity,
                        defaultBranch = cfg.defaultBranch,
                    ).also(GitRepoRegistry::put)
                    indexer.fullScan(cfg.repoId, gitRepo)
                }
            }
        }
    }
}
