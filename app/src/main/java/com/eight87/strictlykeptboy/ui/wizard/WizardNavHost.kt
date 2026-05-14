package com.eight87.strictlykeptboy.ui.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment as ComposeAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.a11y.labelString
import com.eight87.strictlykeptboy.ui.a11y.taglineString
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.MasterDetailLayout
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane
import kotlinx.coroutines.launch

const val TestTagWizardPreviewPane = "Wizard-PreviewPane"

// Test tags
const val TestTagWizard = "Wizard"
const val TestTagWizardWelcome = "Wizard-Welcome"
const val TestTagWizardSpecies = "Wizard-Species"
const val TestTagWizardIdentity = "Wizard-Identity"
// Phase 2.2.B — single Lifestyle screen (6 cards) subsumes the old
// Alignment + Lifestyle + Mode screens. TestTagWizardLifestyle stays
// stable so snapshot tests don't churn; the per-card tags are
// `Wizard-LifestyleCard-<enum-name>`.
const val TestTagWizardLifestyle = "Wizard-Lifestyle"
const val TestTagWizardRoles = "Wizard-Roles"
const val TestTagWizardTemplates = "Wizard-Templates"
const val TestTagWizardGit = "Wizard-Git"
const val TestTagWizardScaffold = "Wizard-Scaffold"
const val TestTagWizardDone = "Wizard-Done"
const val TestTagWizardShareWithDom = "Wizard-ShareWithDom"
const val TestTagWizardNext = "Wizard-Next"
const val TestTagWizardBack = "Wizard-Back"
const val TestTagWizardSkip = "Wizard-Skip"
const val TestTagWizardDiscardDialog = "Wizard-DiscardDialog"

/**
 * Linear screen sequence. Phase 2.2.B collapsed Alignment + Lifestyle +
 * Mode into a single six-card Lifestyle screen (D-2.2.c). 12 → 10
 * screens. Phase 2.1.I.4 appends a Share-with-dom screen after Done;
 * it's only entered when [shouldShowShareWithDom] returns true.
 */
// Round 2.9 — Welcome screen dropped per user direction. First screen on
// fresh launch is Species (sticker pack pick). The Welcome composable
// stays in the file in case a re-entry path wants it later, but it's not
// in SCREEN_ORDER anymore.
private val SCREEN_ORDER: List<WizardScreen> = listOf(
    WizardScreen.Species,
    WizardScreen.Identity,
    WizardScreen.Lifestyle,
    WizardScreen.Roles,
    WizardScreen.Templates,
    WizardScreen.Git,
    WizardScreen.Scaffold,
    WizardScreen.Done,
    WizardScreen.ShareWithDom,
)

/**
 * Phase 2.1.I.4 — the Share-with-dom screen only renders for sub/switch
 * users who are kept-by-human (or AI but want to also share). Strict
 * predicate: alignment ∈ {Submissive, Switch} AND mode pick = KeptByHuman.
 * Keeping it strict prevents the screen from showing up for users who
 * picked KeptByAi (where there is no human dom to share with).
 */
internal fun shouldShowShareWithDom(draft: WizardDraft): Boolean {
    val alignmentOk = draft.alignment == Alignment.Submissive ||
        draft.alignment == Alignment.Switch
    val keptByHuman = draft.effectiveModePick == WizardModePick.KeptByHuman
    return alignmentOk && keptByHuman
}

/**
 * Phase K.1 / LW-A — wizard host. Replaces the stub destination in AppScaffold.
 *
 * @param initialScreen optionally start later than Welcome (K.12 re-run path).
 * @param initialDraft optionally pre-seed from current repo state (K.12).
 * @param neutralMode when true, hides kink role + kink templates (K.14).
 * @param onScaffold called when the user reaches the Scaffold screen — the
 *   caller drives [WizardScaffolder.materialize] and reports completion.
 * @param onFinish called after the Done CTA; navigates back to Schedule.
 * @param onCancel called when the user discards mid-wizard.
 */
