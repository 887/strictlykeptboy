package com.eight87.strictlykeptboy.ui.wizard

/**
 * Phase K — design-a-lifestyle wizard state model (K.1 / LW-A).
 *
 * Immutable [WizardDraft] is the single source of truth that travels through
 * the nine wizard screens. All mutations are pure copies; disk writes happen
 * exclusively in [com.eight87.strictlykeptboy.ui.wizard.WizardScaffolder]
 * on Screen 8 (K.9 / LW-I) — the wizard is otherwise pure draft state.
 */

/**
 * 8 species + custom pack (K.3 / LW-C).
 *
 * Phase U.4 / F11 note: [label] is the **wire-format** identifier — used for
 * stable TOML payload (e.g. `species = "Bat"` in repo identity files) and for
 * fallback toString display. The user-facing translatable label is resolved
 * via `SpeciesChoice.labelString()` in `ui/a11y/EnumLabels.kt`. Do NOT change
 * these literals — they participate in the on-disk repo schema.
 */
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

/**
 * Four alignments per K.4 / LW-D.
 *
 * Phase U.4 / F11 note: [label] + [tagline] are **wire-format** identifiers.
 * Translatable UI labels resolve via `Alignment.labelString()` /
 * `Alignment.taglineString()` in `ui/a11y/EnumLabels.kt`.
 */
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

/**
 * 17 roles per K.6 / LW-F.1. `self-care` is always-on; `kink` hidden when
 * unaligned.
 *
 * Phase U.4 / F11 note: [label] is **wire-format** — written to
 * `calendars/<id>/calendar.toml` `name = ...` field by [WizardScaffolder].
 * Translatable UI labels resolve via `RoleId.labelString()` in `ui/a11y/`.
 */
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

    // Phase 2.1.I.5 — per-atom time-of-day bucket (HH:mm). Starter heuristic;
    // the existing wizard stub stacked every atom at 09:00, producing N
    // overlapping 15-min blocks per role. This map spreads them across a
    // morning / midday / evening band so the user's first-day schedule
    // actually looks like a day. NOT a final per-(alignment, lifestyle)
    // matrix — that's a future phase. Anything not listed falls back to
    // [DefaultDtstart].
    //
    // Bucket logic: morning (07:00–10:00), midday (12:00–14:00),
    // afternoon/evening (17:00–22:00). Pre/post-sleep anchors (brush-teeth,
    // meds, journal) lean to start/end of day.
    private val DTSTART_BUCKETS: Map<String, String> = mapOf(
        // SelfCare — morning + evening anchors
        "brush-teeth" to "07:00",
        "shower" to "07:30",
        "shave" to "07:45",
        "skincare" to "07:50",
        "hydration-check" to "12:00",
        "sunlight-10min" to "12:15",
        "bedtime-wind-down" to "22:00",
        // Workout — late afternoon
        "pushups" to "17:00",
        "situps" to "17:05",
        "squats" to "17:10",
        "pull-ups" to "17:15",
        "planks" to "17:20",
        "cardio-30min" to "17:00",
        "stretch-15min" to "17:45",
        "foam-roll" to "17:30",
        // Study
        "focus-block-25min" to "10:00",
        "deep-work-90min" to "10:00",
        "review-flashcards" to "20:00",
        "read-20pg" to "20:30",
        "weekly-review" to "18:00",
        // Work
        "deep-work-am" to "09:00",
        "deep-work-pm" to "14:00",
        "inbox-triage" to "08:30",
        "stand-up-15min" to "09:30",
        "weekly-planning" to "09:00",
        "weekly-shutdown" to "17:30",
        // University
        "lecture-block" to "10:00",
        "lab-block" to "13:00",
        "assignment-block" to "18:00",
        "office-hours" to "15:00",
        // Freelance
        "client-block" to "10:00",
        "invoicing" to "16:00",
        "weekly-pipeline-review" to "17:00",
        "deep-work-block" to "09:30",
        // Kink — start/end-of-day check-ins
        "cage-check" to "07:00",
        "plug-check" to "21:30",
        "posture-check" to "12:00",
        "collar-check" to "07:15",
        "edge-and-stop" to "22:15",
        "kegels" to "12:30",
        "grooming" to "07:45",
        "weigh-in" to "07:30",
        "journal-entry" to "22:00",
        "check-in-with-keeper" to "21:00",
        // Social
        "call-friend" to "18:00",
        "coffee-with-someone" to "10:30",
        "send-a-message" to "12:00",
        "plan-meetup" to "19:00",
        // Family
        "call-family" to "19:30",
        "family-meal" to "18:30",
        "family-event" to "12:00",
        // Creative
        "practice-instrument" to "19:00",
        "draw-15min" to "20:00",
        "write-page" to "08:00",
        "edit-photos" to "20:30",
        // Hobby
        "hobby-block-1h" to "20:00",
        // Recovery
        "nap-20min" to "14:00",
        "meditation-10min" to "06:30",
        "walk-20min" to "12:30",
        "bath" to "21:00",
        "therapy-prep" to "16:00",
        // Spirituality
        "morning-practice" to "06:30",
        "evening-practice" to "21:00",
        "weekly-service" to "10:00",
        "scripture-read" to "07:00",
        // Health
        "meds-am" to "08:00",
        "meds-pm" to "21:00",
        "vitamins" to "08:00",
        "doctor-followup" to "10:00",
        "bloodwork-quarterly" to "08:30",
        // Finance
        "weekly-budget-review" to "10:00",
        "monthly-budget-close" to "10:00",
        "invoices-out" to "11:00",
        "expenses-in" to "20:00",
        // Household
        "dishes" to "19:30",
        "laundry" to "10:00",
        "trash-out" to "07:30",
        "groceries" to "17:30",
        "deep-clean-weekly" to "10:00",
        "bills-out" to "11:00",
        // PetCare
        "feed-am" to "07:00",
        "feed-pm" to "18:00",
        "walk-am" to "07:30",
        "walk-pm" to "18:30",
        "litter-clean" to "10:00",
        "vet-followup" to "10:00",
    )

    /**
     * Fallback for atoms not present in [DTSTART_BUCKETS]; we keep 09:00
     * for parity with the legacy WizardScaffolder behaviour so unexpected
     * atoms still get a sensible default rather than midnight.
     */
    const val DefaultDtstart: String = "09:00"

    /** Returns "HH:mm" time-of-day for an atomId. */
    fun dtstartHmFor(atomId: String): String = DTSTART_BUCKETS[atomId] ?: DefaultDtstart

    /**
     * Phase 2.1.I.6 — inverted-default atom registry. These atoms exist
     * primarily as "did you do your habit?" prompts whose default truth
     * value is YES (completed-by-schedule). Daily hygiene, meds, feeding,
     * shower, and similar baseline routines.
     */
    private val INVERTED_ATOMS: Set<String> = setOf(
        "brush-teeth", "shower", "skincare", "hydration-check",
        "bedtime-wind-down", "sunlight-10min",
        "meds-am", "meds-pm", "vitamins",
        "feed-am", "feed-pm", "walk-am", "walk-pm", "litter-clean",
        "cage-check", "plug-check", "collar-check", "kegels",
        "morning-practice", "evening-practice",
        "meditation-10min",
    )

    fun isInvertedAtom(atomId: String): Boolean = atomId in INVERTED_ATOMS
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

