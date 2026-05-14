package com.eight87.strictlykeptboy.notif

import com.eight87.strictlykeptboy.store.IdentityTomlData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.1.J.3 / DDD.11 — notification body is register-aware (praise
 * term + honorific surface), but `private = true` short-circuits to
 * the generic body so nothing leaks onto a lockscreen preview.
 */
class NotifBodyIdentityTest {

    private val custom = IdentityTomlData.LockedDefaults.copy(
        praiseTerm = "good kit",
        honorificForDom = "Sir",
    )

    @Test fun nonPrivate_event_includes_praise_and_honorific() {
        val body = IdentityNotifBody.bodyFor(
            identity = custom,
            title = "4pm sync",
            privateEvent = false,
        )
        assertTrue("body should contain praise term", body.contains("good kit"))
        assertTrue("body should contain honorific", body.contains("Sir"))
        assertTrue("body should reference the title", body.contains("4pm sync"))
    }

    @Test fun private_event_collapses_to_generic_body() {
        val body = IdentityNotifBody.bodyFor(
            identity = custom,
            title = "4pm sync",
            privateEvent = true,
        )
        assertEquals(IdentityNotifBody.GENERIC_BODY, body)
        assertTrue("must not contain praise term", !body.contains("good kit"))
        assertTrue("must not contain honorific", !body.contains("Sir"))
    }

    @Test fun null_identity_collapses_to_generic_body() {
        val body = IdentityNotifBody.bodyFor(
            identity = null,
            title = "anything",
            privateEvent = false,
        )
        assertEquals(IdentityNotifBody.GENERIC_BODY, body)
    }

    @Test fun blank_honorific_drops_trailing_clause() {
        val body = IdentityNotifBody.bodyFor(
            identity = custom.copy(honorificForDom = ""),
            title = "yoga",
            privateEvent = false,
        )
        assertTrue("starts with praise + comma", body.startsWith("good kit, "))
        assertTrue("must not have honorific clause", !body.contains(", Sir"))
    }

    @Test fun briefing_salutation_uses_praise_term() {
        val salutation = IdentityNotifBody.briefingSalutation(custom, IdentityNotifBody.TimeOfDay.Morning)
        assertEquals("Morning, good kit", salutation)
    }

    @Test fun briefing_salutation_null_identity_fallback() {
        val salutation = IdentityNotifBody.briefingSalutation(null, IdentityNotifBody.TimeOfDay.Evening)
        assertEquals("Evening, you", salutation)
    }
}
