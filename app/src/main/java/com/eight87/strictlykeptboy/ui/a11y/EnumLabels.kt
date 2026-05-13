package com.eight87.strictlykeptboy.ui.a11y

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.repos.AddRepoProvider
import com.eight87.strictlykeptboy.ui.scaffold.ScheduleViewTab
import com.eight87.strictlykeptboy.ui.scaffold.TopDestination
import com.eight87.strictlykeptboy.ui.tasks.QuickAddTarget
import com.eight87.strictlykeptboy.ui.tasks.TaskViewTab
import com.eight87.strictlykeptboy.ui.wizard.Alignment
import com.eight87.strictlykeptboy.ui.wizard.EmojiDensity
import com.eight87.strictlykeptboy.ui.wizard.Honorific
import com.eight87.strictlykeptboy.ui.wizard.Lifestyle
import com.eight87.strictlykeptboy.ui.wizard.RoleId
import com.eight87.strictlykeptboy.ui.wizard.SpeciesChoice
import com.eight87.strictlykeptboy.ui.wizard.ToneRegister

/**
 * Phase U.4 — closes F11 (refactor-solid.md).
 *
 * Centralised enum → `@StringRes labelRes` mapping + Composable + Context
 * label resolvers. Every enum that previously exposed a hardcoded
 * `.label: String` for UI display now routes through here so the string
 * lives in `strings.xml` and participates in the locale-fallback chain
 * (Phase U.5).
 *
 * The legacy `.label: String` fields on these enums are kept BUT are
 * scoped to **wire format** (TOML payload identifiers written to
 * `calendar.toml` / `identity.toml` by [com.eight87.strictlykeptboy.ui.wizard.WizardScaffolder]).
 * Those values are part of the on-disk repo schema (D.3) and must NOT
 * be localised. UI surfaces MUST use [labelString] / [labelRes] instead.
 *
 * SOLID notes:
 * - **S/I:** one file, one job — turn an enum value into a `@StringRes`.
 *   Composables that need a label depend on this thin surface, not on
 *   any UI module.
 * - **O:** new enum variants extend the `when` exhaustively; the compiler
 *   guarantees coverage. Adding a new enum joins the existing pattern.
 * - **D:** UI depends on the abstraction (`labelRes`) rather than the
 *   concrete English literal.
 */

// ---------------------------------------------------------------------------
// TopDestination
// ---------------------------------------------------------------------------

@get:StringRes
val TopDestination.labelRes: Int
    get() = when (this) {
        TopDestination.Schedule -> R.string.dest_schedule
        TopDestination.Tasks -> R.string.dest_tasks
        TopDestination.Together -> R.string.dest_together
        TopDestination.Repos -> R.string.dest_repos
        TopDestination.Wizard -> R.string.dest_wizard
        TopDestination.Reviews -> R.string.dest_reviews
        TopDestination.Settings -> R.string.dest_settings
    }

@Composable
fun TopDestination.labelString(): String = stringResource(labelRes)

fun TopDestination.labelString(context: Context): String = context.getString(labelRes)

// ---------------------------------------------------------------------------
// ScheduleViewTab
// ---------------------------------------------------------------------------

@get:StringRes
val ScheduleViewTab.labelRes: Int
    get() = when (this) {
        ScheduleViewTab.Day -> R.string.schedule_view_tab_day
        ScheduleViewTab.Week -> R.string.schedule_view_tab_week
        ScheduleViewTab.Month -> R.string.schedule_view_tab_month
        ScheduleViewTab.Agenda -> R.string.schedule_view_tab_agenda
        ScheduleViewTab.Year -> R.string.schedule_view_tab_year
    }

@Composable
fun ScheduleViewTab.labelString(): String = stringResource(labelRes)

fun ScheduleViewTab.labelString(context: Context): String = context.getString(labelRes)

// ---------------------------------------------------------------------------
// TaskViewTab
// ---------------------------------------------------------------------------

@get:StringRes
val TaskViewTab.labelRes: Int
    get() = when (this) {
        TaskViewTab.Combined -> R.string.task_view_tab_combined
        TaskViewTab.Today -> R.string.task_view_tab_today
        TaskViewTab.PerList -> R.string.task_view_tab_per_list
        TaskViewTab.Shopping -> R.string.task_view_tab_shopping
        TaskViewTab.Standing -> R.string.task_view_tab_standing
    }

