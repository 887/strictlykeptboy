package com.eight87.strictlykeptboy.ui.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkRouterTest {

    private val gid = "abcdef0123456789:01900000-0000-7000-8000-000000000001"

    @Test fun null_uri_is_invalid() {
        assertTrue(DeepLinkRouter.route(null) is DeepLinkRouter.Action.Invalid)
    }

    @Test fun blank_uri_is_invalid() {
        assertTrue(DeepLinkRouter.route("   ") is DeepLinkRouter.Action.Invalid)
    }

    @Test fun malformed_uri_is_invalid() {
        assertTrue(DeepLinkRouter.route("not a url") is DeepLinkRouter.Action.Invalid)
    }

    @Test fun event_routes_to_open_event() {
        val action = DeepLinkRouter.route("strictlykeptboy://event/$gid")
        assertTrue(action is DeepLinkRouter.Action.OpenEvent)
    }

    @Test fun task_routes_to_open_task() {
        val action = DeepLinkRouter.route("strictlykeptboy://task/$gid")
        assertTrue(action is DeepLinkRouter.Action.OpenTask)
    }

    @Test fun repo_routes_to_open_repo() {
        val action = DeepLinkRouter.route("strictlykeptboy://repo/01900000-0000-7000-8000-000000000001")
        assertTrue(action is DeepLinkRouter.Action.OpenRepo)
    }

    @Test fun bonus_routes_to_open_bonus() {
        val action = DeepLinkRouter.route("strictlykeptboy://bonus/$gid")
        assertTrue(action is DeepLinkRouter.Action.OpenBonus)
    }

    @Test fun review_routes_to_open_review() {
        val action = DeepLinkRouter.route("strictlykeptboy://review/deadbeef")
        assertTrue(action is DeepLinkRouter.Action.OpenReview)
    }

    @Test fun expired_link_routes_to_expired() {
        val expiredIso = "2020-01-01T00:00:00Z"
        // Use a "now" well past the 5min grace.
        val now = java.time.Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        val action = DeepLinkRouter.route(
            "strictlykeptboy://event/$gid#expires=$expiredIso",
            nowEpochMs = now,
        )
        assertTrue("got $action", action is DeepLinkRouter.Action.Expired)
    }

    @Test fun within_clock_skew_grace_is_not_expired() {
        val expires = java.time.Instant.parse("2026-01-01T00:00:00Z")
        val now = expires.toEpochMilli() + 60_000  // 1 minute past expiry
        val action = DeepLinkRouter.route(
            "strictlykeptboy://event/$gid#expires=${expires}",
            nowEpochMs = now,
        )
        assertFalse("got $action", action is DeepLinkRouter.Action.Expired)
    }

    @Test fun universal_link_routes_same_as_custom_scheme() {
        val a = DeepLinkRouter.route("strictlykeptboy://event/$gid")
        val b = DeepLinkRouter.route("https://strictlykeptboy.app/link/event/$gid")
        assertEquals(a::class, b::class)
    }

    @Test fun redact_token_replaces_token_fragment() {
        val redacted = DeepLinkRouter.redactToken("strictlykeptboy://event/$gid#token=secret&expires=2099-01-01T00:00:00Z")
        assertTrue(redacted.contains("token=<len=6>"))
        assertFalse(redacted.contains("secret"))
        assertTrue(redacted.contains("expires=2099-01-01T00:00:00Z"))
    }

    @Test fun redact_token_is_idempotent_on_no_fragment() {
        val s = "strictlykeptboy://event/$gid"
        assertEquals(s, DeepLinkRouter.redactToken(s))
    }
}
