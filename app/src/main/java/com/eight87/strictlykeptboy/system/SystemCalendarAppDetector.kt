package com.eight87.strictlykeptboy.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Round 2.18.F.7 — detect installed candidate "system calendar" apps so
 * the Settings → External calendars → "Suppress system notifications"
 * surface can list exactly the apps the user can mute on this device.
 *
 * Detection strategy:
 *  - Probe a small list of well-known calendar package names with
 *    [PackageManager.getApplicationInfo]. A best-effort allowlist —
 *    Android doesn't expose a stable "is calendar app" category, and
 *    `queryIntentActivities(ACTION_MAIN)` over every calendar candidate
 *    pulls back too many false positives.
 *  - For each present package, build an [Intent] pointing at
 *    `Settings.ACTION_APP_NOTIFICATION_SETTINGS` so a tap in the row
 *    opens that app's notification-settings page directly.
 *
 * SOLID-S: pure detector + intent factory. UI lives in
 * [com.eight87.strictlykeptboy.ui.settings.ExternalCalendarsScreen].
 */
object SystemCalendarAppDetector {

    /** Known calendar-app package candidates. Order = display order. */
    val KNOWN_CANDIDATES: List<Candidate> = listOf(
        Candidate("com.google.android.calendar", "Google Calendar"),
        Candidate("com.microsoft.office.outlook", "Outlook"),
        Candidate("com.samsung.android.calendar", "Samsung Calendar"),
        Candidate("ws.xsoh.etar", "Etar"),
        Candidate("com.boltapps.businesscalendar", "Business Calendar"),
        Candidate("com.boltapps.businesscalendar2", "Business Calendar 2"),
        Candidate("com.android.calendar", "AOSP Calendar"),
        Candidate("com.huawei.calendar", "Huawei Calendar"),
        Candidate("com.miui.calendar", "Mi Calendar"),
        Candidate("net.dgrcode.android.simplecal", "Simple Calendar"),
        Candidate("com.simplemobiletools.calendar.pro", "Simple Calendar Pro"),
        Candidate("at.bitfire.davdroid", "DAVx⁵"),
    )

    /**
     * Return the subset of [KNOWN_CANDIDATES] currently installed on the
     * device.
     */
    fun detectInstalled(
        context: Context,
        candidates: List<Candidate> = KNOWN_CANDIDATES,
    ): List<InstalledCandidate> {
        val pm = context.packageManager
        val out = mutableListOf<InstalledCandidate>()
        for (cand in candidates) {
            val label = readLabel(pm, cand.packageName) ?: continue
            out += InstalledCandidate(
                packageName = cand.packageName,
                displayName = label.ifBlank { cand.displayName },
                fallbackLabel = cand.displayName,
            )
        }
        return out
    }

    private fun readLabel(pm: PackageManager, pkg: String): String? = try {
        val ai = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(ai).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: Throwable) {
        null
    }

    /**
     * Build an `ACTION_APP_NOTIFICATION_SETTINGS` intent for [packageName].
     * The caller adds `FLAG_ACTIVITY_NEW_TASK` if launching outside an
     * Activity context.
     */
    fun appNotificationSettingsIntent(packageName: String): Intent =
        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)

    data class Candidate(val packageName: String, val displayName: String)

    data class InstalledCandidate(
        val packageName: String,
        /** Resolved via PM, falls back to [fallbackLabel] when empty. */
        val displayName: String,
        val fallbackLabel: String,
    )
}
