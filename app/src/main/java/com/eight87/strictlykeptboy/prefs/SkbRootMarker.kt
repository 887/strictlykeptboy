package com.eight87.strictlykeptboy.prefs

import com.eight87.strictlykeptboy.store.TomlReader
import com.eight87.strictlykeptboy.store.TomlTable
import com.eight87.strictlykeptboy.store.TomlValue
import com.eight87.strictlykeptboy.store.TomlWriter
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Round 2.17.A.3 — `.skb-root` marker file at the parent folder root.
 *
 * Per D-2.17.d. Plain TOML so users / peer agents can hand-edit:
 *
 * ```toml
 * version = 1
 * app_id = "com.eight87.strictlykeptboy"
 * created_at = "2026-05-15T12:34:56Z"
 * created_by = "Pixel 9 Pro"
 * ```
 *
 * Used to:
 *  - detect "this folder is already ours" on re-pick (short-circuits the
 *    `Documents/strictlykeptboy/` create dance),
 *  - drive the Settings "Adopt existing folder" flow,
 *  - refuse to nuke a non-skb folder during destructive Restore
 *    (D-2.17.g).
 */
object SkbRootMarker {

    const val FILE_NAME: String = ".skb-root"
    const val VERSION: Int = 1
    const val APP_ID: String = "com.eight87.strictlykeptboy"

    /**
     * Frozen view of a parsed marker.
     *
     * @param version monotonically-increasing; reader rejects unknown values.
     * @param appId always [APP_ID] for our marker; a foreign app_id is a
     *   parse rejection.
     * @param createdAt ISO-8601 datetime as written.
     * @param createdBy device name at creation time; may be blank if the
     *   author couldn't determine it.
     */
    data class Marker(
        val version: Int,
        val appId: String,
        val createdAt: String,
        val createdBy: String,
    )

    /** Probe whether [parent] currently carries a valid marker. */
    fun isSkbRoot(parent: File): Boolean = read(parent) != null

    /**
     * Read and validate the marker at `<parent>/.skb-root`. Returns
     * `null` when the file is missing, malformed, written by a different
     * app, or carries an unknown schema version.
     */
    fun read(parent: File): Marker? {
        val file = File(parent, FILE_NAME)
        if (!file.isFile) return null
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val table = runCatching { TomlReader.parse(text) }.getOrNull() ?: return null
        val version = (table.scalars["version"] as? TomlValue.I64)?.value?.toInt() ?: return null
        if (version != VERSION) return null
        val appId = (table.scalars["app_id"] as? TomlValue.Str)?.value ?: return null
        if (appId != APP_ID) return null
        val createdAt = (table.scalars["created_at"] as? TomlValue.Str)?.value ?: return null
        val createdBy = (table.scalars["created_by"] as? TomlValue.Str)?.value ?: ""
        return Marker(version, appId, createdAt, createdBy)
    }

    /**
     * Write a fresh marker to `<parent>/.skb-root`. Overwrites any
     * existing marker (which is fine — the only mutable field is
     * `created_at`, and Phase E.5 may re-stamp during a folder move).
     * Creates the parent directory if missing.
     *
     * @param parent the directory that becomes the strictlykeptboy parent.
     * @param deviceName free-form device identifier; trimmed and emitted
     *   as a basic-string. Pass an empty string if unavailable.
     * @param nowIso optional ISO-8601 timestamp override for tests.
     */
    fun write(parent: File, deviceName: String, nowIso: String? = null) {
        parent.mkdirs()
        val createdAt = nowIso ?: OffsetDateTime
            .now(ZoneOffset.UTC)
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val table = TomlTable().apply {
            scalars["version"] = TomlValue.I64(VERSION.toLong())
            scalars["app_id"] = TomlValue.Str(APP_ID)
            scalars["created_at"] = TomlValue.Str(createdAt)
            scalars["created_by"] = TomlValue.Str(deviceName.trim())
        }
        File(parent, FILE_NAME).writeText(TomlWriter.emit(table), Charsets.UTF_8)
    }
}
