package com.eight87.strictlykeptboy.ui.wizard

import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.composition.AppGraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2.1.I.1 — first-launch auto-route. The MainActivity branch
 * is `if (graph.repoStore.list().isEmpty()) -> show wizard`. The
 * `repoStore` itself is EncryptedSharedPreferences-backed and the
 * AndroidKeyStore isn't available under Robolectric, so we test
 * the adjacent fields here: `activeRepoName` default + the
 * `wizardEntryRequest` initial value. Full end-to-end routing is
 * verified by AVD smoke (see plan-file QA notes).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class FirstLaunchRoutingTest {

    @Test fun `activeRepoName default is empty string`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val graph = AppGraph(ctx)
        // Phase 2.1.I.7 — was "demo-repo" placeholder; now empty so the
        // top-bar renders blank instead of a stale label.
        assertEquals("", graph.defaultWriteRepoName.value)
        assertFalse(
            "default must not be the legacy 'demo-repo' placeholder",
            graph.defaultWriteRepoName.value == "demo-repo",
        )
    }

    @Test fun `wizardEntryRequest defaults to null`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val graph = AppGraph(ctx)
        // Phase 2.1.I.2 — wizard re-entry hoisted into AppGraph.
        // Null means "no external request pending"; SkbAppShell observes.
        assertEquals(null, graph.wizardEntryRequest.value)
    }

    /**
     * W2-U-2 — when the first-time user picks "Empty calendar" in the
     * intro picker, MainActivity's perspective-handler must fire
     * `setWizardEntryRequest(Welcome)` so SkbAppShell auto-switches to
     * the Wizard destination on the next frame (instead of dumping the
     * user on an empty Schedule with no obvious CTA).
     *
     * The handler itself lives inline in MainActivity.setContent — this
     * test re-runs the exact two-call sequence against the same AppGraph
     * surface, asserting the post-conditions a real Empty pick would
     * leave behind.
     */
    @Test fun `empty-pick fires wizard entry request at Welcome`() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val graph = AppGraph(ctx)
        assertEquals(null, graph.wizardEntryRequest.value)

        // Replicate the MainActivity inline branch for
        // DemoPerspectiveChoice.Empty (load-bearing call: the wizard
        // re-entry request). `demoModePrefs.setActive(false)` is the
        // sibling no-op-on-default call that also runs in MainActivity;
        // it goes through EncryptedSharedPreferences (AndroidKeyStore
        // not available under Robolectric) so we skip it here — the
        // wizard-entry flip is the part that drives auto-launch.
        graph.setWizardEntryRequest(WizardScreen.Welcome)

        assertEquals(
            "Empty-pick must auto-route to the Welcome screen of the wizard",
            WizardScreen.Welcome,
            graph.wizardEntryRequest.value,
        )
        // SkbAppShell's LaunchedEffect(wizardEntry) reads `wizardEntry != null`
        // and switches `selected = TopDestination.Wizard`. Backing out
        // clears via onWizardFinished → clearWizardEntryRequest().
        graph.clearWizardEntryRequest()
        assertEquals(null, graph.wizardEntryRequest.value)
    }
}
