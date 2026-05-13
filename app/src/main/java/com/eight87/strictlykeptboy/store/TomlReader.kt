package com.eight87.strictlykeptboy.store

/**
 * Minimal TOML reader covering the value subset declared in [TomlValue]
 * — basic strings, bare ints / floats / bools, RFC-3339 dates and
 * datetimes, inline arrays, inline tables, sections (`[a.b]`), and
 * array-of-tables (`[[a.b]]`).
 *
 * Out of scope (and rejected with a parse error if encountered):
 *  - multi-line basic strings (`"""..."""`)
 *  - literal strings (`'...'` / `'''...'''`)
 *  - dotted keys
 *  - hex / octal / binary integers with `_` separators (single-form `_`
 *    in plain decimal IS supported)
 *  - inline tables that span multiple lines
 *
 * This is intentional: our schema (DM-B) is fully under our control,
 * so we constrain the surface to make the round-trip cheap. The
 * validator (DM-H) is where richer constraints live.
 */
class TomlParseException(message: String, val line: Int) : RuntimeException("line $line: $message")

object TomlReader {

    fun parse(text: String): TomlTable {
        val lines = text.split('\n')
        val root = TomlTable()
        var current: TomlTable = root
        var currentPath = ""
        // For array-of-tables: when we open `[[a.b]]`, current points at the
        // newest table; subsequent scalars belong to it.

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            i++
            val line = raw.trimStart()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) continue

            if (line.startsWith("[[")) {
                val end = line.indexOf("]]")
                if (end < 0) throw TomlParseException("unterminated [[ header", i)
                val name = line.substring(2, end).trim()
                val (table, leaf) = ensurePath(root, name, asArrayOfTables = true)
                current = leaf
                currentPath = name
                @Suppress("UNUSED_VARIABLE") val _t = table
                continue
            }
            if (line.startsWith("[")) {
                val end = line.indexOf("]")
                if (end < 0) throw TomlParseException("unterminated [ header", i)
                val name = line.substring(1, end).trim()
                val (_, leaf) = ensurePath(root, name, asArrayOfTables = false)
                current = leaf
                currentPath = name
                continue
            }

