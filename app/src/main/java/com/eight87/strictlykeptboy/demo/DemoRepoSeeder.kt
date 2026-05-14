package com.eight87.strictlykeptboy.demo

import com.eight87.strictlykeptboy.avatar.AssetPackLoader
import com.eight87.strictlykeptboy.git.AuthorIdentity
import com.eight87.strictlykeptboy.ui.wizard.EmojiDensity
import com.eight87.strictlykeptboy.ui.wizard.Honorific
import com.eight87.strictlykeptboy.ui.wizard.LifestyleCard
import com.eight87.strictlykeptboy.ui.wizard.PronounSet
import com.eight87.strictlykeptboy.ui.wizard.RoleId
import com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice
import com.eight87.strictlykeptboy.ui.wizard.ToneRegister
import com.eight87.strictlykeptboy.ui.wizard.WizardDraft
import com.eight87.strictlykeptboy.ui.wizard.WizardScaffolder
import java.io.File
import java.nio.file.Files

/**
 * Round 2.15 — generates a read-only demo repo for a given [LifestyleCard]
 * perspective. Reuses [WizardScaffolder.materialize] under the hood so the
 * demo repo looks identical to a real one (same calendars, templates, identity
 * + mode TOML), only flagged `isDemo = true` upstream.
 *
 * Always uses Bat as the species (D-2.15.b). Each perspective gets a fixed
 * praise / pronoun / honorific / tone preset so the demo flow always lands on
 * coherent copy without making the user customize.
 *
 * Idempotent: if the target dir already exists it gets wiped first.
 */
object DemoRepoSeeder {

    suspend fun seed(
        parentDir: File,
        perspective: LifestyleCard,
        author: AuthorIdentity = AuthorIdentity("demo", "demo@strictlykeptboy.local"),
        assetPackLoader: AssetPackLoader? = null,
    ): WizardScaffolder.Outcome {
        if (parentDir.exists()) {
            parentDir.walkBottomUp().forEach { it.delete() }
        }
        parentDir.mkdirs()
        val draft = draftFor(perspective)
        return WizardScaffolder.materialize(
            parentDir = parentDir,
            draft = draft,
            author = author,
            assetPackLoader = assetPackLoader,
        )
    }

    fun draftFor(perspective: LifestyleCard): WizardDraft {
        val preset = presets.getValue(perspective)
        return WizardDraft(
            species = SpeciesChoice.Bat,
            alignment = perspective.alignment,
            lifestyle = perspective.lifestyle,
            modePick = perspective.modePick,
            hasPartner = perspective.hasPartner,
            praiseTerms = preset.praiseTerms,
            pronouns = preset.pronouns,
            honorific = preset.honorific,
            tone = preset.tone,
            emojiDensity = preset.emojiDensity,
            displayName = preset.displayName,
            roles = preset.roles,
            enabledTemplates = preset.enabledTemplates,
        )
    }

    /** Folder name for a perspective, used under `<filesDir>/demo-repos/`. */
    fun folderName(perspective: LifestyleCard): String = "demo-${perspective.name.lowercase()}"

    private data class Preset(
        val displayName: String,
        val praiseTerms: List<String>,
        val pronouns: PronounSet,
        val honorific: Honorific,
        val tone: ToneRegister,
        val emojiDensity: EmojiDensity,
        val roles: Set<RoleId> = setOf(RoleId.SelfCare, RoleId.Kink),
        val enabledTemplates: Map<RoleId, Set<String>> = emptyMap(),
    )

    private val presets: Map<LifestyleCard, Preset> = mapOf(
        LifestyleCard.PetKeptByAi to Preset(
            displayName = "demo — pet kept by AI",
            praiseTerms = listOf("good boy", "good pup"),
            pronouns = PronounSet.HeHim,
            honorific = Honorific.Sir,
            tone = ToneRegister.WarmNeutral,
            emojiDensity = EmojiDensity.Medium,
        ),
        LifestyleCard.PetKeptByPartner to Preset(
            displayName = "demo — pet kept by partner",
            praiseTerms = listOf("good boy", "sweet thing"),
            pronouns = PronounSet.HeHim,
            honorific = Honorific.Daddy,
            tone = ToneRegister.SoftKinky,
            emojiDensity = EmojiDensity.Medium,
        ),
        LifestyleCard.PetSelfKept to Preset(
            displayName = "demo — self-kept",
            praiseTerms = listOf("champ", "you star"),
            pronouns = PronounSet.HeHim,
            honorific = Honorific.None,
            tone = ToneRegister.Playful,
            emojiDensity = EmojiDensity.Light,
        ),
        LifestyleCard.DomKeepingPets to Preset(
            displayName = "demo — dom keeping pets",
            praiseTerms = listOf("good kitten", "good pup"),
            pronouns = PronounSet.TheyThem,
            honorific = Honorific.None,
            tone = ToneRegister.WarmNeutral,
            emojiDensity = EmojiDensity.Light,
            roles = setOf(RoleId.SelfCare, RoleId.Kink),
        ),
        LifestyleCard.Switch to Preset(
            displayName = "demo — switch",
            praiseTerms = listOf("good boy", "champ"),
            pronouns = PronounSet.TheyThem,
            honorific = Honorific.Sir,
            tone = ToneRegister.WarmNeutral,
            emojiDensity = EmojiDensity.Medium,
        ),
        LifestyleCard.JustCalendar to Preset(
            displayName = "demo — plain",
            praiseTerms = listOf("good"),
            pronouns = PronounSet.TheyThem,
            honorific = Honorific.None,
            tone = ToneRegister.Clinical,
            emojiDensity = EmojiDensity.Off,
            roles = setOf(RoleId.SelfCare),
        ),
    )
}
