package com.eight87.strictlykeptboy.prefs.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof that [SecurePrefs] and the legacy androidx.security
 * EncryptedSharedPreferences (1.1.0-alpha06, which wrote existing users' files) are
 * interchangeable on the same file, keysets and Android Keystore master key.
 * Uses its own file name so the app's real stores are never touched.
 */
@RunWith(AndroidJUnit4::class)
class SecurePrefsLegacyCompatDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun legacy(): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            FILE,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    @Before @After fun wipe() {
        context.deleteSharedPreferences(FILE)
    }

    @Test fun legacyWritesNewReads() {
        legacy().edit().putString("pat.token", "ghp_legacy").putBoolean("flag", true).putLong("n", 7L).commit()
        val current = SecurePrefs.open(context, FILE)
        assertEquals("ghp_legacy", current.getString("pat.token", null))
        assertTrue(current.getBoolean("flag", false))
        assertEquals(7L, current.getLong("n", 0L))
    }

    @Test fun newWritesLegacyReads() {
        SecurePrefs.open(context, FILE).edit()
            .putString("pat.token", "ghp_current").putStringSet("s", setOf("x", "y")).commit()
        val old = legacy()
        assertEquals("ghp_current", old.getString("pat.token", null))
        assertEquals(setOf("x", "y"), old.getStringSet("s", null))
    }

    @Test fun freshFileCreatedByNewIsOpenedByLegacy() {
        val current = SecurePrefs.open(context, FILE)
        current.edit().putInt("i", 3).commit()
        assertEquals(3, legacy().getInt("i", 0))
        assertEquals(mapOf("i" to 3), SecurePrefs.open(context, FILE).all)
    }

    private companion object {
        const val FILE = "secure_prefs_device_compat_test"
    }
}
