package com.eight87.skb.cli.core

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Atomic write per CLI-B.2 / DM-E.1.
 *
 * Strategy: write to `<dir>/.<id>.<ext>.tmp.<pid>.<nano>`, then
 * `Files.move(tmp, target, ATOMIC_MOVE, REPLACE_EXISTING)`. Falls back
 * to non-atomic REPLACE_EXISTING if the filesystem doesn't support
 * ATOMIC_MOVE (documented limitation; same trade-off `:app`'s
 * EntityWriter makes — see store/EntityWriter.kt for the parallel
 * implementation, kept intentionally separate to avoid an Android-leaf
 * dep here).
 */
object AtomicWriter {
  fun writeUtf8(target: Path, text: String) {
    Files.createDirectories(target.parent)
    val pid = ProcessHandle.current().pid()
    val tmp = target.resolveSibling(
      "." + target.fileName.toString() + ".tmp.$pid." + System.nanoTime()
    )
    Files.write(tmp, text.toByteArray(StandardCharsets.UTF_8))
    try {
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: UnsupportedOperationException) {
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }
}
