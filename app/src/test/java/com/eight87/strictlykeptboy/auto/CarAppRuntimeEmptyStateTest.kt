package com.eight87.strictlykeptboy.auto

import com.eight87.strictlykeptboy.store.IdentityPronouns
import com.eight87.strictlykeptboy.store.IdentityTomlData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.1.G.4 — empty-state copy honors the user's praise term +
 * register choice from identity.toml.
 */
class CarAppRuntimeEmptyStateTest {

    @Test fun null_identity_keeps_plain_template_copy() {
        assertEquals("Nothing scheduled today.", AutoRowFormatter.emptyStateCopy(null))
    }

    @Test fun sub_register_uses_praise_term() {
        val identity = IdentityTomlData(
            praiseTerm = "good boy",
            pronouns = IdentityPronouns.HeHim,
            honorificForDom = "Sir",
            toneRegister = "soft-kinky",
        )
        val out = AutoRowFormatter.emptyStateCopy(identity)
        assertTrue("must contain praise term", out.contains("good boy"))
        assertTrue("must read as cleared", out.startsWith("all clear"))
    }

    @Test fun sub_register_picks_up_user_praise_term_override() {
        val identity = IdentityTomlData(
            praiseTerm = "pup",
            pronouns = IdentityPronouns.HeHim,
            honorificForDom = "Sir",
            toneRegister = "strict-kinky",
        )
        val out = AutoRowFormatter.emptyStateCopy(identity)
        assertTrue("must thread custom praise term", out.contains("pup"))
    }

    @Test fun plain_register_uses_honorific() {
        val identity = IdentityTomlData(
            praiseTerm = "good boy",
            pronouns = IdentityPronouns.HeHim,
            honorificForDom = "Sir",
            toneRegister = "plain",
        )
        val out = AutoRowFormatter.emptyStateCopy(identity)
        assertTrue("must include honorific", out.contains("Sir"))
        assertTrue("must say 'nothing scheduled'", out.startsWith("nothing scheduled"))
    }
}
