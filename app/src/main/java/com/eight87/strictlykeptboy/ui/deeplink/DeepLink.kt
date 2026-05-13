package com.eight87.strictlykeptboy.ui.deeplink

import java.net.URLDecoder

/**
 * Phase MM — universal deep-link grammar for the strictlykeptboy app.
 *
 * Two URL families are accepted (per D.42 / SH-A):
 *   1. Custom scheme:  `strictlykeptboy://<host>/<path>?<query>#<fragment>`
 *   2. Universal link: `https://strictlykeptboy.app/link/<host>/<path>?<query>#<fragment>`
 *
 * The Phase O `strictlykeptboy://share?...` link is a sibling shape
 * handled by [com.eight87.strictlykeptboy.ui.share.ShareLinkReceiver];
 * this parser deliberately does NOT swallow share-links — they keep
 * their own surface.
 *
 * Targets supported (MM.1):
 *
 *   strictlykeptboy://event/<global-id>
 *   strictlykeptboy://task/<global-id>
 *   strictlykeptboy://repo/<repo-id>
 *   strictlykeptboy://bonus/<global-id>
 *   strictlykeptboy://review/<commit-sha>
 *
 * Where `<global-id>` = `<repo-fingerprint>:<entity-uuid>` per FB-A
 * (16-hex `<repo-fingerprint>`, UUIDv7 `<entity-uuid>`), and
 * `<repo-id>` is the local repo id (UUIDv7 used by [RepoStore]).
 *
 * Fragment-only secrets per SH-B.3 (`token=`, `expires=`). The
 * fragment is parsed but stored separately so the consume-once wipe in
 * MM.4 has a clear field to clear after first use.
 *
 * Pure Kotlin: no Android imports. The Activity glue layer constructs
 * a [String] from `Intent.getData()` and feeds it to [DeepLink.parse].
 */
sealed interface DeepLink {

    val token: String?
    val expiresIso: String?

    data class EventTarget(
        val globalId: String,
        override val token: String? = null,
        override val expiresIso: String? = null,
    ) : DeepLink

    data class TaskTarget(
        val globalId: String,
        override val token: String? = null,
        override val expiresIso: String? = null,
    ) : DeepLink

    data class RepoTarget(
        val repoId: String,
        override val token: String? = null,
        override val expiresIso: String? = null,
    ) : DeepLink

    data class BonusTarget(
        val globalId: String,
        override val token: String? = null,
        override val expiresIso: String? = null,
    ) : DeepLink

    data class ReviewTarget(
        val commitSha: String,
        override val token: String? = null,
        override val expiresIso: String? = null,
    ) : DeepLink

    companion object {

        const val SCHEME = "strictlykeptboy"
        const val UNIVERSAL_HOST = "strictlykeptboy.app"
        const val UNIVERSAL_PREFIX = "/link/"

        /**
         * Best-effort parser. Returns null when the input does not
         * match the deep-link grammar — callers fall through to other
         * receivers (e.g. the Phase O share-link path).
         */
        fun parse(raw: String): DeepLink? {
            val rest = stripScheme(raw.trim()) ?: return null
            val fragment = rest.substringAfter('#', missingDelimiterValue = "")
            val beforeFragment = rest.substringBefore('#')
            // `query` is reserved for future use (params alongside the
            // target id). Parsed-and-discarded today; the frame stays so
            // future extensions slot in without rewriting the parser.
            val beforeQuery = beforeFragment.substringBefore('?')
            val parts = beforeQuery.split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            val host = parts[0]
            val payload = parts.drop(1).joinToString("/")
            if (payload.isBlank()) return null
            val frag = parseKv(fragment)
            val token = frag["token"]
            val expires = frag["expires"]
            return when (host) {
                "event" -> EventTarget(payload, token, expires)
                "task" -> TaskTarget(payload, token, expires)
                "repo" -> RepoTarget(payload, token, expires)
                "bonus" -> BonusTarget(payload, token, expires)
                "review" -> ReviewTarget(payload, token, expires)
                else -> null
            }
        }

        private fun stripScheme(uri: String): String? {
            val customPrefix = "$SCHEME://"
            val httpsLink = "https://$UNIVERSAL_HOST$UNIVERSAL_PREFIX"
            val httpLink = "http://$UNIVERSAL_HOST$UNIVERSAL_PREFIX"
            return when {
                uri.startsWith(customPrefix) -> uri.removePrefix(customPrefix)
                uri.startsWith(httpsLink) -> uri.removePrefix(httpsLink)
                uri.startsWith(httpLink) -> uri.removePrefix(httpLink)
                else -> null
            }
        }

        private fun parseKv(blob: String): Map<String, String> {
            if (blob.isBlank()) return emptyMap()
            return blob.split('&').mapNotNull { p ->
                val i = p.indexOf('=')
                if (i < 0) null
                else {
                    val k = p.substring(0, i)
                    val v = runCatching { URLDecoder.decode(p.substring(i + 1), "UTF-8") }
                        .getOrDefault(p.substring(i + 1))
                    k to v
                }
            }.toMap()
        }
    }
}
