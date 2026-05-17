package com.eight87.strictlykeptboy

import com.eight87.strictlykeptboy.ui.wizard.Alignment
import com.eight87.strictlykeptboy.ui.wizard.EmojiDensity
import com.eight87.strictlykeptboy.ui.wizard.Honorific
import com.eight87.strictlykeptboy.ui.wizard.Lifestyle
import com.eight87.strictlykeptboy.ui.wizard.RoleId
import com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice
import com.eight87.strictlykeptboy.ui.wizard.ToneRegister
import org.junit.Test

/**
 * TR-C.1 — wire-format enum `.label` stability lock.
 *
 * Every enum that exposes a `.label: String` used by [WizardScaffolder] for
 * TOML round-trip (`calendar.toml` / `identity.toml`) must stay **English**
 * and stable across releases. The translatable UI label resolves through
 * `labelString()` / `labelRes` in `ui/a11y/EnumLabels.kt` and is decoupled
 * from these literals (T-4, see `docs/plans/translations.md`).
 *
 * This test asserts the exact wire-format spelling for every variant. It
 * is intentionally NOT Robolectric (no `Context` / no `R.string.*` lookup)
 * so that a future PR routing these through resources would fail to
 * compile here and force a deliberate decision instead of silently
 * breaking the on-disk repo schema (D.3 / T-4) for every user worldwide.
 *
 * Adding a new enum variant: extend the relevant `expect` map below with
 * the exact English wire-format literal.
 */
class EnumLabelStabilityTest {

    @Test fun species_choice_labels_are_stable_english() {
        val expect = mapOf(
            SpeciesChoice.Bat to "Bat",
            SpeciesChoice.Bunny to "Bunny",
            SpeciesChoice.Cat to "Cat",
            SpeciesChoice.CatChan to "Cat-chan",
            SpeciesChoice.Fox to "Fox",
            SpeciesChoice.FoxChan to "Fox-chan",
            SpeciesChoice.Lion to "Lion",
            SpeciesChoice.Tiger to "Tiger",
            SpeciesChoice.Wolf to "Wolf",
        )
        assertAllStable("SpeciesChoice", expect) { it.label }
    }

    @Test fun alignment_labels_and_taglines_are_stable_english() {
        val expectLabel = mapOf(
            Alignment.Dominant to "Dominant",
            Alignment.Submissive to "Submissive",
            Alignment.Switch to "Switch",
            Alignment.UnalignedPrivate to "Unaligned (private)",
        )
        val expectTagline = mapOf(
            Alignment.Dominant to "you set the rules",
            Alignment.Submissive to "you follow the rules",
            Alignment.Switch to "you do both — depending",
            Alignment.UnalignedPrivate to "skip the kink framing entirely",
        )
        assertAllStable("Alignment.label", expectLabel) { it.label }
        assertAllStable("Alignment.tagline", expectTagline) { it.tagline }
    }

    @Test fun role_id_labels_are_stable_english() {
        val expect = mapOf(
            RoleId.Work to "Work",
            RoleId.University to "University",
            RoleId.Freelance to "Freelance",
            RoleId.Workout to "Workout",
            RoleId.Study to "Study",
            RoleId.Kink to "Kink",
            RoleId.Social to "Social",
            RoleId.Family to "Family",
            RoleId.Creative to "Creative",
            RoleId.Hobby to "Hobby",
            RoleId.Recovery to "Recovery",
            RoleId.Spirituality to "Spirituality",
            RoleId.Health to "Health",
            RoleId.Finance to "Finance",
            RoleId.Household to "Household",
            RoleId.PetCare to "Pet Care",
            RoleId.SelfCare to "Self-Care",
        )
        assertAllStable("RoleId", expect) { it.label }
    }

    @Test fun honorific_labels_are_stable_english() {
        val expect = mapOf(
            Honorific.None to "(none)",
            Honorific.Sir to "Sir",
            Honorific.Daddy to "Daddy",
            Honorific.Master to "Master",
            Honorific.Mistress to "Mistress",
            Honorific.Owner to "Owner",
            Honorific.Keeper to "Keeper",
            Honorific.Captain to "Captain",
        )
        assertAllStable("Honorific", expect) { it.label }
    }

    @Test fun tone_register_labels_are_stable_english() {
        val expect = mapOf(
            ToneRegister.SoftKinky to "soft-kinky",
            ToneRegister.Playful to "playful",
            ToneRegister.WarmNeutral to "warm-neutral",
            ToneRegister.Clinical to "clinical",
            ToneRegister.StrictClinical to "strict-clinical",
        )
        assertAllStable("ToneRegister", expect) { it.label }
    }

    @Test fun emoji_density_labels_are_stable_english() {
        val expect = mapOf(
            EmojiDensity.Off to "off",
            EmojiDensity.Light to "light",
            EmojiDensity.Medium to "medium",
            EmojiDensity.Heavy to "heavy",
        )
        assertAllStable("EmojiDensity", expect) { it.label }
    }

    @Test fun lifestyle_ids_are_stable_english() {
        // Lifestyle has no `.label` field — `Lifestyle.id` is the wire-format
        // identifier instead (composed via `labelString(Alignment)` in
        // `ui/a11y/EnumLabels.kt`). Lock the IDs because they participate in
        // the `mode.toml` / wizard payload.
        val expect = mapOf(
            Lifestyle.SingleFree to "single-free",
            Lifestyle.SingleStrict to "single-strict",
            Lifestyle.SingleRoutine to "single-routine",
            Lifestyle.PartneredFree to "partnered-free",
            Lifestyle.PartneredStrict to "partnered-strict",
            Lifestyle.PartneredRoutine to "partnered-routine",
        )
        assertAllStable("Lifestyle.id", expect) { it.id }
    }

    /**
     * Exhaustiveness sentinel: if a new variant is added without updating
     * the per-enum map above, the entry-set sizes diverge and this catches
     * it. A targeted message points at the enum that drifted.
     */
    @Test fun every_variant_is_covered() {
        check(SpeciesChoice.entries.size == 9) { "SpeciesChoice variant count changed — update stability test" }
        check(Alignment.entries.size == 4) { "Alignment variant count changed — update stability test" }
        check(RoleId.entries.size == 17) { "RoleId variant count changed — update stability test" }
        check(Honorific.entries.size == 8) { "Honorific variant count changed — update stability test" }
        check(ToneRegister.entries.size == 5) { "ToneRegister variant count changed — update stability test" }
        check(EmojiDensity.entries.size == 4) { "EmojiDensity variant count changed — update stability test" }
        check(Lifestyle.entries.size == 6) { "Lifestyle variant count changed — update stability test" }
    }

    private fun <T> assertAllStable(name: String, expect: Map<T, String>, project: (T) -> String) {
        for ((variant, want) in expect) {
            val got = project(variant)
            check(got == want) {
                "$name.$variant wire-format label drifted: expected \"$want\" but got \"$got\" — " +
                    "this is part of the on-disk repo schema (T-4 in docs/plans/translations.md). " +
                    "If translation is needed, route through ui/a11y/EnumLabels.kt; never change .label."
            }
        }
    }
}
