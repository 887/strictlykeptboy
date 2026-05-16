package com.eight87.strictlykeptboy.ui.wizard.intro

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment as ComposeAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.wizard.LifestyleCard

const val TestTagIntroWizard = "IntroWizard"
const val TestTagIntroManifesto = "IntroWizard-Manifesto"
const val TestTagIntroPicker = "IntroWizard-Picker"
const val TestTagIntroPickerConfirm = "IntroWizard-Confirm"
const val TestTagIntroRichDemoRow = "IntroWizard-Card-RichDemo"
const val TestTagIntroRichDemoPill = "IntroWizard-RichDemo-Pill"

/**
 * Round 2.20 Phase C — what the picker emits.
 *
 * The legacy five-card lifestyle picker maps to [Lifestyle];
 * Round 2.20's pre-baked rich-demo repo maps to [RichDemo]. Both
 * downstream branches share the same `RepoStore.add` tail in
 * `MainActivity`, just differing in how the on-disk tree is produced
 * (WizardScaffolder vs. asset extraction).
 *
 * SOLID — sibling sealed type instead of stuffing `RichDemo` into
 * [LifestyleCard]: rich-demo is NOT a wizard-scaffolder-driven preset
 * (no alignment / lifestyle / modePick / hasPartner mapping makes
 * sense for it), so the enum that drives `DemoRepoSeeder.draftFor`
 * stays a closed set of scaffolder-compatible perspectives.
 */
sealed class DemoPerspectiveChoice {
    /** Round 2.20 — the pre-baked rich demo (extracted from APK assets). */
    data object RichDemo : DemoPerspectiveChoice()

    /** Round 2.15 — wizard-scaffolder-driven perspective. */
    data class Lifestyle(val card: LifestyleCard) : DemoPerspectiveChoice()
}

/**
 * Round 2.15 — first-launch onboarding host. Two screens only:
 *
 *  1. **Manifesto** — bat in the spotlight, one paragraph telling the user
 *     this is a calendar + todo app for kinky people. Bail-out hint.
 *  2. **Perspective picker** — Round 2.20's pre-baked rich-demo row
 *     ("Kept Life — full week") FIRST + default-selected, followed by
 *     the five Round 2.15 cards from [LifestyleCard] minus
 *     `JustCalendar` (the app is openly kink-positive — D.55 / K-1..K-7).
 *
 * On confirm: [onPerspectiveChosen] is called with the selected choice.
 * The host upstream materializes the demo repo and flips into demo mode.
 *
 * Deliberately NOT a fork of `WizardNavHost`. The long 10-step wizard is the
 * "build my first real repo" path; this is the "give me something to look
 * at" path. Keeping them separate keeps either free to evolve.
 */
@Composable
fun IntroWizardHost(
    onPerspectiveChosen: (DemoPerspectiveChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(IntroStep.Manifesto) }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().testTag(TestTagIntroWizard).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when (step) {
                    IntroStep.Manifesto -> "Step 1 of 2"
                    IntroStep.Picker -> "Step 2 of 2"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (step) {
                    IntroStep.Manifesto -> ManifestoStep()
                    IntroStep.Picker -> PerspectivePickerStep(
                        onConfirm = onPerspectiveChosen,
                        onBack = { step = IntroStep.Manifesto },
                    )
                }
            }
            if (step == IntroStep.Manifesto) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { step = IntroStep.Picker },
                        modifier = Modifier.testTag("IntroWizard-Continue"),
                    ) { Text("Continue") }
                }
            }
        }
    }
}

private enum class IntroStep { Manifesto, Picker }

