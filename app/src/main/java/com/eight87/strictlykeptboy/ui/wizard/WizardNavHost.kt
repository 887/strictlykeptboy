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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment as ComposeAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import kotlinx.coroutines.launch

// Test tags
const val TestTagWizard = "Wizard"
const val TestTagWizardWelcome = "Wizard-Welcome"
const val TestTagWizardSpecies = "Wizard-Species"
const val TestTagWizardAlignment = "Wizard-Alignment"
const val TestTagWizardIdentity = "Wizard-Identity"
const val TestTagWizardLifestyle = "Wizard-Lifestyle"
const val TestTagWizardRoles = "Wizard-Roles"
const val TestTagWizardTemplates = "Wizard-Templates"
const val TestTagWizardGit = "Wizard-Git"
const val TestTagWizardScaffold = "Wizard-Scaffold"
const val TestTagWizardDone = "Wizard-Done"
const val TestTagWizardNext = "Wizard-Next"
const val TestTagWizardBack = "Wizard-Back"
const val TestTagWizardSkip = "Wizard-Skip"
const val TestTagWizardDiscardDialog = "Wizard-DiscardDialog"

/** Linear screen sequence. Renumbered into a single source-of-truth ordering. */
private val SCREEN_ORDER: List<WizardScreen> = listOf(
    WizardScreen.Welcome,
    WizardScreen.Species,
    WizardScreen.Alignment,
    WizardScreen.Identity,
    WizardScreen.Lifestyle,
    WizardScreen.Roles,
    WizardScreen.Templates,
    WizardScreen.Git,
    WizardScreen.Scaffold,
    WizardScreen.Done,
)

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

    BackHandler(enabled = current != WizardScreen.Welcome && current != WizardScreen.Done) {
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
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) { Text("Keep going") }
            },
            title = { Text("Discard wizard progress?") },
            text = { Text("Nothing has been written to disk yet — leaving now drops everything you've chosen.") },
        )
    }

    Column(
        modifier = modifier.fillMaxSize().testTag(TestTagWizard).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Progress dots
        ProgressRow(currentIndex = SCREEN_ORDER.indexOf(current), total = SCREEN_ORDER.size)
        Spacer(Modifier.height(4.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BatMascotSticker(screen = current)
            when (current) {
                WizardScreen.Welcome -> WelcomeScreen(onGo = ::goNext)
                WizardScreen.Species -> SpeciesScreen(
                    draft = draft,
                    onUpdate = { draft = it.normalize() },
                )
                WizardScreen.Alignment -> AlignmentScreen(
                    draft = draft,
                    onUpdate = { draft = it.normalize() },
                )
                WizardScreen.Identity -> IdentityScreen(
                    draft = draft,
                    onUpdate = { draft = it.normalize() },
                )
                WizardScreen.Lifestyle -> LifestyleScreen(
                    draft = draft,
                    onUpdate = { draft = it.normalize() },
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
                                    ?: "Unknown error"
                            }
                        }
                    }
                }
                WizardScreen.Done -> DoneScreen(draft = draft, onOpen = onFinish)
            }
        }

        // Buttons
        if (current != WizardScreen.Welcome &&
            current != WizardScreen.Scaffold &&
            current != WizardScreen.Done
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = ::goBack,
                    modifier = Modifier.testTag(TestTagWizardBack),
                ) { Text("Back") }
                if (current == WizardScreen.Species || current == WizardScreen.Templates) {
                    TextButton(
                        onClick = ::goNext,
                        modifier = Modifier.testTag(TestTagWizardSkip),
                    ) { Text("Skip") }
                }
                Button(
                    onClick = ::goNext,
                    modifier = Modifier.testTag(TestTagWizardNext),
                ) { Text("Continue") }
            }
        } else if (current == WizardScreen.Scaffold && scaffoldProgress == ScaffoldProgress.Failed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    // Reset and retry by re-entering the scaffold screen.
                    scaffoldProgress = ScaffoldProgress.Idle
                    scaffoldError = null
                }) { Text("Try again") }
                TextButton(onClick = onCancel) { Text("Cancel") }
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
            text = "Step ${currentIndex + 1} of $total",
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
                contentDescription = "bat mascot — ${screen.stickerKey}",
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
            "let's design your lifestyle",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "by the end you'll have a living calendar, not an empty app.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onGo,
            modifier = Modifier.testTag(TestTagWizardNext),
        ) { Text("let's go") }
        Text(
            "you can change any of this later in Settings → Add more to my lifestyle.",
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
        Text("Pick your avatar species", style = MaterialTheme.typography.titleMedium)
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
                        Text(species.label, style = MaterialTheme.typography.titleMedium)
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null)
                    }
                }
            }
        }
        if (draft.species == SpeciesChoice.ChooseYourOwn) {
            OutlinedTextField(
                value = draft.customPackUrl,
                onValueChange = { onUpdate(draft.copy(customPackUrl = it)) },
                label = { Text("Custom sticker-pack URL (GitHub)") },
                modifier = Modifier.fillMaxWidth().testTag("Wizard-Species-PackUrl"),
            )
        }
    }
}

