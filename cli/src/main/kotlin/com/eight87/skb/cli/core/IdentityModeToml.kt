package com.eight87.skb.cli.core

import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase DDD.1 / DDD.7 / DM-Y — CLI-side `identity.toml` + `mode.toml`
 * codecs. Mirrors `:app`'s `IdentityTomlCodec` / `ModeTomlCodec` for the
 * narrow shape the CLI needs.
 *
 * Why duplicate? Same rationale as MiniToml.kt — pulling `:app/store`
 * into `:cli` would drag Entities + EntityWriter + RepoBootstrap +
 * Frontmatter + ktoml dependencies in. The shape is fixed and bounded.
 *
 * SOLID.S: only round-trips these two files.
 */
data class CliIdentity(
    val schemaVersion: Int = 1,
    val praiseTerm: String = "good boy",
    val altTerms: List<String> = emptyList(),
    val pronounsSubject: String = "he",
    val pronounsObject: String = "him",
    val pronounsPossessive: String = "his",
    val pronounsReflexive: String = "himself",
    val honorificForDom: String = "Sir",
    val toneRegister: String = "soft-kinky",
    val emojiDensity: String = "medium",
)

object CliIdentityToml {
  const val FILE_NAME = "identity.toml"

  fun emit(d: CliIdentity): String = buildString {
    append("schema_version = ").append(d.schemaVersion).append('\n')
    append("\n[praise]\n")
    append("term = ").append(quote(d.praiseTerm)).append('\n')
    if (d.altTerms.isNotEmpty()) {
      append("alt_terms = ")
      append(d.altTerms.joinToString(prefix = "[", postfix = "]", separator = ", ") { quote(it) })
      append('\n')
    }
    append("\n[pronouns]\n")
    append("subject = ").append(quote(d.pronounsSubject)).append('\n')
    append("object = ").append(quote(d.pronounsObject)).append('\n')
    append("possessive = ").append(quote(d.pronounsPossessive)).append('\n')
    append("reflexive = ").append(quote(d.pronounsReflexive)).append('\n')
    append("\n[honorific_for_dom]\n")
    append("term = ").append(quote(d.honorificForDom)).append('\n')
    append("\n[tone]\n")
    append("register = ").append(quote(d.toneRegister)).append('\n')
    append("emoji_density = ").append(quote(d.emojiDensity)).append('\n')
  }

  /** Section-aware tiny parser sufficient for what [emit] produces. */
  fun parse(text: String): CliIdentity {
    val sections = parseSections(text)
    val root = sections[""] ?: emptyMap()
    val praise = sections["praise"] ?: emptyMap()
    val pron = sections["pronouns"] ?: emptyMap()
    val hon = sections["honorific_for_dom"] ?: emptyMap()
    val tone = sections["tone"] ?: emptyMap()
    return CliIdentity(
      schemaVersion = root["schema_version"]?.toIntOrNull() ?: 1,
      praiseTerm = praise["term"] ?: "good boy",
      altTerms = parseArray(praise["alt_terms"]),
      pronounsSubject = pron["subject"] ?: "he",
      pronounsObject = pron["object"] ?: "him",
      pronounsPossessive = pron["possessive"] ?: "his",
      pronounsReflexive = pron["reflexive"] ?: "himself",
      honorificForDom = hon["term"] ?: "Sir",
      toneRegister = tone["register"] ?: "soft-kinky",
      emojiDensity = tone["emoji_density"] ?: "medium",
    )
  }

  fun read(repoRoot: Path): CliIdentity {
    val p = repoRoot.resolve(FILE_NAME)
    if (!Files.isRegularFile(p)) return CliIdentity()
    return parse(Files.readString(p))
  }

  fun write(repoRoot: Path, d: CliIdentity): Path {
    val p = repoRoot.resolve(FILE_NAME)
    Files.write(p, emit(d).toByteArray(Charsets.UTF_8))
    return p
  }
}

