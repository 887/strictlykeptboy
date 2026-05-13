package com.eight87.skb.cli.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.time.LocalDate

class BonusJournalRefTest {
  @get:Rule val tmp = TemporaryFolder()

  @Test fun bonusTaskCarriesBonusTrueAndOptional() {
    val text = BonusLayout.serialize(
      id = "01876543-7890-7abc-89ab-cdef01234567",
      title = "polish the leash",
      assignedBy = "Sir",
      createdIso = "2026-05-13T10:00:00Z",
      due = "2026-05-15",
    )
    assertTrue(text.contains("bonus = true"))
    assertTrue(text.contains("optional = true"))
    assertTrue(text.contains("kind = \"bonus_task\""))
    assertTrue(text.contains("due = \"2026-05-15\""))
  }

  @Test fun journalSecondEntrySameDayGetsNumericSuffix() {
    val root = tmp.newFolder("r").toPath()
    val date = LocalDate.of(2026, 5, 13)
    val p1 = JournalLayout.nextFilePath(root, date)
    JournalLayout.writeAtomic(root, p1, JournalLayout.serialize("01876543-7890-7abc-89ab-cdef01234567", "alex", "2026-05-13T10:00:00Z", "first"))
    val p2 = JournalLayout.nextFilePath(root, date)
    JournalLayout.writeAtomic(root, p2, JournalLayout.serialize("01876543-7890-7abc-89ab-cdef01234568", "alex", "2026-05-13T11:00:00Z", "second"))
    val p3 = JournalLayout.nextFilePath(root, date)
    assertEquals("journal/2026-05-13.md", root.relativize(p1).toString().replace('\\', '/'))
    assertEquals("journal/2026-05-13-2.md", root.relativize(p2).toString().replace('\\', '/'))
    assertEquals("journal/2026-05-13-3.md", root.relativize(p3).toString().replace('\\', '/'))
  }

  @Test fun referencesWriteBackAddAndRemove() {
    val root = tmp.newFolder("r").toPath()
    val refsFile = root.resolve("references.toml")
    Files.writeString(refsFile, """
      schema_version = 1

      [[reference]]
      url = "https://example.com/sub.git"
      display_name = "kept"

      [[reference]]
      url = "https://example.com/other.git"
      display_name = "other"
    """.trimIndent())

    val target = "a".repeat(16)
    ReferencesWriteBack.setWriteBack(root, "https://example.com/sub.git", target)
    val after = Files.readString(refsFile)
    assertTrue(after.contains("write_back_target = \"$target\""))

    ReferencesWriteBack.setWriteBack(root, "https://example.com/sub.git", null)
    val cleaned = Files.readString(refsFile)
    assertFalse(cleaned.contains("write_back_target"))
  }

  @Test fun referencesWriteBackRejectsMissingReferenceBlock() {
    val root = tmp.newFolder("r").toPath()
    val refsFile = root.resolve("references.toml")
    Files.writeString(refsFile, "schema_version = 1\n")
    try {
      ReferencesWriteBack.setWriteBack(root, "https://example.com/missing.git", "a".repeat(16))
      throw AssertionError("expected IAE")
    } catch (_: IllegalArgumentException) { /* ok */ }
  }
}
