package com.eight87.skb.cli

import com.eight87.skb.cli.commands.CalGroup
import com.eight87.skb.cli.commands.EventGroup
import com.eight87.skb.cli.commands.HelpCommand
import com.eight87.skb.cli.commands.RepoGroup
import com.eight87.skb.cli.commands.TaskGroup
import com.eight87.skb.cli.commands.TzGroup
import com.eight87.skb.cli.commands.overrideGitOps
import com.eight87.skb.cli.core.CliError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Round 2.24 Phase F.4 — `skb tz convert` command coverage.
 *
 * Verifies the two conversion modes from D-2.24.f against (a) a
 * one-off event, (b) a recurring rule, and (c) the not-found / invalid-
 * zone error paths.
 */
class TzConvertCommandTest {
  @get:Rule val tmp = TemporaryFolder()

  private lateinit var repo: Path
  private val fake = FakeGit()

  @Before fun setup() {
    overrideGitOps(fake)
    repo = tmp.newFolder("repo").toPath()
  }

  @After fun teardown() { overrideGitOps(null) }

  private fun run(vararg args: String) {
    val skb = Skb()
    val ctxOf = { skb.ctx }
    skb.subcommands(EventGroup(ctxOf), TaskGroup(ctxOf), TzGroup(ctxOf), CalGroup(ctxOf), RepoGroup(ctxOf), HelpCommand(ctxOf))
    skb.parse(args.toList())
  }

  /** Initializes a fresh repo with one calendar + writes a Berlin event at 09:00. */
  private fun seedBerlinEvent(): Path {
    run("repo", "init", repo.toString(), "--default-calendar", "Personal", "--default-tz", "Europe/Berlin")
    run(
      "--repo", repo.toString(), "event", "add",
      "--title", "Standup",
      "--start", "2026-05-12T09:00:00+02:00",
      "--duration", "PT30M",
    )
    val evFiles = Files.walk(repo.resolve("calendars")).use { s ->
      s.filter { Files.isRegularFile(it) && it.toString().endsWith(".md") }.toList()
    }
    assertEquals(1, evFiles.size)
    val file = evFiles.first()
    // Stamp the event with a tz_id so the convert command has an old
    // source zone to translate from. `event add` doesn't write tz_id
    // today; we patch it in directly (mirroring how Phase A round-trips).
    val txt = Files.readString(file)
    val patched = txt.replaceFirst(
      "kind = \"event\"\n",
      "kind = \"event\"\ntz_id = \"Europe/Berlin\"\n",
    )
    Files.writeString(file, patched)
    return file
  }

  @Test fun defaultModePreservesLocalClock() {
    val file = seedBerlinEvent()
    val id = file.fileName.toString().removeSuffix(".md")

    run("--repo", repo.toString(), "tz", "convert", id, "America/New_York")

    val out = Files.readString(file)
    assertTrue("expected new tz_id", out.contains("tz_id = \"America/New_York\""))
    // 09:00 Berlin → 09:00 NY (local clock preserved); the offset on
    // 2026-05-12 in NY is -04:00 (DST).
    assertTrue(
      "expected 09:00 local preserved in NY, got:\n$out",
      out.contains("2026-05-12T09:00:00-04:00"),
    )
    assertFalse(out.contains("+02:00"))
  }

  @Test fun shiftInstantModePreservesUtcInstant() {
    val file = seedBerlinEvent()
    val id = file.fileName.toString().removeSuffix(".md")

    run("--repo", repo.toString(), "tz", "convert", id, "America/New_York", "--shift-instant")

    val out = Files.readString(file)
    assertTrue(out.contains("tz_id = \"America/New_York\""))
    // 09:00 Berlin (+02:00) = 07:00 UTC = 03:00 New York (-04:00).
    assertTrue(
      "expected 03:00 NY (instant preserved), got:\n$out",
      out.contains("2026-05-12T03:00:00-04:00"),
    )
  }

  @Test fun recurrenceRuleDtstartConvertsBodyUntouched() {
    run("repo", "init", repo.toString(), "--default-calendar", "Personal", "--default-tz", "Europe/Berlin")
    // Write a recurrence rule file by hand (CLI doesn't have a
    // `recurrence add` surface yet — that's Round 3 follow-up).
    val calId = Files.list(repo.resolve("calendars")).use { it.toList().first().fileName.toString() }
    val ruleId = "01900000-1111-7000-8000-000000000001"
    val ruleDir = repo.resolve("calendars/$calId/recurrences")
    Files.createDirectories(ruleDir)
    val rulePath = ruleDir.resolve("$ruleId.md")
    Files.writeString(
      rulePath,
      """
      +++
      id = "$ruleId"
      kind = "recurrence"
      title = "Feed cat"
      dtstart = 2026-05-15T07:20:00
      duration = "PT10M"
      tz_id = "Europe/Berlin"
      rrule = "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR"
      +++
      Half tin wet food.
      """.trimIndent() + "\n",
    )

    run("--repo", repo.toString(), "tz", "convert", ruleId, "America/New_York")

    val out = Files.readString(rulePath)
    assertTrue("tz_id rewritten", out.contains("tz_id = \"America/New_York\""))
    // Default mode preserves the local clock: 07:20 stays 07:20, bare
    // (no offset) because rules pair the wall-clock with tz_id.
    assertTrue(
      "expected dtstart with NY-local 07:20 preserved, got:\n$out",
      out.contains("dtstart = 2026-05-15T07:20:00"),
    )
    // RRULE body untouched.
    assertTrue(out.contains("rrule = \"FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR\""))
    // Markdown body preserved.
    assertTrue(out.contains("Half tin wet food."))
  }

  @Test fun invalidEventIdReturnsNotFound() {
    run("repo", "init", repo.toString(), "--default-calendar", "Personal", "--default-tz", "Europe/Berlin")
    try {
      run("--repo", repo.toString(), "tz", "convert", "deadbeef-no-such-id", "America/New_York")
      fail("expected NOT_FOUND")
    } catch (e: CliError) {
      assertEquals(com.eight87.skb.cli.core.ExitCode.NOT_FOUND, e.code)
    }
  }

  @Test fun invalidZoneReturnsUsageError() {
    val file = seedBerlinEvent()
    val id = file.fileName.toString().removeSuffix(".md")
    try {
      run("--repo", repo.toString(), "tz", "convert", id, "Not/A_Zone")
      fail("expected USAGE")
    } catch (e: CliError) {
      assertEquals(com.eight87.skb.cli.core.ExitCode.USAGE, e.code)
    }
  }
}
