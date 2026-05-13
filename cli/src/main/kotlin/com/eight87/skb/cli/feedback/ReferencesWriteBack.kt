package com.eight87.skb.cli.feedback

import com.eight87.skb.cli.core.AtomicWriter
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase YY.8 / FB-H — `references.toml` `write_back_target` field.
 *
 * Schema extension per FB-H.1 LOCK: each reference may carry an
 * optional `write_back_target = "<repo-fingerprint>"` line signalling
 * "this referenced repo is willing to receive feedback files from us,
 * addressed to entities owned by that fingerprint".
 *
 * UI hook (FB-H.2): only references with `write_back_target` set get
 * the "+ react" affordance. Other references are view-only.
 *
 * Opt-in by the REFERENCING repo's author, never by the receiver.
 *
 * This file is intentionally tiny — it manipulates one field on a
 * `references.toml` whose full schema lives in Phase NN / DM-Q. The CLI
 * does block-level read/write without fully parsing the rest of the
 * file (preserves any keys the CLI doesn't model).
 */
object ReferencesWriteBack {

  /**
   * Set or remove a `write_back_target` line on the reference block
   * matching [referenceUrl] inside `<repoRoot>/references.toml`.
   *
   * If the file doesn't exist or doesn't contain a matching reference
   * block, throws [IllegalArgumentException] — the CLI maps that to a
   * `NOT_FOUND` exit code.
   */
  fun setWriteBack(repoRoot: Path, referenceUrl: String, target: String?) {
    if (target != null) require(GlobalId.isValidFingerprint(target)) { "write_back_target must be a 16-hex fingerprint: $target" }
    val file = repoRoot.resolve("references.toml")
    require(Files.isRegularFile(file)) { "references.toml not found in $repoRoot" }
    val lines = Files.readString(file).split('\n').toMutableList()

    // Find the `[[reference]]` block whose `url = "<referenceUrl>"` (or
    // url present in a `remotes = [...]` array) matches.
    var blockStart = -1
    var blockEnd = -1
    var i = 0
    while (i < lines.size) {
      val ln = lines[i].trim()
      if (ln.startsWith("[[reference]]")) {
        // find end of this block (next `[[` or eof)
        var j = i + 1
        while (j < lines.size && !lines[j].trim().startsWith("[[")) j++
        val block = lines.subList(i, j).joinToString("\n")
        if (blockMatches(block, referenceUrl)) {
          blockStart = i
          blockEnd = j
          break
        }
        i = j
      } else i++
    }
    require(blockStart >= 0) { "reference block with url=$referenceUrl not found" }

    // Strip any existing write_back_target line in the block.
    val out = mutableListOf<String>()
    out.addAll(lines.subList(0, blockStart))
    for (k in blockStart until blockEnd) {
      val ln = lines[k]
      if (ln.trim().startsWith("write_back_target")) continue
      out.add(ln)
    }
    // Append the new write_back_target line at the end of the block, if requested.
    if (target != null) {
      // ensure the block ends with a newline before our insert
      // (insertion point = blockEnd, which is the line after the block)
      val insertAt = out.size
      out.add(insertAt, "write_back_target = \"$target\"")
    }
    out.addAll(lines.subList(blockEnd, lines.size))

    AtomicWriter.writeUtf8(file, out.joinToString("\n"))
  }

  private fun blockMatches(block: String, referenceUrl: String): Boolean {
    for (rawLine in block.lines()) {
      val ln = rawLine.trim()
      if (ln.startsWith("url")) {
        val v = ln.substringAfter('=').trim().trim('"')
        if (v == referenceUrl) return true
      }
      if (ln.startsWith("remotes")) {
        // crude scan for the URL inside the array literal
        if (ln.contains("\"$referenceUrl\"")) return true
      }
    }
    return false
  }
}
