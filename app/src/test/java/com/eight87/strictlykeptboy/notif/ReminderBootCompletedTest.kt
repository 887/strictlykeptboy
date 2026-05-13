package com.eight87.strictlykeptboy.notif

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2.1.F.8 — BootCompletedReceiver decides whether to kick the
 * re-arm worker based on the broadcast action. The decision logic is
 * the load-bearing part; the actual WorkManager dispatch is verified
 * by the AVD smoke test.
 *
 * The receiver wraps the WorkManager kick in
 * [BootCompletedReceiver.kickRearm]; this test substitutes that path
 * via [BootCompletedReceiver.kickForTest] so we can observe the
 * dispatch decision without spinning up WorkManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class ReminderBootCompletedTest {

    private var kicked = 0

    @Before fun setUp() {
        kicked = 0
        BootCompletedReceiver.kickForTest = { kicked++ }
    }

    @After fun tearDown() {
        BootCompletedReceiver.kickForTest = null
    }

    private fun fire(action: String) {
        val receiver = BootCompletedReceiver()
        receiver.onReceive(ApplicationProvider.getApplicationContext(), Intent(action))
    }

    @Test fun bootCompletedDispatchesKick() {
        fire(Intent.ACTION_BOOT_COMPLETED)
        assertEquals(1, kicked)
    }

    @Test fun packageReplacedDispatchesKick() {
        fire(Intent.ACTION_MY_PACKAGE_REPLACED)
        assertEquals(1, kicked)
    }

    @Test fun unrelatedActionIsIgnored() {
        fire("android.intent.action.SOMETHING_ELSE")
        assertEquals(0, kicked)
    }
}