            // key = value
            val eq = indexOfTopLevelEq(line)
            if (eq < 0) throw TomlParseException("expected '=' in: $line", i)
            val key = line.substring(0, eq).trim().let { stripBareOrQuoted(it, i) }
            val valStr = stripInlineComment(line.substring(eq + 1).trim())
            val value = parseValue(valStr, i)
            current.scalars[key] = value
        }
        return root
    }

    private fun ensurePath(
        root: TomlTable,
        dotted: String,
        asArrayOfTables: Boolean,
    ): Pair<TomlTable, TomlTable> {
        val parts = dotted.split('.').map { it.trim() }
        if (parts.any { it.isEmpty() }) throw TomlParseException("empty path segment in $dotted", -1)
        var cur = root
        for (j in 0 until parts.size - 1) {
            val name = parts[j]
            // Walk into the current-most array-of-tables element if one
            // exists (e.g. `[[entry.subbeat]]` attaches to the LATEST
            // `[[entry]]`). Falls back to sections for plain `[a.b]`.
            val aoChild = cur.aotables[name]?.lastOrNull()
            cur = aoChild ?: cur.sections.getOrPut(name) { TomlTable() }
        }
        val leafName = parts.last()
        val leaf = if (asArrayOfTables) {
            val list = cur.aotables.getOrPut(leafName) { mutableListOf() }
            val t = TomlTable()
            list += t
            t
        } else {
            cur.sections.getOrPut(leafName) { TomlTable() }
        }
        return cur to leaf
    }

    private fun stripBareOrQuoted(k: String, lineNo: Int): String {
        if (k.startsWith("\"") && k.endsWith("\"") && k.length >= 2) {
            return unquote(k, lineNo)
        }
        return k
    }

    private fun indexOfTopLevelEq(line: String): Int {
        var inStr = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\\' && inStr -> { i += 2; continue }
                c == '"' -> inStr = !inStr
                c == '=' && !inStr -> return i
            }
            i++
        }
        return -1
    }

    private fun stripInlineComment(s: String): String {
        var inStr = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && inStr -> { i += 2; continue }
                c == '"' -> inStr = !inStr
                c == '#' && !inStr -> return s.substring(0, i).trimEnd()
            }
            i++
        }
        return s
    }

    private fun parseValue(s: String, lineNo: Int): TomlValue {
        if (s.isEmpty()) throw TomlParseException("empty value", lineNo)
        val c = s[0]
        return when {
            c == '"' -> TomlValue.Str(unquote(s, lineNo))
            s == "true" -> TomlValue.Bool(true)
            s == "false" -> TomlValue.Bool(false)
            c == '[' -> parseArray(s, lineNo)
            c == '{' -> parseInlineTable(s, lineNo)
            isDateLike(s) -> classifyDate(s)
            isNumeric(s) -> parseNumber(s, lineNo)
            else -> throw TomlParseException("unrecognised value: $s", lineNo)
        }
    }

    private fun isDateLike(s: String): Boolean =
        s.length >= 8 && s[4] == '-' && s[7] == '-' && s[0].isDigit()

    private fun classifyDate(s: String): TomlValue {
        // 2026-05-12             → LocalDate
        // 2026-05-12T14:00:00    → LocalDateTime
        // 2026-05-12 14:00:00    → LocalDateTime (TOML allows space)
        // 2026-05-12T14:00:00Z   → OffsetDateTime
        // 2026-05-12T14:00:00+02:00 → OffsetDateTime
        if (s.length == 10) return TomlValue.LocalDate(s)
        val hasOffset = s.endsWith("Z") || run {
            // search after position 10 for +/- offset
            val tail = s.substring(10)
            tail.contains('+') || tail.lastIndexOf('-') > 0
        }
        // TOML allows space between date and time; normalise to 'T'.
        val canonical = if (s.length > 10 && s[10] == ' ') s.substring(0, 10) + "T" + s.substring(11) else s
        return if (hasOffset) TomlValue.OffsetDateTime(canonical) else TomlValue.LocalDateTime(canonical)
    }

    private fun isNumeric(s: String): Boolean {
        val first = s[0]
        return first.isDigit() || first == '-' || first == '+'
    }

    private fun parseNumber(s: String, lineNo: Int): TomlValue {
        val cleaned = s.replace("_", "")
        if (cleaned.contains('.') || cleaned.contains('e') || cleaned.contains('E')) {
            return TomlValue.F64(cleaned.toDoubleOrNull()
                ?: throw TomlParseException("bad float $s", lineNo))
        }
        return TomlValue.I64(cleaned.toLongOrNull()
            ?: throw TomlParseException("bad int $s", lineNo))
    }

    private fun unquote(s: String, lineNo: Int): String {
        if (!s.startsWith("\"") || !s.endsWith("\"") || s.length < 2) {
            throw TomlParseException("unterminated string $s", lineNo)
        }
        val body = s.substring(1, s.length - 1)
        val out = StringBuilder(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\' && i + 1 < body.length) {
                when (val esc = body[i + 1]) {
                    '\\' -> out.append('\\')
                    '"' -> out.append('"')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    '/' -> out.append('/')
                    'u' -> {
                        if (i + 5 >= body.length + 1) throw TomlParseException("short \\u", lineNo)
                        val hex = body.substring(i + 2, i + 6)
                        out.append(hex.toInt(16).toChar())
                        i += 4
                    }
                    else -> throw TomlParseException("unknown escape \\$esc", lineNo)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun parseArray(s: String, lineNo: Int): TomlValue.Arr {
        if (!s.endsWith("]")) throw TomlParseException("unterminated array $s", lineNo)
        val inner = s.substring(1, s.length - 1).trim()
        if (inner.isEmpty()) return TomlValue.Arr(emptyList())
        val parts = splitTopLevel(inner, ',', lineNo)
        return TomlValue.Arr(parts.map { parseValue(it.trim(), lineNo) })
    }

    private fun parseInlineTable(s: String, lineNo: Int): TomlValue.InlineTable {
        if (!s.endsWith("}")) throw TomlParseException("unterminated inline table $s", lineNo)
        val inner = s.substring(1, s.length - 1).trim()
        val map = LinkedHashMap<String, TomlValue>()
        if (inner.isEmpty()) return TomlValue.InlineTable(map)
        val parts = splitTopLevel(inner, ',', lineNo)
        for (p in parts) {
            val eq = indexOfTopLevelEq(p)
            if (eq < 0) throw TomlParseException("inline table entry without '=': $p", lineNo)
            val k = stripBareOrQuoted(p.substring(0, eq).trim(), lineNo)
            val v = parseValue(p.substring(eq + 1).trim(), lineNo)
            map[k] = v
        }
        return TomlValue.InlineTable(map)
    }

    /** Splits on top-level [delim], ignoring delimiters inside `"..."`, `[...]`, or `{...}`. */
    private fun splitTopLevel(s: String, delim: Char, lineNo: Int): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var inStr = false
        var bracket = 0
        var brace = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' && inStr -> { cur.append(c); if (i + 1 < s.length) { cur.append(s[i + 1]); i++ } }
                c == '"' -> { inStr = !inStr; cur.append(c) }
                !inStr && c == '[' -> { bracket++; cur.append(c) }
                !inStr && c == ']' -> { bracket--; cur.append(c) }
                !inStr && c == '{' -> { brace++; cur.append(c) }
                !inStr && c == '}' -> { brace--; cur.append(c) }
                !inStr && c == delim && bracket == 0 && brace == 0 -> {
                    out += cur.toString(); cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        if (inStr || bracket != 0 || brace != 0) throw TomlParseException("unbalanced delimiters", lineNo)
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }
}
