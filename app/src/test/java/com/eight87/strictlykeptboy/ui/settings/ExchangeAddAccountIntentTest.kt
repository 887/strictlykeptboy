package com.eight87.strictlykeptboy.ui.settings

import android.provider.Settings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Round 2.18.H — verify the "Add Exchange account" intent is
 * `Settings.ACTION_ADD_ACCOUNT` with the correct
 * `EXTRA_ACCOUNT_TYPES` filter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExchangeAddAccountIntentTest {

    @Test fun addExchangeAccountIntent_filtersToExchangeOnly() {
        val intent = ExternalAccountsIntentFactory.addExchangeAccountIntent()
        assertEquals(Settings.ACTION_ADD_ACCOUNT, intent.action)
        val types = intent.getStringArrayExtra(Settings.EXTRA_ACCOUNT_TYPES)
        assertNotNull(types)
        assertArrayEquals(arrayOf("com.android.exchange"), types)
    }

    @Test fun syncSettingsIntent_hasCorrectAction() {
        val intent = ExternalAccountsIntentFactory.openSyncSettingsIntent()
        assertEquals(Settings.ACTION_SYNC_SETTINGS, intent.action)
    }
}