@Composable
fun TaskViewTab.labelString(): String = stringResource(labelRes)

fun TaskViewTab.labelString(context: Context): String = context.getString(labelRes)

// ---------------------------------------------------------------------------
// SpeciesChoice
// ---------------------------------------------------------------------------

@get:StringRes
val SpeciesChoice.labelRes: Int
    get() = when (this) {
        SpeciesChoice.Bat -> R.string.species_bat
        SpeciesChoice.Bunny -> R.string.species_bunny
        SpeciesChoice.Cat -> R.string.species_cat
        SpeciesChoice.Fox -> R.string.species_fox
        SpeciesChoice.Lion -> R.string.species_lion
        SpeciesChoice.Tiger -> R.string.species_tiger
        SpeciesChoice.Wolf -> R.string.species_wolf
        SpeciesChoice.ChooseYourOwn -> R.string.species_choose_your_own
    }

@Composable
fun SpeciesChoice.labelString(): String = stringResource(labelRes)

fun SpeciesChoice.labelString(context: Context): String = context.getString(labelRes)

// ---------------------------------------------------------------------------
// Alignment
// ---------------------------------------------------------------------------

@get:StringRes
val Alignment.labelRes: Int
    get() = when (this) {
        Alignment.Dominant -> R.string.alignment_dominant
        Alignment.Submissive -> R.string.alignment_submissive
        Alignment.Switch -> R.string.alignment_switch
        Alignment.UnalignedPrivate -> R.string.alignment_unaligned_private
    }

@get:StringRes
val Alignment.taglineRes: Int
    get() = when (this) {
        Alignment.Dominant -> R.string.alignment_dominant_tagline
        Alignment.Submissive -> R.string.alignment_submissive_tagline
        Alignment.Switch -> R.string.alignment_switch_tagline
        Alignment.UnalignedPrivate -> R.string.alignment_unaligned_private_tagline
    }

@Composable
fun Alignment.labelString(): String = stringResource(labelRes)

@Composable
fun Alignment.taglineString(): String = stringResource(taglineRes)

fun Alignment.labelString(context: Context): String = context.getString(labelRes)

// ---------------------------------------------------------------------------
// RoleId
// ---------------------------------------------------------------------------

@get:StringRes
val RoleId.labelRes: Int
    get() = when (this) {
        RoleId.Work -> R.string.role_work
        RoleId.University -> R.string.role_university
        RoleId.Freelance -> R.string.role_freelance
        RoleId.Workout -> R.string.role_workout
        RoleId.Study -> R.string.role_study
        RoleId.Kink -> R.string.role_kink
        RoleId.Social -> R.string.role_social
        RoleId.Family -> R.string.role_family
        RoleId.Creative -> R.string.role_creative
        RoleId.Hobby -> R.string.role_hobby
        RoleId.Recovery -> R.string.role_recovery
        RoleId.Spirituality -> R.string.role_spirituality
        RoleId.Health -> R.string.role_health
        RoleId.Finance -> R.string.role_finance
        RoleId.Household -> R.string.role_household
        RoleId.PetCare -> R.string.role_pet_care
        RoleId.SelfCare -> R.string.role_self_care
    }

@Composable
fun RoleId.labelString(): String = stringResource(labelRes)

fun RoleId.labelString(context: Context): String = context.getString(labelRes)

// ---------------------------------------------------------------------------
// Honorific
// ---------------------------------------------------------------------------

@get:StringRes
val Honorific.labelRes: Int
    get() = when (this) {
        Honorific.None -> R.string.honorific_none
        Honorific.Sir -> R.string.honorific_sir
        Honorific.Daddy -> R.string.honorific_daddy
        Honorific.Master -> R.string.honorific_master
        Honorific.Mistress -> R.string.honorific_mistress
        Honorific.Owner -> R.string.honorific_owner
        Honorific.Keeper -> R.string.honorific_keeper
        Honorific.Captain -> R.string.honorific_captain
    }

@Composable
fun Honorific.labelString(): String = stringResource(labelRes)

fun Honorific.labelString(context: Context): String = context.getString(labelRes)

// ---------------------------------------------------------------------------
// ToneRegister
// ---------------------------------------------------------------------------

