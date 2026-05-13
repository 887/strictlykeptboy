package com.eight87.skb.cli.core

/**
 * Serializes a `+++`-fenced TOML frontmatter + Markdown body file per
 * DM-D. Body may be empty; both an opening and closing fence are
 * always written, even for an empty body, so the file shape is stable.
 */
object Frontmatter {
  fun serialize(table: TomlTable, body: String = ""): String = buildString {
    append("+++\n")
    append(table.emit())
    append("+++\n")
    if (body.isNotEmpty()) {
      if (!body.startsWith("\n")) append('\n')
      append(body)
      if (!body.endsWith("\n")) append('\n')
    }
  }

  /** Returns (table, body). Body excludes the trailing fence newline. */
  fun parse(text: String): Pair<TomlTable, String> {
    val lines = text.lines()
    if (lines.isEmpty() || lines[0].trim() != "+++") {
      return TomlTable() to text
    }
    val end = (1 until lines.size).firstOrNull { lines[it].trim() == "+++" } ?: return TomlTable() to text
    val tomlBlock = lines.subList(1, end).joinToString("\n")
    val body = if (end + 1 < lines.size) lines.subList(end + 1, lines.size).joinToString("\n").trimStart('\n') else ""
    return MiniToml.parse(tomlBlock) to body
  }
}
