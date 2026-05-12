package com.eight87.strictlykeptboy.ui.wizard

/**
 * Phase K — design-a-lifestyle wizard state model (K.1 / LW-A).
 *
 * Immutable [WizardDraft] is the single source of truth that travels through
 * the nine wizard screens. All mutations are pure copies; disk writes happen
 * exclusively in [com.eight87.strictlykeptboy.ui.wizard.WizardScaffolder]
 * on Screen 8 (K.9 / LW-I) — the wizard is otherwise pure draft state.
 */

/** 8 species + custom pack (K.3 / LW-C). */
enum class SpeciesChoice(val id: String, val label: String) {
    Bat("bat", "Bat"),
    Bunny("bunny", "Bunny"),
    Cat("cat", "Cat"),
    Fox("fox", "Fox"),
    Lion("lion", "Lion"),
    Tiger("tiger", "Tiger"),
    Wolf("wolf", "Wolf"),
    ChooseYourOwn("custom", "Choose your own"),
}

/** Four alignments per K.4 / LW-D. */
enum class Alignment(val id: String, val label: String, val tagline: String) {
    Dominant("dominant", "Dominant", "you set the rules"),
    Submissive("submissive", "Submissive", "you follow the rules"),
    Switch("switch", "Switch", "you do both — depending"),
    UnalignedPrivate("unaligned-private", "Unaligned (private)", "skip the kink framing entirely"),
}

/** Lifestyle phrasing depends on [Alignment] per K.5 / LW-E. */
enum class Lifestyle(val id: String) {
    SingleFree("single-free"),
    SingleStrict("single-strict"),
    SingleRoutine("single-routine"),
    PartneredFree("partnered-free"),
    PartneredStrict("partnered-strict"),
    PartneredRoutine("partnered-routine"),
}

/** Labels derived from the active alignment (K.5 / LW-E.2). */
fun Lifestyle.labelFor(alignment: Alignment): String {
    val partnered = id.startsWith("partnered")
    val partnerWord = if (partnered) "partnered" else "single"
    return when (id.substringAfter('-')) {
        "free" -> "$partnerWord — free"
        "routine" -> "$partnerWord — routine"
        "strict" -> when (alignment) {
            Alignment.Submissive -> "$partnerWord — strictly-kept"
            Alignment.Dominant -> "$partnerWord — strictly-keeping"
            Alignment.Switch -> "$partnerWord — strictly-shared"
            // Should never surface in UI for unaligned (we hide strict mode).
            Alignment.UnalignedPrivate -> "$partnerWord — routine"
        }
        else -> id
    }
}

/** 17 roles per K.6 / LW-F.1. `self-care` is always-on; `kink` hidden when unaligned. */
enum class RoleId(val id: String, val label: String, val emoji: String, val priority: Int) {
    Work("work", "Work", "💼", 550),
    University("university", "University", "🎓", 550),
    Freelance("freelance", "Freelance", "🧰", 540),
    Workout("workout", "Workout", "🏋️", 500),
    Study("study", "Study", "📚", 500),
    Kink("kink", "Kink", "🖤", 700),
    Social("social", "Social", "☕", 400),
    Family("family", "Family", "👪", 500),
    Creative("creative", "Creative", "🎨", 400),
    Hobby("hobby", "Hobby", "🎲", 380),
    Recovery("recovery", "Recovery", "🛁", 600),
    Spirituality("spirituality", "Spirituality", "🕯️", 500),
    Health("health", "Health", "🩺", 650),
    Finance("finance", "Finance", "💸", 450),
    Household("household", "Household", "🧺", 450),
    PetCare("pet-care", "Pet Care", "🐾", 600),
    SelfCare("self-care", "Self-Care", "🪥", 600);

    companion object {
        fun fromId(id: String): RoleId? = entries.firstOrNull { it.id == id }
    }
}

/** Per-atom template id; each atom becomes one recurring event on scaffold. */
data class TemplateId(val role: RoleId, val atomId: String, val label: String) {
    /** Whether this template is kink-tagged (filtered under neutral / unaligned-private). */
    val isKinkTagged: Boolean get() = role == RoleId.Kink
}

