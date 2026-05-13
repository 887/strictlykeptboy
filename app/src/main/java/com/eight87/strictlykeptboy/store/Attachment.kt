package com.eight87.strictlykeptboy.store

/**
 * Phase BBB.5 / DM-W — Event attachment schema.
 *
 * Six locked sealed kinds (DM-W.2): `link`, `qr`, `file`, `barcode`,
 * `vcard`, `location`. Discriminator field on disk is `kind`.
 *
 * Storage convention (DM-W.3): asset files live at
 * `attachments/<event-id>/<filename>` (repo-relative). Files >100 KB
 * MUST be tracked via Git-LFS (Phase Z) — see [LFS_THRESHOLD_BYTES].
 *
 * Privacy inheritance (DM-W.4): the host event's `private = true` flag
 * propagates to every attachment; lockscreen previews suppress the
 * attachment indicator entirely when private (per D.57).
 *
 * SOLID-S: this file's single responsibility is the
 * attachment-array codec on event frontmatter. SOLID-O: adding a new
 * attachment kind = new sealed-subclass + new `when` branch in
 * [Attachment.fromTable] / [Attachment.writeInto]; no other call-sites
 * change. SOLID-D: pure Kotlin, no Android imports.
 *
 * Round-trip is exercised by `AttachmentParseTest`.
 */
sealed interface Attachment {

    /** TOML discriminator value. */
    val kind: String

    /** External URL link (`kind = "link"`). */
    data class Link(val url: String, val label: String? = null) : Attachment {
        override val kind: String get() = KIND
        companion object { const val KIND = "link" }
    }

    /**
     * QR code (`kind = "qr"`). Either an inline `data` payload, an
     * on-disk `file` reference (`attachments/<event-id>/...`), or both.
     */
    data class Qr(val file: String? = null, val data: String? = null) : Attachment {
        init {
            require(file != null || data != null) { "qr attachment needs file or data" }
        }
        override val kind: String get() = KIND
        companion object { const val KIND = "qr" }
    }

    /** Arbitrary file attachment with declared `mime_type` and `size_bytes`. */
    data class File(
        val file: String,
        val mimeType: String,
        val sizeBytes: Long,
        val description: String? = null,
    ) : Attachment {
        override val kind: String get() = KIND
        companion object { const val KIND = "file" }
    }

    /** Boarding-pass / parcel-track barcode (`kind = "barcode"`). */
    data class Barcode(
        val file: String,
        val format: BarcodeFormat,
        val data: String,
    ) : Attachment {
        override val kind: String get() = KIND
        companion object { const val KIND = "barcode" }
    }

    /** vCard / contact card (`kind = "vcard"`). */
    data class VCard(val file: String) : Attachment {
        override val kind: String get() = KIND
        companion object { const val KIND = "vcard" }
    }

    /** Geographic point (`kind = "location"`). */
    data class Location(
        val lat: Double,
        val lon: Double,
        val label: String? = null,
    ) : Attachment {
        init {
            require(lat in -90.0..90.0) { "lat $lat outside ±90" }
            require(lon in -180.0..180.0) { "lon $lon outside ±180" }
        }
        override val kind: String get() = KIND
        companion object { const val KIND = "location" }
    }

    enum class BarcodeFormat(val tomlValue: String) {
        Aztec("aztec"),
        Pdf417("pdf417"),
        Code128("code128");
        companion object {
            fun fromToml(s: String?): BarcodeFormat? = entries.firstOrNull { it.tomlValue == s }
        }
    }

    companion object {
        /** DM-W.3: files larger than this MUST be Git-LFS'd. */
        const val LFS_THRESHOLD_BYTES: Long = 100 * 1024L

        /**
         * Parse a single attachment table (one `[[attachment]]` entry).
         * Returns null when the table is malformed (caller logs / warns).
         */
        fun fromTable(t: TomlTable): Attachment? {
            return when (t.getString("kind")) {
                Link.KIND -> {
                    val url = t.getString("url") ?: return null
                    Link(url = url, label = t.getString("label"))
                }
                Qr.KIND -> {
                    val file = t.getString("file")
                    val data = t.getString("data")
                    if (file == null && data == null) null
                    else Qr(file = file, data = data)
                }
                File.KIND -> {
                    val file = t.getString("file") ?: return null
                    val mime = t.getString("mime_type") ?: return null
                    val size = t.getLong("size_bytes") ?: return null
                    File(file = file, mimeType = mime, sizeBytes = size, description = t.getString("description"))
                }
                Barcode.KIND -> {
                    val file = t.getString("file") ?: return null
                    val fmt = BarcodeFormat.fromToml(t.getString("format")) ?: return null
                    val data = t.getString("data") ?: return null
                    Barcode(file = file, format = fmt, data = data)
                }
                VCard.KIND -> {
                    val file = t.getString("file") ?: return null
                    VCard(file = file)
                }
                Location.KIND -> {
                    val lat = (t.scalars["lat"] as? TomlValue.F64)?.value
                        ?: t.getLong("lat")?.toDouble()
                        ?: return null
                    val lon = (t.scalars["lon"] as? TomlValue.F64)?.value
                        ?: t.getLong("lon")?.toDouble()
                        ?: return null
                    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
                    Location(lat = lat, lon = lon, label = t.getString("label"))
                }
                else -> null
            }
        }

        /** Read every `[[attachment]]` table off a parsed event frontmatter. */
        fun readArray(frontmatter: TomlTable): List<Attachment> {
            val rows = frontmatter.aotables["attachment"] ?: return emptyList()
            return rows.mapNotNull { fromTable(it) }
        }

        /** Write the array back into a frontmatter table. No-op when empty. */
        fun writeArray(frontmatter: TomlTable, attachments: List<Attachment>) {
            if (attachments.isEmpty()) return
            val rows = attachments.map { it.toTable() }.toMutableList()
            frontmatter.aotables["attachment"] = rows
        }
    }

    /** Serialise a single attachment into a TOML table. */
    fun toTable(): TomlTable {
        val t = TomlTable()
        t.putString("kind", kind)
        when (val a = this) {
            is Link -> {
                t.putString("url", a.url)
                t.putString("label", a.label)
            }
            is Qr -> {
                t.putString("file", a.file)
                t.putString("data", a.data)
            }
            is File -> {
                t.putString("file", a.file)
                t.putString("mime_type", a.mimeType)
                t.putLong("size_bytes", a.sizeBytes)
                t.putString("description", a.description)
            }
            is Barcode -> {
                t.putString("file", a.file)
                t.putString("format", a.format.tomlValue)
                t.putString("data", a.data)
            }
            is VCard -> {
                t.putString("file", a.file)
            }
            is Location -> {
                t.scalars["lat"] = TomlValue.F64(a.lat)
                t.scalars["lon"] = TomlValue.F64(a.lon)
                t.putString("label", a.label)
            }
        }
        return t
    }
}
