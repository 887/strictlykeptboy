package com.eight87.strictlykeptboy.store

/**
 * Splits and joins TOML-frontmatter + Markdown-body documents per
 * DM-A.2 / DM-A.3.
 *
 * Strict fence rule: the file MUST start with `+++` on the first line
 * (LF or CRLF). The closing fence is a line containing exactly `+++`.
 * Files not starting with `+++` are returned as
 * [FrontmatterDoc.Kind.NoFrontmatter] with the whole file as body and
 * an empty table — tolerant for hand-written notes; the validator
 * decides whether to surface as a broken entry.
 */
object FrontmatterReader {

    private const val FENCE = "+++"

    fun parse(text: String): FrontmatterDoc {
        val normalised = text.replace("\r\n", "\n")
        if (!normalised.startsWith("$FENCE\n") && normalised != FENCE && !normalised.startsWith("$FENCE\r")) {
            return FrontmatterDoc(TomlTable(), text, FrontmatterDoc.Kind.NoFrontmatter)
        }
        val afterOpen = normalised.substring(FENCE.length + 1)
        val close = findClosingFence(afterOpen)
            ?: return FrontmatterDoc(TomlTable(), text, FrontmatterDoc.Kind.MalformedFrontmatter)
        val toml = afterOpen.substring(0, close.start)
        val body = afterOpen.substring(close.end).let { rest ->
            // Drop a single leading newline after the closing fence if present.
            if (rest.startsWith("\n")) rest.drop(1) else rest
        }
        val table = try {
            TomlReader.parse(toml)
        } catch (t: TomlParseException) {
            return FrontmatterDoc(TomlTable(), text, FrontmatterDoc.Kind.MalformedFrontmatter)
        }
        return FrontmatterDoc(table, body, FrontmatterDoc.Kind.Ok)
    }

    private data class FenceMatch(val start: Int, val end: Int)

    private fun findClosingFence(s: String): FenceMatch? {
        var i = 0
        while (i < s.length) {
            val nl = s.indexOf('\n', i)
            val lineEnd = if (nl < 0) s.length else nl
            val line = s.substring(i, lineEnd)
            if (line == FENCE) return FenceMatch(i, lineEnd)
            if (nl < 0) return null
            i = nl + 1
        }
        return null
    }
}

/**
 * Serialises a [FrontmatterDoc] back to disk bytes. LF endings, single
 * trailing newline on the body. Body is emitted exactly as supplied
 * (the caller is responsible for any body normalisation).
 */
object FrontmatterWriter {

    private const val FENCE = "+++"

    fun serialize(doc: FrontmatterDoc): String {
        val sb = StringBuilder()
        sb.append(FENCE).append('\n')
        val toml = TomlWriter.emit(doc.frontmatter)
        sb.append(toml)
        if (!toml.endsWith("\n")) sb.append('\n')
        sb.append(FENCE).append('\n')
        val body = doc.body
        if (body.isNotEmpty()) {
            sb.append(body)
            if (!body.endsWith("\n")) sb.append('\n')
        }
        return sb.toString()
    }
}
