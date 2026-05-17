package com.eight87.strictlykeptboy.backup

import java.util.Locale

/**
 * Round 2.28 / SOLID #8b — extracted from MainActivity.
 *
 * Round 2.17 Phase F.3 — format a byte count for the export Toast.
 * Small, base-10 (matches what file managers display).
 */
internal fun humanBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = n.toDouble() / 1024.0
    var i = 0
    while (v >= 1024.0 && i < units.size - 1) { v /= 1024.0; i++ }
    return String.format(Locale.ROOT, "%.1f %s", v, units[i])
}