enum class CliRepoMode(val wire: String) {
  Free("free"), StrictlyKept("strictly-kept");
  companion object {
    fun fromWire(s: String?): CliRepoMode = when (s?.trim()) {
      "strictly-kept" -> StrictlyKept
      "free", null -> Free
      else -> throw IllegalArgumentException("Unknown mode: $s")
    }
  }
}

data class CliMode(
    val schemaVersion: Int = 1,
    val mode: CliRepoMode = CliRepoMode.Free,
    val writeBackTarget: String? = null,
    val domPersona: String? = null,
    val domCadence: String? = null,
    val keptSince: String? = null,
)

object CliModeToml {
  const val FILE_NAME = "mode.toml"

  fun emit(d: CliMode): String = buildString {
    append("schema_version = ").append(d.schemaVersion).append('\n')
    append("mode = ").append(quote(d.mode.wire)).append('\n')
    d.writeBackTarget?.let { append("write_back_target = ").append(quote(it)).append('\n') }
    d.domPersona?.let { append("dom_persona = ").append(quote(it)).append('\n') }
    d.domCadence?.let { append("dom_cadence = ").append(quote(it)).append('\n') }
    d.keptSince?.let { append("kept_since = ").append(it).append('\n') }
  }

  fun parse(text: String): CliMode {
    val sections = parseSections(text)
    val root = sections[""] ?: emptyMap()
    return CliMode(
      schemaVersion = root["schema_version"]?.toIntOrNull() ?: 1,
      mode = CliRepoMode.fromWire(root["mode"]),
      writeBackTarget = root["write_back_target"],
      domPersona = root["dom_persona"],
      domCadence = root["dom_cadence"],
      keptSince = root["kept_since"],
    )
  }

  fun read(repoRoot: Path): CliMode {
    val p = repoRoot.resolve(FILE_NAME)
    if (!Files.isRegularFile(p)) return CliMode()
    return parse(Files.readString(p))
  }

  fun write(repoRoot: Path, d: CliMode): Path {
    val p = repoRoot.resolve(FILE_NAME)
    Files.write(p, emit(d).toByteArray(Charsets.UTF_8))
    return p
  }
}

private fun quote(s: String): String =
  "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

private fun unquote(s: String): String {
  val inner = s.trim().removePrefix("\"").removeSuffix("\"")
  return inner.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
}

private fun parseArray(raw: String?): List<String> {
  if (raw == null) return emptyList()
  val body = raw.trim().removePrefix("[").removeSuffix("]").trim()
  if (body.isEmpty()) return emptyList()
  val out = mutableListOf<String>()
  var i = 0
  while (i < body.length) {
    while (i < body.length && body[i] != '"') i++
    if (i >= body.length) break
    val unused = ++i
    val sb = StringBuilder()
    while (i < body.length && body[i] != '"') {
      if (body[i] == '\\' && i + 1 < body.length) { sb.append(body[i + 1]); i += 2 }
      else { sb.append(body[i]); i++ }
    }
    out.add(sb.toString())
    i++
  }
  return out
}

/** Returns map of section-name (empty = root) → key/value map. */
private fun parseSections(text: String): Map<String, Map<String, String>> {
  val out = linkedMapOf<String, MutableMap<String, String>>()
  out[""] = linkedMapOf()
  var current: MutableMap<String, String> = out[""]!!
  for (rawLine in text.lines()) {
    val line = rawLine.trim()
    if (line.isEmpty() || line.startsWith("#")) continue
    if (line.startsWith("[") && line.endsWith("]")) {
      val name = line.removePrefix("[").removeSuffix("]").trim()
      current = out.getOrPut(name) { linkedMapOf() }
      continue
    }
    val eq = line.indexOf('=')
    if (eq < 0) continue
    val key = line.substring(0, eq).trim()
    val rhs = line.substring(eq + 1).trim()
    val value = if (rhs.startsWith("\"")) unquote(rhs) else rhs
    current[key] = value
  }
  return out
}
