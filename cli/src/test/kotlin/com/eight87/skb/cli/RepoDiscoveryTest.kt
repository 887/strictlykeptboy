package com.eight87.skb.cli

import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.RepoDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class RepoDiscoveryTest {
  @get:Rule val tmp = TemporaryFolder()

  @Test fun explicitRepoOverrides() {
    val repo = tmp.newFolder("r").toPath()
    Files.createDirectories(repo.resolve(".strictlykeptboy"))
    val result = RepoDiscovery.discover(explicit = repo.toString(), env = emptyMap(), cwd = tmp.root.toPath())
    assertEquals(repo.toAbsolutePath(), result.toAbsolutePath())
  }

  @Test fun envVarWins() {
    val repo = tmp.newFolder("r").toPath()
    Files.createDirectories(repo.resolve(".strictlykeptboy"))
    val result = RepoDiscovery.discover(
      explicit = null,
      env = mapOf("SKB_REPO" to repo.toString()),
      cwd = tmp.root.toPath(),
    )
    assertEquals(repo.toAbsolutePath(), result.toAbsolutePath())
  }

  @Test fun walksUpFromCwd() {
    val repo = tmp.newFolder("r").toPath()
    Files.createDirectories(repo.resolve(".strictlykeptboy"))
    val deep = repo.resolve("a/b/c").also { Files.createDirectories(it) }
    val result = RepoDiscovery.discover(explicit = null, env = emptyMap(), cwd = deep)
    assertEquals(repo.toAbsolutePath(), result.toAbsolutePath())
  }

  @Test fun notFoundThrowsExit2() {
    val cwd = tmp.newFolder("isolated").toPath()
    try {
      RepoDiscovery.discover(explicit = null, env = emptyMap(), cwd = cwd, home = cwd.parent)
      fail("expected CliError")
    } catch (e: CliError) {
      assertEquals(ExitCode.NOT_FOUND, e.code)
    }
  }

  @Test fun explicitNonRepoThrowsCorrupt() {
    val empty = tmp.newFolder("empty").toPath()
    try {
      RepoDiscovery.discover(explicit = empty.toString(), env = emptyMap(), cwd = empty)
      fail("expected CliError")
    } catch (e: CliError) {
      assertEquals(ExitCode.CORRUPT, e.code)
    }
  }
}
