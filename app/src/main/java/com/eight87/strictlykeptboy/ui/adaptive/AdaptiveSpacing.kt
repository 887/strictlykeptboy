package com.eight87.strictlykeptboy.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eight87.strictlykeptboy.theme.LocalDensityScale
import com.eight87.strictlykeptboy.theme.scaled

/**
 * Phase R.5 — tablet-specific touch targets + spacing scale.
 *
 * Composes on top of the existing Phase F.3 [LocalDensityScale]
 * CompositionLocal. The user-chosen density multiplier still applies;
 * we layer the window-class multiplier *over* it so a Spacious tablet
 * gets the most generous spacing and a Compact phone the tightest.
 *
 * Multipliers per the R.5 brief:
 * - Compact:  1.00× (phone default)
 * - Medium:   1.25× (small tablet / foldable)
 * - Expanded: 1.50× (large tablet)
 *
 * Touch targets (per Material guidance for accessibility on big-screen
 * devices — finger-with-glove ergonomics on a couch tablet):
 * - Compact:  48 dp (Material default)
 * - Medium:   56 dp
 * - Expanded: 64 dp
 */
object AdaptiveSpacing {
    fun multiplier(widthClass: WindowWidthSizeClass): Float = when (widthClass) {
        WindowWidthSizeClass.Compact -> 1.0f
        WindowWidthSizeClass.Medium -> 1.25f
        WindowWidthSizeClass.Expanded -> 1.5f
    }

    fun minInteractiveSize(widthClass: WindowWidthSizeClass): Dp = when (widthClass) {
        WindowWidthSizeClass.Compact -> 48.dp
        WindowWidthSizeClass.Medium -> 56.dp
        WindowWidthSizeClass.Expanded -> 64.dp
    }
}

/**
 * Returns [base] scaled by both the density preference *and* the
 * ambient [LocalWindowWidthSizeClass] multiplier.
 */
@Composable
@ReadOnlyComposable
fun adaptiveDp(base: Dp): Dp {
    val widthClass = LocalWindowWidthSizeClass.current
    val densityScale = LocalDensityScale.current
    val widthMul = AdaptiveSpacing.multiplier(widthClass)
    return (base.scaled(densityScale).value * widthMul).dp
}

/** Convenience for composables that need the current min interactive target. */
@Composable
@ReadOnlyComposable
fun minInteractiveSize(): Dp =
    AdaptiveSpacing.minInteractiveSize(LocalWindowWidthSizeClass.current)
