package com.eight87.strictlykeptboy.demo

import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.store.TomlReader
import com.eight87.strictlykeptboy.store.TomlValue
import java.io.File

/**
 * Round 2.20 Phase C — turn an extracted rich-demo repo on disk into
 * a [RepoConfig] suitable for `RepoStore.add`.
 *
 * Unlike the wizard-scaffolder demo flow (Round 2.15) which gets
 * `repoId` / `defaultCalendarId` / `defaultTodolistId` from the
 * `WizardScaffolder.Outcome`, the rich-demo repo is **pre-baked** in
 * `app/src/main/assets/rich-demo-repo/`. We read `.strictlykeptboy/repo.toml`
 * (the same meta file the scaffolder writes for real repos) to recover
 * those IDs at registration time. If parsing fails or fields are
 * missing, we fall back to filesystem discovery (first calendar dir,
 * first todolist dir) so the picker never deadlocks on a malformed
 * demo bundle — Phase D's parsability test will catch genuine drift.
 *
 * SOLID — single responsibility: assets-on-disk → RepoConfig. The
 * extractor itself ([RichDemoSeeder]) stays innocent of RepoStore
 * vocabulary. Both writers (DemoRepoSeeder + this) ultimately funnel
 * into the same `RepoStore.add(RepoConfig)` tail in MainActivity.
 */
object RichDemoRegistrar {

    /** Display name for the rich-demo repo row in the repo picker. */
    const val DISPLAY_NAME = "demo · kept-life"

    /** Author identity baked into the demo (no real commits get made). */
    val DEMO_AUTHOR: AuthorIdentity =
        AuthorIdentity("demo", "demo@strictlykeptboy.local")

    /**
     * Build a [RepoConfig] from the extracted rich-demo root.
     *
     * `repoRoot` is the directory returned by
     * `RichDemoSeeder.seedIfNeeded(parentDir)` — i.e. the
     * `<parentDir>/rich-demo/` folder containing `AGENTS.md`,
     * `calendars/`, `todolists/`, etc.
     */
    fun buildConfig(repoRoot: File): RepoConfig {
        val meta = readRepoMeta(repoRoot)
        val repoId = meta?.scalar("id") ?: repoRoot.name
        val displayName = meta?.scalar("name")?.let { DISPLAY_NAME } ?: DISPLAY_NAME
        val defaultCalendarId = meta?.scalar("default_calendar")
            ?: meta?.scalar("default_calendar_id")
            ?: discoverFirstCalendarId(repoRoot)
        val defaultTodolistId = meta?.scalar("default_todolist")
            ?: meta?.scalar("default_todolist_id")
            ?: discoverFirstTodolistId(repoRoot)
        val emoji = meta?.scalar("emoji") ?: "✨"
        return RepoConfig(
            repoId = repoId,
            displayName = displayName,
            rootDir = repoRoot.absolutePath,
            remotes = emptyList(),
            primaryRemote = null,
            authorIdentity = DEMO_AUTHOR,
            defaultCalendarId = defaultCalendarId,
            defaultTodolistId = defaultTodolistId,
            iconEmoji = emoji,
            iconSpecies = "Bat",
            isDemo = true,
        )
    }

    // --- internals --------------------------------------------------

    private fun readRepoMeta(repoRoot: File): com.eight87.strictlykeptboy.store.TomlTable? {
        val file = File(repoRoot, ".strictlykeptboy/repo.toml")
        if (!file.isFile) return null
        return runCatching { TomlReader.parse(file.readText(Charsets.UTF_8)) }
            .getOrNull()
    }

    private fun com.eight87.strictlykeptboy.store.TomlTable.scalar(key: String): String? =
        (scalars[key] as? TomlValue.Str)?.value

    private fun discoverFirstCalendarId(repoRoot: File): String? {
        val calendars = File(repoRoot, "calendars")
        if (!calendars.isDirectory) return null
        // Prefer the calendar.toml `id` field over the folder name —
        // the folder name is a slug, the id is a UUIDv7.
        return calendars.listFiles().orEmpty()
            .asSequence()
            .filter { it.isDirectory && File(it, "calendar.toml").isFile }
            .sortedBy { it.name }
            .mapNotNull { dir ->
                val txt = File(dir, "calendar.toml").runCatching {
                    readText(Charsets.UTF_8)
                }.getOrNull() ?: return@mapNotNull null
                runCatching { TomlReader.parse(txt) }.getOrNull()?.scalar("id")
            }
            .firstOrNull()
    }

    private fun discoverFirstTodolistId(repoRoot: File): String? {
        val todolists = File(repoRoot, "todolists")
        if (!todolists.isDirectory) return null
        return todolists.listFiles().orEmpty()
            .asSequence()
            .filter { it.isDirectory && File(it, "todolist.toml").isFile }
            .sortedBy { it.name }
            .mapNotNull { dir ->
                val txt = File(dir, "todolist.toml").runCatching {
                    readText(Charsets.UTF_8)
                }.getOrNull() ?: return@mapNotNull null
                runCatching { TomlReader.parse(txt) }.getOrNull()?.scalar("id")
            }
            .firstOrNull()
    }
}
