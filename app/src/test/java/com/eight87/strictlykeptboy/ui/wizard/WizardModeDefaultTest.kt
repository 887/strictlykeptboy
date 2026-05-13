package com.eight87.strictlykeptboy.ui.wizard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 2.1.I.3 — alignment-driven mode default.
 *
 * Submissive → KeptByAi (genesis use-case: solo Submissive kept by an
 * AI dom). Everything else → Free.
 */
class WizardModeDefaultTest {

    @Test fun `submissive defaults to kept-by-ai`() {
        assertEquals(WizardModePick.KeptByAi, defaultModeFor(Alignment.Submissive))
    }

    @Test fun `dominant defaults to free`() {
        assertEquals(WizardModePick.Free, defaultModeFor(Alignment.Dominant))
    }

    @Test fun `switch defaults to free`() {
        assertEquals(WizardModePick.Free, defaultModeFor(Alignment.Switch))
    }

    @Test fun `unaligned-private defaults to free`() {
        assertEquals(WizardModePick.Free, defaultModeFor(Alignment.UnalignedPrivate))
    }

    @Test fun `WizardDraft effectiveModePick reflects alignment`() {
        val sub = WizardDraft(alignment = Alignment.Submissive)
        assertEquals(WizardModePick.KeptByAi, sub.effectiveModePick)

        val unaligned = WizardDraft(alignment = Alignment.UnalignedPrivate)
        assertEquals(WizardModePick.Free, unaligned.effectiveModePick)
    }

    @Test fun `explicit modePick wins over default`() {
        val draft = WizardDraft(
            alignment = Alignment.Submissive,
            modePick = WizardModePick.Free,
        )
        assertEquals(WizardModePick.Free, draft.effectiveModePick)
    }
}
