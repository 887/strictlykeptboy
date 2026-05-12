package com.eight87.strictlykeptboy.store

/**
 * Minimal TOML value AST used by the store layer's frontmatter
 * reader/writer. Covers the subset our schemas (DM-B) actually need:
 * scalars, native dates/datetimes, arrays, inline tables, and (via
 * [TomlTable]) sections and array-of-tables.
 *
 * Rationale for not using ktoml's tree API directly: ktoml's internal
 * node model has changed across minor versions and exposing it leaks
 * implementation details into our entity layer. Our schema is bounded
 * — a small purpose-built value AST gives us byte-stable round-trips
 * for the value subset we actually emit. See DM-A.4: comment
 * preservation is explicitly out of scope for v1.
 */
sealed interface TomlValue {

    @JvmInline value class Str(val value: String) : TomlValue
    @JvmInline value class I64(val value: Long) : TomlValue
    @JvmInline value class Bool(val value: Boolean) : TomlValue
    @JvmInline value class F64(val value: Double) : TomlValue

    /** TOML local-date: `2026-05-12`. */
    @JvmInline value class LocalDate(val text: String) : TomlValue
    /** TOML local-datetime: `2026-05-12T14:00:00` (no offset). */
    @JvmInline value class LocalDateTime(val text: String) : TomlValue
    /** TOML offset-datetime: `2026-05-12T14:00:00+02:00`. */
    @JvmInline value class OffsetDateTime(val text: String) : TomlValue
    /** TOML local-time: `14:00:00`. */
    @JvmInline value class LocalTime(val text: String) : TomlValue

    data class Arr(val items: List<TomlValue>) : TomlValue
    data class InlineTable(val entries: LinkedHashMap<String, TomlValue>) : TomlValue
}

/**
 * Ordered TOML table — top-level scalars in [scalars], child sections
 * in [sections] (keyed by dotted path), array-of-tables in [aotables].
 *
 * Insertion order is preserved on write (DM-A.3).
 */
class TomlTable {
    val scalars: LinkedHashMap<String, TomlValue> = LinkedHashMap()
    val sections: LinkedHashMap<String, TomlTable> = LinkedHashMap()
    val aotables: LinkedHashMap<String, MutableList<TomlTable>> = LinkedHashMap()

    fun put(key: String, value: TomlValue) { scalars[key] = value }
    fun getString(key: String): String? = (scalars[key] as? TomlValue.Str)?.value
    fun getLong(key: String): Long? = (scalars[key] as? TomlValue.I64)?.value
    fun getInt(key: String): Int? = getLong(key)?.toInt()
    fun getBool(key: String): Boolean? = (scalars[key] as? TomlValue.Bool)?.value
    fun getLocalDate(key: String): String? = (scalars[key] as? TomlValue.LocalDate)?.text
    fun getLocalDateTime(key: String): String? = (scalars[key] as? TomlValue.LocalDateTime)?.text
    fun getOffsetDateTime(key: String): String? = (scalars[key] as? TomlValue.OffsetDateTime)?.text
    fun getStringArray(key: String): List<String>? =
        (scalars[key] as? TomlValue.Arr)?.items?.mapNotNull { (it as? TomlValue.Str)?.value }

    /** Datetime-or-date getter: returns whatever string the source had. */
    fun getDateLike(key: String): String? = when (val v = scalars[key]) {
        is TomlValue.Str -> v.value
        is TomlValue.LocalDate -> v.text
        is TomlValue.LocalDateTime -> v.text
        is TomlValue.OffsetDateTime -> v.text
        else -> null
    }

    fun putString(key: String, v: String?) { if (v != null) scalars[key] = TomlValue.Str(v) }
    fun putLong(key: String, v: Long?) { if (v != null) scalars[key] = TomlValue.I64(v) }
    fun putInt(key: String, v: Int?) { if (v != null) scalars[key] = TomlValue.I64(v.toLong()) }
    fun putBool(key: String, v: Boolean?) { if (v != null) scalars[key] = TomlValue.Bool(v) }
    fun putLocalDate(key: String, v: String?) { if (v != null) scalars[key] = TomlValue.LocalDate(v) }
    fun putLocalDateTime(key: String, v: String?) { if (v != null) scalars[key] = TomlValue.LocalDateTime(v) }
    fun putOffsetDateTime(key: String, v: String?) { if (v != null) scalars[key] = TomlValue.OffsetDateTime(v) }
    fun putStringArray(key: String, v: List<String>?) {
        if (v != null) scalars[key] = TomlValue.Arr(v.map { TomlValue.Str(it) })
    }
}