@Composable
fun WizardNavHost(
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onScaffold: suspend (WizardDraft) -> Result<Unit>,
    modifier: Modifier = Modifier,
    initialScreen: WizardScreen = WizardScreen.Species,
    initialDraft: WizardDraft = WizardDraft(),
    neutralMode: Boolean = false,
    /**
     * Phase 2.1.I.4 — callback fired when the user requests to generate a
     * share link from the Share-with-dom screen. Caller wires this to the
     * existing [com.eight87.strictlykeptboy.ui.share.ShareSheet] with the
     * just-scaffolded repo and `allowWriteBack` pre-checked.
     */
    onShareWithDom: () -> Unit = {},
) {
    // v1: in-memory draft only. SavedStateHandle-backed persistence is a
    // follow-up (LW-A.5 mid-wizard exit safety beyond config-change is
    // deferred until the wizard ViewModel lands).
    var draft by remember { mutableStateOf(initialDraft) }
    var current by remember { mutableStateOf(initialScreen) }
    var showDiscard by remember { mutableStateOf(false) }
    var scaffoldProgress by remember { mutableStateOf(ScaffoldProgress.Idle) }
    var scaffoldError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val widthClass = LocalWindowWidthSizeClass.current

    fun goNext() {
        val idx = SCREEN_ORDER.indexOf(current)
        if (idx in 0 until SCREEN_ORDER.size - 1) {
            current = SCREEN_ORDER[idx + 1]
        }
    }

    fun goBack() {
        val idx = SCREEN_ORDER.indexOf(current)
        if (idx > 0) current = SCREEN_ORDER[idx - 1]
    }

    // Round 2.9 — Welcome dropped; allow back from any non-Done screen.
    BackHandler(enabled = current != WizardScreen.Done) {
        if (draft.hasUserChoices) showDiscard = true else onCancel()
    }

    if (showDiscard) {
        AlertDialog(
            modifier = Modifier.testTag(TestTagWizardDiscardDialog),
            onDismissRequest = { showDiscard = false },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    onCancel()
                }) { Text(stringResource(R.string.wizard_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text(stringResource(R.string.wizard_discard_keep_going)) }
            },
            title = { Text(stringResource(R.string.wizard_discard_dialog_title)) },
            text = { Text(stringResource(R.string.wizard_discard_dialog_body)) },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().testTag(TestTagWizard).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Progress dots
        ProgressRow(currentIndex = SCREEN_ORDER.indexOf(current), total = SCREEN_ORDER.size)
        Spacer(Modifier.height(4.dp))

        val stepContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BatMascotSticker(screen = current)
            when (current) {
                WizardScreen.Welcome -> WelcomeScreen(onGo = ::goNext)
                WizardScreen.Species -> SpeciesScreen(
                    draft = draft,
                    onUpdate = { draft = it.normalize() },
                )
                WizardScreen.Identity -> IdentityScreen(
                    draft = draft,
                    onUpdate = { draft = it.normalize() },
                )
                WizardScreen.Lifestyle -> LifestyleCardScreen(
                    draft = draft,
                    onUpdate = { draft = it },
                )
                WizardScreen.Roles -> RolesScreen(
                    draft = draft,
                    neutralMode = neutralMode,
                    onUpdate = { draft = it.normalize() },
                )
                WizardScreen.Templates -> TemplatesScreen(
                    draft = draft,
                    neutralMode = neutralMode,
                    onUpdate = { draft = it.normalize() },
                )
                WizardScreen.Git -> GitScreen(
                    draft = draft,
                    onUpdate = { draft = it.normalize() },
                )
                WizardScreen.Scaffold -> {
                    ScaffoldScreen(progress = scaffoldProgress, error = scaffoldError)
                    if (scaffoldProgress == ScaffoldProgress.Idle) {
                        scope.launch {
                            scaffoldProgress = ScaffoldProgress.Running
                            val result = onScaffold(draft)
                            if (result.isSuccess) {
                                scaffoldProgress = ScaffoldProgress.Done
                                current = WizardScreen.Done
                            } else {
                                scaffoldProgress = ScaffoldProgress.Failed
                                scaffoldError = result.exceptionOrNull()?.message
                            }
                        }
                    }
                }
                WizardScreen.Done -> DoneScreen(
                    draft = draft,
                    // Phase 2.1.I.4 — if a human dom is selected, route to
                    // the Share-with-dom screen first; otherwise finish.
                    onOpen = {
                        if (shouldShowShareWithDom(draft)) {
                            current = WizardScreen.ShareWithDom
                        } else {
                            onFinish()
                        }
                    },
                )
                WizardScreen.ShareWithDom -> ShareWithDomScreen(
                    draft = draft,
                    onGenerateLink = {
                        onShareWithDom()
                        onFinish()
                    },
                    onSkip = onFinish,
                )
            }
        }
        }

        // H.1 — two-pane on Medium/Expanded: step on the left, identity-driven
        // preview on the right. Compact keeps the single-column flow (preview
        // already lives inline inside IdentityScreen + DoneScreen there).
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (widthClass.isTwoPane()) {
                MasterDetailLayout(
                    widthClass = widthClass,
                    master = stepContent,
                    detail = {
                        WizardIdentityPreviewPane(
                            draft = draft,
                            screen = current,
                        )
                    },
                )
            } else {
                stepContent()
            }
        }

        // Buttons
        if (current != WizardScreen.Scaffold &&
            current != WizardScreen.Done &&
            current != WizardScreen.ShareWithDom
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = ::goBack,
                    modifier = Modifier.testTag(TestTagWizardBack),
                ) { Text(stringResource(R.string.wizard_back)) }
                // Round 2.6 — Species (sticker pack) is mandatory; only
                // Templates retains a skip affordance now.
                if (current == WizardScreen.Templates) {
                    TextButton(
                        onClick = ::goNext,
                        modifier = Modifier.testTag(TestTagWizardSkip),
                    ) { Text(stringResource(R.string.wizard_skip)) }
                }
                Button(
                    onClick = ::goNext,
                    modifier = Modifier.testTag(TestTagWizardNext),
                ) { Text(stringResource(R.string.wizard_continue)) }
            }
        } else if (current == WizardScreen.Scaffold && scaffoldProgress == ScaffoldProgress.Failed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    // Reset and retry by re-entering the scaffold screen.
                    scaffoldProgress = ScaffoldProgress.Idle
                    scaffoldError = null
                }) { Text(stringResource(R.string.wizard_try_again)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.wizard_cancel)) }
            }
        }
    }
}

