package com.eight87.strictlykeptboy.store

/**
 * Parsed shape of a TOML-frontmatter + Markdown-body file.
 *
 * Per DM-A.1. Bodies retain their exact byte content (modulo EOL
 * normalisation on write per DM-A.3).
 *
 * @param frontmatter the parsed TOML table (empty when `kind == NoFrontmatter`)
 * @param body the markdown body, exactly as it appeared after the closing fence
 * @param kind one of [Kind] — used by the validator (DM-H) to surface broken entries
 */
data class FrontmatterDoc(
    val frontmatter: TomlTable,
    val body: String,
    val kind: Kind = Kind.Ok,
) {
    enum class Kind { Ok, NoFrontmatter, MalformedFrontmatter }
}
