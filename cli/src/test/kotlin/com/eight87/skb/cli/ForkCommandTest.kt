package com.eight87.skb.cli

import com.eight87.skb.cli.commands.CalGroup
import com.eight87.skb.cli.commands.EventGroup
import com.eight87.skb.cli.commands.HelpCommand
import com.eight87.skb.cli.commands.RepoGroup
import com.eight87.skb.cli.commands.TaskGroup
import com.eight87.skb.cli.commands.overrideGitOps
import com.eight87.skb.cli.repo.ForkCommand
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase SS.4 — `skb fork` CLI surface.
 *
 * Drives [Skb] with a fake GitOps and exercises the fork mechanic over
 * a scaffolded repo. Validates new repo-id, invalidated fingerprint
 * cache, and the appended `references.toml` back-reference.
 */
class ForkCommandTest {
  @get:Rule val tmp = TemporaryFolder()

  private val fake = FakeGit()
  private lateinit var source: Path

  @Before fun setup() {
    overrideGitOps(fake)
    source = tmp.newFolder("src").toPath()
    run("repo", "init", source.toString(), "--name", "shared-cal", "--default-tz", "UTC")
    // Plant a stale fingerprint cache so we can verify invalidation.
    Files.writeString(source.resolve(".strictlykeptboy/repo-fingerprint"), "deadbeefcafef00d\n")
  }

  @After fun teardown() {
    overrideGitOps(null)
  }

  private fun run(vararg args: String) {
    val skb = Skb()
    val ctxOf = { skb.ctx }
    skb.subcommands(
      EventGroup(ctxOf),
      TaskGroup(ctxOf),
      CalGroup(ctxOf),
      RepoGroup(ctxOf),
      ForkCommand(ctxOf),
      HelpCommand(ctxOf),
    )
    skb.parse(args.toList())
  }

  @Test fun forkClonesAndResetsRepoId() {
    val dst = tmp.newFolder("dst").toPath()
    // newFolder creates the dir; the fork command needs it empty of the
    // strictlykeptboy marker dir but presence of the dir itself is fine.
    Files.delete(dst) // make path non-existent so fork creates it cleanly
    run("--repo", source.toString(), "fork", "--source", source.toString(), "--name", "shared-cal", dst.toString())

    val newId = Files.readString(dst.resolve(".strictlykeptboy/repo-id")).trim()
    val srcId = Files.readString(source.resolve(".strictlykeptboy/repo-id")).trim()
    assertNotEquals(srcId, newId)
    assertTrue(newId.matches(Regex("^[0-9a-f-]{36}$")))
    // fingerprint cache wiped
    assertFalse(Files.exists(dst.resolve(".strictlykeptboy/repo-fingerprint")))
    // references.toml back-reference written
    val refs = Files.readString(dst.resolve("references.toml"))
    assertTrue("expected back-reference block in $refs", refs.contains("[[reference]]"))
    assertTrue(refs.contains("read_only = true"))
    assertTrue(refs.contains("display_name = \"shared-cal\""))
  }

  @Test fun forkRefusesExistingStrictlyKeptboyDestination() {
    val dst = tmp.newFolder("dst2").toPath()
    Files.createDirectories(dst.resolve(".strictlykeptboy"))
    var threw = false
    try {
      run("--repo", source.toString(), "fork", "--source", source.toString(), dst.toString())
    } catch (e: Throwable) {
      threw = true
    }
    assertTrue("expected fork to throw on populated destination", threw)
  }
}
