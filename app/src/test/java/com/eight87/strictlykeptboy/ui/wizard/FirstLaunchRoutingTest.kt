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
}
