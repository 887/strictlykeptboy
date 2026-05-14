package com.eight87.strictlykeptboy.ui.wizard

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 2.2.B.6 — replaces [WizardModeDefaultTest] /
 * [WizardPetModeDefaultTest] (both deleted). The wizard's
 * Alignment + Lifestyle + Mode three-screen flow collapsed into the
 * six-card [LifestyleCard] enum (D-2.2.c); each card writes the four
 * data-layer fields atomically.
 *
 * This test pins each card's (alignment, lifestyle, modePick, hasPartner)
 * tuple — drift on any field surfaces as a failure here so the on-disk
 * mode.toml / identity.toml wire format stays stable.
 */
class WizardLifestyleCardTest {

    @Test fun `default card is solo-sub kept by AI dom`() {
        assertEquals(LifestyleCard.PetKeptByAi, LifestyleCard.Default)
    }

    @Test fun `PetKeptByAi tuple`() {
        val c = LifestyleCard.PetKeptByAi
        assertEquals(Alignment.Submissive, c.alignment)
        assertEquals(Lifestyle.SingleStrict, c.lifestyle)
        assertEquals(WizardModePick.KeptByAi, c.modePick)
        assertEquals(false, c.hasPartner)
    }

    @Test fun `PetKeptByPartner tuple`() {
        val c = LifestyleCard.PetKeptByPartner
        assertEquals(Alignment.Submissive, c.alignment)
        assertEquals(Lifestyle.PartneredStrict, c.lifestyle)
        assertEquals(WizardModePick.KeptByHuman, c.modePick)
        assertEquals(true, c.hasPartner)
    }

    @Test fun `PetSelfKept tuple`() {
        val c = LifestyleCard.PetSelfKept
        assertEquals(Alignment.Submissive, c.alignment)
        assertEquals(Lifestyle.SingleStrict, c.lifestyle)
        assertEquals(WizardModePick.SelfKeep, c.modePick)
        assertEquals(false, c.hasPartner)
    }

    @Test fun `DomKeepingPets tuple`() {
        val c = LifestyleCard.DomKeepingPets
        assertEquals(Alignment.Dominant, c.alignment)
        assertEquals(Lifestyle.PartneredStrict, c.lifestyle)
        assertEquals(WizardModePick.Free, c.modePick)
        assertEquals(true, c.hasPartner)
    }

    @Test fun `Switch tuple`() {
        val c = LifestyleCard.Switch
        assertEquals(Alignment.Switch, c.alignment)
        assertEquals(Lifestyle.PartneredStrict, c.lifestyle)
        assertEquals(WizardModePick.Free, c.modePick)
        assertEquals(true, c.hasPartner)
    }

    @Test fun `JustCalendar tuple`() {
        val c = LifestyleCard.JustCalendar
        assertEquals(Alignment.UnalignedPrivate, c.alignment)
        assertEquals(Lifestyle.SingleFree, c.lifestyle)
        assertEquals(WizardModePick.Free, c.modePick)
        assertEquals(false, c.hasPartner)
    }

    @Test fun `applyLifestyleCard sets all four fields atomically`() {
        val fresh = WizardDraft()
        val applied = fresh.applyLifestyleCard(LifestyleCard.PetKeptByPartner)
        assertEquals(Alignment.Submissive, applied.alignment)
        assertEquals(Lifestyle.PartneredStrict, applied.lifestyle)
        assertEquals(WizardModePick.KeptByHuman, applied.modePick)
        assertEquals(true, applied.hasPartner)
    }

    @Test fun `applyLifestyleCard PetKeptByAi normalizes draft (drops kink-off mismatch)`() {
        // PetKeptByAi → Submissive + SingleStrict, so kink stays available.
        val applied = WizardDraft().applyLifestyleCard(LifestyleCard.PetKeptByAi)
        assertEquals(WizardModePick.KeptByAi, applied.effectiveModePick)
        assertEquals(false, applied.kinkOff)
    }

    @Test fun `applyLifestyleCard JustCalendar trips kinkOff invariant`() {
        // JustCalendar → UnalignedPrivate, which flips kinkOff. The
        // wizard's `normalize()` clamps lifestyle + role + tone in that
        // case; we just assert the alignment landed correctly.
        val applied = WizardDraft().applyLifestyleCard(LifestyleCard.JustCalendar)
        assertEquals(Alignment.UnalignedPrivate, applied.alignment)
        assertEquals(true, applied.kinkOff)
    }

    @Test fun `effectiveModePick falls back to default card when modePick null`() {
        val draft = WizardDraft(modePick = null)
        assertEquals(WizardModePick.KeptByAi, draft.effectiveModePick)
    }

    @Test fun `fromDraft reverse-lookup matches an applied card`() {
        for (card in LifestyleCard.entries) {
            val draft = WizardDraft().applyLifestyleCard(card)
            assertEquals(
                "fromDraft round-trip for $card",
                card,
                LifestyleCard.fromDraft(draft),
            )
        }
    }
}
