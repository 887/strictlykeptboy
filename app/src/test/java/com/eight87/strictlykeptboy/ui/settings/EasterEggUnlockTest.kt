package com.eight87.strictlykeptboy.ui.settings

import com.eight87.strictlykeptboy.ui.settings.categories.EasterEggController
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase F46 cleanup — was a Compose UI test referencing
 * `ABOUT_EASTER_EGG_*` compat-stub constants (7-tap / 2s window) that
 * never matched the live [EasterEggController] (3-tap / 5s window).
 *
 * Rewritten to drive the controller directly with a synthetic clock —
 * the controller is framework-free, so we don't need Robolectric +
 * compose-rule to exercise it. The about-card tap wiring is already
 * covered by the surrounding settings/category tests.
 */
class EasterEggUnlockTest {
    @Test fun `three rapid taps reveal`() {
        val ctrl = EasterEggController()
        assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, ctrl.tap(1_000L))
        assertEquals(EasterEggController.Outcome.SecondPromptSnackbar, ctrl.tap(1_100L))
        assertEquals(EasterEggController.Outcome.Reveal, ctrl.tap(1_200L))
    }

    @Test fun `two taps do not reveal`() {
        val ctrl = EasterEggController()
        ctrl.tap(1_000L)
        val out = ctrl.tap(1_100L)
        assertEquals(EasterEggController.Outcome.SecondPromptSnackbar, out)
    }

    @Test fun `gap longer than window resets counter`() {
        val ctrl = EasterEggController()
        ctrl.tap(1_000L)
        ctrl.tap(1_100L)
        // Big gap — past DEFAULT_WINDOW_MILLIS (5_000L). Next tap should
        // be treated as the FIRST tap of a fresh sequence.
        val out = ctrl.tap(1_100L + EasterEggController.DEFAULT_WINDOW_MILLIS + 1L)
        assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, out)
    }

    @Test fun `reveal resets counter so next tap is first prompt again`() {
        val ctrl = EasterEggController()
        ctrl.tap(1_000L)
        ctrl.tap(1_100L)
        ctrl.tap(1_200L) // Reveal
        // After Reveal the controller resets — the next tap should be
        // treated as a fresh first-prompt.
        assertEquals(EasterEggController.Outcome.FirstPromptSnackbar, ctrl.tap(1_300L))
    }
}
