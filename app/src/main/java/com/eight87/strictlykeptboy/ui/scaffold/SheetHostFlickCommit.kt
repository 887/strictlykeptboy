package com.eight87.strictlykeptboy.ui.scaffold

/**
 * Round 2.16.G — pure flick-commit decision extracted from
 * [NowPlayingSheetHost]'s drag-settle handler in [SkbAppShell].
 *
 * Given the sheet's progress at drag-start, its progress at drag-end,
 * and the flick threshold (5% of sheet travel = 0.05f, per the
 * tonearmboy port), return the commit target the sheet should
 * animate to:
 *
 *  - decisive upward flick (moved > +threshold) → 1f (open)
 *  - decisive downward flick (moved < -threshold) → 0f (close)
 *  - otherwise position-fallback: end >= 0.5f → 1f, else 0f
 *
 * Kept package-internal — this is a Round 2.16 implementation detail
 * exposed only for unit testing the commit math without spinning up
 * the full draggable + nested-scroll integration in Robolectric.
 */
internal fun flickCommitTarget(
    start: Float,
    end: Float,
    threshold: Float = 0.05f,
): Float {
    val moved = end - start
    return when {
        moved > threshold -> 1f
        moved < -threshold -> 0f
        else -> if (end >= 0.5f) 1f else 0f
    }
}
