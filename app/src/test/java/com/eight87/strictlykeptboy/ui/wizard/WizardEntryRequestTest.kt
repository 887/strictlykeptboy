package com.eight87.strictlykeptboy.ui.wizard

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.composition.AppGraph
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2.1.I.2 — `wizardEntryRequest` is the signal from Settings →
 * Lifestyle (and any future surface) to the shell, asking the shell
 * to switch to `TopDestination.Wizard` and pre-position the host at
 * the named screen. SkbAppShell observes via `collectAsState` and a
 * `LaunchedEffect`. Verified at the data-flow level here; the shell
 * routing is covered by manual AVD smoke.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class WizardEntryRequestTest {

    @Test fun `setting wizardEntryRequest publishes the requested screen`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val graph = AppGraph(ctx)
        assertEquals(null, graph.wizardEntryRequest.value)
        graph.setWizardEntryRequest(WizardScreen.Roles)
        assertEquals(WizardScreen.Roles, graph.wizardEntryRequest.value)
        // Reset.
        graph.clearWizardEntryRequest()
        assertEquals(null, graph.wizardEntryRequest.value)
    }

    @Test fun `share-with-dom predicate is strict on alignment plus mode`() {
        // Sub + kept-by-human → show.
        val subHuman = WizardDraft(
            alignment = Alignment.Submissive,
            modePick = WizardModePick.KeptByHuman,
        )
        assertEquals(true, shouldShowShareWithDom(subHuman))

        // Sub + kept-by-AI → no share screen (the AI dom is local).
        val subAi = WizardDraft(
            alignment = Alignment.Submissive,
            modePick = WizardModePick.KeptByAi,
        )
        assertEquals(false, shouldShowShareWithDom(subAi))

        // Dominant + kept-by-human → no (Dominant doesn't share-to-receive).
        val domHuman = WizardDraft(
            alignment = Alignment.Dominant,
            modePick = WizardModePick.KeptByHuman,
        )
        assertEquals(false, shouldShowShareWithDom(domHuman))

        // Switch + kept-by-human → show (switch can also be sub).
        val switchHuman = WizardDraft(
            alignment = Alignment.Switch,
            modePick = WizardModePick.KeptByHuman,
        )
        assertEquals(true, shouldShowShareWithDom(switchHuman))
    }
}