/** Atomic-template registry per K.7 / LW-G.2 (locked roster). */
object TemplateRegistry {
    private val atoms: Map<RoleId, List<String>> = mapOf(
        RoleId.SelfCare to listOf(
            "brush-teeth", "shower", "shave", "skincare", "hydration-check",
            "sunlight-10min", "bedtime-wind-down",
        ),
        RoleId.Workout to listOf(
            "pushups", "situps", "squats", "pull-ups", "planks",
            "cardio-30min", "stretch-15min", "foam-roll",
        ),
        RoleId.Study to listOf(
            "focus-block-25min", "deep-work-90min", "review-flashcards",
            "read-20pg", "weekly-review",
        ),
        RoleId.Work to listOf(
            "deep-work-am", "deep-work-pm", "inbox-triage", "stand-up-15min",
            "weekly-planning", "weekly-shutdown",
        ),
        RoleId.University to listOf(
            "lecture-block", "lab-block", "assignment-block", "office-hours", "weekly-review",
        ),
        RoleId.Freelance to listOf(
            "client-block", "invoicing", "weekly-pipeline-review", "deep-work-block",
        ),
        RoleId.Kink to listOf(
            "cage-check", "plug-check", "posture-check", "collar-check",
            "edge-and-stop", "kegels", "grooming", "weigh-in",
            "journal-entry", "check-in-with-keeper",
        ),
        RoleId.Social to listOf("call-friend", "coffee-with-someone", "send-a-message", "plan-meetup"),
        RoleId.Family to listOf("call-family", "family-meal", "family-event"),
        RoleId.Creative to listOf("practice-instrument", "draw-15min", "write-page", "edit-photos"),
        RoleId.Hobby to listOf("hobby-block-1h"),
        RoleId.Recovery to listOf("nap-20min", "meditation-10min", "walk-20min", "bath", "therapy-prep"),
        RoleId.Spirituality to listOf(
            "morning-practice", "evening-practice", "weekly-service", "scripture-read",
        ),
        RoleId.Health to listOf("meds-am", "meds-pm", "vitamins", "doctor-followup", "bloodwork-quarterly"),
        RoleId.Finance to listOf("weekly-budget-review", "monthly-budget-close", "invoices-out", "expenses-in"),
        RoleId.Household to listOf("dishes", "laundry", "trash-out", "groceries", "deep-clean-weekly", "bills-out"),
        RoleId.PetCare to listOf("feed-am", "feed-pm", "walk-am", "walk-pm", "litter-clean", "vet-followup"),
    )

    fun templatesFor(role: RoleId): List<TemplateId> =
        (atoms[role] ?: emptyList()).map { atomId ->
            TemplateId(role = role, atomId = atomId, label = humanize(atomId))
        }

    /** Filtered by neutral-mode / unaligned-private. */
    fun visibleTemplatesFor(role: RoleId, neutralMode: Boolean): List<TemplateId> {
        if (neutralMode && role == RoleId.Kink) return emptyList()
        return templatesFor(role)
    }

    private fun humanize(atomId: String): String =
        atomId.replace('-', ' ').replaceFirstChar { it.uppercase() }
}

/** Praise chips per K.5a / LW Screen 3.5. */
object PraiseRegistry {
    val defaults: List<String> = listOf(
        "good boy", "good girl", "good pup", "good kitten", "sweet thing",
        "darling", "love", "buddy", "champ", "you star",
    )
}

/** Pronouns helper. */
data class PronounSet(
    val subject: String,
    val obj: String,
    val possessive: String,
    val reflexive: String,
    val label: String,
) {
    companion object {
        val HeHim = PronounSet("he", "him", "his", "himself", "he/him")
        val SheHer = PronounSet("she", "her", "hers", "herself", "she/her")
        val TheyThem = PronounSet("they", "them", "theirs", "themself", "they/them")
        val ItIts = PronounSet("it", "it", "its", "itself", "it/its")
        val defaults: List<PronounSet> = listOf(HeHim, SheHer, TheyThem, ItIts)
    }
}

/** Honorific options per K.5a; skipped if alignment ∈ {Dominant, UnalignedPrivate}. */
enum class Honorific(val label: String) {
    None("(none)"),
    Sir("Sir"),
    Daddy("Daddy"),
    Master("Master"),
    Mistress("Mistress"),
    Owner("Owner"),
    Keeper("Keeper"),
    Captain("Captain"),
}

/** Tone register per K.5a / LW Screen 3.5. */
enum class ToneRegister(val id: String, val label: String) {
    SoftKinky("soft-kinky", "soft-kinky"),
    Playful("playful", "playful"),
    WarmNeutral("warm-neutral", "warm-neutral"),
    Clinical("clinical", "clinical"),
    StrictClinical("strict-clinical", "strict-clinical"),
}

