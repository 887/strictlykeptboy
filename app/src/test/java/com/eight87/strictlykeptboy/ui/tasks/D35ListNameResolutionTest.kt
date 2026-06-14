package com.eight87.strictlykeptboy.ui.tasks

import com.eight87.strictlykeptboy.resolver.TodolistMeta
import com.eight87.strictlykeptboy.resolver.TodolistRef
import com.eight87.strictlykeptboy.resolver.RepoRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

/**
 * D-35 regression guard — verifies that todolist display names resolve
 * to friendly names (not raw UUIDs) from the snapshot path.
 *
 * The root cause was that IndexerSnapshotPublisher created TodolistMeta
 * with displayName = uuid. buildTodolistInfo skips a meta whose
 * displayName equals the todolistId — so the uuid always showed.
 * The fix: IndexerSnapshotPublisher.readTodolistNamesFromDisk reads the
 * todolist.toml files eagerly so the snapshot carries real names.
 *
 * Unit-testable part: buildTodolistInfo resolution priority.
 */
class D35ListNameResolutionTest {

    private val uuid = "0190a0aa-2222-7000-8a0a-000000000001"
    private val repoId = "repo-1"
    private val repoRef = RepoRef(repoId)

    // ─── buildTodolistInfo resolution priority ────────────────────────────────

    @Test fun `buildTodolistInfo uses meta displayName when it differs from id`() {
        // Simulates the FIXED IndexerSnapshotPublisher that provides a real name.
        val meta = TodolistMeta(
            ref = TodolistRef(uuid),
            repo = repoRef,
            displayName = "Daily rituals",
        )
        val info = buildTodolistInfo(
            todolistId = uuid,
            repoId = repoId,
            meta = meta,
        )
        assertEquals("should use the real name from meta", "Daily rituals", info.name)
        assertNotEquals("should NOT show the raw UUID", uuid, info.name)
    }

    @Test fun `buildTodolistInfo falls back to uuid when meta displayName equals id`() {
        // Simulates the UNFIXED path where meta.displayName was the UUID.
        val meta = TodolistMeta(
            ref = TodolistRef(uuid),
            repo = repoRef,
            displayName = uuid, // this is what the old code produced
        )
        val info = buildTodolistInfo(
            todolistId = uuid,
            repoId = repoId,
            meta = meta,
        )
        // Without a fallbackDisplayName and no TOML scan, it falls back to the uuid.
        assertEquals("without real name, id is the last resort", uuid, info.name)
    }

    @Test fun `buildTodolistInfo uses fallbackDisplayName over uuid meta`() {
        // Simulates the RepoTomlScanner providing a fallback name.
        val meta = TodolistMeta(
            ref = TodolistRef(uuid),
            repo = repoRef,
            displayName = uuid,
        )
        val info = buildTodolistInfo(
            todolistId = uuid,
            repoId = repoId,
            meta = meta,
            fallbackDisplayName = "Daily rituals",
        )
        assertEquals("fallback name should win over uuid meta", "Daily rituals", info.name)
    }

    @Test fun `buildTodolistInfo uses meta displayName over fallback when real`() {
        // When the fixed snapshot provides a real name, it should win.
        val meta = TodolistMeta(
            ref = TodolistRef(uuid),
            repo = repoRef,
            displayName = "Play",
        )
        val info = buildTodolistInfo(
            todolistId = uuid,
            repoId = repoId,
            meta = meta,
            fallbackDisplayName = "Old fallback",
        )
        assertEquals("meta real name beats fallback", "Play", info.name)
    }

    // ─── Disk-scan result via temp dir ───────────────────────────────────────

    @Test fun `readTodolistNamesFromDisk returns empty map for missing dir`() {
        val nonExistent = File("/tmp/skb-test-nonexistent-${System.nanoTime()}")
        val map = readTodolistNamesForTest(nonExistent.absolutePath)
        assertEquals("non-existent dir should yield empty map", emptyMap<String, String>(), map)
    }

    @Test fun `readTodolistNamesFromDisk parses real toml files`() {
        val parentDir = kotlin.io.path.createTempDirectory("skb-test-repo-root").toFile()
        try {
            val todolistsDir = File(parentDir, "todolists").also { it.mkdirs() }
            val slugDir = File(todolistsDir, "daily-rituals").also { it.mkdirs() }
            File(slugDir, "todolist.toml").writeText(
                """
                schema_version = 1
                id = "$uuid"
                kind = "todolist"
                name = "Daily rituals"
                """.trimIndent()
            )
            val map = readTodolistNamesForTest(parentDir.absolutePath)
            assertEquals("should resolve real name from toml", "Daily rituals", map[uuid])
        } finally {
            parentDir.deleteRecursively()
        }
    }

    // ─── Helper that exercises the same logic as IndexerSnapshotPublisher ─────

    /**
     * Mirror of IndexerSnapshotPublisher.readTodolistNamesFromDisk().
     * Kept here as a pure function so it's unit-testable without spinning
     * up the full composition stack.
     */
    private fun readTodolistNamesForTest(repoRoot: String): Map<String, String> {
        val dir = File(repoRoot, "todolists")
        if (!dir.isDirectory) return emptyMap()
        val result = mutableMapOf<String, String>()
        dir.listFiles()?.forEach { slugDir ->
            val toml = File(slugDir, "todolist.toml")
            if (!toml.isFile) return@forEach
            runCatching {
                // Minimal TOML parse: just extract id and name lines.
                val text = toml.readText(Charsets.UTF_8)
                val idLine = text.lines().firstOrNull { it.trimStart().startsWith("id") }
                val nameLine = text.lines().firstOrNull { it.trimStart().startsWith("name") }
                val idVal = idLine?.substringAfter("=")?.trim()?.removeSurrounding("\"")
                val nameVal = nameLine?.substringAfter("=")?.trim()?.removeSurrounding("\"")
                if (idVal != null && nameVal != null) result[idVal] = nameVal
            }
        }
        return result
    }
}
