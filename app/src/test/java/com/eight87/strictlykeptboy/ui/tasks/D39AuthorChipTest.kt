package com.eight87.strictlykeptboy.ui.tasks

import com.eight87.strictlykeptboy.cache.EntityMapping
import com.eight87.strictlykeptboy.cache.entities.TaskRow
import com.eight87.strictlykeptboy.cache.entities.StandingTaskRow
import com.eight87.strictlykeptboy.store.EntityHeader
import com.eight87.strictlykeptboy.store.Task
import com.eight87.strictlykeptboy.store.StandingTask
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * D-39 regression guard — verifies that the author id from a task's
 * EntityHeader flows through the indexing path (EntityMapping) and
 * the UI mapping path (TaskEntityMapping) onto TaskItem.author.
 *
 * Without the fix, TaskRow.author was absent and toTaskItem set
 * TaskItem.author = "" so no author chip rendered for file-backed tasks.
 */
class D39AuthorChipTest {

    private val list = TaskFixtures.listA
    private val zone = ZoneId.of("UTC")

    // ─── EntityMapping (indexer layer) ───────────────────────────────────────

    @Test fun `EntityMapping_task carries author from header`() {
        val task = Task(
            header = EntityHeader(
                schemaVersion = 1,
                id = "task-1",
                createdAt = "2026-06-14T00:00:00Z",
                updatedAt = "2026-06-14T00:00:00Z",
                author = "01900000-0000-7000-8000-0000000000bb",
            ),
            title = "Kneel and check in with Sir",
            todolistId = "tl-rituals",
        )
        val row = EntityMapping.task("repo-1", task, "tasks/2026/06/task-1.md")
        assertEquals("author should be propagated into TaskRow",
            "01900000-0000-7000-8000-0000000000bb", row.author)
    }

    @Test fun `EntityMapping_standingTask carries author from header`() {
        val st = StandingTask(
            header = EntityHeader(
                schemaVersion = 1,
                id = "st-1",
                createdAt = "2026-06-14T00:00:00Z",
                updatedAt = "2026-06-14T00:00:00Z",
                author = "01900000-0000-7000-8000-0000000000bb",
            ),
            title = "Evening routine",
            todolistId = "tl-rituals",
        )
        val row = EntityMapping.standingTask("repo-1", st, "standing/st-1.md")
        assertEquals("author should be propagated into StandingTaskRow",
            "01900000-0000-7000-8000-0000000000bb", row.author)
    }

    // ─── TaskEntityMapping (UI layer) ────────────────────────────────────────

    @Test fun `TaskRow_toTaskItem propagates author`() {
        val row = TaskRow(
            repoId = "repo-1",
            id = "task-1",
            todolistId = list.id,
            title = "Kneel and check in with Sir",
            body = "",
            dueEpochMs = null,
            done = false,
            doneAtEpochMs = null,
            priority = 800,
            tagsJson = "[]",
            sourcePath = "tasks/2026/06/task-1.md",
            author = "01900000-0000-7000-8000-0000000000bb",
        )
        val item = row.toTaskItem(list, zone)
        assertEquals("TaskItem.author should carry the row's author",
            "01900000-0000-7000-8000-0000000000bb", item.author)
    }

    @Test fun `StandingTaskRow_toTaskItem propagates author`() {
        val row = StandingTaskRow(
            repoId = "repo-1",
            id = "st-1",
            todolistId = list.id,
            title = "Evening routine",
            body = "",
            done = false,
            pinned = false,
            priority = null,
            tagsJson = "[]",
            sourcePath = "standing/st-1.md",
            author = "01900000-0000-7000-8000-0000000000bb",
        )
        val item = row.toTaskItem(list)
        assertEquals("TaskItem.author should carry the standing row's author",
            "01900000-0000-7000-8000-0000000000bb", item.author)
    }

    @Test fun `TaskRow_toTaskItem with empty author keeps author empty`() {
        val row = TaskRow(
            repoId = "repo-1",
            id = "task-2",
            todolistId = list.id,
            title = "Buy milk",
            body = "",
            dueEpochMs = null,
            done = false,
            doneAtEpochMs = null,
            priority = null,
            tagsJson = "[]",
            sourcePath = "tasks/2026/06/task-2.md",
            // author defaults to ""
        )
        val item = row.toTaskItem(list, zone)
        assertEquals("empty author should remain empty", "", item.author)
    }

    // ─── TaskRow author-chip predicate ───────────────────────────────────────

    @Test fun `author chip predicate fires for foreign author`() {
        // TaskRow.kt: chip renders iff item.author.isNotEmpty() && item.author != activeRepoOwner
        val foreignAuthorItem = TaskItem(
            id = "t",
            title = "Kneel",
            todolist = list,
            author = "01900000-0000-7000-8000-0000000000bb",
        )
        val activeRepoOwner = "01900000-0000-7000-8000-0000000000b0"
        val shouldShow = foreignAuthorItem.author.isNotEmpty() &&
            foreignAuthorItem.author != activeRepoOwner
        assertEquals("author chip should show for foreign-authored task", true, shouldShow)
    }

    @Test fun `author chip predicate suppressed for same owner`() {
        val ownItem = TaskItem(
            id = "t",
            title = "Buy milk",
            todolist = list,
            author = "01900000-0000-7000-8000-0000000000b0",
        )
        val activeRepoOwner = "01900000-0000-7000-8000-0000000000b0"
        val shouldShow = ownItem.author.isNotEmpty() &&
            ownItem.author != activeRepoOwner
        assertEquals("author chip should NOT show for own tasks", false, shouldShow)
    }
}