private enum class ScaffoldProgress { Idle, Running, Done, Failed }

@Composable
private fun ProgressRow(currentIndex: Int, total: Int) {
    val frac = (currentIndex + 1).toFloat() / total.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { frac },
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
        Text(
            text = stringResource(R.string.wizard_step_of, currentIndex + 1, total),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun BatMascotSticker(screen: WizardScreen) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        contentAlignment = ComposeAlign.Center,
    ) {
        Column(horizontalAlignment = ComposeAlign.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.about_bat),
                contentDescription = stringResource(R.string.cd_wizard_mascot, screen.stickerKey),
                modifier = Modifier.size(96.dp),
            )
            Text(
                text = screen.stickerKey,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.testTag("Wizard-StickerKey-${screen.name}"),
            )
        }
    }
}

// --- Screen 1 Welcome ----------------------------------------------------------

@Composable
private fun WelcomeScreen(onGo: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardWelcome),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = ComposeAlign.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.wizard_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            stringResource(R.string.wizard_welcome_blurb),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onGo,
            modifier = Modifier.testTag(TestTagWizardNext),
        ) { Text(stringResource(R.string.wizard_welcome_cta)) }
        Text(
            stringResource(R.string.wizard_welcome_footnote),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// --- Screen 2 Species ----------------------------------------------------------

@Composable
private fun SpeciesScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardSpecies),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.wizard_species_prompt), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.wizard_species_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Use a single-column flow on phones; 2-col grid is acceptable but
        // simpler to use a Column of clickable cards for v1.
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.height(380.dp),
        ) {
            items(SpeciesChoice.entries.toList()) { species ->
                val selected = draft.species == species
                Card(
                    onClick = { onUpdate(draft.copy(species = species)) },
                    colors = if (selected) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) else CardDefaults.cardColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("Wizard-Species-${species.id}"),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(species.labelString(), style = MaterialTheme.typography.titleMedium)
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null)
                    }
                }
            }
        }
        if (draft.species == SpeciesChoice.ChooseYourOwn) {
            // Round 2.6 — no git clone here. Picking "Customize later"
            // scaffolds the repo with the Bat pack and tells the user how
            // to swap in their own artwork after first launch.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("Wizard-Species-CustomizeLaterInfo"),
            ) {
                Text(
                    text = stringResource(R.string.wizard_species_customize_later_info),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

// --- Screen 3.5 Identity (praise, pronouns, honorific, tone, emoji) -----------

@Composable
private fun IdentityScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    val showHonorific = draft.alignment == Alignment.Submissive || draft.alignment == Alignment.Switch
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardIdentity),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.wizard_identity_prompt), style = MaterialTheme.typography.titleMedium)

        // Praise chips (multi-select).
        Text(stringResource(R.string.wizard_identity_praise_label), style = MaterialTheme.typography.bodyMedium)
        WrappingChipRow {
            PraiseRegistry.defaults.forEach { term ->
                val on = term in draft.praiseTerms
                FilterChip(
                    selected = on,
                    onClick = {
                        val next = if (on) draft.praiseTerms - term else draft.praiseTerms + term
                        onUpdate(draft.copy(praiseTerms = next.ifEmpty { listOf("good boy") }))
                    },
                    label = { Text(term) },
                    modifier = Modifier.testTag("Wizard-Praise-${term.replace(' ', '-')}"),
                )
            }
        }

        // Pronouns radio.
        Text(stringResource(R.string.wizard_identity_pronouns_label), style = MaterialTheme.typography.bodyMedium)
        for (p in PronounSet.defaults) {
            Row(verticalAlignment = ComposeAlign.CenterVertically) {
                RadioButton(
                    selected = draft.pronouns == p,
                    onClick = { onUpdate(draft.copy(pronouns = p)) },
                    modifier = Modifier.testTag("Wizard-Pronouns-${p.subject}"),
                )
                Text(p.label)
            }
        }

        if (showHonorific) {
            Text(stringResource(R.string.wizard_identity_honorific_label), style = MaterialTheme.typography.bodyMedium)
            WrappingChipRow {
                Honorific.entries.forEach { h ->
                    FilterChip(
                        selected = draft.honorific == h,
                        onClick = { onUpdate(draft.copy(honorific = h)) },
                        label = { Text(h.labelString()) },
                        modifier = Modifier.testTag("Wizard-Honorific-${h.name}"),
                    )
                }
            }
        }

        Text(stringResource(R.string.wizard_identity_tone_label), style = MaterialTheme.typography.bodyMedium)
        WrappingChipRow {
            ToneRegister.entries.forEach { t ->
                FilterChip(
                    selected = draft.tone == t,
                    onClick = { onUpdate(draft.copy(tone = t)) },
                    label = { Text(t.labelString()) },
                    modifier = Modifier.testTag("Wizard-Tone-${t.id}"),
                )
            }
        }

        Text(stringResource(R.string.wizard_identity_emoji_label), style = MaterialTheme.typography.bodyMedium)
        WrappingChipRow {
            EmojiDensity.entries.forEach { e ->
                FilterChip(
                    selected = draft.emojiDensity == e,
                    onClick = { onUpdate(draft.copy(emojiDensity = e)) },
                    label = { Text(e.labelString()) },
                    modifier = Modifier.testTag("Wizard-Emoji-${e.id}"),
                )
            }
        }

        // Live preview
        Card(
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Identity-Preview"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.wizard_identity_preview_label), style = MaterialTheme.typography.labelMedium)
                val honoraryPrefix = if (draft.honorific != Honorific.None) "${draft.honorific.labelString()}, " else ""
                val praiseDefault = stringResource(R.string.wizard_identity_praise_default)
                val praise = draft.praiseTerms.firstOrNull() ?: praiseDefault
                val emoji = when (draft.emojiDensity) {
                    EmojiDensity.Off -> ""
                    EmojiDensity.Light -> " ✓"
                    EmojiDensity.Medium -> " ✓ ;3"
                    EmojiDensity.Heavy -> " ✓ ;3 ✨"
                }
                Text(
                    stringResource(R.string.wizard_identity_preview_line, honoraryPrefix, praise, emoji),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// --- Screen 4 Lifestyle (Phase 2.2.B — six-card collapse) ----------------------

/**
 * Phase 2.2.B — collapsed Lifestyle screen. Six mutually-exclusive
 * cards (D-2.2.c); each card atomically writes (alignment, lifestyle,
 * modePick, hasPartner) via [WizardDraft.applyLifestyleCard]. Pre-
 * selected default = [LifestyleCard.PetKeptByAi] (solo-sub kept by an
 * AI dom — the project's genesis use-case).
 */
@Composable
private fun LifestyleCardScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    // Reverse-lookup the currently-active card; falls back to Default
    // when the draft is fresh (no card chosen yet) or to null when the
    // user has hand-edited via Settings into a non-card combination.
    val current = LifestyleCard.fromDraft(draft) ?: LifestyleCard.Default

    // Phase 2.2.B — auto-apply the default card on first entry so the
    // user can simply "Continue" without tapping and have the wizard
    // emit the matching (alignment, lifestyle, modePick, hasPartner)
    // tuple on materialization. Idempotent: re-applying the same card
    // is a no-op for the equality check via [LifestyleCard.fromDraft].
    LaunchedEffect(Unit) {
        if (LifestyleCard.fromDraft(draft) == null) {
            onUpdate(draft.applyLifestyleCard(LifestyleCard.Default))
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardLifestyle),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.wizard_lifestyle_card_prompt),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            stringResource(R.string.wizard_lifestyle_card_blurb),
            style = MaterialTheme.typography.bodySmall,
        )
        for (card in LifestyleCard.entries) {
            val selected = card == current
            Card(
                onClick = { onUpdate(draft.applyLifestyleCard(card)) },
                colors = if (selected) CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) else CardDefaults.cardColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("Wizard-LifestyleCard-${card.name}"),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "${card.emoji}  ${stringResource(card.titleRes)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(card.subtitleRes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@get:androidx.annotation.StringRes
private val LifestyleCard.titleRes: Int
    get() = when (this) {
        LifestyleCard.PetKeptByAi -> R.string.lifestyle_card_pet_kept_by_ai_title
        LifestyleCard.PetKeptByPartner -> R.string.lifestyle_card_pet_kept_by_partner_title
        LifestyleCard.PetSelfKept -> R.string.lifestyle_card_pet_self_kept_title
        LifestyleCard.DomKeepingPets -> R.string.lifestyle_card_dom_keeping_pets_title
        LifestyleCard.Switch -> R.string.lifestyle_card_switch_title
        LifestyleCard.JustCalendar -> R.string.lifestyle_card_just_calendar_title
    }

@get:androidx.annotation.StringRes
private val LifestyleCard.subtitleRes: Int
    get() = when (this) {
        LifestyleCard.PetKeptByAi -> R.string.lifestyle_card_pet_kept_by_ai_subtitle
        LifestyleCard.PetKeptByPartner -> R.string.lifestyle_card_pet_kept_by_partner_subtitle
        LifestyleCard.PetSelfKept -> R.string.lifestyle_card_pet_self_kept_subtitle
        LifestyleCard.DomKeepingPets -> R.string.lifestyle_card_dom_keeping_pets_subtitle
        LifestyleCard.Switch -> R.string.lifestyle_card_switch_subtitle
        LifestyleCard.JustCalendar -> R.string.lifestyle_card_just_calendar_subtitle
    }

// --- Screen 5 Roles ------------------------------------------------------------

@Composable
private fun RolesScreen(
    draft: WizardDraft,
    neutralMode: Boolean,
    onUpdate: (WizardDraft) -> Unit,
) {
    val visibleRoles = RoleId.entries.filter { role ->
        !((neutralMode || draft.kinkOff) && role == RoleId.Kink)
    }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardRoles),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.wizard_roles_prompt), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.wizard_roles_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        // Pinned self-care chip.
        Row(verticalAlignment = ComposeAlign.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.cd_wizard_roles_always_on))
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.wizard_roles_selfcare_locked))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.height(520.dp),
        ) {
            items(visibleRoles) { role ->
                val isSelfCare = role == RoleId.SelfCare
                val on = role in draft.roles
                FilterChip(
                    selected = on,
                    onClick = {
                        if (isSelfCare) return@FilterChip // non-toggleable
                        val next = if (on) draft.roles - role else draft.roles + role
                        onUpdate(draft.copy(roles = next))
                    },
                    label = {
                        Row(verticalAlignment = ComposeAlign.CenterVertically) {
                            Text(role.emoji)
                            Spacer(Modifier.size(4.dp))
                            Text(role.labelString())
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("Wizard-Role-${role.id}"),
                    enabled = !isSelfCare || on,
                )
            }
        }
    }
}

