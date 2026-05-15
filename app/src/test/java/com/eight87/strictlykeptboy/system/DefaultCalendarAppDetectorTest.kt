package com.eight87.strictlykeptboy.system

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Round 2.18 Phase I — verifies the "am I the default calendar app?"
 * probe routes through `PackageManager.resolveActivity` with the
 * MATCH_DEFAULT_ONLY flag and compares the resolved package to ours.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DefaultCalendarAppDetectorTest {

    private val intent = Intent(Intent.ACTION_VIEW).apply { type = "text/calendar" }

    @Test fun returns_true_when_resolved_package_matches_our_package() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = ctx.packageName
                name = "DummyActivity"
            }
        }
        Shadows.shadowOf(ctx.packageManager).addResolveInfoForIntent(intent, info)

        assertTrue(DefaultCalendarAppDetector.isDefaultCalendarApp(ctx))
    }

    @Test fun returns_false_when_resolved_package_is_a_different_app() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.example.somecalendar"
                name = "DummyActivity"
            }
        }
        Shadows.shadowOf(ctx.packageManager).addResolveInfoForIntent(intent, info)

        assertFalse(DefaultCalendarAppDetector.isDefaultCalendarApp(ctx))
    }

    @Test fun returns_false_when_no_resolver_matches() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        // No resolve info registered.
        assertFalse(DefaultCalendarAppDetector.isDefaultCalendarApp(ctx))
    }
}
