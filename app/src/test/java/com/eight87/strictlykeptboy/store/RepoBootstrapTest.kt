package com.eight87.strictlykeptboy.store

import com.eight87.strictlykeptboy.git.AuthorIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class RepoBootstrapTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun scaffoldEmitsAllExpectedFiles() = runTest {
        val root = tmp.newFolder("freshrepo")
        val result = RepoBootstrap.scaffold(
            rootDir = root,
            spec = RepoBootstrap.Spec(
                repoName = "Alex personal",
                tzId = "Europe/Berlin",
                identity = AuthorIdentity("Alex", "alex@example.com"),
                seedCalendars = listOf("Personal", "Work"),
                seedTodolists = listOf("Chores"),
            ),
        )
        assertTrue(Files.exists(result.agentsMdPath))
        assertTrue(Files.exists(result.claudeMdPath))
        assertTrue(Files.exists(result.identityTomlPath))
        assertTrue(Files.exists(result.repoMetaPath))
        assertTrue(Files.exists(result.schemaMetaPath))
        assertTrue(Files.exists(result.identityFilePath))

        val agents = String(Files.readAllBytes(result.agentsMdPath), Charsets.UTF_8)
        assertTrue("repo name substituted", agents.contains("Alex personal"))
        assertTrue("tz substituted", agents.contains("Europe/Berlin"))
        assertTrue("identity id substituted", agents.contains(result.identityId))
        assertTrue("identity.toml pointer present per DM-Y.4",
            agents.contains("identity.toml"))

        val identityToml = String(Files.readAllBytes(result.identityTomlPath), Charsets.UTF_8)
        assertTrue(identityToml.contains("good boy"))
        assertTrue(identityToml.contains("[praise]"))
        assertTrue(identityToml.contains("[pronouns]"))

        val schema = TomlReader.parse(String(Files.readAllBytes(result.schemaMetaPath), Charsets.UTF_8))
        assertEquals(SchemaMigrationRunner.LATEST_SCHEMA, schema.getInt("schema_version"))

        val migrationResult = SchemaMigrationRunner.migrateIfNeeded(root)
        assertEquals(SchemaMigrationRunner.MigrationResult.NoOp, migrationResult)

        // Calendars + todolists seeded
        assertEquals(2, result.calendarIds.size)
        assertEquals(1, result.todolistIds.size)
        for ((_, cid) in result.calendarIds) {
            assertTrue(Files.exists(root.toPath().resolve("calendars/$cid/calendar.toml")))
            assertTrue(Files.isDirectory(root.toPath().resolve("calendars/$cid/events")))
        }
    }

    @Test fun migrateIfNeededReportsNotManagedOnEmptyDir() = runTest {
        val root = tmp.newFolder("bare")
        val r = SchemaMigrationRunner.migrateIfNeeded(root)
        assertTrue(r is SchemaMigrationRunner.MigrationResult.NotManaged)
    }
}
