package com.eight87.strictlykeptboy.store

/**
 * Hand-rolled TOML writer for the value subset enumerated in
 * [TomlValue]. Emits LF line endings (DM-A.3 — we normalise on write).
 *
 * Output ordering is deterministic and stable:
 *   1. top-level scalars in [TomlTable.scalars] insertion order
 *   2. then `[section]` blocks in [TomlTable.sections] insertion order
 *   3. then `[[array-of-tables]]` blocks in [TomlTable.aotables]
 *
 * Strings are emitted as basic-strings with the minimal escape set.
 * Multi-line / literal strings are not produced — the schema never
 * requires them.
 */
object TomlWriter {

    fun emit(table: TomlTable): String {
        val sb = StringBuilder()
        emitInto(sb, table, prefix = "")
        return sb.toString()
    }

    private fun emitInto(sb: StringBuilder, t: TomlTable, prefix: String) {
        for ((k, v) in t.scalars) {
            sb.append(escapeKey(k)).append(" = ").append(formatValue(v)).append('\n')
        }
        for ((name, sub) in t.sections) {
            val header = if (prefix.isEmpty()) name else "$prefix.$name"
            sb.append('\n').append('[').append(header).append(']').append('\n')
            emitInto(sb, sub, header)
        }
        for ((name, list) in t.aotables) {
            val header = if (prefix.isEmpty()) name else "$prefix.$name"
            for (sub in list) {
                sb.append('\n').append("[[").append(header).append("]]").append('\n')
                emitInto(sb, sub, header)
            }
        }
    }

    private fun escapeKey(k: String): String =
        if (k.isNotEmpty() && k.all { it.isLetterOrDigit() || it == '_' || it == '-' }) k
        else "\"" + k.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    internal fun formatValue(v: TomlValue): String = when (v) {
        is TomlValue.Str -> quoteString(v.value)
        is TomlValue.I64 -> v.value.toString()
        is TomlValue.Bool -> v.value.toString()
        is TomlValue.F64 -> {
            val d = v.value
            when {
                d.isNaN() -> "nan"
                d == Double.POSITIVE_INFINITY -> "inf"
                d == Double.NEGATIVE_INFINITY -> "-inf"
                else -> d.toString()
            }
        }
        is TomlValue.LocalDate -> v.text
        is TomlValue.LocalDateTime -> v.text
        is TomlValue.OffsetDateTime -> v.text
        is TomlValue.LocalTime -> v.text
        is TomlValue.Arr -> "[" + v.items.joinToString(", ") { formatValue(it) } + "]"
        is TomlValue.InlineTable -> "{ " + v.entries.entries.joinToString(", ") {
            "${escapeKey(it.key)} = ${formatValue(it.value)}"
        } + " }"
    }

    private fun quoteString(s: String): String {
        val out = StringBuilder(s.length + 2)
        out.append('"')
        for (c in s) {
            when (c) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> {
                    if (c.code < 0x20 || c.code == 0x7F) {
                        out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    } else out.append(c)
                }
            }
        }
        out.append('"')
        return out.toString()
    }
}
