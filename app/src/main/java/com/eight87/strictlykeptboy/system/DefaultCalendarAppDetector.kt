package com.eight87.strictlykeptboy.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Round 2.18 Phase I — "am I the default calendar app?" detector.
 *
 * Android has no `RoleManager.ROLE_CALENDAR`. The closest signal that
 * skb is the user's default calendar app is whether the system would
 * route a `text/calendar` (`.ics`) ACTION_VIEW intent at us without
 * showing a chooser. We probe the resolver with
 * `PackageManager.MATCH_DEFAULT_ONLY` and compare the resolved package
 * to our own.
 *
 * Caveats:
 *  - Returns `false` when the user has multiple candidate apps but has
 *    not yet picked "Always" — Android resolves to the chooser, not to
 *    us, so this signal is conservative (false-negative friendly).
 *  - Returns `true` if skb is the *only* installed app that handles
 *    `text/calendar`, even before the user has consciously picked
 *    skb — that's still effectively "you're the default" so we treat
 *    it as the truth.
 *  - Pre-API 33 vs. post-API 33 behaviour of MATCH_DEFAULT_ONLY is
 *    consistent enough for this surface; we don't need the
 *    PackageManager.ResolveInfoFlags overload.
 */
object DefaultCalendarAppDetector {

    /** True iff resolving an `.ics` ACTION_VIEW points at our package. */
    fun isDefaultCalendarApp(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply { type = "text/calendar" }
        val resolved = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull() ?: return false
        return resolved.activityInfo?.packageName == context.packageName
    }
}
