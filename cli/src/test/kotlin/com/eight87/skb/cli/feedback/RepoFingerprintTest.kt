package com.eight87.skb.cli.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class RepoFingerprintTest {
  @get:Rule val tmp = TemporaryFolder()

  private class FakeReader(var sha: String?) : GitTreeReader {
    override fun rootTreeSha(repoRoot: Path) = sha
  }

  @Test fun fingerprintIs16HexLowercase() {
    val r = tmp.newFolder("r").toPath()
    val fp = RepoFingerprint.computeOrLoad(r, FakeReader("aa".repeat(20)))
    assertNotNull(fp)
    assertTrue("fingerprint shape: $fp", RepoFingerprint.isValidFingerprint(fp!!))
  }

  @Test fun fingerprintStableAcrossInvocations() {
    val r = tmp.newFolder("r").toPath()
    val reader = FakeReader("a".repeat(40))
    val a = RepoFingerprint.computeOrLoad(r, reader)
    val b = RepoFingerprint.computeOrLoad(r, reader)
    assertEquals(a, b)
    assertTrue(Files.isRegularFile(r.resolve(".strictlykeptboy/repo-fingerprint")))
  }

  @Test fun emptyRepoReturnsNull() {
    val r = tmp.newFolder("r").toPath()
    val fp = RepoFingerprint.computeOrLoad(r, FakeReader(null))
    assertNull(fp)
  }

  @Test fun stableAcrossNonRootChange() {
    val r = tmp.newFolder("r").toPath()
    val reader = FakeReader("X".repeat(40).lowercase())
    val first = RepoFingerprint.computeOrLoad(r, reader)!!
    val second = RepoFingerprint.computeOrLoad(r, reader)
    assertEquals(first, second)
  }

  @Test fun rootRewriteDetected() {
    val r = tmp.newFolder("r").toPath()
    val readerA = FakeReader("a".repeat(40))
    val a = RepoFingerprint.computeOrLoad(r, readerA)!!
    val readerB = FakeReader("b".repeat(40))
    val state = RepoFingerprint.detectRootRewrite(r, readerB)
    assertTrue(state is RootRewriteState.RootRewritten)
    val rewritten = state as RootRewriteState.RootRewritten
    assertEquals(a, rewritten.oldFingerprint)
    assertNotEquals(a, rewritten.newFingerprint)
  }

  @Test fun cacheMissRederives() {
    val r = tmp.newFolder("r").toPath()
    val reader = FakeReader("a".repeat(40))
    val a = RepoFingerprint.computeOrLoad(r, reader)!!
    Files.delete(r.resolve(".strictlykeptboy/repo-fingerprint"))
    val b = RepoFingerprint.computeOrLoad(r, reader)
    assertEquals(a, b)
  }

  @Test fun gitignoreAppendIsIdempotent() {
    val r = tmp.newFolder("r").toPath()
    RepoFingerprint.ensureGitignored(r)
    RepoFingerprint.ensureGitignored(r)
    val gi = Files.readString(r.resolve(".gitignore"))
    val count = gi.lines().count { it.trim() == RepoFingerprint.GITIGNORE_LINE }
    assertEquals(1, count)
  }

  @Test fun globalIdParseAndFormat() {
    val gid = GlobalId.parse("a1b2c3d4e5f60718:01876543-7890-7abc-89ab-cdef01234567")
    assertNotNull(gid)
    assertEquals("a1b2c3d4e5f60718", gid!!.repoFingerprint)
    assertEquals("a1b2c3d4e5f60718:01876543-7890-7abc-89ab-cdef01234567", gid.toString())
    assertNull(GlobalId.parse("a1b2c3d4e5f60718:00000000-0000-0000-0000-000000000000"))
    assertNull(GlobalId.parse("zzz:01876543-7890-7abc-89ab-cdef01234567"))
  }
}
