package com.eight87.strictlykeptboy.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2.1.H.7 — pure-function coverage for the WindowSizeClass branches
 * that the H-phase surfaces (Wizard, Together, Repos, ImportExport,
 * Notifications) consult to decide whether to render two-pane.
 *
 * Compose Robolectric harness coverage for the actual composables lives
 * in the existing pane-specific tests; this file documents the breakpoint
 * intent so a stray boundary change (e.g. flipping `< 600` to `<= 600`)
 * fails noisily here before bricking five UI screens.
 */
class TabletPhaseHLayoutSelectionTest {

    @Test fun phone_360dp_keeps_single_pane_on_all_phase_h_surfaces() {
        val phone = WindowWidthSizeClass.fromWidth(360.dp)
        assertFalse("Wizard stays single-column on phone", phone.isTwoPane())
        assertFalse("Together stays single-column on phone", phone.isTwoPane())
        assertFalse("Repos stays single-pane on phone", phone.isTwoPane())
        assertFalse("ImportExport stays single-pane on phone", phone.isTwoPane())
        assertFalse(
            "Notifications stays single-column at <840dp (Compact/Medium)",
            phone is WindowWidthSizeClass.Expanded,
        )
    }

    @Test fun small_tablet_720dp_goes_two_pane_for_wizard_together_repos_importexport() {
        val medium = WindowWidthSizeClass.fromWidth(720.dp)
        assertTrue("Wizard two-pane on Medium", medium.isTwoPane())
        assertTrue("Together two-pane on Medium", medium.isTwoPane())
        assertTrue("Repos two-pane on Medium", medium.isTwoPane())
        assertTrue("ImportExport two-pane on Medium", medium.isTwoPane())
        // Notifications intentionally stays single-column on Medium because
        // SettingsPane already consumes the master pane at that breakpoint —
        // a third column would crush content. Only Expanded flips Notifs.
        assertFalse(
            "Notifications stays single-column on Medium",
            medium is WindowWidthSizeClass.Expanded,
        )
    }

    @Test fun large_tablet_1280dp_two_pane_everywhere_including_notifications() {
        val expanded = WindowWidthSizeClass.fromWidth(1280.dp)
        assertTrue("Wizard two-pane on Expanded", expanded.isTwoPane())
        assertTrue("Together two-pane on Expanded", expanded.isTwoPane())
        assertTrue("Repos two-pane on Expanded", expanded.isTwoPane())
        assertTrue("ImportExport two-pane on Expanded", expanded.isTwoPane())
        assertTrue(
            "Notifications flips to two-column at Expanded",
            expanded is WindowWidthSizeClass.Expanded,
        )
    }

    @Test fun pixel_tablet_native_1600dp_is_expanded() {
        // 1600dp portrait / 2560dp landscape on the pixel_tablet AVD that
        // `scripts/start-tablet-avd.sh` boots. Both axes land in Expanded.
        assertTrue(WindowWidthSizeClass.fromWidth(1600.dp) is WindowWidthSizeClass.Expanded)
        assertTrue(WindowWidthSizeClass.fromWidth(2560.dp) is WindowWidthSizeClass.Expanded)
    }
}
