package com.eight87.skb.cli.core

/**
 * Tiny TOML writer/reader for the CLI's narrow needs.
 *
 * Why duplicate (not extract `:app`'s `store/TomlWriter.kt`)? — pulling
 * the full hand-rolled codec in would also drag Entities (571 LOC),
 * EntityWriter, RepoBootstrap, etc. The CLI's Phase-X scope only needs
 * to round-trip a fixed shape: TOML frontmatter between `+++` fences,
 * with strings, ints, booleans, datetimes (raw RFC 3339), and
 * homogeneous string arrays. That's well below the threshold where a
 * shared `:core` Gradle module pays back the build-graph complexity.
 * If a Round-3 phase needs the full codec, extract then.
 *
 * SOLID.S: this file does TOML serialization + minimal parsing only.
 * SOLID.O: emitters are pure functions per value-shape; adding a new
 * value-shape adds a new `when` branch in [TomlValue.emit] only.
 */

sealed class TomlValue {
  abstract fun emit(): String
}

data class TomlString(val v: String) : TomlValue() {
  override fun emit(): String = "\"" + v
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\t", "\\t") + "\""
}

data class TomlInt(val v: Long) : TomlValue() {
  override fun emit(): String = v.toString()
}

data class TomlBool(val v: Boolean) : TomlValue() {
  override fun emit(): String = if (v) "true" else "false"
}

/** RFC 3339 datetime emitted bare (no quotes), per TOML offset-datetime spec. */
data class TomlDateTime(val isoOffset: String) : TomlValue() {
  override fun emit(): String = isoOffset
}

data class TomlStringArray(val items: List<String>) : TomlValue() {
  override fun emit(): String = items.joinToString(
    prefix = "[",
    postfix = "]",
    separator = ", ",
  ) { TomlString(it).emit() }
}

/**
 * Ordered key/value map. Insertion order is preserved on emit, which is
 * load-bearing for a stable git diff (DM-E).
 */
class TomlTable {
  private val entries = mutableListOf<Pair<String, TomlValue>>()

  fun put(key: String, value: TomlValue): TomlTable { entries.add(key to value); return this }
  fun putString(key: String, v: String) = put(key, TomlString(v))
  fun putInt(key: String, v: Long) = put(key, TomlInt(v))
  fun putInt(key: String, v: Int) = put(key, TomlInt(v.toLong()))
  fun putBool(key: String, v: Boolean) = put(key, TomlBool(v))
  fun putDateTime(key: String, v: String) = put(key, TomlDateTime(v))
  fun putStringArray(key: String, v: List<String>) = put(key, TomlStringArray(v))

  fun get(key: String): TomlValue? = entries.firstOrNull { it.first == key }?.second
  /** Insertion-ordered view of all (key, value) pairs. */
  fun entries(): List<Pair<String, TomlValue>> = entries.toList()
  fun getString(key: String): String? = (get(key) as? TomlString)?.v
  fun getInt(key: String): Long? = (get(key) as? TomlInt)?.v
  fun getBool(key: String): Boolean? = (get(key) as? TomlBool)?.v
  fun getDateTime(key: String): String? = (get(key) as? TomlDateTime)?.isoOffset
  fun getStringArray(key: String): List<String>? = (get(key) as? TomlStringArray)?.items

  fun emit(): String = buildString {
    for ((k, v) in entries) {
      append(k).append(" = ").append(v.emit()).append('\n')
    }
  }
}

/**
 * Minimal TOML parser — top-level key/value only, no nested tables /
 * arrays-of-tables / inline tables. Enough to read what we wrote.
 * Robust parsers live in `:app`; the CLI reads only files it (or its
 * sibling app) emitted, so this scope is correct.
 */
object MiniToml {
  fun parse(text: String): TomlTable {
    val t = TomlTable()
    for (rawLine in text.lines()) {
      val line = rawLine.trim()
      if (line.isEmpty() || line.startsWith("#")) continue
      if (line.startsWith("[")) continue // skip section headers we don't model
      val eq = line.indexOf('=')
      if (eq < 0) continue
      val key = line.substring(0, eq).trim()
      val rhs = line.substring(eq + 1).trim()
      val v = parseValue(rhs) ?: continue
      t.put(key, v)
    }
    return t
  }

  private fun parseValue(rhs: String): TomlValue? {
    if (rhs.isEmpty()) return null
    if (rhs.startsWith("\"")) {
      // crude unescape; round-trip mirror of TomlString.emit
      val inner = rhs.removePrefix("\"").removeSuffix("\"")
      return TomlString(
        inner.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
      )
    }
    if (rhs == "true") return TomlBool(true)
    if (rhs == "false") return TomlBool(false)
    if (rhs.startsWith("[") && rhs.endsWith("]")) {
      val body = rhs.removePrefix("[").removeSuffix("]").trim()
      if (body.isEmpty()) return TomlStringArray(emptyList())
      val items = splitStringArray(body)
      return TomlStringArray(items)
    }
    val asLong = rhs.toLongOrNull()
    if (asLong != null) return TomlInt(asLong)
    // assume datetime-ish (contains `T` + digits)
    if (rhs.contains('T') && rhs[0].isDigit()) return TomlDateTime(rhs)
    return TomlString(rhs)
  }

  private fun splitStringArray(body: String): List<String> {
    // Items are quoted strings comma-separated. Honours commas inside quotes.
    val out = mutableListOf<String>()
    var i = 0
    while (i < body.length) {
      while (i < body.length && body[i] != '"') i++
      if (i >= body.length) break
      val start = ++i
      val sb = StringBuilder()
      while (i < body.length && body[i] != '"') {
        if (body[i] == '\\' && i + 1 < body.length) { sb.append(body[i + 1]); i += 2 }
        else { sb.append(body[i]); i++ }
      }
      out.add(sb.toString())
      i++ // skip closing "
    }
    return out
  }
}
