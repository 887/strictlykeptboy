package com.eight87.strictlykeptboy.avatar

import java.security.MessageDigest

/**
 * Phase WW.4 — pack-id derivation (D.69).
 *
 * Mirrors the D.51 source-repo-id discipline: SHA-256 of the normalized
 * clone URL, truncated to 16 hex chars. Same URL → same id across
 * devices / processes / app reinstalls, so user-data files referring to
 * pack ids stay valid.
 *
 * Normalization (closest-fit to D.51):
 *   - lowercase
 *   - strip trailing `/` and `.git` if present
 *   - leading/trailing whitespace removed
 */
object PackId {

    /** Derive a 16-hex pack id from a clone URL (or any stable string). */
    fun fromCloneUrl(url: String): String {
        val normalized = normalize(url)
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(64)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            hex.append(HEX[(v ushr 4) and 0xF])
            hex.append(HEX[v and 0xF])
        }
        return hex.substring(0, 16)
    }

    /** Default-pack ids: stable, human-readable, namespaced by `default-`. */
    fun forBundledSpecies(species: String): String = DEFAULT_PACK_ID_PREFIX + species.lowercase()

    internal fun normalize(url: String): String {
        var s = url.trim().lowercase()
        // Iteratively strip trailing slashes and a `.git` suffix until
        // neither matches. Handles e.g. `…/bar.git/` and `…/bar/.git`.
        var changed = true
        while (changed) {
            changed = false
            while (s.endsWith("/")) { s = s.dropLast(1); changed = true }
            if (s.endsWith(".git")) { s = s.dropLast(4); changed = true }
        }
        return s
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
