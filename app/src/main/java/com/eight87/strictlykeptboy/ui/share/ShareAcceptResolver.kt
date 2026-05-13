package com.eight87.strictlykeptboy.ui.share

import com.eight87.strictlykeptboy.store.ReferencesManifest

/**
 * Phase RR.3 / RR.4 — recipient-side acceptance flow.
 *
 * Given a decoded [ShareLink] and a snapshot of the recipient's currently
 * configured repos, decide whether to:
 *   * skip (duplicate — recipient already has the source repo, detected
 *     by URL or repo-fingerprint match per SH-G.4), or
 *   * import the share as a new entry (writing `references.toml` per
 *     SH-G.5 + NN.1..NN.8 + honouring the sender's `write_back_target`
 *     flag per YY.8).
 *
 * Pure data flow — no Android, no JGit, no filesystem. The composition
 * root passes in a snapshot of `KnownRepo` rows and consumes the
 * returned [Decision]; actually adding to `RepoStore` + writing the
 * recipient's `references.toml` lives at the call site.
 *
 * SOLID.S — one job: classify accept actions. SOLID.O — new decision
 * cases land as new sealed variants. SOLID.I — narrow `KnownRepo`
 * interface (id + remotes + fingerprint) instead of a fat `RepoConfig`.
 */
object ShareAcceptResolver {

    /**
     * The narrow view the resolver needs of an already-configured repo:
     * repo-id (UUIDv7), known remote URLs (case-insensitive match
     * surface), and an optional source-tree fingerprint (16 hex chars per
     * FB-A LOCK; null for empty repos / unmaterialized).
     */
    data class KnownRepo(
        val repoId: String,
        val remoteUrls: List<String>,
        val fingerprint: String? = null,
    )

    /**
     * Optional recipient-side info that augments the resolver:
     * [recipientFingerprint] is the recipient's own repo-fingerprint
     * used to skip self-shares; [sourceFingerprint] is what we know
     * about the share's source (typically discovered after clone). Both
     * are nullable — share-links don't have to embed a fingerprint.
     */
    data class AcceptContext(
        val knownRepos: List<KnownRepo>,
        val recipientFingerprint: String? = null,
        val sourceFingerprint: String? = null,
    )

    sealed interface Decision {
        /** Recipient already has this exact repo configured (URL match). */
        data class DuplicateByUrl(val existing: KnownRepo, val matchedUrl: String) : Decision

        /** Recipient already has a repo with the same source-tree fingerprint. */
        data class DuplicateByFingerprint(val existing: KnownRepo, val fingerprint: String) : Decision

        /** Sharing one's own repo with oneself — silently no-op. */
        data class SelfShareSkip(val fingerprint: String) : Decision

        /** Net-new accept; caller should clone + register + write references.toml. */
        data class Import(
            val link: ShareLink,
            val plannedReference: ReferencesManifest.Entry,
        ) : Decision
    }

    /**
     * Classify the accept action.
     *
     * Duplicate detection precedence per SH-G.4:
     * 1. self-share (recipient fingerprint == source fingerprint)
     * 2. fingerprint match against any known repo
     * 3. URL match (case-insensitive) against any known repo's remotes
     * 4. otherwise: Import
     *
     * The [pendingRepoId] is the UUIDv7 the caller intends to assign to
     * the new repo iff this resolves to [Decision.Import]; it goes into
     * the [ReferencesManifest.Entry] back-link.
     */
    fun classify(
        link: ShareLink,
        ctx: AcceptContext,
        pendingRepoId: String,
    ): Decision {
        // 1. self-share?
        val srcFp = ctx.sourceFingerprint
        val myFp = ctx.recipientFingerprint
        if (srcFp != null && myFp != null && srcFp.equals(myFp, ignoreCase = true)) {
            return Decision.SelfShareSkip(srcFp)
        }
        // 2. fingerprint match against any known
        if (srcFp != null) {
            ctx.knownRepos.firstOrNull { it.fingerprint?.equals(srcFp, ignoreCase = true) == true }
                ?.let { return Decision.DuplicateByFingerprint(it, srcFp) }
        }
        // 3. URL match
        val shareUrls = link.urls.map { it.lowercase() }.toSet()
        ctx.knownRepos.forEach { kr ->
            val match = kr.remoteUrls.firstOrNull { it.lowercase() in shareUrls }
            if (match != null) return Decision.DuplicateByUrl(kr, match)
        }
        // 4. import — plan the references.toml entry.
        val entry = ReferencesManifest.Entry(
            repoId = pendingRepoId,
            displayName = link.sourceLabel ?: deriveLabel(link.urls.first()),
            urls = link.urls,
            required = false,
            writeBackTarget = if (link.allowWriteBack) srcFp else null,
        )
        return Decision.Import(link, entry)
    }

    private fun deriveLabel(url: String): String {
        val trimmed = url.trimEnd('/')
        val tail = trimmed.substringAfterLast('/').removeSuffix(".git")
        return if (tail.isNotBlank()) tail else trimmed
    }
}