/**
 * Honorific options per K.5a; skipped if alignment ∈ {Dominant, UnalignedPrivate}.
 *
 * Phase U.4 / F11 note: [label] is **wire-format** — written to
 * `identity.toml` `[honorific].term = ...` by [WizardScaffolder].
 * Translatable UI labels resolve via `Honorific.labelString()`.
 */
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

/**
 * Tone register per K.5a / LW Screen 3.5.
 *
 * Phase U.4 / F11 note: [label] is **wire-format** (matches [id] by convention).
 * Translatable UI labels resolve via `ToneRegister.labelString()`.
 */
enum class ToneRegister(val id: String, val label: String) {
    SoftKinky("soft-kinky", "soft-kinky"),
    Playful("playful", "playful"),
    WarmNeutral("warm-neutral", "warm-neutral"),
    Clinical("clinical", "clinical"),
    StrictClinical("strict-clinical", "strict-clinical"),
}

/**
 * Emoji density per K.5a.
 *
 * Phase U.4 / F11 note: [label] is **wire-format**.
 * Translatable UI labels resolve via `EmojiDensity.labelString()`.
 */
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
    /**
     * Phase 2.1.I.3 — mode pick. Null until the Mode screen sets it; the
     * Mode screen computes its default from [alignment] on first render
     * (Submissive → KeptByAi, everything else → Free). Materialization
     * resolves null → Free.
     */
    val modePick: WizardModePick? = null,
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

    /**
     * Phase 2.1.I.3 — effective mode pick. Falls back to [defaultModeFor]
     * when the user hasn't visited the Mode screen yet.
     */
    val effectiveModePick: WizardModePick
        get() = modePick ?: defaultModeFor(alignment)

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
    // Phase 2.1.I.3 — Mode screen sits between Lifestyle and Roles. Default
    // = strictly-kept for Submissive, free otherwise. One-tap override.
    Mode("mode-pick"),
    Roles("roles-notebook"),
    Templates("templates-checklist"),
    Git("git-setup"),
    Scaffold("scaffold-pleased"),
    Done("handoff-wave"),
    // Phase 2.1.I.4 — Share-with-dom screen only renders on Done path when
    // alignment ∈ {Submissive, Switch} AND mode = strictly-kept AND a
    // human-dom is selected. Skippable. Reuses ShareSheet.
    ShareWithDom("share-with-dom"),
}

/**
 * Phase 2.1.I.3 — wizard-side mode pick. Maps to [RepoMode] on
 * materialization. `KeptByAi` writes mode=strictly-kept + a builtin
 * dom_persona; `KeptByHuman` writes mode=strictly-kept with no persona
 * (a share-link will populate write_back_target later); `Free` and
 * `SelfKeep` map directly.
 *
 * Wire-format strings live alongside [RepoMode.wire]; the wizard label
 * is rendered separately so the screen can show friendlier copy.
 */
enum class WizardModePick(val id: String) {
    Free("free"),
    KeptByAi("kept-by-ai"),
    KeptByHuman("kept-by-human"),
    SelfKeep("self-keep"),
}

/**
 * Phase 2.1.I.3 — alignment-driven default. Submissive → KeptByAi (the
 * project's genesis use-case is solo Submissive kept by an AI dom);
 * everything else (Dominant / Switch / UnalignedPrivate) → Free.
 */
fun defaultModeFor(alignment: Alignment): WizardModePick = when (alignment) {
    Alignment.Submissive -> WizardModePick.KeptByAi
    else -> WizardModePick.Free
}
