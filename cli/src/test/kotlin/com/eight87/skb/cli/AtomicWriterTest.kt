package com.eight87.skb.cli

import com.eight87.skb.cli.core.AtomicWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class AtomicWriterTest {
  @get:Rule val tmp = TemporaryFolder()

  @Test fun createsParentDirsAndWritesText() {
    val target = tmp.root.toPath().resolve("a/b/c/file.md")
    AtomicWriter.writeUtf8(target, "hello\n")
    assertEquals("hello\n", Files.readString(target))
  }

  @Test fun overwritesExistingFile() {
    val target = tmp.root.toPath().resolve("x.md")
    AtomicWriter.writeUtf8(target, "v1\n")
    AtomicWriter.writeUtf8(target, "v2\n")
    assertEquals("v2\n", Files.readString(target))
  }

  @Test fun noTmpFileLeftBehind() {
    val target = tmp.root.toPath().resolve("y.md")
    AtomicWriter.writeUtf8(target, "ok\n")
    val leftovers = Files.list(target.parent).use { s -> s.filter { it.fileName.toString().contains(".tmp.") }.count() }
    assertFalse(leftovers > 0)
  }
}