// --- Screen 6 Templates --------------------------------------------------------

@Composable
private fun TemplatesScreen(
    draft: WizardDraft,
    neutralMode: Boolean,
    onUpdate: (WizardDraft) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardTemplates),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.wizard_templates_prompt),
            style = MaterialTheme.typography.titleMedium,
        )
        val orderedRoles = RoleId.entries.filter { it in draft.roles }
        for (role in orderedRoles) {
            val templates = TemplateRegistry.visibleTemplatesFor(
                role = role,
                neutralMode = neutralMode || draft.kinkOff,
            )
            if (templates.isEmpty()) continue
            Card(modifier = Modifier.fillMaxWidth().testTag("Wizard-TemplateGroup-${role.id}")) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        stringResource(R.string.wizard_templates_role_label, role.emoji, role.labelString()),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    WrappingChipRow {
                        templates.forEach { t ->
                            val enabledSet = draft.enabledTemplates[role]
                            // Default: all on if no explicit set yet (smart-defaults).
                            val on = enabledSet?.contains(t.atomId) ?: true
                            FilterChip(
                                selected = on,
                                onClick = {
                                    val current = enabledSet
                                        ?: templates.map { it.atomId }.toSet()
                                    val next = if (on) current - t.atomId else current + t.atomId
                                    val merged = draft.enabledTemplates + (role to next)
                                    onUpdate(draft.copy(enabledTemplates = merged))
                                },
                                label = { Text(t.label) },
                                modifier = Modifier.testTag("Wizard-Template-${role.id}-${t.atomId}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Screen 7 Git --------------------------------------------------------------

@Composable
private fun GitScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardGit),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.wizard_git_prompt), style = MaterialTheme.typography.titleMedium)

        // Phone-only (default, recommended)
        Card(
            onClick = { onUpdate(draft.copy(gitChoice = GitChoice.PhoneOnly)) },
            colors = if (draft.gitChoice is GitChoice.PhoneOnly) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else CardDefaults.cardColors(),
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-PhoneOnly"),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.wizard_git_phone_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.wizard_git_phone_blurb),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Self-hosted Forgejo
        val selfHosted = draft.gitChoice as? GitChoice.SelfHostedForgejo
        Card(
            onClick = {
                onUpdate(draft.copy(gitChoice = GitChoice.SelfHostedForgejo("", "")))
            },
            colors = if (selfHosted != null) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else CardDefaults.cardColors(),
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-Forgejo"),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.wizard_git_forgejo_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.wizard_git_forgejo_blurb),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (selfHosted != null) {
                    OutlinedTextField(
                        value = selfHosted.instanceUrl,
                        onValueChange = {
                            onUpdate(draft.copy(gitChoice = selfHosted.copy(instanceUrl = it)))
                        },
                        label = { Text(stringResource(R.string.wizard_git_forgejo_instance_url)) },
                        modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-Forgejo-Url"),
                    )
                    OutlinedTextField(
                        value = selfHosted.oauthClientId,
                        onValueChange = {
                            onUpdate(draft.copy(gitChoice = selfHosted.copy(oauthClientId = it)))
                        },
                        label = { Text(stringResource(R.string.wizard_git_forgejo_oauth_client_id)) },
                        modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-Forgejo-Client"),
                    )
                    Text(
                        stringResource(R.string.wizard_git_forgejo_oauth_note),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // GitHub
        Card(
            onClick = { onUpdate(draft.copy(gitChoice = GitChoice.GitHub())) },
            colors = if (draft.gitChoice is GitChoice.GitHub) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else CardDefaults.cardColors(),
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-GitHub"),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.wizard_git_github_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.wizard_git_github_blurb),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (draft.gitChoice is GitChoice.GitHub) {
                    Text(
                        stringResource(R.string.wizard_git_github_oauth_note),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        OutlinedTextField(
            value = draft.displayName,
            onValueChange = { onUpdate(draft.copy(displayName = it)) },
            label = { Text(stringResource(R.string.wizard_git_calendar_repo_name)) },
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-Name"),
        )
    }
}

// --- Screen 8 Scaffold ---------------------------------------------------------

@Composable
private fun ScaffoldScreen(progress: ScaffoldProgress, error: String?) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardScaffold),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = ComposeAlign.CenterHorizontally,
    ) {
        Text(
            when (progress) {
                ScaffoldProgress.Idle -> stringResource(R.string.wizard_scaffold_idle)
                ScaffoldProgress.Running -> stringResource(R.string.wizard_scaffold_running)
                ScaffoldProgress.Done -> stringResource(R.string.wizard_scaffold_done)
                ScaffoldProgress.Failed -> stringResource(R.string.wizard_scaffold_failed)
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (progress == ScaffoldProgress.Running || progress == ScaffoldProgress.Idle) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        val displayError = error ?: if (progress == ScaffoldProgress.Failed) stringResource(R.string.wizard_scaffold_unknown_error) else null
        if (displayError != null) {
            Text(displayError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// --- Screen 9 Done -------------------------------------------------------------

@Composable
private fun DoneScreen(draft: WizardDraft, onOpen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardDone),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = ComposeAlign.CenterHorizontally,
    ) {
        Text(stringResource(R.string.wizard_done_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.wizard_done_blurb, draft.species.labelString()),
            style = MaterialTheme.typography.bodyMedium,
        )
        // Live-ish preview
        val fallbackNowCard = stringResource(R.string.wizard_done_fallback_now_card_title)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.wizard_done_now_card_label), style = MaterialTheme.typography.labelMedium)
                Text(
                    stringResource(R.string.wizard_done_next_up, chooseFirstNowCardTitle(draft, fallbackNowCard)),
                    style = MaterialTheme.typography.titleMedium,
                )
                val praiseDefault = stringResource(R.string.wizard_identity_praise_default)
                val praise = draft.praiseTerms.firstOrNull() ?: praiseDefault
                Text(stringResource(R.string.wizard_done_praise_line, praise), style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick = onOpen,
            modifier = Modifier.testTag("Wizard-Done-Open"),
        ) { Text(stringResource(R.string.wizard_done_open)) }
    }
}

private fun chooseFirstNowCardTitle(draft: WizardDraft, fallback: String): String {
    // Prefer a self-care or workout atom; falls back to first role/template.
    val ordered = listOf(RoleId.SelfCare, RoleId.Workout, RoleId.Work).filter { it in draft.roles }
    for (role in ordered) {
        val tmpls = TemplateRegistry.templatesFor(role)
        if (tmpls.isNotEmpty()) return tmpls.first().label
    }
    return fallback
}

// --- Phase 2.1.I.4 Share-with-dom screen ---------------------------------------

@Composable
private fun ShareWithDomScreen(
    draft: WizardDraft,
    onGenerateLink: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardShareWithDom),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = ComposeAlign.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.wizard_share_with_dom_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.wizard_share_with_dom_blurb),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onGenerateLink,
            modifier = Modifier.testTag("Wizard-ShareWithDom-Generate"),
        ) {
            Text(stringResource(R.string.wizard_share_with_dom_generate))
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier.testTag("Wizard-ShareWithDom-Skip"),
        ) {
            Text(stringResource(R.string.wizard_share_with_dom_skip))
        }
    }
}

