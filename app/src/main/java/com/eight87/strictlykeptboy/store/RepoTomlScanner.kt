package com.eight87.strictlykeptboy.store

import com.eight87.strictlykeptboy.resolver.TodolistMeta
import com.eight87.strictlykeptboy.ui.tasks.TodolistInfo
import com.eight87.strictlykeptboy.ui.tasks.TodolistMode
import com.eight87.strictlykeptboy.ui.tasks.buildTodolistInfo
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Round 2.28 / SOLID #8b — extracted from MainActivity.
 *
 * Cold-start TOML scanner for per-repo `todolists/<id>/todolist.toml`
 * files. Caches the parsed `(uuid → TodolistInfo)` map so each repo
 * is scanned at most once per scanner instance. The dir name on disk
 * is a slug, while task frontmatter references the UUID, so we must
 * read the toml to bridge the two.
 *
 * Lifecycle: callers create one scanner per `LaunchedEffect` (i.e.
 * one per cold start), reset via [resetIfRepoSetChanged] when the
 * configured repo set's `(repoId, rootDir)` tuple changes, and call
 * [readTomlInfo] for each task row to get a UI-ready `TodolistInfo`.
 */
class RepoTomlScanner {
    private val todolistByUuid = ConcurrentHashMap<String, TodolistInfo>()
    private val scannedRepos = ConcurrentHashMap<String, Boolean>()
    private var lastRepoIdsKey = ""

    /** Clear caches when the repo set changes (rootDir may have moved). */
    fun resetIfRepoSetChanged(repoIdsKey: String) {
        if (repoIdsKey != lastRepoIdsKey) {
            todolistByUuid.clear()
            scannedRepos.clear()
            lastRepoIdsKey = repoIdsKey
        }
    }

    fun scanRepoTomls(repoRoot: File, repoId: String) {
        if (scannedRepos.putIfAbsent("$repoId@${repoRoot.absolutePath}", true) != null) return
        val todolistsDir = File(repoRoot, "todolists")
        if (!todolistsDir.isDirectory) return
        todolistsDir.listFiles()?.forEach { dir ->
            val toml = File(dir, "todolist.toml")
            if (!toml.isFile) return@forEach
            runCatching {
                val txt = toml.readText(Charsets.UTF_8)
                val tbl = TomlReader.parse(txt)
                val uuid = tbl.getString("id") ?: return@runCatching
                val displayName = tbl.getString("name")
                val emoji = tbl.getString("emoji")
                val colorSeed = tbl.getString("color")
                    ?: tbl.getString("color_seed")
                val priority = tbl.getInt("priority") ?: 0
                val modeStr = tbl.getString("mode")?.lowercase()
                val mode = if (modeStr == "shopping") {
                    TodolistMode.Shopping
                } else TodolistMode.Standard
                todolistByUuid["$repoId::$uuid"] = buildTodolistInfo(
                    todolistId = uuid,
                    repoId = repoId,
                    meta = null,
                    fallbackDisplayName = displayName,
                    fallbackEmoji = emoji,
                    fallbackColorSeed = colorSeed,
                    fallbackPriority = priority,
                    fallbackMode = mode,
                )
            }
        }
    }

    fun readTomlInfo(
        repoRoot: File,
        repoId: String,
        todolistId: String,
        meta: TodolistMeta?,
    ): TodolistInfo {
        scanRepoTomls(repoRoot, repoId)
        todolistByUuid["$repoId::$todolistId"]?.let { return it }
        return buildTodolistInfo(
            todolistId = todolistId,
            repoId = repoId,
            meta = meta,
        )
    }
}
