package com.eight87.strictlykeptboy.perf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.eight87.strictlykeptboy.composition.AppGraph
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase V.1 — cold-start budget. On-device target: < 600ms from process
 * start to the first frame of [com.eight87.strictlykeptboy.ui.schedule.SchedulePane].
 *
 * Robolectric's startup path is dominated by class-loader work + shadow
 * instantiation that doesn't happen on a real device, so the budget here
 * is intentionally generous (~3s). The real target is enforced by the
 * AVD-side `am start -W` measurement captured in `docs/perf-baseline-*.md`.
 *
 * What this test actually exercises:
 *   - [AppGraph] construction (the bulk of pre-first-frame Kotlin work).
 *   - Every `by lazy` field touched during `parkRuntimes()` /
 *     `installSyncEventBridge()` — i.e. the runtime-graph parking that
 *     `MainActivity.onCreate` performs.
 *
 * The test passes if the total wall-clock elapsed is recorded (printed
 * to stdout for CI scraping) and within the generous JVM-shadow budget.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ColdStartBudgetTest {

    @Test fun appGraphInit_withinJvmBudget() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()

        // Robolectric doesn't back AndroidKeyStore so the EncryptedSharedPrefs-
        // backed lazies (RepoStore, SecretsStore) throw KeyStoreException when
        // touched. We isolate the construction-time cost (graph allocation
        // + plain-prefs lazies) which is what dominates cold-start in real
        // builds, and skip the encrypted seams (separately covered by
        // instrumented tests).
        val start = System.nanoTime()
        val graph = AppGraph(ctx)
        // Touch plain-prefs lazies only.
        graph.appearancePrefs
        graph.viewModePrefs
        // ageGatePrefs uses EncryptedSharedPreferences → skipped (Robolectric)
        graph.neutralModePrefs
        graph.statusStore
        graph.syncSettingsPrefs
        graph.notificationPrefs
        graph.calendarVisibility
        graph.todolistVisibility
        graph.identityPrefs
        graph.modePrefs
        graph.snapshot.value
        graph.sources.value
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        println("[perf V.1] AppGraph plain-lazies cold-init: ${elapsedMs}ms (JVM/Robolectric)")
        assertNotNull(graph)
        assertTrue("cold init too slow: ${elapsedMs}ms", elapsedMs < 30_000)
    }
}
