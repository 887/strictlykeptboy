package com.eight87.strictlykeptboy.auto

import com.eight87.strictlykeptboy.store.IdentityPronouns
import com.eight87.strictlykeptboy.store.IdentityTomlData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Phase 2.1.G.2 — identity-driven row text.
 *
 * Pins:
 *  - sub register emits "your <time> — <title>, <honorific>"
 *  - null identity stays on the plain `HH:mm  <title>` template
 *  - honorific suppressed when blank / "(none)"
 */
class CarAppRuntimeIdentityCopyTest {

    private val start = ZonedDateTime.of(2026, 5, 13, 16, 0, 0, 0, ZoneId.of("UTC"))

    private fun submissive(honorific: String = "Sir", praise: String = "good boy") = IdentityTomlData(
        praiseTerm = praise,
        pronouns = IdentityPronouns.HeHim,
        honorificForDom = honorific,
        toneRegister = "soft-kinky",
    )

    @Test fun null_identity_uses_plain_template() {
        val out = AutoRowFormatter.rowTitle(start, "Standup", identity = null)
        assertTrue(out.startsWith("16:00"))
        assertFalse("plain template must not include 'your '", out.contains("your "))
        assertFalse("plain template must not include honorific", out.contains(", Sir"))
    }

    @Test fun sub_register_emits_honorific_and_praise_voice() {
        val out = AutoRowFormatter.rowTitle(start, "Standup", identity = submissive())
        assertTrue("must include 'your '", out.contains("your "))
        assertTrue("must include the time", out.contains("16:00"))
        assertTrue("must include the title", out.contains("Standup"))
        assertTrue("must end with honorific", out.endsWith(", Sir"))
    }

    @Test fun honorific_blank_collapses_to_no_suffix() {
        val out = AutoRowFormatter.rowTitle(start, "Standup", identity = submissive(honorific = ""))
        assertTrue("must include 'your '", out.contains("your "))
        assertFalse("must not append trailing ', '", out.endsWith(", "))
    }

    @Test fun honorific_explicit_none_collapses_to_no_suffix() {
        val out = AutoRowFormatter.rowTitle(start, "Standup", identity = submissive(honorific = "(none)"))
        assertFalse("must not include '(none)'", out.contains("(none)"))
    }
}
