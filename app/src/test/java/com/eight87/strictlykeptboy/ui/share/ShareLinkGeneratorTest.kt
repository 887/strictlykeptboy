package com.eight87.strictlykeptboy.ui.share

import com.eight87.strictlykeptboy.git.AuthMethod
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.git.RemoteBinding
import com.eight87.strictlykeptboy.git.RemoteName
import com.eight87.strictlykeptboy.git.RepoConfig
import com.eight87.strictlykeptboy.git.Transport
import com.eight87.strictlykeptboy.share.ShareMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShareLinkGeneratorTest {

    private fun makeRepo(remotes: List<RemoteBinding>): RepoConfig = RepoConfig(
        repoId = "r1",
        displayName = "My Repo",
        rootDir = "/tmp/r1",
        remotes = remotes,
        primaryRemote = remotes.firstOrNull()?.name,
        authorIdentity = AuthorIdentity("alex", "a@x.com"),
    )

    private val origin = RemoteBinding(
        RemoteName.ORIGIN, "https://example.com/origin.git",
        Transport.HttpsOAuth, AuthMethod.OAuthGitHub,
    )
    private val mirror = RemoteBinding(
        RemoteName("mirror-1"), "https://example.com/mirror.git",
        Transport.HttpsPat, AuthMethod.ManualPat,
    )

    @Test fun read_only_round_trip() {
        val link = ShareLinkGenerator.build(
            makeRepo(listOf(origin)),
            ShareLinkGenerator.SharePolicy(
                mode = ShareMode.ReadOnly,
                expiry = ShareLinkGenerator.Expiry.None,
            ),
        )!!
        val encoded = ShareLinkCodec.encode(link)
        val decoded = ShareLinkCodec.decode(encoded)!!
        assertEquals(listOf(origin.url), decoded.urls)
        assertEquals(ShareMode.ReadOnly, decoded.mode)
        assertNull(decoded.expiryIso)
    }

    @Test fun read_write_round_trip() {
        val link = ShareLinkGenerator.build(
            makeRepo(listOf(origin)),
            ShareLinkGenerator.SharePolicy(
                mode = ShareMode.ReadWrite,
                expiry = ShareLinkGenerator.Expiry.None,
            ),
        )!!
        val decoded = ShareLinkCodec.decode(ShareLinkCodec.encode(link))!!
        assertEquals(ShareMode.ReadWrite, decoded.mode)
    }

    @Test fun expiry_days_serializes_to_iso() {
        val now = Instant.parse("2026-05-01T00:00:00Z")
        val link = ShareLinkGenerator.build(
            makeRepo(listOf(origin)),
            ShareLinkGenerator.SharePolicy(
                mode = ShareMode.ReadOnly,
                expiry = ShareLinkGenerator.Expiry.Days(7),
            ),
            now = now,
        )!!
        assertEquals("2026-05-08T00:00:00Z", link.expiryIso)
        val decoded = ShareLinkCodec.decode(ShareLinkCodec.encode(link))!!
        assertEquals("2026-05-08T00:00:00Z", decoded.expiryIso)
    }

    @Test fun mirror_remotes_excluded_by_default() {
        val link = ShareLinkGenerator.build(
            makeRepo(listOf(origin, mirror)),
            ShareLinkGenerator.SharePolicy(
                mode = ShareMode.ReadOnly,
                expiry = ShareLinkGenerator.Expiry.None,
                includeMirrorRemotes = false,
            ),
        )!!
        assertEquals(listOf(origin.url), link.urls)
    }

    @Test fun mirror_remotes_included_when_toggled() {
        val link = ShareLinkGenerator.build(
            makeRepo(listOf(origin, mirror)),
            ShareLinkGenerator.SharePolicy(
                mode = ShareMode.ReadOnly,
                expiry = ShareLinkGenerator.Expiry.None,
                includeMirrorRemotes = true,
            ),
        )!!
        assertEquals(listOf(origin.url, mirror.url), link.urls)
        val decoded = ShareLinkCodec.decode(ShareLinkCodec.encode(link))!!
        assertEquals(listOf(origin.url, mirror.url), decoded.urls)
    }

    @Test fun decode_rejects_invalid_uri() {
        assertNull(ShareLinkCodec.decode("https://example.com"))
        assertNull(ShareLinkCodec.decode("strictlykeptboy://share"))
        assertNull(ShareLinkCodec.decode("strictlykeptboy://share?url=x")) // no mode
    }

    @Test fun decode_accepts_repo_alias() {
        val decoded = ShareLinkCodec.decode(
            "strictlykeptboy://share?repo=https%3A%2F%2Fx%2Fy.git&mode=read-only",
        )!!
        assertEquals("https://x/y.git", decoded.urls.single())
    }

    @Test fun expiry_check() {
        assertFalse(ShareLinkExpiry.isExpired(null, 0L))
        assertFalse(ShareLinkExpiry.isExpired("not-iso", 0L))
        assertTrue(ShareLinkExpiry.isExpired("2020-01-01T00:00:00Z", System.currentTimeMillis()))
        assertFalse(ShareLinkExpiry.isExpired("2999-01-01T00:00:00Z", System.currentTimeMillis()))
    }

    @Test fun encodes_calendar_and_source_label() {
        val link = ShareLinkGenerator.build(
            makeRepo(listOf(origin)),
            ShareLinkGenerator.SharePolicy(
                mode = ShareMode.ReadOnly,
                expiry = ShareLinkGenerator.Expiry.None,
                calendarId = "cal-7",
                sourceLabel = "weekend cal",
            ),
        )!!
        val decoded = ShareLinkCodec.decode(ShareLinkCodec.encode(link))!!
        assertEquals("cal-7", decoded.calendarId)
        assertEquals("weekend cal", decoded.sourceLabel)
    }

    // SOLID Liskov fix #6 — no-origin repos (D.74 first-class) used to
    // crash with `error("Cannot share a repo with no remotes")`. The
    // soft-failure contract is: `build` and `buildUri` return null and
    // the call site (ShareSheetContent) disables the Share affordance.
    @Test fun build_returns_null_for_no_remote_repo() {
        val link = ShareLinkGenerator.build(
            makeRepo(emptyList()),
            ShareLinkGenerator.SharePolicy(
                mode = ShareMode.ReadOnly,
                expiry = ShareLinkGenerator.Expiry.None,
            ),
        )
        assertNull(link)
    }

    @Test fun buildUri_returns_null_for_no_remote_repo() {
        val uri = ShareLinkGenerator.buildUri(
            makeRepo(emptyList()),
            ShareLinkGenerator.SharePolicy(
                mode = ShareMode.ReadOnly,
                expiry = ShareLinkGenerator.Expiry.None,
            ),
        )
        assertNull(uri)
    }
}
