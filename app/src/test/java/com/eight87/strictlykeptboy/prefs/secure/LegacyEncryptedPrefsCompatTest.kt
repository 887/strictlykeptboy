package com.eight87.strictlykeptboy.prefs.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.Aead
import com.google.crypto.tink.DeterministicAead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.Parameters
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.daead.DeterministicAeadConfig
import com.google.crypto.tink.daead.PredefinedDeterministicAeadParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cross-checks [TinkEncryptedSharedPreferences] against the real legacy androidx.security
 * EncryptedSharedPreferences (1.1.0-alpha06, the build that wrote existing users' files),
 * both sharing one backing file and one pair of keysets: everything the legacy class wrote
 * must read back through the new one, and vice versa.
 *
 * Robolectric has no AndroidKeyStore, so the legacy instance is built through its
 * package-private constructor with in-memory keysets instead of `create(...)`; the
 * Keystore-wrapped path is covered on-device by `SecurePrefsLegacyCompatDeviceTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class LegacyEncryptedPrefsCompatTest {
    private lateinit var backing: SharedPreferences
    private lateinit var legacy: SharedPreferences
    private lateinit var current: SharedPreferences

    @Before fun setUp() {
        DeterministicAeadConfig.register()
        AeadConfig.register()
        val daead = newKeyset(PredefinedDeterministicAeadParameters.AES256_SIV)
            .getPrimitive(RegistryConfiguration.get(), DeterministicAead::class.java)
        val aead = newKeyset(PredefinedAeadParameters.AES256_GCM)
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        backing = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        backing.edit().clear().commit()
        legacy = EncryptedSharedPreferences::class.java
            .getDeclaredConstructor(
                String::class.java,
                String::class.java,
                SharedPreferences::class.java,
                Aead::class.java,
                DeterministicAead::class.java,
            )
            .apply { isAccessible = true }
            .newInstance(FILE, "_androidx_security_master_key_", backing, aead, daead)
        current = TinkEncryptedSharedPreferences(backing, EncryptedPrefsCodec(daead, aead, FILE))
    }

    private fun writeAll(p: SharedPreferences) {
        p.edit()
            .putString("pat.token.repo.origin", "ghp_ünïcødé-🐈")
            .putString("empty", "")
            .putStringSet("set", setOf("a", "b", "ünï"))
            .putInt("int", -42)
            .putLong("long", Long.MIN_VALUE)
            .putFloat("float", 3.25f)
            .putBoolean("bool", true)
            .putString("nullString", null)
            .commit()
    }

    private fun assertReadsAll(p: SharedPreferences) {
        assertEquals("ghp_ünïcødé-🐈", p.getString("pat.token.repo.origin", null))
        assertEquals("", p.getString("empty", "default"))
        assertEquals(setOf("a", "b", "ünï"), p.getStringSet("set", null))
        assertEquals(-42, p.getInt("int", 0))
        assertEquals(Long.MIN_VALUE, p.getLong("long", 0))
        assertEquals(3.25f, p.getFloat("float", 0f), 0f)
        assertTrue(p.getBoolean("bool", false))
        assertNull(p.getString("nullString", null))
        assertTrue(p.contains("int"))
        assertFalse(p.contains("missing"))
        assertEquals("fallback", p.getString("int", "fallback"))
        assertEquals(
            mapOf(
                "pat.token.repo.origin" to "ghp_ünïcødé-🐈",
                "empty" to "",
                "set" to setOf("a", "b", "ünï"),
                "int" to -42,
                "long" to Long.MIN_VALUE,
                "float" to 3.25f,
                "bool" to true,
                "nullString" to null,
            ),
            p.all,
        )
    }

    private fun newKeyset(params: Parameters): KeysetHandle =
        KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.generateEntryFromParameters(params).withRandomId().makePrimary())
            .build()

    @Test fun legacyWrittenFileReadsThroughCurrent() {
        writeAll(legacy)
        assertReadsAll(current)
    }

    @Test fun currentWrittenFileReadsThroughLegacy() {
        writeAll(current)
        assertReadsAll(legacy)
    }

    @Test fun bothWritersProduceTheSameStorageKeys() {
        writeAll(legacy)
        val legacyKeys = backing.all.keys.toSet()
        backing.edit().clear().commit()
        writeAll(current)
        assertEquals(legacyKeys, backing.all.keys.toSet())
    }

    @Test fun removeAndClearInteroperate() {
        writeAll(legacy)
        current.edit().remove("int").commit()
        assertFalse(legacy.contains("int"))
        current.edit().clear().putBoolean("kept", true).commit()
        assertEquals(mapOf("kept" to true), legacy.all)
    }

    @Test fun listenersSeeChangedKeys() {
        val seen = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> seen += key }
        current.registerOnSharedPreferenceChangeListener(listener)
        current.edit().putInt("a", 1).remove("b").commit()
        current.unregisterOnSharedPreferenceChangeListener(listener)
        current.edit().putInt("c", 1).apply()
        assertEquals(listOf("a", "b"), seen)
    }

    @Test fun emptySetReadsAsDefaultLikeLegacy() {
        current.edit().putStringSet("s", emptySet()).commit()
        assertEquals(setOf("d"), current.getStringSet("s", setOf("d")))
        assertEquals(setOf("d"), legacy.getStringSet("s", setOf("d")))
    }

    @Test fun reservedKeysetNamesAreRefused() {
        val threw = runCatching {
            current.getString(EncryptedPrefsCodec.KEY_KEYSET_PREF, null)
        }.exceptionOrNull()
        assertTrue(threw is SecurityException)
    }

    @Test fun tamperedValueReadsAsDefault() {
        current.edit().putString("k", "v").commit()
        val storageKey = backing.all.keys.single()
        backing.edit().putString(storageKey, "AAAA").commit()
        assertEquals("d", current.getString("k", "d"))
    }

    private companion object {
        const val FILE = "secrets_v1_compat_test"
    }
}
