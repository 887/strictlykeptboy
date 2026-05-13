package com.eight87.skb.cli.feedback

import com.eight87.skb.cli.core.Uuid7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackFileTest {
  private val fpA = "a".repeat(16)
  private val fpB = "b".repeat(16)

  @Test fun serializeRoundTrip() {
    val id = Uuid7.generate()
    val entityId = Uuid7.generate()
    val target = GlobalId(fpA, entityId)
    val fb = FeedbackFile(
      id = id,
      target = target,
      targetKind = TargetKind.EVENT,
      author = "alex",
      authorRepo = fpB,
      created = "2026-05-13T10:00:00Z",
      reactions = listOf("heart", "fire"),
      body = "good boy",
    )
    val text = fb.serialize()
    assertTrue(text.contains("schema_version = 1"))
    assertTrue(text.contains("target = \"$target\""))
    assertTrue(text.contains("target_kind = \"event\""))
    val parsed = FeedbackFile.parse(text)
    assertEquals(fb, parsed)
  }

  @Test fun parseReturnsNullOnMalformedTarget() {
    val text = """
      +++
      schema_version = 1
      id = "01876543-7890-7abc-89ab-cdef01234567"
      target = "not-a-global-id"
      target_kind = "event"
      author = "x"
      author_repo = "${"a".repeat(16)}"
      created = 2026-05-13T10:00:00Z
      reactions = []
      +++
    """.trimIndent()
    assertNull(FeedbackFile.parse(text))
  }

  @Test fun rejectsInvalidFeedbackId() {
    try {
      FeedbackFile(
        id = "not-a-uuid",
        target = GlobalId(fpA, Uuid7.generate()),
        targetKind = TargetKind.EVENT,
        author = "a",
        authorRepo = fpB,
        created = "2026-05-13T10:00:00Z",
        reactions = listOf("heart"),
      )
      throw AssertionError("expected IAE")
    } catch (_: IllegalArgumentException) { /* ok */ }
  }
}
