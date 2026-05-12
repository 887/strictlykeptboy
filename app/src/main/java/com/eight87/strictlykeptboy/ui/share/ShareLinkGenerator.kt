package com.eight87.strictlykeptboy.ui.share

import com.eight87.strictlykeptboy.git.RepoConfig
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Phase O.1 — turn a repo + chosen options into a [ShareLink] URI string.
 *
 * Pure: no Android dependencies. Takes a narrow [SharePolicy] input
 * rather than the whole UI state (R.X.1 — narrow data interfaces).
 */
object ShareLinkGenerator {

    data class SharePolicy(
        val mode: ShareMode,
        val expiry: Expiry,
        val includeMirrorRemotes: Boolean = false,
        val calendarId: String? = null,
        val sourceLabel: String? = null,
        val backLink: String? = null,
    )

    /** Expiry policy — sealed so callers can't smuggle invalid combinations. */
    sealed interface Expiry {
        object None : Expiry
        data class Days(val n: Int) : Expiry {
            init { require(n > 0) { "Expiry.Days requires positive n" } }
        }
        data class At(val instant: Instant) : Expiry
    }

    fun build(repo: RepoConfig, policy: SharePolicy, now: Instant = Instant.now()): ShareLink {
        val all = repo.remotes.map { it.url }
        val primaryUrl = repo.remotes.firstOrNull { it.name == repo.primaryRemote }?.url
            ?: all.firstOrNull()
            ?: error("Cannot share a repo with no remotes")
        val urls = if (policy.includeMirrorRemotes && all.size > 1) {
            buildList { add(primaryUrl); addAll(all.filter { it != primaryUrl }) }
        } else {
            listOf(primaryUrl)
        }
        val expiry = when (val e = policy.expiry) {
            Expiry.None -> null
            is Expiry.Days -> now.plus(e.n.toLong(), ChronoUnit.DAYS).toString()
            is Expiry.At -> e.instant.toString()
        }
        return ShareLink(
            urls = urls,
            mode = policy.mode,
            calendarId = policy.calendarId,
            expiryIso = expiry,
            sourceLabel = policy.sourceLabel ?: repo.displayName,
            backLink = policy.backLink,
        )
    }

    fun buildUri(repo: RepoConfig, policy: SharePolicy, now: Instant = Instant.now()): String =
        ShareLinkCodec.encode(build(repo, policy, now))
}