// --- Small helpers -------------------------------------------------------------

/**
 * Phase 2.1.H.1 — read-only identity-driven live preview that sits in the
 * detail pane on Medium/Expanded widths. Mirrors the inline preview cards
 * inside [IdentityScreen] + [DoneScreen] but stays visible across every
 * wizard step so tablet users see their choices accumulate. No edits
 * happen here — the step forms on the left own state.
 */
@Composable
private fun WizardIdentityPreviewPane(draft: WizardDraft, screen: WizardScreen) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(TestTagWizardPreviewPane),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.wizard_identity_preview_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val honoraryPrefix = if (draft.honorific != Honorific.None) {
            "${draft.honorific.labelString()}, "
        } else ""
        val praiseDefault = stringResource(R.string.wizard_identity_praise_default)
        val praise = draft.praiseTerms.firstOrNull() ?: praiseDefault
        val emoji = when (draft.emojiDensity) {
            EmojiDensity.Off -> ""
            EmojiDensity.Light -> " ✓"
            EmojiDensity.Medium -> " ✓ ;3"
            EmojiDensity.Heavy -> " ✓ ;3 ✨"
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    stringResource(R.string.wizard_identity_preview_line, honoraryPrefix, praise, emoji),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Summary of choices so far, regardless of which screen we're on.
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Species: ${draft.species.labelString()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Alignment: ${draft.alignment.labelString()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (draft.honorific != Honorific.None) {
                    Text(
                        "Honorific: ${draft.honorific.labelString()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "Tone: ${draft.tone.labelString()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (draft.praiseTerms.isNotEmpty()) {
                    Text(
                        "Praise: ${draft.praiseTerms.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (draft.roles.isNotEmpty()) {
                    val roleLabels = draft.roles.map { it.labelString() }
                    Text(
                        "Roles: ${roleLabels.joinToString(", ")}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Text(
            "Step: ${screen.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WrappingChipRow(content: @Composable () -> Unit) {
    // Compose has no built-in FlowRow in M3 below 1.4; we approximate with
    // a horizontally-scrolling Row for v1. Switch to FlowRow once available.
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) { content() }
}
