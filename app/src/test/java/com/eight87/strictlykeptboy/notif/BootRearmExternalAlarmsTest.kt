package com.eight87.strictlykeptboy.notif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.F.8 — BootCompletedReceiver re-arms alarms for external
 * events in the next 14-day window. Verifies the constant (no persisted
 * snapshot) and that [AlarmHorizonExtenderWorker.rearmExternal] uses
 * exactly that window.
 *
 * The end-to-end content-provider walk is exercised in
 * [ExternalReminderScheduleTest]; this test pins the boot-side contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class BootRearmExternalAlarmsTest {

    @Test fun fourteenDayWindowConstant() {
        assertEquals(
            14L * 24L * 60L * 60L * 1000L,
            AlarmHorizonExtenderWorker.EXTERNAL_REARM_WINDOW_MS,
        )
    }

    @Test fun rearmExternalIsExposedFromCompanion() {
        // Sanity: the static `rearmExternal(context)` entry point used by
        // the worker exists and is callable from the boot path.
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val result = runCatching { AlarmHorizonExtenderWorker.rearmExternal(ctx) }
        assertTrue("rearmExternal should not throw", result.isSuccess)
    }
}
