package com.eight87.strictlykeptboy.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** R.5 — touch-target + spacing-multiplier scaling per window class. */
class TouchTargetScalingTest {
    @Test fun min_interactive_size_48dp_on_compact() {
        assertEquals(48.dp, AdaptiveSpacing.minInteractiveSize(WindowWidthSizeClass.Compact))
    }

    @Test fun min_interactive_size_56dp_on_medium() {
        assertEquals(56.dp, AdaptiveSpacing.minInteractiveSize(WindowWidthSizeClass.Medium))
    }

    @Test fun min_interactive_size_64dp_on_expanded() {
        assertEquals(64.dp, AdaptiveSpacing.minInteractiveSize(WindowWidthSizeClass.Expanded))
    }

    @Test fun multiplier_1x_on_compact() {
        assertEquals(1.0f, AdaptiveSpacing.multiplier(WindowWidthSizeClass.Compact), 0.0001f)
    }

    @Test fun multiplier_125x_on_medium() {
        assertEquals(1.25f, AdaptiveSpacing.multiplier(WindowWidthSizeClass.Medium), 0.0001f)
    }

    @Test fun multiplier_150x_on_expanded() {
        assertEquals(1.5f, AdaptiveSpacing.multiplier(WindowWidthSizeClass.Expanded), 0.0001f)
    }
}