/** Emoji density per K.5a. */
enum class EmojiDensity(val id: String, val label: String) {
    Off("off", "off"),
    Light("light", "light"),
    Medium("medium", "medium"),
    Heavy("heavy", "heavy"),
}

/** Git setup outcome per K.8 / LW-H. */
sealed interface GitChoice {
    data object PhoneOnly : GitChoice
    data class SelfHostedForgejo(val instanceUrl: String, val oauthClientId: String) : GitChoice
    data class GitHub(val accountHint: String? = null) : GitChoice
}

/**
 * Immutable wizard draft. Copy-on-mutate. Disk writes happen only at scaffold
 * time (K.9). Persists in [SavedStateHandle] across config changes (LW-A.5);
 * for v1 we keep it in-memory in the host composable.
 */
data class WizardDraft(
    val species: SpeciesChoice = SpeciesChoice.Bat,
    val customPackUrl: String = "",
    val alignment: Alignment = Alignment.UnalignedPrivate,
    val praiseTerms: List<String> = listOf("good boy"),
    val pronouns: PronounSet = PronounSet.HeHim,
    val honorific: Honorific = Honorific.None,
    val tone: ToneRegister = ToneRegister.WarmNeutral,
    val emojiDensity: EmojiDensity = EmojiDensity.Medium,
    val lifestyle: Lifestyle = Lifestyle.SingleFree,
    val roles: Set<RoleId> = setOf(RoleId.SelfCare), // self-care always on
    val enabledTemplates: Map<RoleId, Set<String>> = emptyMap(),
    val gitChoice: GitChoice = GitChoice.PhoneOnly,
    val displayName: String = "my calendar",
    val repoIconEmoji: String? = null,
) {
    /** True iff alignment hides kink role / templates / strict-X phrasing. */
    val kinkOff: Boolean get() = alignment == Alignment.UnalignedPrivate

    /** Apply downstream invariants triggered by alignment changes. */
    fun normalize(): WizardDraft {
        val updatedRoles = roles + RoleId.SelfCare
        val filteredRoles = if (kinkOff) updatedRoles - RoleId.Kink else updatedRoles
        // Auto-on kink if kinky alignment and user hasn't explicitly opted out:
        // for screen-level UX, the roles-screen flips it via toggle, not here.
        val honor = if (alignment == Alignment.Dominant || alignment == Alignment.UnalignedPrivate) {
            Honorific.None
        } else honorific
        val toneDefault = if (kinkOff && tone == ToneRegister.SoftKinky) ToneRegister.WarmNeutral
        else tone
        val lifestyleClamped = if (kinkOff && lifestyle == Lifestyle.SingleStrict) Lifestyle.SingleRoutine
        else if (kinkOff && lifestyle == Lifestyle.PartneredStrict) Lifestyle.PartneredRoutine
        else lifestyle
        // Drop kink-template entries if kink-off.
        val filteredTemplates = if (kinkOff) enabledTemplates - RoleId.Kink else enabledTemplates
        return copy(
            roles = filteredRoles,
            honorific = honor,
            tone = toneDefault,
            lifestyle = lifestyleClamped,
            enabledTemplates = filteredTemplates,
        )
    }

    /** True when the draft contains anything worth confirming-before-discarding. */
    val hasUserChoices: Boolean
        get() = species != SpeciesChoice.Bat ||
            alignment != Alignment.UnalignedPrivate ||
            roles != setOf(RoleId.SelfCare) ||
            praiseTerms != listOf("good boy") ||
            honorific != Honorific.None ||
            pronouns != PronounSet.HeHim ||
            tone != ToneRegister.WarmNeutral ||
            enabledTemplates.isNotEmpty() ||
            gitChoice !is GitChoice.PhoneOnly
}

/** Wizard screen identifiers; drives nav + bat-mascot sticker key per LW-A.6. */
enum class WizardScreen(val stickerKey: String) {
    Welcome("welcome-wave"),
    Species("species-greeting"),
    Alignment("align-reaction"),
    Identity("name-tag"),         // Screen 3.5 (K.5a)
    Lifestyle("life-reaction"),
    Roles("roles-notebook"),
    Templates("templates-checklist"),
    Git("git-setup"),
    Scaffold("scaffold-pleased"),
    Done("handoff-wave"),
}
