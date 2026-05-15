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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
// Round 2.14 — Welcome reinstated as the wizard intro. Explains what the
// next ~minute looks like so users aren't dropped straight into a chooser
// with no context.
private val SCREEN_ORDER: List<WizardScreen> = listOf(
    WizardScreen.Welcome,
    // Round 2.17.D — storage-folder picker. Auto-skipped when the
    // ParentLocationGate already reports Confirmed (returning user).
    WizardScreen.Storage,
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
    initialScreen: WizardScreen = WizardScreen.Welcome,
    initialDraft: WizardDraft = WizardDraft(),
    neutralMode: Boolean = false,
    /**
     * Phase 2.1.I.4 — callback fired when the user requests to generate a
     * share link from the Share-with-dom screen. Caller wires this to the
     * existing [com.eight87.strictlykeptboy.ui.share.ShareSheet] with the
     * just-scaffolded repo and `allowWriteBack` pre-checked.
     */
    onShareWithDom: () -> Unit = {},
    /**
     * Round 2.17.D — prefs surface backing the Storage step. When non-null,
     * the wizard auto-skips the Storage step if `ParentLocationGate` already
     * reports `Confirmed` and auto-advances when the user picks External /
     * Internal and the prefs flip. Tests pass `null` to keep the step
     * stationary so callbacks can be asserted.
     */
    repoStoragePrefs: com.eight87.strictlykeptboy.prefs.RepoStoragePrefs? = null,
    /**
     * Round 2.17.D — "Save on your phone" CTA on the Storage step. Caller
     * launches the SAF tree picker; the wizard listens to
     * [repoStoragePrefs] and advances once the new parent confirms.
     */
    onPickExternalStorage: () -> Unit = {},
    /**
     * Round 2.17.D — "Keep inside the app" CTA on the Storage step. Caller
     * writes `ParentLocation.Internal(filesDir/strictlykeptboy)` and the
     * `.skb-root` marker; the wizard auto-advances when the prefs flip.
     */
    onPickInternalStorage: () -> Unit = {},
) {
    // v1: in-memory draft only. SavedStateHandle-backed persistence is a
    // follow-up (LW-A.5 mid-wizard exit safety beyond config-change is
    // deferred until the wizard ViewModel lands).
    var draft by remember { mutableStateOf(initialDraft) }
    var current by remember { mutableStateOf(initialScreen) }
    // Round 2.17.D — observe parent-location prefs so the Storage step
    // can auto-skip (returning user, gate already Confirmed) and auto-
    // advance when the user picks a parent.
    val storageState = repoStoragePrefs?.state?.collectAsState()
    val contentResolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    var showDiscard by remember { mutableStateOf(false) }
    var scaffoldProgress by remember { mutableStateOf(ScaffoldProgress.Idle) }
    var scaffoldError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val widthClass = LocalWindowWidthSizeClass.current

    fun goNext() {
        val idx = SCREEN_ORDER.indexOf(current)
        if (idx in 0 until SCREEN_ORDER.size - 1) current = SCREEN_ORDER[idx + 1]
    }

    fun goBack() {
        val idx = SCREEN_ORDER.indexOf(current)
        if (idx > 0) current = SCREEN_ORDER[idx - 1]
    }

    // Round 2.9 — Welcome dropped; allow back from any non-Done screen.
    BackHandler(enabled = current != WizardScreen.Done) {
        if (draft.hasUserChoices) showDiscard = true else onCancel()
    }

    // Round 2.17.D — auto-skip Storage when the gate is already Confirmed
    // (returning user re-runs the wizard after blowing away their repo).
    // Re-keyed on the prefs state so an out-of-band Confirmation (e.g. user
    // picked External in another surface while wizard is open) also skips.
    val storageStateValue = storageState?.value
    // Snapshot the prefs value the first time the user arrives on Storage.
    // We treat ANY later change (Internal-→-External or External-→-other)
    // as the user's pick and advance — the gate re-eval is the primary
    // gate, but the snapshot diff is the belt-and-braces fallback for
    // when the gate's `persistedUriPermissions` probe is racy after a
    // freshly-returned SAF grant.
    val initialStorageValue = remember(current == WizardScreen.Storage) {
        if (current == WizardScreen.Storage) storageStateValue else null
    }
    LaunchedEffect(current, storageStateValue) {
        if (current != WizardScreen.Storage) return@LaunchedEffect
        val prefs = repoStoragePrefs ?: return@LaunchedEffect
        val gateState = com.eight87.strictlykeptboy.prefs.ParentLocationGate.evaluate(
            storagePrefs = prefs,
            contentResolver = contentResolver,
        )
        val shouldAdvance =
            gateState is com.eight87.strictlykeptboy.prefs.ParentLocationGate.State.Confirmed ||
            (initialStorageValue != null && storageStateValue != null &&
                storageStateValue != initialStorageValue)
        if (shouldAdvance) {
            val idx = SCREEN_ORDER.indexOf(WizardScreen.Storage)
            if (idx in 0 until SCREEN_ORDER.size - 1) current = SCREEN_ORDER[idx + 1]
        }
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

    Surface(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().testTag(TestTagWizard).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Progress dots
        ProgressRow(currentIndex = SCREEN_ORDER.indexOf(current), total = SCREEN_ORDER.size)
        Spacer(Modifier.height(4.dp))

        val stepContent: @Composable () -> Unit = {
        // Species screen manages its own layout (mascot suppressed,
        // internal scroll on the list region only) so the screen chrome
        // — prompt, explainer, Continue/Back — stays visible while the
        // species options scroll.
        if (current == WizardScreen.Species) {
            SpeciesScreen(
                draft = draft,
                onUpdate = { draft = it.normalize() },
            )
        } else if (current == WizardScreen.Identity) {
            IdentityScreen(
                draft = draft,
                onUpdate = { draft = it.normalize() },
            )
        } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BatMascotSticker(screen = current)
            when (current) {
                WizardScreen.Welcome -> WelcomeScreen()
                WizardScreen.Storage -> StorageStep(
                    // External pick fires the SAF picker; the wizard waits
                    // for `prefs.state` to flip to Confirmed before
                    // advancing (LaunchedEffect above).
                    onPickExternal = onPickExternalStorage,
                    // Internal pick is synchronous (no SAF round-trip), so
                    // wrap the callback to advance the wizard immediately.
                    // The Phase A gate is now Confirmed (marker written +
                    // Internal pref set), so subsequent re-entries would
                    // auto-skip this screen.
                    onPickInternal = {
                        onPickInternalStorage()
                        val idx = SCREEN_ORDER.indexOf(WizardScreen.Storage)
                        if (idx in 0 until SCREEN_ORDER.size - 1) {
                            current = SCREEN_ORDER[idx + 1]
                        }
                    },
                )
                WizardScreen.Species -> Unit // handled above
                WizardScreen.Identity -> Unit // handled above
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
        // Round 2.17.D — Storage step has no Continue/Back row; the user
        // must pick one of the two cards. Back is still handled via the
        // system back button (BackHandler above), which triggers the
        // discard-confirm dialog when needed.
        if (current != WizardScreen.Scaffold &&
            current != WizardScreen.Done &&
            current != WizardScreen.ShareWithDom &&
            current != WizardScreen.Storage
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
private fun WelcomeScreen() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardWelcome),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.wizard_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            stringResource(R.string.wizard_welcome_blurb),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.wizard_welcome_footnote),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Screen 2 Species ----------------------------------------------------------

@Composable
private fun SpeciesScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    // Round 2.14 — fixed chrome (prompt + explainer) at top; the list of
    // hero-sized species cards scrolls in its own region so the wizard's
    // Continue / Back stay visible. Anime variants (Cat-chan / Fox-chan)
    // sink to the bottom so realistic species lead the list.
    val animeIds = setOf(SpeciesChoice.CatChan, SpeciesChoice.FoxChan)
    val cards = SpeciesChoice.entries.sortedBy { if (it in animeIds) 1 else 0 }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTagWizardSpecies),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.wizard_species_prompt), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.wizard_species_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("Wizard-Species-CustomizeInfo"),
        ) {
            Text(
                text = stringResource(R.string.wizard_species_customize_later_info),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            cards.forEach { species ->
                val selected = draft.species == species
                Card(
                    onClick = { onUpdate(draft.copy(species = species)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("Wizard-Species-${species.id}"),
                    colors = if (selected) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ) else CardDefaults.cardColors(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = ComposeAlign.CenterHorizontally,
                    ) {
                        Text(
                            text = speciesPreviewEmoji(species),
                            style = MaterialTheme.typography.displayLarge,
                        )
                        Text(
                            species.labelString(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null)
                    }
                }
            }
        }
    }
}

/** Stock-placeholder emoji preview for each species — replaced when
 *  bundled-pack artwork ships (Phase WW). */
private fun speciesPreviewEmoji(species: SpeciesChoice): String = when (species) {
    SpeciesChoice.Bat -> "🦇"
    SpeciesChoice.Bunny -> "🐰"
    SpeciesChoice.Cat -> "🐱"
    SpeciesChoice.CatChan -> "🐱"
    SpeciesChoice.Fox -> "🦊"
    SpeciesChoice.FoxChan -> "🦊"
    SpeciesChoice.Lion -> "🦁"
    SpeciesChoice.Tiger -> "🐯"
    SpeciesChoice.Wolf -> "🐺"
}

// --- Screen 3.5 Identity (praise, pronouns, honorific, tone, emoji) -----------

@Composable
private fun IdentityScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    val showHonorific = draft.alignment == Alignment.Submissive || draft.alignment == Alignment.Switch
    var customPraise by remember { mutableStateOf("") }
    // Merge defaults with whatever the user already picked (incl.
    // custom-added terms) so custom entries render as their own chips.
    val praiseChipTerms = (PraiseRegistry.defaults + draft.praiseTerms).distinct()
    Column(
        modifier = Modifier.fillMaxSize().testTag(TestTagWizardIdentity),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.wizard_identity_prompt), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.wizard_identity_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Praise chips (multi-select) + custom-term input.
            Text(stringResource(R.string.wizard_identity_praise_label), style = MaterialTheme.typography.bodyMedium)
            WrappingChipRow {
                praiseChipTerms.forEach { term ->
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
            Row(
                verticalAlignment = ComposeAlign.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = customPraise,
                    onValueChange = { customPraise = it },
                    label = { Text("Add custom term") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("Wizard-Praise-Custom-Input"),
                )
                TextButton(
                    enabled = customPraise.isNotBlank() && customPraise.trim() !in draft.praiseTerms,
                    onClick = {
                        val term = customPraise.trim()
                        if (term.isNotEmpty()) {
                            onUpdate(draft.copy(praiseTerms = draft.praiseTerms + term))
                            customPraise = ""
                        }
                    },
                    modifier = Modifier.testTag("Wizard-Praise-Custom-Add"),
                ) { Text("Add") }
            }

            // Pronouns as compact chips.
            Text(stringResource(R.string.wizard_identity_pronouns_label), style = MaterialTheme.typography.bodyMedium)
            WrappingChipRow {
                PronounSet.defaults.forEach { p ->
                    FilterChip(
                        selected = draft.pronouns == p,
                        onClick = { onUpdate(draft.copy(pronouns = p)) },
                        label = { Text(p.label) },
                        modifier = Modifier.testTag("Wizard-Pronouns-${p.subject}"),
                    )
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
        val matched = LifestyleCard.fromDraft(draft)
        if (matched == null || matched == LifestyleCard.JustCalendar) {
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
        // Round 2.12 — language-equivalence explainer. The app defaults
        // to pet-coded wording everywhere (matching the project name),
        // but sub/pet/bottom describe the same role here, and dom/owner
        // likewise. Every label is customizable post-wizard in
        // Settings → Identity (praise terms, honorifics, role labels).
        Card(
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Lifestyle-LanguageExplainer"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = stringResource(R.string.wizard_lifestyle_language_explainer),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp),
            )
        }
        // Round 2.12 — JustCalendar is now picked on the FramingChoice
        // screen, not here. Hiding it keeps the kink-path picker focused.
        for (card in LifestyleCard.entries.filter { it != LifestyleCard.JustCalendar }) {
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