@get:StringRes
val ToneRegister.labelRes: Int
    get() = when (this) {
        ToneRegister.SoftKinky -> R.string.tone_soft_kinky
        ToneRegister.Playful -> R.string.tone_playful
        ToneRegister.WarmNeutral -> R.string.tone_warm_neutral
        ToneRegister.Clinical -> R.string.tone_clinical
        ToneRegister.StrictClinical -> R.string.tone_strict_clinical
    }

@Composable
fun ToneRegister.labelString(): String = stringResource(labelRes)

fun ToneRegister.labelString(context: Context): String = context.getString(labelRes)

// ---------------------------------------------------------------------------
// EmojiDensity
// ---------------------------------------------------------------------------

@get:StringRes
val EmojiDensity.labelRes: Int
    get() = when (this) {
        EmojiDensity.Off -> R.string.emoji_density_off
        EmojiDensity.Light -> R.string.emoji_density_light
        EmojiDensity.Medium -> R.string.emoji_density_medium
        EmojiDensity.Heavy -> R.string.emoji_density_heavy
    }

@Composable
fun EmojiDensity.labelString(): String = stringResource(labelRes)

fun EmojiDensity.labelString(context: Context): String = context.getString(labelRes)

// ---------------------------------------------------------------------------
// Lifestyle — composed by alignment per K.5 / LW-E.2.
// The wire-format identifier remains [Lifestyle.id]; this resolves the
// human-readable form using the partnered/single + suffix string parts so
// each locale can supply its own joiner ("partnered — strictly-kept").
// ---------------------------------------------------------------------------

@Composable
fun Lifestyle.labelString(alignment: Alignment): String {
    val partnered = id.startsWith("partnered")
    val partnerWord = stringResource(
        if (partnered) R.string.lifestyle_partner_word_partnered
        else R.string.lifestyle_partner_word_single,
    )
    val suffixRes = when (id.substringAfter('-')) {
        "free" -> R.string.lifestyle_suffix_free
        "routine" -> R.string.lifestyle_suffix_routine
        "strict" -> when (alignment) {
            Alignment.Submissive -> R.string.lifestyle_suffix_strictly_kept
            Alignment.Dominant -> R.string.lifestyle_suffix_strictly_keeping
            Alignment.Switch -> R.string.lifestyle_suffix_strictly_shared
            // Should never surface in UI for unaligned (we hide strict mode);
            // fall back to "routine" for Liskov-safe rendering.
            Alignment.UnalignedPrivate -> R.string.lifestyle_suffix_routine
        }
        else -> R.string.lifestyle_suffix_routine
    }
    val suffix = stringResource(suffixRes)
    return stringResource(R.string.lifestyle_label_format, partnerWord, suffix)
}

// ---------------------------------------------------------------------------
// AddRepoProvider
// ---------------------------------------------------------------------------

@get:StringRes
val AddRepoProvider.labelRes: Int
    get() = when (this) {
        AddRepoProvider.GitHub -> R.string.add_repo_provider_github
        AddRepoProvider.Forgejo -> R.string.add_repo_provider_forgejo
        AddRepoProvider.GitLab -> R.string.add_repo_provider_gitlab
        AddRepoProvider.Other -> R.string.add_repo_provider_other
        AddRepoProvider.AlreadyCloned -> R.string.add_repo_provider_already_cloned
    }

@Composable
fun AddRepoProvider.labelString(): String = stringResource(labelRes)

fun AddRepoProvider.labelString(context: Context): String = context.getString(labelRes)

// ---------------------------------------------------------------------------
// QuickAddTarget — sealed; only the data objects have static labels.
// ---------------------------------------------------------------------------

/**
 * Resolves the user-facing label for a [QuickAddTarget]. The
 * [QuickAddTarget.Todolist] case is dynamic (emoji + todolist name)
 * and routes through the underlying `label` directly — the per-list
 * name is user-supplied content, not framework chrome.
 */
@Composable
fun QuickAddTarget.labelString(): String = when (this) {
    is QuickAddTarget.Todolist -> label
    QuickAddTarget.TodayEvent -> stringResource(R.string.quick_add_target_today_event)
    QuickAddTarget.TomorrowEvent -> stringResource(R.string.quick_add_target_tomorrow_event)
}
