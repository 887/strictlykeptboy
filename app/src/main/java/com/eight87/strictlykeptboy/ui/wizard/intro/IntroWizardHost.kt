package com.eight87.strictlykeptboy.ui.wizard.intro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.R
import com.eight87.strictlykeptboy.ui.wizard.LifestyleCard

const val TestTagIntroWizard = "IntroWizard"
const val TestTagIntroManifesto = "IntroWizard-Manifesto"
const val TestTagIntroPicker = "IntroWizard-Picker"

/**
 * Round 2.15 — first-launch onboarding host. Two screens only:
 *
 *  1. **Manifesto** — bat in the spotlight, one paragraph telling the user
 *     this is a calendar + todo app for kinky people. Bail-out hint.
 *  2. **Perspective picker** — five cards from [LifestyleCard], minus
 *     `JustCalendar` (the app is openly kink-positive — D.55 / K-1..K-7).
 *
 * On pick: [onPerspectiveChosen] is called with the selected card. The host
 * upstream materializes the demo repo and flips into demo mode.
 *
 * Deliberately NOT a fork of `WizardNavHost`. The long 10-step wizard is the
 * "build my first real repo" path; this is the "give me something to look
 * at" path. Keeping them separate keeps either free to evolve.
 */
@Composable
fun IntroWizardHost(
    onPerspectiveChosen: (LifestyleCard) -> Unit,
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
                        onPick = onPerspectiveChosen,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step == IntroStep.Picker) {
                    TextButton(onClick = { step = IntroStep.Manifesto }) {
                        Text("Back")
                    }
                }
                if (step == IntroStep.Manifesto) {
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
private fun PerspectivePickerStep(onPick: (LifestyleCard) -> Unit) {
    val cards = listOf(
        LifestyleCard.PetKeptByAi,
        LifestyleCard.PetKeptByPartner,
        LifestyleCard.PetSelfKept,
        LifestyleCard.DomKeepingPets,
        LifestyleCard.Switch,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(TestTagIntroPicker),
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
        cards.forEach { card ->
            Card(
                onClick = { onPick(card) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("IntroWizard-Card-${card.name}"),
                colors = CardDefaults.cardColors(),
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
