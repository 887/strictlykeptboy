package com.eight87.skb.cli

import com.eight87.skb.cli.commands.CalGroup
import com.eight87.skb.cli.commands.EventGroup
import com.eight87.skb.cli.commands.HelpCommand
import com.eight87.skb.cli.commands.RepoGroup
import com.eight87.skb.cli.commands.TaskGroup
import com.eight87.skb.cli.commands.overrideGitOps
import com.eight87.skb.cli.core.CliContext
import com.eight87.skb.cli.core.GitOps
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives the full CLI via [Skb] parse calls, with a fake GitOps that
 * tracks commits in-memory. Validates X.4 atomic write + auto-commit
 * + X.6 dry-run + X.11 repo discovery via --repo, end-to-end.
 */
class EndToEndTest {
  @get:Rule val tmp = TemporaryFolder()

  private lateinit var repo: Path
  private val fake = FakeGit()

  @Before fun setup() {
    overrideGitOps(fake)
    repo = tmp.newFolder("repo").toPath()
  }

  @After fun teardown() {
    overrideGitOps(null)
  }

  private fun run(vararg args: String) {
    val skb = Skb()
    val ctxOf = { skb.ctx }
    skb.subcommands(EventGroup(ctxOf), TaskGroup(ctxOf), CalGroup(ctxOf), RepoGroup(ctxOf), HelpCommand(ctxOf))
    skb.parse(args.toList())
  }

  @Test fun repoInitCreatesScaffoldAndCommits() {
    run("repo", "init", repo.toString(), "--name", "Test", "--default-calendar", "Personal", "--default-tz", "Europe/Berlin")
    assertTrue(Files.isDirectory(repo.resolve(".strictlykeptboy")))
    assertTrue(Files.isRegularFile(repo.resolve(".strictlykeptboy/repo.toml")))
    assertTrue(Files.list(repo.resolve("calendars")).use { it.count() } == 1L)
    assertEquals(1, fake.inits.size)
    assertEquals(1, fake.commits.size)
    assertTrue(fake.commits[0].message.startsWith("initial scaffold for"))
  }

  @Test fun eventAddWritesFileAndCommits() {
    run("repo", "init", repo.toString(), "--default-calendar", "Personal")
    run("--repo", repo.toString(), "event", "add", "--title", "Dentist", "--start", "2026-05-12T14:00:00+02:00", "--duration", "PT45M")
    val evDir = repo.resolve("calendars")
    val eventFiles = Files.walk(evDir).use { s -> s.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.toList() }
    assertEquals(1, eventFiles.size)
    val text = Files.readString(eventFiles.first())
    assertTrue(text.contains("Dentist"))
    assertTrue(text.contains("kind = \"event\""))
    assertEquals(2, fake.commits.size)
    assertTrue(fake.commits[1].message == "add event \"Dentist\" in Personal")
  }

  @Test fun dryRunDoesNotWriteOrCommit() {
    run("repo", "init", repo.toString(), "--default-calendar", "Personal")
    val before = fake.commits.size
    run("--repo", repo.toString(), "--dry-run", "event", "add", "--title", "DryEvent", "--start", "2026-05-12T14:00:00+02:00", "--duration", "PT45M")
    val files = Files.walk(repo.resolve("calendars")).use { s -> s.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.count() }
    assertEquals(0L, files)
    assertEquals(before, fake.commits.size)
  }

  @Test fun taskAddWithDueWritesAndCommits() {
    run("repo", "init", repo.toString(), "--default-list", "Chores")
    run("--repo", repo.toString(), "task", "add", "--title", "Reply", "--due", "2026-05-13")
    val taskFiles = Files.walk(repo.resolve("todolists")).use { s -> s.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.toList() }
    assertEquals(1, taskFiles.size)
    val text = Files.readString(taskFiles.first())
    assertTrue(text.contains("Reply"))
    assertTrue(text.contains("kind = \"task\""))
  }

  @Test fun standingTaskWhenNoDue() {
    run("repo", "init", repo.toString(), "--default-list", "Chores")
    run("--repo", repo.toString(), "task", "add", "--title", "Standing one")
    val text = Files.walk(repo.resolve("todolists")).use { s ->
      s.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.findFirst().get()
    }
    assertTrue(Files.readString(text).contains("standing_task"))
  }

  @Test fun calAddCreatesCalendarToml() {
    run("repo", "init", repo.toString())
    run("--repo", repo.toString(), "cal", "add", "--name", "Work", "--tz", "UTC")
    val cals = Files.list(repo.resolve("calendars")).use { it.toList() }
    assertEquals(1, cals.size)
    val toml = cals[0].resolve("calendar.toml")
    assertTrue(Files.isRegularFile(toml))
    assertTrue(Files.readString(toml).contains("Work"))
  }

  @Test fun eventListEmittedJson() {
    run("repo", "init", repo.toString(), "--default-calendar", "Personal")
    run("--repo", repo.toString(), "event", "add", "--title", "E1", "--start", "2026-05-12T14:00:00+02:00", "--duration", "PT30M")
    // can't easily intercept echo without a custom Clikt console; reaching for
    // the underlying repo is enough — list is exercised in other path. Just
    // make sure list doesn't blow up.
    run("--repo", repo.toString(), "event", "list", "--from", "2026-05-01", "--to", "2026-06-01")
  }
}

class FakeGit : GitOps {
  data class Commit(val files: List<String>, val message: String, val author: String)
  val commits = mutableListOf<Commit>()
  val inits = mutableListOf<Path>()
  override fun addAndCommit(repoRoot: Path, files: List<String>, message: String, authorName: String, authorEmail: String): String? {
    commits.add(Commit(files, message, "$authorName <$authorEmail>"))
    return "abc1234"
  }
  override fun init(path: Path) { inits.add(path) }
  override fun headShortSha(repoRoot: Path): String? = if (commits.isNotEmpty()) "abc1234" else null
}
