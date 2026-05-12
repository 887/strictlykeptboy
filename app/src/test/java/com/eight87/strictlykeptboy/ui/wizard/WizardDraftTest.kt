package com.eight87.strictlykeptboy.ui.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardDraftTest {

    @Test fun `default draft has self-care always on`() {
        val draft = WizardDraft().normalize()
        assertTrue(RoleId.SelfCare in draft.roles)
    }

    @Test fun `unaligned-private removes kink role and clamps lifestyle to routine`() {
        val draft = WizardDraft(
            alignment = Alignment.UnalignedPrivate,
            roles = setOf(RoleId.SelfCare, RoleId.Kink, RoleId.Workout),
            lifestyle = Lifestyle.PartneredStrict,
            tone = ToneRegister.SoftKinky,
            enabledTemplates = mapOf(RoleId.Kink to setOf("cage-check")),
        ).normalize()
        assertFalse("kink role hidden under unaligned-private", RoleId.Kink in draft.roles)
        assertEquals(Lifestyle.PartneredRoutine, draft.lifestyle)
        assertEquals(ToneRegister.WarmNeutral, draft.tone)
        assertTrue("kink templates dropped", draft.enabledTemplates[RoleId.Kink] == null)
    }

    @Test fun `dominant alignment forces honorific=None`() {
        val draft = WizardDraft(
            alignment = Alignment.Dominant,
            honorific = Honorific.Sir,
        ).normalize()
        assertEquals(Honorific.None, draft.honorific)
    }

    @Test fun `lifestyle phrasing maps submissive to strictly-kept`() {
        assertTrue(Lifestyle.SingleStrict.labelFor(Alignment.Submissive).contains("strictly-kept"))
        assertTrue(Lifestyle.PartneredStrict.labelFor(Alignment.Dominant).contains("strictly-keeping"))
        assertTrue(Lifestyle.SingleStrict.labelFor(Alignment.Switch).contains("strictly-shared"))
    }

    @Test fun `hasUserChoices true once user picks something`() {
        assertFalse(WizardDraft().hasUserChoices)
        assertTrue(WizardDraft(species = SpeciesChoice.Fox).hasUserChoices)
        assertTrue(WizardDraft(alignment = Alignment.Submissive).hasUserChoices)
    }

    @Test fun `template registry hides kink for neutral mode`() {
        assertTrue(TemplateRegistry.visibleTemplatesFor(RoleId.Kink, neutralMode = false).isNotEmpty())
        assertTrue(TemplateRegistry.visibleTemplatesFor(RoleId.Kink, neutralMode = true).isEmpty())
        assertTrue(TemplateRegistry.visibleTemplatesFor(RoleId.SelfCare, neutralMode = true).isNotEmpty())
    }

    @Test fun `template registry registers expected atoms for workout`() {
        val workout = TemplateRegistry.templatesFor(RoleId.Workout).map { it.atomId }
        assertTrue("pushups" in workout)
        assertTrue("situps" in workout)
        assertTrue("squats" in workout)
    }
}
