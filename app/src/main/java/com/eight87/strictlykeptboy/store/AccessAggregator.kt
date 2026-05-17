package com.eight87.strictlykeptboy.store

import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.RepoStore
import com.eight87.strictlykeptboy.share.ShareLink
import com.eight87.strictlykeptboy.share.ShareMode

/**
 * Round 2.2.D.6 — read-only aggregator for the global Access category.
 *
 * Builds a flat list of [AccessRow] across every configured repo's
 * [ShareLink]s. The actual per-repo share-link persistence layer hasn't
 * shipped yet (Phase RR.6 is still open) — so [ShareLinkSource] is a
 * pluggable interface that returns an empty list by default. When the
 * Phase RR persistence layer arrives, swap in a real implementation
 * via [AppGraph] and the Access category lights up.
 *
 * The aggregator never mutates share-link state.
 */
data class AccessRow(
    val repoId: String,
    val repoName: String,
    val recipientLabel: String,
    val mode: ShareMode,
    val singleUse: Boolean,
    val expiresAtMs: Long?,
)

/**
 * Narrow handle the aggregator depends on. Default impl returns empty
 * for every repo — see [EmptyShareLinkSource].
 */
fun interface ShareLinkSource {
    fun getForRepo(repoId: String): List<ShareLink>
}

object EmptyShareLinkSource : ShareLinkSource {
    override fun getForRepo(repoId: String): List<ShareLink> = emptyList()
}

class AccessAggregator(
    private val repoStore: RepoStore,
    private val shareLinks: ShareLinkSource = EmptyShareLinkSource,
) {

    /** Snapshot the aggregated rows. Pure read; safe to call from UI. */
    fun aggregate(): List<AccessRow> {
        val repos: List<RepoConfig> = repoStore.list()
        return repos.flatMap { cfg ->
            shareLinks.getForRepo(cfg.repoId).mapIndexed { idx, link ->
                AccessRow(
                    repoId = cfg.repoId,
                    repoName = cfg.displayName.ifBlank { cfg.repoId },
                    recipientLabel = link.sourceLabel
                        ?: link.urls.firstOrNull()?.let { trimRecipient(it) }
                        ?: "recipient-${idx + 1}",
                    mode = link.mode,
                    singleUse = link.singleUseToken,
                    expiresAtMs = parseExpiry(link.expiryIso),
                )
            }
        }
    }

    private fun trimRecipient(url: String): String {
        // Strip scheme + trailing `.git`; keep last two path segments for
        // a compact "host/repo" label.
        val stripped = url
            .substringAfter("://")
            .removeSuffix(".git")
            .removeSuffix("/")
        val segs = stripped.split('/')
        return if (segs.size >= 2) "${segs[segs.size - 2]}/${segs.last()}" else stripped
    }

    private fun parseExpiry(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull()
    }
}
