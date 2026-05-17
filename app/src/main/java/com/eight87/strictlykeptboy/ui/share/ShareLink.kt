package com.eight87.strictlykeptboy.ui.share

import com.eight87.strictlykeptboy.share.ShareLink
import com.eight87.strictlykeptboy.share.ShareMode
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Phase O.1 — `strictlykeptboy://share` deep-link codec.
 *
 * The data types [ShareLink] + [ShareMode] live in the neutral
 * top-level `share/` package so `store/AccessAggregator` can consume
 * them without violating R.X.6 (`store/` → `ui/` is forbidden). The
 * codec + expiry helper stay here next to the UI surfaces that wire
 * them up ([ShareSheet], [ShareLinkReceiver], [ShareLinkGenerator]).
 */
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
            if (link.allowWriteBack) add("writeback" to "true")
            if (link.singleUseToken) add("single_use" to "true")
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
        val writeBack = pairs.firstOrNull { it.first == "writeback" }?.second == "true"
        val singleUse = pairs.firstOrNull { it.first == "single_use" }?.second == "true"
        return ShareLink(
            urls = urls,
            mode = mode,
            calendarId = calendar,
            expiryIso = expiry,
            sourceLabel = name,
            backLink = back,
            allowWriteBack = writeBack,
            singleUseToken = singleUse,
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
