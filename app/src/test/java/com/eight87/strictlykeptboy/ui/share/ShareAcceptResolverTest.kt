package com.eight87.strictlykeptboy.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase RR.6 — recipient-side accept classification + dup-detection.
 *
 * These tests stay pure-JVM (no Robolectric) since [ShareAcceptResolver]
 * has no Android dependencies. The matching Compose-level test for
 * [ShareSheet] write-back toggle wiring lives in [ShareSheetTest].
 */
class ShareAcceptResolverTest {

    private val link = ShareLink(
        urls = listOf("https://example.com/origin.git"),
        mode = ShareMode.ReadOnly,
        sourceLabel = "Master's schedule",
    )
    private val linkWithWriteBack = link.copy(allowWriteBack = true)

    @Test fun new_share_imports_with_planned_reference() {
        val decision = ShareAcceptResolver.classify(
            link,
            ShareAcceptResolver.AcceptContext(knownRepos = emptyList()),
            pendingRepoId = "01900000-0000-7000-8000-000000000001",
        )
        val import = decision as ShareAcceptResolver.Decision.Import
        assertEquals(link, import.link)
        assertEquals("01900000-0000-7000-8000-000000000001", import.plannedReference.repoId)
        assertEquals("Master's schedule", import.plannedReference.displayName)
        assertEquals(listOf("https://example.com/origin.git"), import.plannedReference.urls)
        assertNull(import.plannedReference.writeBackTarget)
    }

    @Test fun new_share_with_write_back_emits_target() {
        val decision = ShareAcceptResolver.classify(
            linkWithWriteBack,
            ShareAcceptResolver.AcceptContext(
                knownRepos = emptyList(),
                sourceFingerprint = "abcdef0123456789",
            ),
            pendingRepoId = "01900000-0000-7000-8000-000000000002",
        )
        val import = decision as ShareAcceptResolver.Decision.Import
        assertEquals("abcdef0123456789", import.plannedReference.writeBackTarget)
    }

    @Test fun duplicate_by_url_detected_case_insensitive() {
        val existing = ShareAcceptResolver.KnownRepo(
            repoId = "existing-1",
            remoteUrls = listOf("HTTPS://example.com/Origin.git"),
        )
        val decision = ShareAcceptResolver.classify(
            link,
            ShareAcceptResolver.AcceptContext(knownRepos = listOf(existing)),
            pendingRepoId = "ignored",
        )
        val dup = decision as ShareAcceptResolver.Decision.DuplicateByUrl
        assertEquals("existing-1", dup.existing.repoId)
    }

    @Test fun duplicate_by_fingerprint_wins_over_url_mismatch() {
        val existing = ShareAcceptResolver.KnownRepo(
            repoId = "existing-2",
            remoteUrls = listOf("https://other.host/repo.git"),
            fingerprint = "abcdef0123456789",
        )
        val decision = ShareAcceptResolver.classify(
            link.copy(urls = listOf("https://elsewhere/repo.git")),
            ShareAcceptResolver.AcceptContext(
                knownRepos = listOf(existing),
                sourceFingerprint = "abcdef0123456789",
            ),
            pendingRepoId = "ignored",
        )
        assertTrue(decision is ShareAcceptResolver.Decision.DuplicateByFingerprint)
    }

    @Test fun self_share_skipped_when_fingerprints_match() {
        val decision = ShareAcceptResolver.classify(
            link,
            ShareAcceptResolver.AcceptContext(
                knownRepos = emptyList(),
                recipientFingerprint = "abcdef0123456789",
                sourceFingerprint = "abcdef0123456789",
            ),
            pendingRepoId = "ignored",
        )
        assertTrue(decision is ShareAcceptResolver.Decision.SelfShareSkip)
    }

    @Test fun derives_label_from_url_when_link_has_none() {
        val noLabel = link.copy(sourceLabel = null)
        val decision = ShareAcceptResolver.classify(
            noLabel,
            ShareAcceptResolver.AcceptContext(knownRepos = emptyList()),
            pendingRepoId = "pid",
        )
        val import = decision as ShareAcceptResolver.Decision.Import
        assertEquals("origin", import.plannedReference.displayName)
    }

    @Test fun encode_decode_round_trip_preserves_flags() {
        val l = ShareLink(
            urls = listOf("https://x/y.git"),
            mode = ShareMode.ReadOnly,
            allowWriteBack = true,
            singleUseToken = true,
        )
        val decoded = ShareLinkCodec.decode(ShareLinkCodec.encode(l))!!
        assertTrue(decoded.allowWriteBack)
        assertTrue(decoded.singleUseToken)
    }
}