@Composable
private fun ManifestoStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(TestTagIntroManifesto),
        horizontalAlignment = ComposeAlign.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.about_bat),
            contentDescription = "strictlykeptboy",
            modifier = Modifier.size(160.dp),
        )
        Text(
            "strictlykeptboy",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "A calendar + todo app for kinky people. " +
                "Routines, self-care, reminders, and a praise voice " +
                "that addresses you the way you want to be addressed.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "If you're not into that, you'll probably be happier with a plain " +
                "calendar app — there are a million of those.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Tap Continue to pick a demo perspective. Demo data is read-only — " +
                "explore the app first, then build your own calendar when you're ready.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PerspectivePickerStep(
    onConfirm: (DemoPerspectiveChoice) -> Unit,
    onBack: () -> Unit,
) {
    val lifestyleCards = listOf(
        LifestyleCard.PetKeptByAi,
        LifestyleCard.PetKeptByPartner,
        LifestyleCard.PetSelfKept,
        LifestyleCard.DomKeepingPets,
        LifestyleCard.Switch,
    )
    // Round 2.20 C.3 — default selection is RichDemo on first launch.
    var selection by remember {
        mutableStateOf<DemoPerspectiveChoice>(DemoPerspectiveChoice.RichDemo)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(TestTagIntroPicker),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Pick your demo", style = MaterialTheme.typography.titleMedium)
            Text(
                "Each perspective seeds a read-only demo repo with " +
                    "matching calendars, tasks, identity, and voice. You can switch " +
                    "demos any time from Repositories.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Rich demo row — FIRST, with Recommended pill + longer paragraph.
            val isRichSelected = selection is DemoPerspectiveChoice.RichDemo
            Card(
                onClick = { selection = DemoPerspectiveChoice.RichDemo },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTagIntroRichDemoRow),
                colors = if (isRichSelected) {
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = ComposeAlign.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "✨  Kept Life — full week",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        RecommendedPill()
                    }
                    Text(
                        "the example we ship that shows everything",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Two weeks of a UK junior dev kept by an AI dom called " +
                            "Boy Keeper — work, gym, cage rituals, owner check-ins, " +
                            "a Brighton weekend and a Berlin convention trip. Shows " +
                            "off every skb feature in one go.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // Legacy lifestyle perspectives — one-line each.
            lifestyleCards.forEach { card ->
                val selected = (selection as? DemoPerspectiveChoice.Lifestyle)?.card == card
                Card(
                    onClick = { selection = DemoPerspectiveChoice.Lifestyle(card) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("IntroWizard-Card-${card.name}"),
                    colors = if (selected) {
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    } else {
                        CardDefaults.cardColors()
                    },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "${card.emoji}  ${perspectiveTitle(card)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            perspectiveBlurb(card),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
            Button(
                onClick = { onConfirm(selection) },
                modifier = Modifier.testTag(TestTagIntroPickerConfirm),
            ) { Text("Confirm") }
        }
    }
}

@Composable
private fun RecommendedPill() {
    // semantics() with a stable contentDescription keeps the Box
    // discoverable in `onNodeWithTag` even after Compose's semantics
    // merging walks past purely-decorative Boxes.
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag(TestTagIntroRichDemoPill),
    ) {
        Text(
            "Recommended",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun perspectiveTitle(card: LifestyleCard): String = when (card) {
    LifestyleCard.PetKeptByAi -> "Pet, kept by an AI dom"
    LifestyleCard.PetKeptByPartner -> "Pet, kept by my partner"
    LifestyleCard.PetSelfKept -> "Pet, keeping myself"
    LifestyleCard.DomKeepingPets -> "I keep pet(s)"
    LifestyleCard.Switch -> "We switch"
    LifestyleCard.JustCalendar -> "Plain calendar"
}

private fun perspectiveBlurb(card: LifestyleCard): String = when (card) {
    LifestyleCard.PetKeptByAi -> "Solo — your AI dom keeps you on track."
    LifestyleCard.PetKeptByPartner -> "A human dom keeps you. Share-link to your partner."
    LifestyleCard.PetSelfKept -> "Self-discipline — no AI, no human dom."
    LifestyleCard.DomKeepingPets -> "You're the dom. Pets share calendars with you."
    LifestyleCard.Switch -> "Sometimes dom, sometimes sub — toggleable."
    LifestyleCard.JustCalendar -> "No kink framing."
}
