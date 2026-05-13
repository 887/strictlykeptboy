package com.eight87.strictlykeptboy.ui.wizard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 2.1.M.2 — Pet-Mode-aware alignment defaults.
 *
 * Replaces the simpler default exercised by [WizardModeDefaultTest]:
 *  - Submissive (alone)    → KeptByAi
 *  - Submissive (partner)  → KeptByHuman
 *  - Switch                → Free (regardless of partner)
 *  - Dominant              → Free
 *  - UnalignedPrivate      → Free
 */
class WizardPetModeDefaultTest {

    @Test fun `submissive alone defaults to kept-by-ai`() {
        assertEquals(
            WizardModePick.KeptByAi,
            defaultModeFor(Alignment.Submissive, hasPartner = false),
        )
    }

    @Test fun `submissive with partner defaults to kept-by-human`() {
        assertEquals(
            WizardModePick.KeptByHuman,
            defaultModeFor(Alignment.Submissive, hasPartner = true),
        )
    }

    @Test fun `switch defaults to free regardless of partner`() {
        assertEquals(WizardModePick.Free, defaultModeFor(Alignment.Switch, false))
        assertEquals(WizardModePick.Free, defaultModeFor(Alignment.Switch, true))
    }

    @Test fun `dominant defaults to free`() {
        assertEquals(WizardModePick.Free, defaultModeFor(Alignment.Dominant, false))
        assertEquals(WizardModePick.Free, defaultModeFor(Alignment.Dominant, true))
    }

    @Test fun `unaligned-private defaults to free`() {
        assertEquals(
            WizardModePick.Free,
            defaultModeFor(Alignment.UnalignedPrivate, false),
        )
    }

    @Test fun `WizardDraft hasPartner flips effective default`() {
        val solo = WizardDraft(alignment = Alignment.Submissive, hasPartner = false)
        assertEquals(WizardModePick.KeptByAi, solo.effectiveModePick)

        val partnered = WizardDraft(alignment = Alignment.Submissive, hasPartner = true)
        assertEquals(WizardModePick.KeptByHuman, partnered.effectiveModePick)
    }
}
