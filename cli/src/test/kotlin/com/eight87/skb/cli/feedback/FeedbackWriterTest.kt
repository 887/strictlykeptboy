package com.eight87.skb.cli.feedback

import com.eight87.skb.cli.core.CliError
import com.eight87.skb.cli.core.ExitCode
import com.eight87.skb.cli.core.GitOps
import com.eight87.skb.cli.core.Uuid7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class FeedbackWriterTest {
  @get:Rule val tmp = TemporaryFolder()

  private class FakeGit : GitOps {
    data class Commit(val files: List<String>, val message: String)
    val commits = mutableListOf<Commit>()
    override fun init(path: Path) { /* no-op */ }
    override fun addAndCommit(repoRoot: Path, files: List<String>, message: String, authorName: String, authorEmail: String): String? {
      commits.add(Commit(files, message)); return "abc1234"
    }
    override fun headShortSha(repoRoot: Path): String? = if (commits.isNotEmpty()) "abc1234" else null
  }

  private val fpA = "a".repeat(16)
  private val fpB = "b".repeat(16)

  private fun newWriter(root: Path, git: GitOps = FakeGit(), author: String = "alex"): FeedbackWriter =
    FeedbackWriter(root, author, fpA, "Alex", "alex@example.com", git) { "2026-05-13T10:00:00Z" }

  @Test fun writeFirstFeedbackCreatesFile() {
    val root = tmp.newFolder("repo").toPath()
    val git = FakeGit()
    val w = newWriter(root, git)
    val target = GlobalId(fpB, Uuid7.generate())
    val r = w.react(target, TargetKind.EVENT, listOf("heart"), "", null)
    assertTrue(Files.isRegularFile(r.file))
    assertEquals(1, git.commits.size)
    assertTrue(git.commits[0].message.startsWith("add feedback for"))
    assertFalse(r.replaced)
  }

  @Test fun reactingAgainOverwritesSameFile() {
    val root = tmp.newFolder("repo").toPath()
    val w = newWriter(root)
    val target = GlobalId(fpB, Uuid7.generate())
    val r1 = w.react(target, TargetKind.EVENT, listOf("heart"), "", null)
    val r2 = w.react(target, TargetKind.EVENT, listOf("heart", "fire"), "", null)
    assertEquals(r1.feedbackId, r2.feedbackId)
    assertEquals(r1.file, r2.file)
    assertTrue(r2.replaced)
    val parsed = FeedbackFile.parse(Files.readString(r2.file))!!
    assertEquals(listOf("heart", "fire"), parsed.reactions)
  }

  @Test fun differentAuthorsWriteDifferentFiles() {
    val root = tmp.newFolder("repo").toPath()
    val target = GlobalId(fpB, Uuid7.generate())
    val w1 = newWriter(root, author = "alex")
    val w2 = newWriter(root, author = "bobby")
    val r1 = w1.react(target, TargetKind.EVENT, listOf("heart"), "", null)
    val r2 = w2.react(target, TargetKind.EVENT, listOf("fire"), "", null)
    assertTrue(r1.file != r2.file)
  }

  @Test fun refuseEmptyFeedback() {
    val root = tmp.newFolder("repo").toPath()
    val w = newWriter(root)
    val target = GlobalId(fpB, Uuid7.generate())
    try {
      w.react(target, TargetKind.EVENT, emptyList(), "", null)
      throw AssertionError("expected CliError")
    } catch (e: CliError) { assertEquals(ExitCode.USAGE, e.code) }
  }

  @Test fun refuseDuplicateReactions() {
    val root = tmp.newFolder("repo").toPath()
    val w = newWriter(root)
    val target = GlobalId(fpB, Uuid7.generate())
    try {
      w.react(target, TargetKind.EVENT, listOf("heart", "heart"), "", null)
      throw AssertionError("expected CliError")
    } catch (e: CliError) { assertEquals(ExitCode.USAGE, e.code) }
  }

  @Test fun refuseCrossRepoReply() {
    val root = tmp.newFolder("repo").toPath()
    val w = newWriter(root)
    val target = GlobalId(fpB, Uuid7.generate())
    val nonExistentParent = Uuid7.generate()
    try {
      w.react(target, TargetKind.EVENT, listOf("heart"), "", replyTo = nonExistentParent)
      throw AssertionError("expected NOT_FOUND")
    } catch (e: CliError) { assertEquals(ExitCode.NOT_FOUND, e.code) }
  }

  @Test fun replyToSameRepoParentIsAllowed() {
    val root = tmp.newFolder("repo").toPath()
    val w = newWriter(root)
    val target = GlobalId(fpB, Uuid7.generate())
    val parent = w.react(target, TargetKind.EVENT, emptyList(), "first comment", null)
    val w2 = newWriter(root, author = "bobby")
    val reply = w2.react(target, TargetKind.EVENT, emptyList(), "reply", replyTo = parent.feedbackId)
    val parsedReply = FeedbackFile.parse(Files.readString(reply.file))!!
    assertEquals(parent.feedbackId, parsedReply.replyTo)
  }

  @Test fun removeDeletesFileAndCommits() {
    val root = tmp.newFolder("repo").toPath()
    val git = FakeGit()
    val w = newWriter(root, git)
    val target = GlobalId(fpB, Uuid7.generate())
    val r = w.react(target, TargetKind.EVENT, listOf("heart"), "", null)
    assertTrue(Files.exists(r.file))
    val out = w.remove(target, null)
    assertNotNull(out)
    assertFalse(Files.exists(r.file))
    assertTrue(git.commits.last().message.startsWith("remove feedback for"))
  }

  @Test fun removeIsNoopWhenAbsent() {
    val root = tmp.newFolder("repo").toPath()
    val w = newWriter(root)
    val target = GlobalId(fpB, Uuid7.generate())
    assertNull(w.remove(target, null))
  }

  @Test fun dryRunDoesNotWrite() {
    val root = tmp.newFolder("repo").toPath()
    val git = FakeGit()
    val w = newWriter(root, git)
    val target = GlobalId(fpB, Uuid7.generate())
    val r = w.react(target, TargetKind.EVENT, listOf("heart"), "", null, dryRun = true)
    assertFalse(Files.exists(r.file))
    assertTrue(git.commits.isEmpty())
  }
}
