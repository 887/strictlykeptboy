package com.eight87.strictlykeptboy.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * R.1 — pure-function tests for the breakpoint resolver. No Robolectric
 * needed because [WindowWidthSizeClass.fromWidth] is a stateless map
 * from `Dp` to the sealed type.
 */
class WindowSizeClassDetectionTest {
    @Test fun compact_below_600dp() {
        assertSame(WindowWidthSizeClass.Compact, WindowWidthSizeClass.fromWidth(360.dp))
        assertSame(WindowWidthSizeClass.Compact, WindowWidthSizeClass.fromWidth(599.dp))
    }

    @Test fun medium_600_to_839dp() {
        assertSame(WindowWidthSizeClass.Medium, WindowWidthSizeClass.fromWidth(600.dp))
        assertSame(WindowWidthSizeClass.Medium, WindowWidthSizeClass.fromWidth(720.dp))
        assertSame(WindowWidthSizeClass.Medium, WindowWidthSizeClass.fromWidth(839.dp))
    }

    @Test fun expanded_at_or_above_840dp() {
        assertSame(WindowWidthSizeClass.Expanded, WindowWidthSizeClass.fromWidth(840.dp))
        assertSame(WindowWidthSizeClass.Expanded, WindowWidthSizeClass.fromWidth(1200.dp))
    }

    @Test fun isTwoPane_only_medium_and_expanded() {
        assert(!WindowWidthSizeClass.Compact.isTwoPane())
        assert(WindowWidthSizeClass.Medium.isTwoPane())
        assert(WindowWidthSizeClass.Expanded.isTwoPane())
    }
}
