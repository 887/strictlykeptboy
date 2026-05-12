package com.eight87.strictlykeptboy.ui.share

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Phase O.1 — `strictlykeptboy://share` deep-link model.
 *
 * Scheme:
 *   strictlykeptboy://share
 *     ?url=<repo-url>            (1..N copies — multi-origin per D.74 / ZZ.B.5)
 *     &calendar=<id>             (optional, scopes the share to one calendar)
 *     &mode=read-only|read-write
 *     &expiry=<iso-instant>      (optional, no expiry if absent)
 *     &name=<source-repo-label>  (optional, surfaced as foreign-event chip)
 *     &back=<deep-link-url>      (optional, "Open in source app" CTA)
 *
 * The encoder always uses the parameter spelling `url`; legacy `repo`
 * is also accepted on decode (since the task spec describes that shape
 * in the user-facing description).
 *
 * Pure data — no Android imports. UI lives in [ShareSheet], intent
 * handling lives in [ShareLinkReceiver].
 */
data class ShareLink(
    val urls: List<String>,
    val mode: ShareMode,
    val calendarId: String? = null,
    val expiryIso: String? = null,
    val sourceLabel: String? = null,
    val backLink: String? = null,
) {
    init {
        require(urls.isNotEmpty()) { "ShareLink must carry at least one url" }
    }
}

enum class ShareMode { ReadOnly, ReadWrite;
    fun wire(): String = when (this) { ReadOnly -> "read-only"; ReadWrite -> "read-write" }
    companion object {
        fun parse(s: String?): ShareMode? = when (s) {
            "read-only" -> ReadOnly
            "read-write" -> ReadWrite
            else -> null
        }
    }
}

object ShareLinkCodec {

    const val SCHEME = "strictlykeptboy"
    const val HOST = "share"

    fun encode(link: ShareLink): String {
        val sb = StringBuilder("$SCHEME://$HOST?")
        val params = buildList {
            link.urls.forEach { add("url" to it) }
            link.calendarId?.let { add("calendar" to it) }
            add("mode" to link.mode.wire())
            link.expiryIso?.let { add("expiry" to it) }
            link.sourceLabel?.let { add("name" to it) }
            link.backLink?.let { add("back" to it) }
        }
        params.joinTo(sb, separator = "&") { (k, v) -> "$k=${enc(v)}" }
        return sb.toString()
    }

    /** Returns null when the URI is not a parseable share link. */
    fun decode(uri: String): ShareLink? {
        val cleaned = uri.trim()
        val prefix = "$SCHEME://$HOST"
        if (!cleaned.startsWith(prefix)) return null
        val query = cleaned.substringAfter('?', missingDelimiterValue = "")
        if (query.isBlank()) return null
        val pairs = query.split('&').mapNotNull { p ->
            val i = p.indexOf('=')
            if (i < 0) null else p.substring(0, i) to dec(p.substring(i + 1))
        }
        val urls = pairs.filter { it.first == "url" || it.first == "repo" }.map { it.second }
            .filter { it.isNotBlank() }
        if (urls.isEmpty()) return null
        val mode = ShareMode.parse(pairs.firstOrNull { it.first == "mode" }?.second) ?: return null
        val calendar = pairs.firstOrNull { it.first == "calendar" }?.second
        val expiry = pairs.firstOrNull { it.first == "expiry" }?.second
        val name = pairs.firstOrNull { it.first == "name" }?.second
        val back = pairs.firstOrNull { it.first == "back" }?.second
        return ShareLink(
            urls = urls,
            mode = mode,
            calendarId = calendar,
            expiryIso = expiry,
            sourceLabel = name,
            backLink = back,
        )
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")
}

/**
 * Time-source-injectable expiry check (R.X.1 narrow interface): caller
 * passes the current epoch ms, the codec stays pure.
 */
object ShareLinkExpiry {
    /** Returns true iff [iso] is parseable AND lies strictly before [nowEpochMs]. */
    fun isExpired(iso: String?, nowEpochMs: Long): Boolean {
        if (iso.isNullOrBlank()) return false
        val instant = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return false
        return instant.toEpochMilli() < nowEpochMs
    }
}
