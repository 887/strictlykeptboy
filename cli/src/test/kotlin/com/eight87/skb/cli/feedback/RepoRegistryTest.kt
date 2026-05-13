package com.eight87.skb.cli.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

class RepoRegistryTest {
  @get:Rule val tmp = TemporaryFolder()

  private fun openReg(): RepoRegistry {
    val p = tmp.newFolder("cfg").toPath().resolve("registry.toml")
    return RepoRegistry.openAt(p)
  }

  private val fpA = "a".repeat(16)
  private val fpB = "b".repeat(16)
  private val fpC = "c".repeat(16)

  @Test fun emptyOnFirstRead() {
    val r = openReg()
    assertTrue(r.list().isEmpty())
  }

  @Test fun registerAndRetrieveIsRoundTrip() {
    val r = openReg()
    r.register(fpA, Path.of("/tmp/a"), "alpha")
    r.register(fpB, Path.of("/tmp/b"), "beta")
    val list = r.list()
    assertEquals(2, list.size)
    assertEquals("alpha", r.byFingerprint(fpA)!!.displayName)
  }

  @Test fun reopenPersistsEntries() {
    val path = tmp.newFolder("cfg").toPath().resolve("registry.toml")
    val r1 = RepoRegistry.openAt(path)
    r1.register(fpA, Path.of("/tmp/a"), "alpha")
    val r2 = RepoRegistry.openAt(path)
    assertEquals(1, r2.list().size)
    assertEquals(fpA, r2.list().first().fingerprint)
  }

  @Test fun asymmetricIsolation_FB_F_5() {
    val r = openReg()
    r.register(fpA, Path.of("/a"), "A")
    r.register(fpB, Path.of("/b"), "B")
    r.register(fpC, Path.of("/c"), "C")

    r.isolate(viewerFp = fpC, hidden = fpA)
    val visibleToC = r.allVisibleTo(fpC).map { it.fingerprint }.toSet()
    assertFalse(visibleToC.contains(fpA))
    assertTrue(visibleToC.contains(fpB))
    assertTrue(visibleToC.contains(fpC))
    val visibleToA = r.allVisibleTo(fpA).map { it.fingerprint }.toSet()
    assertTrue(visibleToA.contains(fpC))
  }

  @Test fun unisolateRestoresVisibility() {
    val r = openReg()
    r.register(fpA, Path.of("/a"), "A")
    r.register(fpB, Path.of("/b"), "B")
    r.isolate(fpA, fpB)
    assertFalse(r.allVisibleTo(fpA).any { it.fingerprint == fpB })
    r.unisolate(fpA, fpB)
    assertTrue(r.allVisibleTo(fpA).any { it.fingerprint == fpB })
  }

  @Test fun unregisterRemovesEntry() {
    val r = openReg()
    r.register(fpA, Path.of("/a"), "A")
    r.unregister(fpA)
    assertTrue(r.list().isEmpty())
    assertNull(r.byFingerprint(fpA))
  }

  @Test fun isolateRejectsSelf() {
    val r = openReg()
    r.register(fpA, Path.of("/a"), "A")
    try {
      r.isolate(fpA, fpA)
      throw AssertionError("expected IllegalArgumentException")
    } catch (_: IllegalArgumentException) { /* ok */ }
  }
}
