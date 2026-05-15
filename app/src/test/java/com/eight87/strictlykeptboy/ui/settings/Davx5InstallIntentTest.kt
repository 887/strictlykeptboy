package com.eight87.strictlykeptboy.ui.settings

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.H — verify the DAVx5 install intent factory yields the
 * F-Droid URL when an F-Droid client is present, Play Store URL
 * otherwise. Both intents must be `ACTION_VIEW` with a parseable URI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Davx5InstallIntentTest {

    @Test fun fdroidClientPresent_buildsFdroidIntent() {
        val intent = ExternalAccountsIntentFactory.installDavx5Intent(
            fdroidClientPresent = true,
        )
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertNotNull(intent.data)
        assertEquals(
            ExternalAccountsIntentFactory.URL_FDROID_DAVX5,
            intent.data.toString(),
        )
        assertTrue(
            "expected NEW_TASK flag",
            (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
        )
    }

    @Test fun noFdroidClient_buildsPlayStoreIntent() {
        val intent = ExternalAccountsIntentFactory.installDavx5Intent(
            fdroidClientPresent = false,
        )
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(
            ExternalAccountsIntentFactory.URL_PLAY_DAVX5,
            intent.data.toString(),
        )
    }
}
