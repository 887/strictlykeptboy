package com.eight87.strictlykeptboy.ui.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkTest {

    private val fp = "abcdef0123456789"
    private val uuid = "01900000-0000-7000-8000-000000000001"
    private val gid = "$fp:$uuid"

    @Test fun event_url_parses() {
        val link = DeepLink.parse("strictlykeptboy://event/$gid")
        assertTrue(link is DeepLink.EventTarget)
        assertEquals(gid, (link as DeepLink.EventTarget).globalId)
    }

    @Test fun task_url_parses() {
        val link = DeepLink.parse("strictlykeptboy://task/$gid")
        assertTrue(link is DeepLink.TaskTarget)
    }

    @Test fun repo_url_parses() {
        val link = DeepLink.parse("strictlykeptboy://repo/$uuid")
        assertTrue(link is DeepLink.RepoTarget)
        assertEquals(uuid, (link as DeepLink.RepoTarget).repoId)
    }

    @Test fun bonus_url_parses() {
        val link = DeepLink.parse("strictlykeptboy://bonus/$gid")
        assertTrue(link is DeepLink.BonusTarget)
    }

    @Test fun review_url_parses() {
        val sha = "deadbeefcafef00d"
        val link = DeepLink.parse("strictlykeptboy://review/$sha")
        assertTrue(link is DeepLink.ReviewTarget)
        assertEquals(sha, (link as DeepLink.ReviewTarget).commitSha)
    }

    @Test fun universal_link_parses() {
        val link = DeepLink.parse("https://strictlykeptboy.app/link/event/$gid")
        assertTrue(link is DeepLink.EventTarget)
    }

    @Test fun fragment_token_and_expires_are_captured() {
        val link = DeepLink.parse(
            "strictlykeptboy://event/$gid#token=secretpayload&expires=2099-01-01T00:00:00Z",
        )
        assertNotNull(link)
        assertEquals("secretpayload", link!!.token)
        assertEquals("2099-01-01T00:00:00Z", link.expiresIso)
    }

    @Test fun unknown_host_returns_null() {
        assertNull(DeepLink.parse("strictlykeptboy://chicken/$gid"))
    }

    @Test fun missing_payload_returns_null() {
        assertNull(DeepLink.parse("strictlykeptboy://event/"))
        assertNull(DeepLink.parse("strictlykeptboy://event"))
    }

    @Test fun foreign_scheme_returns_null() {
        assertNull(DeepLink.parse("https://example.com/event/$gid"))
        assertNull(DeepLink.parse("mailto:a@b"))
    }

    @Test fun share_link_is_not_swallowed_by_deeplink_parser() {
        // Phase O `strictlykeptboy://share?...` must not be claimed by
        // this parser; its host is "share" which isn't in the dispatch map.
        assertNull(DeepLink.parse("strictlykeptboy://share?url=https://x/y.git&mode=read-only"))
    }
}