// --- Screen 3 Alignment --------------------------------------------------------

@Composable
private fun AlignmentScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardAlignment),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("How would you like the rules to work?", style = MaterialTheme.typography.titleMedium)
        for (a in Alignment.entries) {
            val selected = draft.alignment == a
            Card(
                onClick = { onUpdate(draft.copy(alignment = a)) },
                colors = if (selected) CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) else CardDefaults.cardColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("Wizard-Alignment-${a.id}"),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(a.label, style = MaterialTheme.typography.titleMedium)
                    Text(a.tagline, style = MaterialTheme.typography.bodySmall)
                }
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
        Text("How should the app speak to you?", style = MaterialTheme.typography.titleMedium)

        // Praise chips (multi-select).
        Text("Praise terms (pick any — alternated):", style = MaterialTheme.typography.bodyMedium)
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
        Text("Pronouns:", style = MaterialTheme.typography.bodyMedium)
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
            Text("Honorific:", style = MaterialTheme.typography.bodyMedium)
            WrappingChipRow {
                Honorific.entries.forEach { h ->
                    FilterChip(
                        selected = draft.honorific == h,
                        onClick = { onUpdate(draft.copy(honorific = h)) },
                        label = { Text(h.label) },
                        modifier = Modifier.testTag("Wizard-Honorific-${h.name}"),
                    )
                }
            }
        }

        Text("Tone register:", style = MaterialTheme.typography.bodyMedium)
        WrappingChipRow {
            ToneRegister.entries.forEach { t ->
                FilterChip(
                    selected = draft.tone == t,
                    onClick = { onUpdate(draft.copy(tone = t)) },
                    label = { Text(t.label) },
                    modifier = Modifier.testTag("Wizard-Tone-${t.id}"),
                )
            }
        }

        Text("Emoji density:", style = MaterialTheme.typography.bodyMedium)
        WrappingChipRow {
            EmojiDensity.entries.forEach { e ->
                FilterChip(
                    selected = draft.emojiDensity == e,
                    onClick = { onUpdate(draft.copy(emojiDensity = e)) },
                    label = { Text(e.label) },
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
                Text("Preview", style = MaterialTheme.typography.labelMedium)
                val honoraryPrefix = if (draft.honorific != Honorific.None) "${draft.honorific.label}, " else ""
                val praise = draft.praiseTerms.firstOrNull() ?: "good boy"
                val emoji = when (draft.emojiDensity) {
                    EmojiDensity.Off -> ""
                    EmojiDensity.Light -> " ✓"
                    EmojiDensity.Medium -> " ✓ ;3"
                    EmojiDensity.Heavy -> " ✓ ;3 ✨"
                }
                Text(
                    "${honoraryPrefix}you did it — $praise.$emoji",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// --- Screen 4 Lifestyle --------------------------------------------------------

@Composable
private fun LifestyleScreen(draft: WizardDraft, onUpdate: (WizardDraft) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TestTagWizardLifestyle),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "What's your day shaped like?",
            style = MaterialTheme.typography.titleMedium,
        )

        val options: List<Lifestyle> = when (draft.alignment) {
            Alignment.UnalignedPrivate -> listOf(
                Lifestyle.SingleFree, Lifestyle.SingleRoutine,
                Lifestyle.PartneredFree, Lifestyle.PartneredRoutine,
            )
            else -> listOf(
                Lifestyle.SingleFree, Lifestyle.SingleStrict,
                Lifestyle.PartneredFree, Lifestyle.PartneredStrict,
            )
        }
        for (opt in options) {
            val selected = draft.lifestyle == opt
            Card(
                onClick = { onUpdate(draft.copy(lifestyle = opt)) },
                colors = if (selected) CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ) else CardDefaults.cardColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("Wizard-Lifestyle-${opt.id}"),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(opt.labelFor(draft.alignment), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
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
        Text("What's typical for you?", style = MaterialTheme.typography.titleMedium)
        Text(
            "pick 3–8 that feel typical (you can add more later)",
            style = MaterialTheme.typography.bodySmall,
        )
        // Pinned self-care chip.
        Row(verticalAlignment = ComposeAlign.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = "always-on")
            Spacer(Modifier.size(4.dp))
            Text("self-care is always on")
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
                            Text(role.label)
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
            "Which activities should I set up?",
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
                        "${role.emoji} ${role.label}",
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
        Text("How should we store your data?", style = MaterialTheme.typography.titleMedium)

        // Phone-only (default, recommended)
        Card(
            onClick = { onUpdate(draft.copy(gitChoice = GitChoice.PhoneOnly)) },
            colors = if (draft.gitChoice is GitChoice.PhoneOnly) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ) else CardDefaults.cardColors(),
            modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-PhoneOnly"),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Keep it on this phone (recommended)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "git-backed locally — full history, hand-editable, future-proof. You can add a remote later.",
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
                Text("Your own Forgejo / Gitea", style = MaterialTheme.typography.titleMedium)
                Text(
                    "if you run a Forgejo or Gitea server, plug it in here.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (selfHosted != null) {
                    OutlinedTextField(
                        value = selfHosted.instanceUrl,
                        onValueChange = {
                            onUpdate(draft.copy(gitChoice = selfHosted.copy(instanceUrl = it)))
                        },
                        label = { Text("Instance URL") },
                        modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-Forgejo-Url"),
                    )
                    OutlinedTextField(
                        value = selfHosted.oauthClientId,
                        onValueChange = {
                            onUpdate(draft.copy(gitChoice = selfHosted.copy(oauthClientId = it)))
                        },
                        label = { Text("OAuth client ID") },
                        modifier = Modifier.fillMaxWidth().testTag("Wizard-Git-Forgejo-Client"),
                    )
                    Text(
                        "Note: OAuth flow stubbed for v1; the wizard falls back to phone-only on finish.",
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
                Text("Sign in with GitHub", style = MaterialTheme.typography.titleMedium)
                Text(
                    "private repo on github.com — works fine, share with a partner later.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (draft.gitChoice is GitChoice.GitHub) {
                    Text(
                        "Note: OAuth flow stubbed for v1; the wizard falls back to phone-only on finish.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        OutlinedTextField(
            value = draft.displayName,
            onValueChange = { onUpdate(draft.copy(displayName = it)) },
            label = { Text("Calendar repo name") },
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
                ScaffoldProgress.Idle -> "Getting things ready…"
                ScaffoldProgress.Running -> "Building your calendar…"
                ScaffoldProgress.Done -> "All set."
                ScaffoldProgress.Failed -> "Something went sideways."
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (progress == ScaffoldProgress.Running || progress == ScaffoldProgress.Idle) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
        Text("your lifestyle is live.", style = MaterialTheme.typography.headlineSmall)
        Text(
            "${draft.species.label} takes it from here ;3",
            style = MaterialTheme.typography.bodyMedium,
        )
        // Live-ish preview
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("now-card preview", style = MaterialTheme.typography.labelMedium)
                Text(
                    "next up: ${chooseFirstNowCardTitle(draft)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                val praise = draft.praiseTerms.firstOrNull() ?: "good boy"
                Text("— $praise", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick = onOpen,
            modifier = Modifier.testTag("Wizard-Done-Open"),
        ) { Text("Open my calendar") }
    }
}

private fun chooseFirstNowCardTitle(draft: WizardDraft): String {
    // Prefer a self-care or workout atom; falls back to first role/template.
    val ordered = listOf(RoleId.SelfCare, RoleId.Workout, RoleId.Work).filter { it in draft.roles }
    for (role in ordered) {
        val tmpls = TemplateRegistry.templatesFor(role)
        if (tmpls.isNotEmpty()) return tmpls.first().label
    }
    return "brush teeth"
}

// --- Small helpers -------------------------------------------------------------

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
