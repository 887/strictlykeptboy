package com.eight87.strictlykeptboy.prefs.secure

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Read-only probe over the app's REAL encrypted stores (whatever earlier builds or this build
 * wrote on the device): every file must decode to the identical map through the legacy
 * androidx.security reader and through [SecurePrefs]. Never writes.
 *
 * Optional instrumentation args pin a specific entry, e.g. a PAT entered through the UI:
 * `-e probeFile secrets_v1 -e probeKeySuffix .origin -e probeKeyPrefix pat.token. -e probeValue tok`.
 */
@RunWith(AndroidJUnit4::class)
class AppSecureFilesCompatProbe {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val args = InstrumentationRegistry.getArguments()

    private fun legacy(file: String) =
        EncryptedSharedPreferences.create(
            context,
            file,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    @Test fun everyAppStoreDecodesIdenticallyThroughBothReaders() {
        for (file in APP_FILES) {
            val old = legacy(file).all
            val current = SecurePrefs.open(context, file).all
            assertEquals("store $file", old, current)
        }
    }

    @Test fun pinnedEntryMatches() {
        val file = args.getString("probeFile") ?: return
        val prefix = args.getString("probeKeyPrefix").orEmpty()
        val expected = args.getString("probeValue")
        val hits = SecurePrefs.open(context, file).all.filterKeys { it.startsWith(prefix) }
        val legacyHits = legacy(file).all.filterKeys { it.startsWith(prefix) }
        assertEquals(legacyHits, hits)
        assertEquals("entries under $prefix in $file: ${hits.keys}", true, hits.values.contains(expected))
    }

    private companion object {
        val APP_FILES = listOf(
            "secrets_v1", "repos_v1", "age_gate_v1", "avatar_overrides_v1", "caldav_mirrors_v1",
            "demo_mode_v1", "repo_storage_v2", "system_calendar_prefs_v1",
        )
    }
}
