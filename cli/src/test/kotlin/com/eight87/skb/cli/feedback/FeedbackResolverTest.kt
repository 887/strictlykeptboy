package com.eight87.skb.cli.feedback

import com.eight87.skb.cli.core.GitOps
import com.eight87.skb.cli.core.Uuid7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

/**
 * Phase YY.9 / FB-I end-to-end fixture builder + cross-repo aggregation
 * + isolation tests (FB-F.5/.6).
 */
class FeedbackResolverTest {
  @get:Rule val tmp = TemporaryFolder()

  private class TestRegistry(reg: RepoRegistry, val subRoot: Path, val domRoot: Path, val subFp: String, val domFp: String, val eventId: String) {
    val registry = reg
  }

  private class FakeGit : GitOps {
    override fun init(path: Path) {}
    override fun addAndCommit(repoRoot: Path, files: List<String>, message: String, authorName: String, authorEmail: String): String? = "abc1234"
    override fun headShortSha(repoRoot: Path): String? = "abc1234"
  }

  private fun setupFixture(): TestRegistry {
    val subRoot = tmp.newFolder("sub").toPath()
    val domRoot = tmp.newFolder("dom").toPath()
    val subFp = "aa".repeat(8)
    val domFp = "bb".repeat(8)
    val eventId = Uuid7.generate()
    val regPath = tmp.newFolder("cfg").toPath().resolve("registry.toml")
    val registry = RepoRegistry.openAt(regPath)
    registry.register(subFp, subRoot, "kept")
    registry.register(domFp, domRoot, "dom-self")
    return TestRegistry(registry, subRoot, domRoot, subFp, domFp, eventId)
  }

  private fun writeFeedbackInDom(t: TestRegistry, reactions: List<String>, body: String = "", replyTo: String? = null, author: String = "Sir"): FeedbackWriter.Outcome {
    val w = FeedbackWriter(
      repoRoot = t.domRoot,
      authorPersonId = author,
      authorRepoFingerprint = t.domFp,
      authorName = author,
      authorEmail = "$author@example.com",
      gitOps = FakeGit(),
    ) { "2026-05-13T10:00:00Z" }
    val target = GlobalId(t.subFp, t.eventId)
    return w.react(target, TargetKind.EVENT, reactions, body, replyTo)
  }

  @Test fun heartCrossRepoSurfacesWithAuthorChip() {
    val t = setupFixture()
    writeFeedbackInDom(t, listOf("heart", "fire"))
    val resolver = FeedbackResolver(t.registry, viewerFp = t.subFp)
    val agg = resolver.aggregate(GlobalId(t.subFp, t.eventId))
    assertEquals(setOf("heart", "fire"), agg.reactionsByToken.keys)
    val author = agg.reactionsByToken["heart"]!!.first()
    assertEquals(t.domFp, author.sourceRepoFingerprint)
    assertEquals("dom-self", author.sourceRepoDisplayName)
  }

  @Test fun isolationHidesHeart_NoCountMasking() {
    val t = setupFixture()
    writeFeedbackInDom(t, listOf("heart"))
    t.registry.isolate(viewerFp = t.subFp, hidden = t.domFp)
    val resolver = FeedbackResolver(t.registry, viewerFp = t.subFp)
    val agg = resolver.aggregate(GlobalId(t.subFp, t.eventId))
    assertTrue("expected zero reactions visible", agg.reactionsByToken.isEmpty())
    val flat = agg.toString()
    assertFalse("must not contain isolated fingerprint: $flat", flat.contains(t.domFp))
    assertFalse("must not contain isolated display name", flat.contains("dom-self"))
  }

  @Test fun isolationIsDirectional() {
    val t = setupFixture()
    writeFeedbackInDom(t, listOf("heart"))
    t.registry.isolate(viewerFp = t.subFp, hidden = t.domFp)
    val domSideResolver = FeedbackResolver(t.registry, viewerFp = t.domFp)
    val agg = domSideResolver.aggregate(GlobalId(t.subFp, t.eventId))
    assertTrue(agg.reactionsByToken.containsKey("heart"))
  }

  @Test fun threadOrderingByCreated() {
    val t = setupFixture()
    val root = writeFeedbackInDom(t, emptyList(), body = "first comment", author = "Sir")
    val replyWriter = FeedbackWriter(
      repoRoot = t.domRoot, authorPersonId = "Sir2", authorRepoFingerprint = t.domFp,
      authorName = "Sir2", authorEmail = "s2@example.com", gitOps = FakeGit(),
    ) { "2026-05-13T11:00:00Z" }
    val target = GlobalId(t.subFp, t.eventId)
    replyWriter.react(target, TargetKind.EVENT, emptyList(), body = "second", replyTo = root.feedbackId)

    val resolver = FeedbackResolver(t.registry, viewerFp = t.subFp)
    val agg = resolver.aggregate(target)
    assertEquals(1, agg.threads.size)
    assertEquals(1, agg.threads[0].replies.size)
    assertEquals("first comment", agg.threads[0].root.body)
    assertEquals("second", agg.threads[0].replies[0].body)
  }

  @Test fun listFeedbackRespectsIsolation_FB_F_5() {
    val t = setupFixture()
    writeFeedbackInDom(t, listOf("heart"))
    t.registry.isolate(viewerFp = t.subFp, hidden = t.domFp)
    val resolver = FeedbackResolver(t.registry, viewerFp = t.subFp)
    val items = resolver.listFeedback(GlobalId(t.subFp, t.eventId))
    assertTrue("expected empty list", items.isEmpty())
  }

  @Test fun replyAlsoVanishesUnderIsolation_FB_F_6() {
    val t = setupFixture()
    val root = writeFeedbackInDom(t, emptyList(), body = "first")
    val replyWriter = FeedbackWriter(
      t.domRoot, "Sir2", t.domFp, "Sir2", "s2@example.com", FakeGit(),
    ) { "2026-05-13T11:00:00Z" }
    val target = GlobalId(t.subFp, t.eventId)
    replyWriter.react(target, TargetKind.EVENT, emptyList(), body = "reply", replyTo = root.feedbackId)
    var agg = FeedbackResolver(t.registry, t.subFp).aggregate(target)
    assertEquals(1, agg.threads.size)
    assertEquals(1, agg.threads[0].replies.size)
    t.registry.isolate(t.subFp, t.domFp)
    agg = FeedbackResolver(t.registry, t.subFp).aggregate(target)
    assertTrue(agg.threads.isEmpty())
  }
}
