package com.eight87.strictlykeptboy.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase R.1 — width-driven adaptive breakpoint.
 *
 * Modelled as a sealed hierarchy (R.X.2) so consumers branch with an
 * exhaustive `when` rather than chained width comparisons. Phone, small
 * tablet/foldable, and large tablet/desktop are distinct cases with
 * different layout intent (single-pane sheet, two-pane Row, two-pane Row
 * with wider gutter), not a numeric continuum.
 *
 * Breakpoints (see `docs/plans/ui-spec.md` UI-R.1):
 * - Compact:   width  < 600 dp
 * - Medium:    600 dp ≤ width < 840 dp
 * - Expanded:  width ≥ 840 dp
 */
sealed interface WindowWidthSizeClass {
    /** Phone, foldable in folded posture. Single-pane layouts; sheets for detail. */
    object Compact : WindowWidthSizeClass

    /** Small tablet / unfolded foldable. Two-pane master-detail with default gutter. */
    object Medium : WindowWidthSizeClass

    /** Large tablet / desktop window. Two-pane master-detail with wider gutter. */
    object Expanded : WindowWidthSizeClass

    companion object {
        fun fromWidth(width: Dp): WindowWidthSizeClass = when {
            width < 600.dp -> Compact
            width < 840.dp -> Medium
            else -> Expanded
        }
    }
}

/** True iff this class wants a two-pane layout (R.2 / R.3 / R.4). */
fun WindowWidthSizeClass.isTwoPane(): Boolean = this !is WindowWidthSizeClass.Compact

/**
 * CompositionLocal exposing the current width class to deep children
 * without threading the value through every composable signature
 * (R.X.7 — narrow data, no god-state).
 */
val LocalWindowWidthSizeClass = compositionLocalOf<WindowWidthSizeClass> {
    WindowWidthSizeClass.Compact
}

/**
 * Wraps [content] in a `BoxWithConstraints` that measures the available
 * width and republishes [LocalWindowWidthSizeClass]. Cheap — one
 * BoxWithConstraints at the app root; no new dependency required (we
 * avoid pulling in `androidx.compose.material3.adaptive` which only
 * exists as alpha on the BoM we're pinned to).
 */
@Composable
fun ProvideWindowSizeClass(
    modifier: Modifier = Modifier,
    content: @Composable (WindowWidthSizeClass) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val widthClass = WindowWidthSizeClass.fromWidth(maxWidth)
        CompositionLocalProvider(LocalWindowWidthSizeClass provides widthClass) {
            content(widthClass)
        }
    }
}
