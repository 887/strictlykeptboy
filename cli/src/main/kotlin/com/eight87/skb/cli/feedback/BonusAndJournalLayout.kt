package com.eight87.skb.cli.feedback

import com.eight87.skb.cli.core.AtomicWriter
import com.eight87.skb.cli.core.Frontmatter
import com.eight87.skb.cli.core.TomlTable
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Phase YY.7 / FB-G — path + writer helpers for `bonus/` tasks and
 * `journal/` entries. These specialised entities ride on the same FB-C
 * feedback machinery for reactions/comments (they each carry an entity
 * UUIDv7 so they map cleanly to global-IDs).
 *
 * Per FB-G LOCK:
 *  - `bonus/<task-uuid>.md` lives at calendar-repo root in a SHARED
 *    repo. The `bonus = true` frontmatter overrides scheduling
 *    pressure (UI never promotes it to dated tasks even with `due`).
 *  - `journal/<yyyy-mm-dd>.md` (or `<yyyy-mm-dd>-<n>.md`) at repo root.
 *    UUIDv7 in frontmatter; the date in the filename is for human
 *    navigation only. Multiple entries per day allowed via numeric
 *    suffix. NOT included in the schedule overlay — they get their
 *    own nav-rail tab.
 *
 * SOLID.S: pure layout + serialization. No git commit hook here; the
 * caller (CLI command) composes the message and commits via [GitOps].
 */
object BonusLayout {
  fun file(repoRoot: Path, taskId: String): Path = repoRoot.resolve("bonus/$taskId.md")

  /**
   * Serialize a bonus task per FB-G.1 LOCK. `bonus = true`, `optional =
   * true`. `assigned_by` is the dom's identity id; the dom's repo
   * fingerprint isn't needed in the frontmatter because the file lives
   * in the shared repo whose fingerprint IS the address.
   */
  fun serialize(
    id: String,
    title: String,
    assignedBy: String,
    createdIso: String,
    due: String? = null,
    priority: String? = null,
    body: String = "",
  ): String {
    val t = TomlTable().apply {
      putInt("schema_version", 1)
      putString("id", id)
      putString("kind", "bonus_task")
      putString("title", title)
      putBool("bonus", true)
      putBool("optional", true)
      putString("assigned_by", assignedBy)
      putDateTime("created", createdIso)
      due?.let { putString("due", it) }
      priority?.let { putString("priority", it) }
    }
    return Frontmatter.serialize(t, body)
  }

  fun writeAtomic(repoRoot: Path, taskId: String, content: String) {
    val f = file(repoRoot, taskId)
    Files.createDirectories(f.parent)
    AtomicWriter.writeUtf8(f, content)
  }
}

object JournalLayout {
  /**
   * Returns the next available journal path for [date] in [repoRoot].
   * If `journal/<date>.md` does not exist, returns that. Else returns
   * `journal/<date>-2.md`, `journal/<date>-3.md`, ... (FB-G.6).
   */
  fun nextFilePath(repoRoot: Path, date: LocalDate): Path {
    val d = DateTimeFormatter.ISO_LOCAL_DATE.format(date)
    val base = repoRoot.resolve("journal/$d.md")
    if (!Files.exists(base)) return base
    var n = 2
    while (true) {
      val candidate = repoRoot.resolve("journal/$d-$n.md")
      if (!Files.exists(candidate)) return candidate
      n++
    }
  }

  fun serialize(
    id: String,
    author: String,
    createdIso: String,
    body: String,
  ): String {
    val t = TomlTable().apply {
      putInt("schema_version", 1)
      putString("id", id)
      putString("kind", "journal_entry")
      putString("author", author)
      putDateTime("created", createdIso)
    }
    return Frontmatter.serialize(t, body)
  }

  fun writeAtomic(repoRoot: Path, target: Path, content: String) {
    Files.createDirectories(target.parent)
    AtomicWriter.writeUtf8(target, content)
  }
}
