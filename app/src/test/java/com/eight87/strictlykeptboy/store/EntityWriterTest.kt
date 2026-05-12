package com.eight87.strictlykeptboy.store

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.stream.Collectors

class EntityWriterTest {

    @get:Rule val tmp = TemporaryFolder()

    private val header = EntityHeader(
        id = "0190d4a0-7fab-7c50-9c1e-2b7a44f6f001",
        createdAt = "2026-05-09T18:30:00+02:00",
        updatedAt = "2026-05-09T18:30:00+02:00",
        author = "01900000-0000-7000-8000-aaaaaaaaaaaa",
    )

    @Test fun writeRoundTripAndAtomicCleanup() = runTest {
        val root = tmp.newFolder("repo")
        val e = Event(
            header = header,
            title = "Foo",
            start = "2026-03-15T10:00:00+02:00",
            end = "2026-03-15T11:00:00+02:00",
            calendarId = "cal-A",
            tags = listOf("x"),
        )
        val rel = EntityWriter.write(root, e)
        val onDisk = root.toPath().resolve(rel)
        assertTrue("file written: $onDisk", Files.exists(onDisk))
        assertEquals("calendars/cal-A/events/2026/03/${e.id}.md",
            rel.toString().replace('\\', '/'))

        // No stray .tmp- files left behind.
        Files.walk(root.toPath()).use { s ->
            val tmps = s.filter { Files.isRegularFile(it) && it.fileName.toString().contains(".tmp-") }
                .collect(Collectors.toList())
            assertTrue("no tmp staging files left: $tmps", tmps.isEmpty())
        }

        val text = String(Files.readAllBytes(onDisk), Charsets.UTF_8)
        val parsed = Event.fromDoc(FrontmatterReader.parse(text))
        assertEquals(e, parsed)
    }

    @Test fun deleteRemovesFile() = runTest {
        val root = tmp.newFolder("repo")
        val s = StandingTask(header, title = "T", todolistId = "list-1")
        val rel = EntityWriter.write(root, s)
        val abs = root.toPath().resolve(rel)
        assertTrue(Files.exists(abs))
        val deleted = EntityWriter.delete(root, s)
        assertTrue(deleted)
        assertFalse(Files.exists(abs))
    }

    @Test fun batchWritesAllOrNoneAttempted() = runTest {
        val root = tmp.newFolder("repo")
        val a = StandingTask(header.copy(id = "id-a"), title = "A", todolistId = "list-1")
        val b = StandingTask(header.copy(id = "id-b"), title = "B", todolistId = "list-1")
        val paths = EntityWriter.writeBatch(root, listOf(a, b))
        assertEquals(2, paths.size)
        for (rel in paths) {
            assertTrue(Files.exists(root.toPath().resolve(rel)))
        }
    }
}
