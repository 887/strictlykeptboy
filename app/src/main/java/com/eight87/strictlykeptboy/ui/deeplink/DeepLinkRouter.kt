package com.eight87.strictlykeptboy.ui.deeplink

/**
 * Phase MM.2 — single-entry router that maps an incoming URI string to
 * an [Action]. The Activity glue layer keeps the parsed link in memory
 * just long enough to dispatch — once dispatched, any secret-bearing
 * fragment is wiped via [redactToken] (MM.4 / SH-B.5).
 *
 * Pure dispatch: takes a wall-clock supplier so tests can pin time.
 * Per SOLID.D the router depends on the abstraction `() -> Long`, not
 * on `System.currentTimeMillis` directly.
 */
object DeepLinkRouter {

    sealed interface Action {
        /** URI didn't match any deep-link target. */
        data class Invalid(val uri: String) : Action

        /** Fragment carried `expires` and now > expires. */
        data class Expired(val link: DeepLink) : Action

        /** Open the event-detail screen. */
        data class OpenEvent(val link: DeepLink.EventTarget) : Action

        /** Open the task-detail screen. */
        data class OpenTask(val link: DeepLink.TaskTarget) : Action

        /** Open repo settings (for the configured repo). */
        data class OpenRepo(val link: DeepLink.RepoTarget) : Action

        /** Open the bonus-task accept sheet. */
        data class OpenBonus(val link: DeepLink.BonusTarget) : Action

        /** Open the review-feed at the given commit. */
        data class OpenReview(val link: DeepLink.ReviewTarget) : Action
    }

    /**
     * Dispatch entry point. Returns [Action.Invalid] for unparseable or
     * unknown shapes; the caller renders an error toast with the
     * verbatim URI for diagnosis (SH-B.9 malformed-URL row).
     */
    fun route(
        rawUri: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Action {
        if (rawUri.isNullOrBlank()) return Action.Invalid(rawUri.orEmpty())
        val link = DeepLink.parse(rawUri) ?: return Action.Invalid(rawUri)
        if (isExpired(link.expiresIso, nowEpochMs)) return Action.Expired(link)
        return when (link) {
            is DeepLink.EventTarget -> Action.OpenEvent(link)
            is DeepLink.TaskTarget -> Action.OpenTask(link)
            is DeepLink.RepoTarget -> Action.OpenRepo(link)
            is DeepLink.BonusTarget -> Action.OpenBonus(link)
            is DeepLink.ReviewTarget -> Action.OpenReview(link)
        }
    }

    /**
     * Returns a redacted form of [uri] safe to write to diagnostic logs.
     * Token fragments are replaced with `token=<len=N>` per SH-B.5.
     */
    fun redactToken(uri: String): String {
        val hashIdx = uri.indexOf('#')
        if (hashIdx < 0) return uri
        val head = uri.substring(0, hashIdx)
        val fragment = uri.substring(hashIdx + 1)
        val redacted = fragment.split('&').joinToString("&") { p ->
            val i = p.indexOf('=')
            if (i > 0 && p.substring(0, i) == "token") "token=<len=${p.length - i - 1}>"
            else p
        }
        return "$head#$redacted"
    }

    private fun isExpired(iso: String?, nowEpochMs: Long): Boolean {
        if (iso.isNullOrBlank()) return false
        val instant = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return false
        // SH-B.6: 5min clock-skew grace.
        return instant.toEpochMilli() + 5 * 60 * 1000 < nowEpochMs
    }
}
