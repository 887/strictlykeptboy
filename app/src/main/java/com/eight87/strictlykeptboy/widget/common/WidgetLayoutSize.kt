package com.eight87.strictlykeptboy.widget.common

/**
 * Phase VV.4 / EEE.3 — pick which RemoteViews layout to inflate based
 * on the AppWidgetManager-reported min-width/min-height in dp.
 */
enum class WidgetLayoutSize {
    Size2x1,
    Size4x2,
    Size4x4;

    companion object {
        fun pick(minWidthDp: Int, minHeightDp: Int): WidgetLayoutSize {
            val w4 = minWidthDp >= 230
            val h4 = minHeightDp >= 230
            val h2 = minHeightDp >= 110
            return when {
                w4 && h4 -> Size4x4
                w4 && h2 -> Size4x2
                else -> Size2x1
            }
        }
    }
}
