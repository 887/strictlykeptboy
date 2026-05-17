package com.eight87.strictlykeptboy.ui.share

import com.eight87.strictlykeptboy.share.ShareLink
import com.eight87.strictlykeptboy.share.ShareMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinkReceiverTest {

    @Test fun unparseable_uri_is_invalid() {
        val action = ShareLinkReceiver.classify("https://example.com")
        assertTrue(action is ShareLinkReceiver.Action.Invalid)
    }

    @Test fun expired_link_classifies_as_expired() {
        val uri = ShareLinkCodec.encode(
            ShareLink(
                urls = listOf("https://x/y.git"),
                mode = ShareMode.ReadOnly,
                expiryIso = "2020-01-01T00:00:00Z",
            ),
        )
        val action = ShareLinkReceiver.classify(uri, nowEpochMs = System.currentTimeMillis())
        assertTrue("got $action", action is ShareLinkReceiver.Action.Expired)
    }

    @Test fun read_only_classifies_to_clone() {
        val uri = ShareLinkCodec.encode(
            ShareLink(urls = listOf("https://x/y.git"), mode = ShareMode.ReadOnly),
        )
        val action = ShareLinkReceiver.classify(uri)
        assertTrue(action is ShareLinkReceiver.Action.CloneReadOnly)
        val link = (action as ShareLinkReceiver.Action.CloneReadOnly).link
        assertEquals("https://x/y.git", link.urls.single())
    }

    @Test fun read_write_classifies_to_add_repo() {
        val uri = ShareLinkCodec.encode(
            ShareLink(urls = listOf("https://x/y.git"), mode = ShareMode.ReadWrite),
        )
        val action = ShareLinkReceiver.classify(uri)
        assertTrue(action is ShareLinkReceiver.Action.LaunchAddRepo)
    }

    @Test fun future_expiry_does_not_trigger_expired() {
        val uri = ShareLinkCodec.encode(
            ShareLink(
                urls = listOf("https://x/y.git"),
                mode = ShareMode.ReadOnly,
                expiryIso = "2999-01-01T00:00:00Z",
            ),
        )
        val action = ShareLinkReceiver.classify(uri)
        assertTrue(action is ShareLinkReceiver.Action.CloneReadOnly)
    }
}
