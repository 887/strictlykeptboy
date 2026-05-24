package com.eight87.strictlykeptboy.ui.wizard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.adaptive.LocalWindowWidthSizeClass
import com.eight87.strictlykeptboy.ui.adaptive.MasterDetailLayout
import com.eight87.strictlykeptboy.ui.adaptive.isTwoPane
import com.eight87.strictlykeptboy.ui.wizard.screens.DoneScreen
import com.eight87.strictlykeptboy.ui.wizard.screens.GitScreen
import com.eight87.strictlykeptboy.ui.wizard.screens.IdentityScreen
import com.eight87.strictlykeptboy.ui.wizard.screens.LifestyleCardScreen
import com.eight87.strictlykeptboy.ui.wizard.screens.RolesScreen
import com.eight87.strictlykeptboy.ui.wizard.screens.ScaffoldScreen
import com.eight87.strictlykeptboy.ui.wizard.screens.ShareWithDomScreen
import com.eight87.strictlykeptboy.ui.wizard.screens.SpeciesScreen
import com.eight87.strictlykeptboy.ui.wizard.screens.TemplatesScreen
import com.eight87.strictlykeptboy.ui.wizard.screens.WelcomeScreen
import com.eight87.strictlykeptboy.ui.wizard.screens.WizardIdentityPreviewPane
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
    // Round 2.18 Phase I — default-calendar-app onboarding card. Auto-
    // skipped when SystemCalendarGlobalPrefs.defaultCalendarOnboardingShown
    // is already true (every run after the first).
    WizardScreen.DefaultCalendar,
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
    /**
     * Round 2.18 Phase I — prefs surface backing the default-calendar
     * onboarding card. When null (tests, unconfigured callers) the
     * step renders unconditionally and "mark shown" is a no-op so the
     * UI is still testable in isolation. When non-null, the wizard
     * auto-skips the step if [com.eight87.strictlykeptboy.system.SystemCalendarGlobalPrefs.defaultCalendarOnboardingShown]
     * is already true, and marks it true on first arrival.
     */
    systemCalendarPrefs: com.eight87.strictlykeptboy.system.SystemCalendarPrefsStore? = null,
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

    // Round 2.18 Phase I — auto-skip the DefaultCalendar step when the
    // one-shot tracker has already flipped. The skip decision is taken
    // ONCE on first arrival (the LaunchedEffect re-keys only when
    // `current` changes), so the user's own "mark shown" on advance
    // doesn't yank the card out from under them mid-interaction. The
    // tracker flip happens when the user advances OFF the step, not on
    // arrival, so the card actually stays visible for them to read.
    LaunchedEffect(current) {
        if (current != WizardScreen.DefaultCalendar) return@LaunchedEffect
        val prefs = systemCalendarPrefs ?: return@LaunchedEffect
        if (prefs.globalState.value.defaultCalendarOnboardingShown) {
            val idx = SCREEN_ORDER.indexOf(WizardScreen.DefaultCalendar)
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
        // W2.4 / U-8 + U-10 — show the user-visible step counter only.
        // Scaffold runs invisibly (auto-progress → Done), and ShareWithDom
        // only renders for kept-by-human users. Filter the order to what
        // the user will actually see for THIS draft.
        val visibleOrder = remember(draft) {
            SCREEN_ORDER.filter { s ->
                s != WizardScreen.Scaffold &&
                (s != WizardScreen.ShareWithDom || shouldShowShareWithDom(draft))
            }
        }
        val visibleIndex = visibleOrder.indexOf(current).let { idx ->
            if (idx >= 0) idx else (visibleOrder.indexOf(WizardScreen.Done).coerceAtLeast(0))
        }
        ProgressRow(currentIndex = visibleIndex, total = visibleOrder.size)
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
                WizardScreen.DefaultCalendar -> DefaultCalendarStep(
                    onAdvance = {
                        systemCalendarPrefs?.markDefaultCalendarOnboardingShown()
                        goNext()
                    },
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
            current != WizardScreen.Storage &&
            current != WizardScreen.DefaultCalendar
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

internal enum class ScaffoldProgress { Idle, Running, Done, Failed }

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

@Composable
internal fun WrappingChipRow(content: @Composable () -> Unit) {
    // Compose has no built-in FlowRow in M3 below 1.4; we approximate with
    // a horizontally-scrolling Row for v1. Switch to FlowRow once available.
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) { content() }
}
